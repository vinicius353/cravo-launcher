# CRAVO Alpha 0.3 — upgrade and migration

The Android application ID changes from `org.levimc.launcher` (Alpha 0.2) to `com.vinicius353.cravo` (Alpha 0.3).

**IMPORTANT:** Android treats these as different applications. Their private storage does **not** migrate automatically. Both can remain installed simultaneously. Do not uninstall Alpha 0.2 until backups have been restored and verified.

## Before installing Alpha 0.3

1. In Alpha 0.2, use **Settings → Original tools → Instances** (or the legacy Instances screen).
2. Use **Backup** / **Batch backup** and export each instance to Downloads.
3. Verify that the backup files actually exist and keep a second copy of any irreplaceable world data.
4. Download and install the Alpha 0.3 APK from its GitHub Actions artifact ZIP.
5. In the new app, open **Profiles → Manage versions → Backup and restore**; use **Restore** / **Import Backup**.
6. Verify the Minecraft installation, mods, and worlds before removing the old app.

Legacy `.levibackup` files retain their original backup schema for compatibility. The backup feature is based on the LeviLauncher component and will be gradually reimplemented in CRAVO. Existing third-party files and Minecraft game data are not modified by the app ID change.

## Technical identifiers

- Android `applicationId`: `com.vinicius353.cravo` (new private data paths, FileProvider authorities).
- Source `namespace`: `org.levimc.launcher` (legacy code namespace remains intentionally until migration is tested).
- Minecraft's own package `com.mojang.minecraftpe` is unchanged.
- This build does **not** bundle or redistribute any Minecraft APK, and does not remove Minecraft's authentication or licensing checks.
- Legacy open-source attribution and license obligations remain.

## Backend API

CurseForge requires an approved developer API key. Without a valid key, the built-in catalog shows a friendly explanation and offers the official Bedrock website. Download and installation integration remain conditional on proper authorization.
