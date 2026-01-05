
![image](https://github-production-user-asset-6210df.s3.amazonaws.com/62836594/531752022-005a5558-fa65-4e95-8ede-d3969d5553ca.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20260104%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260104T184850Z&X-Amz-Expires=300&X-Amz-Signature=c1edc2d4e970ca157f120c0f456f1f57b382a2d0e77cfe0ad87dca43d029655d&X-Amz-SignedHeaders=host)

**Lock Screen Protector**

Pixel-focused LSPosed/Xposed module that blocks power menu and essential quick settings tiles from being accessible in the lockscreen.  
  
  After years of reporting and requesting... Google didn't bother fixing it,
so I built it Myself.

<!-- Badges: replace `theDjay2529/TouchMeNot` with your repo if different -->
[![Downloads](https://img.shields.io/github/downloads/theDjay2529/TouchMeNot/total.svg?style=for-the-badge)](https://github.com/theDjay2529/TouchMeNot/releases)
[![Bug Report](https://img.shields.io/badge/bug-report-red?style=for-the-badge)](https://github.com/theDjay2529/TouchMeNot/issues/new?template=bug_report.md)
[![Feature Request](https://img.shields.io/badge/feature-request-blue?style=for-the-badge)](https://github.com/theDjay2529/TouchMeNot/issues/new?template=feature_request.md)
[![Community](https://img.shields.io/badge/Join-telegram-26A5E4?style=for-the-badge)](https://t.me/+uJDDVqXDoMViMTA1)

## Navigation
1. [Features](#features)
2. [Screenshots](#screenshots)
3. [Requirements](#requirements)
4. [Privacy](#privacy)
5. [Contributing & Feedback](#contributing--feedback)
6. [Star History](#star-history)

## Features
- Block lockscreen Power Menu access: disables global actions menu and Power+Vol-Up combo while locked, with haptic/toast feedback.
- Block Quick Settings tiles: blocks the Internet, Airplane, Bluetooth, Hotspot tiles and footer power menu when device is locked.
- Biometric-gated settings UI: Compose UI with toggles for all protections and an “LSPosed permissions” hint.
- Direct-boot-ready flags: preferences stored in device-protected storage and exposed via ContentProvider for hooked processes; live-updated cache.
- Local logging: writes to `/sdcard/Download/touchmenot_recorder.log` for debugging (never leaves device).

## Screenshots
- TODO: add screenshots (e.g., `docs/screens/home.png`, `docs/screens/toggles.png`).
- Tip: store images under `docs/` or `.github/assets/` and reference them here.

## Requirements
- Pixel devices targeted (tested focus); may work elsewhere if LSPosed scopes are correct.
- LSPosed/Xposed module
  - Scopes: **System Framework** and **System UI** (per UI hint).
  - Metadata: `xposedmodule=true`, min LSPosed version 82.
- Android: minSdk 26; target/compileSdk 36.

## Privacy
- **No Internet permission**: module cannot reach the network.
- Data stored locally: feature toggles in SharedPreferences (device-protected storage); logs to `/sdcard/Download/touchmenot_recorder.log`.
- No telemetry, no data collection, no remote calls. Root/Xposed privileges are only used to block lockscreen actions.  
Your Privacy and Safety is Guaranteed.

## Contributing & Feedback
- Pull Requests welcome: fixes, optimizations, new protections, UI/UX polish.
- Issues: please use the bug/feature templates (see badges above).
- Community chat: join our [Telegram](https://t.me/+uJDDVqXDoMViMTA1). For help or deeper feedback, ping us there.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=theDjay2529/TouchMeNot&type=date&logscale&legend=top-left)](https://www.star-history.com/#theDjay2529/TouchMeNot&type=date&logscale&legend=top-left)
---

### Badge & Asset Guide
- **Downloads badge**: use `https://img.shields.io/github/downloads/<owner>/<repo>/total.svg` once releases exist.
- **Screenshots**: place under `docs/` or `.github/assets/` and reference with relative paths.

