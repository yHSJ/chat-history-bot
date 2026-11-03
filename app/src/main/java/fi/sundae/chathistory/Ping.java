package fi.sundae.chathistory;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

public class Ping extends SlashCommand {

  public Ping() {
    this.name = "ping";
    this.help = "check that the bot is responsive";
  }

  @Override
  protected void execute(SlashCommandEvent event) {
    event.reply("Pong!").setEphemeral(true).queue();
  }
}
