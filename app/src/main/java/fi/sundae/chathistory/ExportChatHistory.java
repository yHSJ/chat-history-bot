package fi.sundae.chathistory;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.text.StringEscapeUtils;

public class ExportChatHistory extends SlashCommand {

  public ExportChatHistory() {
    this.name = "history";
    this.help = "build a CSV of the chat history";
  }

  @Override
  protected void execute(SlashCommandEvent event) {
    event.deferReply(true).queue();
    TextChannel channel = event.getChannel().asTextChannel();

    fetchAllMessages(channel)
        .thenAccept(
            messages -> {
              writeMessages(messages);
              File file = new File("history.csv");
              event
                  .getHook()
                  .editOriginal("See attached for results")
                  .setFiles(FileUpload.fromData(file))
                  .queue();
              file.delete();
            });
  }

  public void writeMessages(List<Message> messages) {
    try (CSVWriter writer = new CSVWriter(new FileWriter("history.csv", true))) {
      writer.writeAll(
          messages.stream()
              .map(
                  (message) ->
                      new String[] {
                        message.getTimeCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        sanitizeContent(message.getContentRaw()),
                        message.getAuthor().getName(),
                        message.getAuthor().getId()
                      })
              .collect(Collectors.toList()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String sanitizeContent(String content) {
    return StringEscapeUtils.escapeCsv(content.replaceAll("\\p{Cntrl}&&[^\r\n\t]", ""));
  }

  private CompletableFuture<List<Message>> fetchAllMessages(TextChannel channel) {
    CompletableFuture<List<Message>> future = new CompletableFuture<>();
    List<Message> messages = new ArrayList<>();

    fetchMessagesBefore(channel, null, messages, future);
    return future;
  }

  private void fetchMessagesBefore(
      TextChannel channel,
      String lastMessageId,
      List<Message> messages,
      CompletableFuture<List<Message>> future) {
    int fetchLimit = 100; // limit imposed by Discord
    if (lastMessageId == null) {

      channel
          .getHistory()
          .retrievePast(fetchLimit)
          .queue(
              retrievedMessages -> {
                messages.addAll(retrievedMessages);

                if (retrievedMessages.size() < fetchLimit) {
                  future.complete(messages);
                } else {
                  String oldestMessageId =
                      retrievedMessages.get(retrievedMessages.size() - 1).getId();
                  fetchMessagesBefore(channel, oldestMessageId, messages, future);
                }
              },
              future::completeExceptionally);
    } else {
      channel
          .getHistoryBefore(lastMessageId, fetchLimit)
          .queue(
              history -> {
                List<Message> retrievedMessages = history.getRetrievedHistory();
                messages.addAll(retrievedMessages);

                if (retrievedMessages.size() < fetchLimit) {
                  future.complete(messages);
                } else {
                  String oldestMessageId =
                      retrievedMessages.get(retrievedMessages.size() - 1).getId();
                  fetchMessagesBefore(channel, oldestMessageId, messages, future);
                }
              },
              future::completeExceptionally);
    }
  }
}
