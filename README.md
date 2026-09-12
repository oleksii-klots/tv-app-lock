<div align="center">

# 🔒 TV App Lock

**English** · [Українською](README.ua.md)

**Put a PIN on any app on your Android TV. Built for parents.**
No root. No cloud. No ads. No tracking.

[![License: MIT](https://img.shields.io/badge/License-MIT-2563eb)](LICENSE)
[![Release](https://img.shields.io/github/v/release/oleksii-klots/tv-app-lock?label=release)](https://github.com/oleksii-klots/tv-app-lock/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/oleksii-klots/tv-app-lock/ci.yml?branch=main&label=build)](https://github.com/oleksii-klots/tv-app-lock/actions)
![Android](https://img.shields.io/badge/Android%20TV-6.0%2B%20(API%2021)-3DDC84?logo=android&logoColor=white)
![Offline](https://img.shields.io/badge/works-offline-059669)
![Size](https://img.shields.io/badge/APK-~0.8%20MB-7c3aed)

</div>

> ## 👪 For parents, first and foremost
> This app exists for one everyday situation: the family TV, a curious kid, and
> apps you don't want opened unsupervised. Everything in it is designed around
> that: a simple 4-digit PIN typed with the living-room remote, a per-app
> switch, a grace window you control, and zero accounts, servers or telemetry.
> It is **child resistance**, not security: think "keep a 5-year-old out of
> YouTube", not "protect state secrets". A determined adult with the remote can
> undo it in Settings — deliberately so, there are no dark patterns to fight.

TV App Lock watches which app is on the foreground of your Android TV and puts
a 4-digit PIN gate on top of the apps you choose — SmartTube, YouTube, Lampa,
whatever you want to keep behind a password. Built for a real living-room
remote: everything is operated with the D-pad.

## How it works

- A tiny foreground service polls the system usage stats every 250 ms.
- When a **locked app enters the foreground**, the launcher is parked to Home
  and a PIN screen is raised over it.
- Correct PIN → you land **inside the app**, and a configurable *grace period*
  (0–60 min or "until reboot") decides how long re-entries stay unlocked.
- A watchdog re-arms the gate if Android silently refuses the overlay launch,
  and an alarm keep-alive resurrects the service if the firmware kills it.
- Everything is local. No network permission at all.

### Why not an accessibility service?

Accessibility-based app lockers (most of the Play Store ones) intercept window
events, but on Android TV they globally change how the remote's OK button is
delivered — short taps start opening the long-press context menu in the
launcher. TV App Lock deliberately uses polling instead of accessibility: zero
interference with input, at the cost of a ~0.5 s window between an app's icon
and the gate appearing.

## Features

| | |
|---|---|
| 🔢 PIN | 4 digits, typed with the remote, auto-saves |
| 📱 App picker | pick any app visible on the TV home screen (LAUNCHER + LEANBACK_LAUNCHER) |
| ⏱️ Grace period | 0 / 5 / 15 / 30 / 60 min / until reboot, cycled from the UI |
| 🔓 State visible | the main screen always shows whether the lock is armed or open |
| ♻️ Survives reboot | boot receiver + keep-alive; no root needed |
| 🌍 UI | English & Ukrainian |

## Requirements

- Android TV 6.0+ (API 21), tested on Android 14
- ADB available for the one-time install and permission grant

## Install

1. Download `tvapplock-release.apk` from the
   [latest release](https://github.com/oleksii-klots/tv-app-lock/releases/latest)
   and verify it against `SHA256SUMS.txt`.
2. Enable ADB debugging on the TV (Settings → Device preferences → USB
   debugging; on some builds you must first tap "Build version" 7 times in
   About).
3. From any computer on the same network:

```bash
adb connect <TV_IP>:5555
adb install tvapplock-release.apk

# grant the two permissions the locker needs (one-time setup)
adb shell appops set com.alexk.tvlock GET_USAGE_STATS allow
adb shell appops set com.alexk.tvlock SYSTEM_ALERT_WINDOW allow

# start the guardian service
adb shell am start-foreground-service com.alexk.tvlock/.LockPollService
```

4. Open **TV App Lock** on the TV → *Set PIN* → pick the apps to lock.

## Known limitations (honest list)

- **~0.5 s race**: polling is not a hook. The locked app may show its first
  frame before the gate rises. Without root/system signature this is not
  solvable completely.
- **Bypassable by anyone who can reach Settings**: disabling Usage Access or
  force-stopping the app lifts the lock. This is child resistance (4–10 y.o.),
  not security against a determined adult.
- **Grace period is a tradeoff**: `0` (ask every time) is the safest; longer
  windows leave the door open for everyone, not just for you.
- The PIN is stored locally, hashed only by SharedPreferences — fine for a
  family TV, never reuse a real password.

## Build from source

Requires JDK 17 and an Android SDK with platform 34 (or just Docker):

```bash
./gradlew assembleRelease            # with env vars RELEASE_KEYSTORE, RELEASE_STORE_PASSWORD, ...
# or fully containerized:
docker build -t tvlock-build .
docker run --rm -v $PWD:/project -v $PWD/.android:/root/.android \
  -v $PWD/secrets:/secrets tvlock-build \
  ./gradlew assembleRelease
```

## Privacy

The app requests no internet permission. The usage-stats permission is used
solely to determine the foreground app, locally and in real time. Nothing ever
leaves the device.

## License

[MIT](LICENSE)
