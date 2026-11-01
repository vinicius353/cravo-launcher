# CRAVO Alpha 0.3 — device test checklist

- [ ] CRAVO 0.2 remains installed alongside 0.3 without overwrite prompts.
- [ ] Android app info shows `com.vinicius353.cravo` as the new app package.
- [ ] Launcher icon is centered and not clipped; compare launcher, Settings, recents, themed icon.
- [ ] Splash uses the CRAVO symbol and coral accent.
- [ ] Main dashboard renders in portrait; bottom tabs animate without content loss.
- [ ] Motion reduction toggle disables major transitions.
- [ ] Profile selection remains persistent after reopening.
- [ ] Versions page lists installed/imported instances.
- [ ] Import APK/APKS selects a local file and shows meaningful compatibility diagnostics.
- [ ] Installed Minecraft runs only with valid licensing/authentication, as before.
- [ ] Backup export from 0.2 and restore in 0.3 preserve worlds and settings.
- [ ] Mods and Packs open native CRAVO hubs; their advanced tools still work.
- [ ] CurseForge with no authorized API key shows a clear explanation, not an unhandled raw 403.
- [ ] CurseForge fallback opens the real official Bedrock catalog.
- [ ] Diagnostics preview, copy and share `.txt` work on Android 15.
- [ ] Closing/resuming an imported game doesn't break activity navigation.
- [ ] No accidental reversion to LeviLauncher launcher branding.

Build CI: `.github/workflows/android.yml` assembles debug APK and uploads `CRAVO-alpha03-debug-apk`. Debug build uses placeholder Firebase values if credentials are unavailable; cloud functions may not operate.
