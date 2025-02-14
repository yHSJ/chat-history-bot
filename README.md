# Chat History Discord Bot

This Discord bot provides a simple slash command to export the chat history of the current channel in a CSV file.

## Requirements
- Java 21+
- Gradle 8.12+
- Docker

## Environment
There are two necessary environment variables:
-  `TOKEN`: the Discord bot API token
- `OWNER_ID`: the Discord user ID of the "owner"

## Setting up a Discord bot

- go to [discord.com/developers](https://discord.com/developers) and click "New Application"
- Navigate to "Bot" on the sidebar
- Toggle on "Message Content Intent"
- Click "Reset Token" and copy the value there. This is your `TOKEN` environment variable
- Navigate to "Oauth2" on the sidebar
- Select "bot" in the OAuth2 URL generator
- Select "Administrator" permissions
- Copy the generated URL and paste that in your browser to invite your bot to join your Discord server


## Running the bot
1) Build the docker image
```shell
docker build -t chat-history-bot .
```

2) Run a docker container
```shell
docker run -e TOKEN=$TOKEN -e OWNER_ID=$OWNER_ID chat-history-bot
```

Note: the logs are not formatted. As long as the bot doesn't crash, it should be running as expected. Your bot should appear as Online in Discord if functioning as expected.


## Usage
Slash Commands:
- `/history`: Export the current chat history into a CSV file that will be uploaded to you privately 