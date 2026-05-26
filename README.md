# Discord Audio Stream Bot

Desktop application for streaming local audio to a Discord voice channel through a bot account.

> This project is a fork/continuation of the original
> [BinkanSalaryman/Discord-Audio-Stream-Bot](https://github.com/BinkanSalaryman/Discord-Audio-Stream-Bot).
> The original repository no longer appears to be maintained; this version keeps the project usable with newer Java/JDA/Discord changes and adds packaging, stability, and UI improvements.

## Features

- Stream a local audio source to Discord.
- Select a microphone or virtual audio device, such as VB-Audio Virtual Cable.
- Desktop UI for configuring the bot token, audio devices, and voice channels.
- Maintenance tab showing the connected voice channel, permissions, auto-join settings, ignored audio users, and audio restart controls.
- Discord slash commands for joining/leaving voice, auto-join, follow-audio, muting listened users, and restarting the audio pipeline.
- Native Windows, Linux, and macOS builds with a bundled Java runtime.

## Installation

The recommended installation method is to download the latest GitHub release.

The `native-*` packages include their own Java runtime, so users do not need to install Java 17 manually.

1. Download the package for your operating system:
   - `Discord Audio Stream Bot-native-windows-x64.zip`
   - `Discord Audio Stream Bot-native-linux-x64.zip`
   - `Discord Audio Stream Bot-native-macos-x64.zip`
2. Extract the archive.
3. Launch the packaged application:
   - Windows: `Discord Audio Stream Bot.exe`
   - Linux: launcher inside the `bin` directory
   - macOS: `.app` application bundle

## Discord Setup

1. Create an application in the [Discord Developer Portal](https://discord.com/developers/applications).
2. In the Bot tab, create a bot and copy its token.
3. Enable `SERVER MEMBERS INTENT`, which is required for some permission checks.
4. Launch Discord Audio Stream Bot.
5. In `Settings`, paste the bot token.
6. In `Home`, connect the bot.
7. In `Maintenance`, invite the bot to a server if needed.

## Usage

To stream audio to Discord:

1. Connect the bot from the `Home` tab.
2. In `Settings`, unmute the bot microphone.
3. Select the input audio device.
4. Join a voice or stage channel with `/join` or from the `Maintenance` tab.

To stream audio from an application or game, use a virtual audio cable such as [VB-Audio Virtual Cable](https://www.vb-audio.com/Cable/), then select its output as the recording device in this app.

## Discord Commands

- `/join` - Join a voice or stage channel.
- `/leave` - Leave the current voice channel.
- `/leave-all` - Leave all connected voice channels. Bot owner only.
- `/autojoin` - Configure the channel to join automatically.
- `/follow-audio` - Follow a user across voice channels.
- `/mute-audio` - Mute/unmute specific users for the bot's audio listener.
- `/restart-audio` - Restart the audio pipeline for the current server.
- `/restart-audio all:true` - Restart all audio pipelines. Bot owner only.
- `/bind` - Restrict which text channels can be used for bot commands.
- `/status`, `/activity`, `/stage`, `/about`, `/invite`, `/stop` - General bot management commands.

## Audio Troubleshooting

If audio becomes unstable or gets stuck:

1. Use the `Restart audio` button in the `Maintenance` tab.
2. Or run the Discord command `/restart-audio`.
3. Check that the correct input device is selected in `Settings`.
4. If you are using a virtual audio cable, make sure the source application is sending audio to the cable input.

## Building From Source

A full JDK is required to create native packages because the build uses `jpackage`.

On Windows:

```powershell
.\gradlew.bat test nativeImageZip
```

On Linux or macOS:

```bash
./gradlew test nativeImageZip
```

Generated archives are written to:

```text
build/distributions/
```

## Automated Releases

The GitHub Actions workflow builds native Windows, Linux, and macOS packages when a Git tag is pushed.

Example:

```bash
git tag v1.0.2
git push origin v1.0.2
```

The GitHub release is created automatically and the native zip files are uploaded to it.

## Credits

Original project:
[BinkanSalaryman/Discord-Audio-Stream-Bot](https://github.com/BinkanSalaryman/Discord-Audio-Stream-Bot)

This repository is an unofficial continuation intended to keep the application usable with current Java, JDA, and Discord behavior.
