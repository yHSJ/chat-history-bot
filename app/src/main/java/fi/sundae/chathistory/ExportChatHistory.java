package fi.sundae.chathistory;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.opencsv.CSVWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportChatHistory extends SlashCommand {
  private static final Logger LOG = LoggerFactory.getLogger(ExportChatHistory.class);

  private static final int MAX_PART_BYTES = 8 * 1024 * 1024;
  // Interaction tokens expire 15 minutes after the command is invoked.
  private static final Duration INTERACTION_WINDOW = Duration.ofMinutes(14);
  private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(15);
  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
  private static final String[] HEADER = {"timestamp", "content", "author_name", "author_id"};

  public ExportChatHistory() {
    this.name = "history";
    this.help = "build a CSV of the chat history";
  }

  @Override
  protected void execute(SlashCommandEvent event) {
    event.deferReply(true).queue();
    new Export(event).start();
  }

  private static String describeChannel(MessageChannel channel) {
    String description =
        String.format("#%s (%s, %s)", channel.getName(), channel.getId(), channel.getType());
    if (channel instanceof ThreadChannel thread) {
      IThreadContainer parent = thread.getParentChannel();
      description +=
          String.format(
              " in #%s (%s, %s), archived=%s",
              parent.getName(), parent.getId(), parent.getType(), thread.isArchived());
    }
    return description;
  }

  private static class Export {
    private final String id;
    private final InteractionHook hook;
    private final User user;
    private final MessageChannel channel;
    private final String filePrefix;
    private final Instant deadline;
    private final Instant startedAt = Instant.now();

    private long messageCount = 0;
    private long rowsInPart = 0;
    private String oldestTimestamp = "-";
    private int partCount = 0;
    private Throwable writeError;
    private Instant lastProgress = Instant.now();
    private ByteArrayOutputStream buffer;
    private CSVWriter writer;
    private CompletableFuture<Void> deliveries = CompletableFuture.completedFuture(null);

    Export(SlashCommandEvent event) {
      this.id = event.getId();
      this.hook = event.getHook().setEphemeral(true);
      this.user = event.getUser();
      this.channel = event.getChannel();
      this.filePrefix = "history-" + channel.getName().replaceAll("[^A-Za-z0-9_-]", "_");
      this.deadline = event.getTimeCreated().toInstant().plus(INTERACTION_WINDOW);

      LOG.info(
          "[export {}] /history by {} ({}) in guild {} ({}), channel {}",
          id,
          user.getName(),
          user.getId(),
          event.getGuild() == null ? "none" : event.getGuild().getName(),
          event.getGuild() == null ? "-" : event.getGuild().getId(),
          describeChannel(channel));
    }

    void start() {
      try {
        openPart();
        channel
            .getIterableHistory()
            .cache(false)
            .forEachAsync(this::writeMessage, error -> {})
            .whenComplete((ignored, error) -> finish(error != null ? error : writeError));
      } catch (Exception e) {
        finish(e);
      }
    }

    private boolean writeMessage(Message message) {
      try {
        appendRow(message);
        return true;
      } catch (RuntimeException e) {
        writeError = e;
        return false;
      }
    }

    private void appendRow(Message message) {
      String timestamp = message.getTimeCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      writer.writeNext(
          new String[] {
            timestamp,
            CONTROL_CHARS.matcher(message.getContentRaw()).replaceAll(""),
            message.getAuthor().getName(),
            message.getAuthor().getId()
          });
      flushWriter();
      messageCount++;
      rowsInPart++;
      oldestTimestamp = timestamp;

      if (buffer.size() >= MAX_PART_BYTES) {
        closePart(false);
        openPart();
      }
      reportProgress();
    }

    private void reportProgress() {
      Instant now = Instant.now();
      if (Duration.between(lastProgress, now).compareTo(PROGRESS_INTERVAL) < 0) {
        return;
      }
      lastProgress = now;
      Duration elapsed = Duration.between(startedAt, now);
      LOG.info(
          "[export {}] {} messages fetched in {}s ({} msg/s), part {}, oldest so far {}",
          id,
          messageCount,
          elapsed.toSeconds(),
          messageCount / Math.max(1, elapsed.toSeconds()),
          partCount,
          oldestTimestamp);
      if (withinInteractionWindow()) {
        hook.editOriginal(
                String.format(
                    "Still working: %,d messages exported so far (back to %s UTC).",
                    messageCount, oldestTimestamp))
            .queue(null, e -> LOG.warn("[export {}] progress update failed", id, e));
      }
    }

    private void finish(Throwable error) {
      Duration elapsed = Duration.between(startedAt, Instant.now());
      if (buffer != null && (rowsInPart > 0 || partCount == 1)) {
        closePart(error != null);
      }

      String summary;
      if (error == null) {
        LOG.info(
            "[export {}] finished: {} messages in {} part(s) after {}s",
            id,
            messageCount,
            partCount,
            elapsed.toSeconds());
        summary =
            String.format(
                "Done: exported %,d messages in %d file(s) after %ds.",
                messageCount, partCount, elapsed.toSeconds());
      } else {
        LOG.error(
            "[export {}] failed after {} messages and {}s in {}",
            id,
            messageCount,
            elapsed.toSeconds(),
            describeChannel(channel),
            error);
        summary =
            String.format(
                "Export failed after %,d messages: %s. Any files above are partial.",
                messageCount, error.getMessage());
      }

      deliveries.thenRun(() -> sendSummary(summary));
    }

    private void openPart() {
      partCount++;
      rowsInPart = 0;
      buffer = new ByteArrayOutputStream();
      writer = new CSVWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
      writer.writeNext(HEADER);
    }

    private void closePart(boolean partial) {
      try {
        writer.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      String fileName =
          String.format("%s-part%d%s.csv", filePrefix, partCount, partial ? "-partial" : "");
      byte[] data = buffer.toByteArray();
      LOG.info("[export {}] queueing {} ({} bytes)", id, fileName, data.length);
      deliveries = deliveries.thenCompose(ignored -> deliver(fileName, data));
    }

    private CompletableFuture<Void> deliver(String fileName, byte[] data) {
      FileUpload upload = FileUpload.fromData(data, fileName);
      CompletableFuture<?> sent;
      String via;
      if (withinInteractionWindow()) {
        via = "interaction followup";
        sent = hook.sendFiles(upload).submit();
      } else {
        via = "DM";
        sent = user.openPrivateChannel().flatMap(dm -> dm.sendFiles(upload)).submit();
      }
      return sent.handle(
          (ignored, error) -> {
            if (error == null) {
              LOG.info("[export {}] sent {} via {}", id, fileName, via);
            } else {
              LOG.error("[export {}] could not send {} via {}", id, fileName, via, error);
            }
            return null;
          });
    }

    private void sendSummary(String summary) {
      if (withinInteractionWindow()) {
        hook.editOriginal(summary)
            .queue(null, e -> LOG.error("[export {}] could not send summary", id, e));
      } else {
        user.openPrivateChannel()
            .flatMap(dm -> dm.sendMessage(summary))
            .queue(null, e -> LOG.error("[export {}] could not DM summary", id, e));
      }
    }

    private boolean withinInteractionWindow() {
      return Instant.now().isBefore(deadline);
    }

    private void flushWriter() {
      try {
        writer.flush();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
