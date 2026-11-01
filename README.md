<div align="center">

<img src="assets/cravo-icon.svg" alt="CRAVO Launcher icon" width="112" />

# CRAVO

**Minecraft Bedrock. Your way.**

A minimalist Android launcher project built around **PvP**, **customization**, and a cleaner experience.

[Releases](https://github.com/vinicius353/cravo-launcher/releases) · [Builds](https://github.com/vinicius353/cravo-launcher/actions) · [Issues](https://github.com/vinicius353/cravo-launcher/issues)

**Android 9+** · **ARM64** · **In development**

</div>

---

## About

**CRAVO Launcher** is an independent Minecraft Bedrock launcher project for Android, built on the open-source LeviLauncher codebase.

The goal is to create a straightforward, customizable experience with particular attention to PvP players and usability.

> **Development status:** Early development. PvP and performance features listed below are goals, not verified or completed features.

## Project goals

- **PvP-first customization** — configurable controls and player-focused options.
- **Clean interface** — fewer distractions, easier navigation.
- **Performance options** — investigate measurable improvements and configurable settings.
- **Game management** — build on the existing foundation for Minecraft versions and content.

## Requirements

The current codebase targets:

- **Android 9.0+** (API 28+).
- **ARM64** devices.
- An officially licensed copy of **Minecraft Bedrock Edition**, installed from Google Play.

Compatibility with specific devices and Minecraft versions is subject to testing.

## Get a build

- **[Releases](https://github.com/vinicius353/cravo-launcher/releases)** — official published versions, when available.
- **[GitHub Actions](https://github.com/vinicius353/cravo-launcher/actions)** — development builds; successful workflow runs may provide APK artifacts.

Development APKs may be unstable and may require additional setup. A successful code compilation does not necessarily mean the application is ready for everyday use.

## Build from source

You'll need JDK 21, Android SDK, Android NDK, and the dependencies configured by Gradle.

```bash
git clone --recurse-submodules https://github.com/vinicius353/cravo-launcher.git
cd cravo-launcher
./gradlew assembleDebug
```

Some build configurations require external service credentials. Refer to the CI workflows and build logs when troubleshooting.

Found a bug or have an idea? [Open an issue](https://github.com/vinicius353/cravo-launcher/issues).

---

## Credits & license

CRAVO Launcher is an independent derivative of [LeviLauncher for Android (LeviLaunchroid)](https://github.com/LiteLDev/LeviLaunchroid), created by [LeviMC / LiteLDev](https://github.com/LiteLDev) and its [contributors](https://github.com/LiteLDev/LeviLaunchroid/graphs/contributors). All original attribution and copyright notices remain applicable.

The project uses the **Apache License 2.0**. See [LICENSE](LICENSE) for its terms; retain applicable copyright, attribution, and license notices when redistributing or modifying the software.

**Minecraft** is a trademark of Mojang Studios / Microsoft. CRAVO Launcher is not affiliated with, endorsed by, or sponsored by Mojang or Microsoft.
