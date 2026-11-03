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

Logs go to stdout with timestamps. Each `/history` run logs lines tagged `[export <interaction id>]` with the channel, progress every 15 seconds, each file sent, and the final result or error. Set `LOG_LEVEL=DEBUG` for more detail from the bot itself.


## Usage
Slash Commands:
- `/history`: Export the current channel's chat history (text channels, threads and forum posts) into CSV files that are sent to you privately. Large histories are split into files of about 8 MB each. If an export runs longer than 14 minutes, the remaining files arrive by DM.
- `/ping`: Replies "Pong!" to confirm the bot is responsive 