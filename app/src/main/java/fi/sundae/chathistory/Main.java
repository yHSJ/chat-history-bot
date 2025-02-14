package fi.sundae.chathistory;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {
  public static void main(String[] args) {
    Dotenv env = Dotenv.configure().ignoreIfMissing().load();
    String token = env.get("TOKEN");
    String ownerId = env.get("OWNER_ID");

    new Bot(token, ownerId);
  }
}
