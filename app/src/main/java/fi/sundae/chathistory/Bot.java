package fi.sundae.chathistory;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Bot {
  public Bot(String token, String ownerId) {
    JDABuilder jdaBuilder =
        JDABuilder.createDefault(token).enableIntents(GatewayIntent.MESSAGE_CONTENT);

    CommandClient commandBuilder =
        new CommandClientBuilder()
            .setOwnerId(ownerId)
            .setStatus(OnlineStatus.ONLINE)
            .addSlashCommand(new ExportChatHistory())
            .build();
    jdaBuilder.addEventListeners(commandBuilder);

    JDA jda = jdaBuilder.build();
  }
}
