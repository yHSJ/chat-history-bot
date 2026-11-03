package fi.sundae.chathistory;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.CommandListener;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bot {
  private static final Logger LOG = LoggerFactory.getLogger(Bot.class);

  public Bot(String token, String ownerId) {
    CommandClient commandClient =
        new CommandClientBuilder()
            .setOwnerId(ownerId)
            .setStatus(OnlineStatus.ONLINE)
            .setListener(new LoggingCommandListener())
            .addSlashCommands(new ExportChatHistory(), new Ping())
            .build();

    JDABuilder.createDefault(token)
        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
        .addEventListeners(commandClient, new ReadyLogger())
        .build();
  }

  private static class ReadyLogger extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
      LOG.info(
          "Logged in as {} in {} guild(s)",
          event.getJDA().getSelfUser().getName(),
          event.getGuildTotalCount());
      for (Guild guild : event.getJDA().getGuilds()) {
        LOG.info(
            "  guild {} ({}), {} members", guild.getName(), guild.getId(), guild.getMemberCount());
      }
    }
  }

  private static class LoggingCommandListener implements CommandListener {
    @Override
    public void onSlashCommand(SlashCommandEvent event, SlashCommand command) {
      LOG.info(
          "/{} invoked by {} ({}) in channel {} ({})",
          command.getName(),
          event.getUser().getName(),
          event.getUser().getId(),
          event.getChannel().getName(),
          event.getChannelType());
    }

    @Override
    public void onTerminatedSlashCommand(SlashCommandEvent event, SlashCommand command) {
      LOG.warn(
          "/{} by {} was rejected by command checks (permissions, cooldown or guild-only)",
          command.getName(),
          event.getUser().getId());
    }

    @Override
    public void onSlashCommandException(
        SlashCommandEvent event, SlashCommand command, Throwable throwable) {
      LOG.error(
          "/{} by {} in channel {} threw",
          command.getName(),
          event.getUser().getId(),
          event.getChannel().getId(),
          throwable);
    }
  }
}
