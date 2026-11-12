# Counter

A simple tally counter for Android, for things you count many times a day:
tasbih and dhikr, japa mala, exercise reps, laps, people through a door.

Most tally counters on the Play Store are full of ads, and some want an account
or the internet for something that is, at heart, `count += 1`. This one is free,
open source (MIT), and has **no ads, no tracking, no analytics, no account and no
internet permission**.

## Features

- **Multiple counters**, each with a name, colour, step size (how much a tap
  adds) and an optional target, with quick presets 33, 99, 100 and 108.
- **One huge tap area.** The whole middle of the counting screen counts, on
  touch-down, with a short vibration on every tap. A minus button and a reset
  (with an undo snackbar) sit at the bottom, out of the way.
- **Targets and rounds.** Reaching the target gives a distinct long-short-long
  vibration and, optionally, a beep. Each counter chooses what happens next:
  - _Ask_: a dialog offers "New round" (back to zero) or "Keep counting".
  - _New round_: the target number stays on screen, and the next tap starts the
    next round from 1. Good for 33 × 3 without touching a dialog.
  - _Keep going_: never resets; buzzes at every multiple of the target.
    Completed rounds are counted in every mode.
- **Volume keys** count while the counting screen is open (setting).
- **Keep screen on** while counting (setting, on by default).
- **Pure black dark theme** for OLED screens (setting).
- **History**: per-counter daily totals and rounds, with today / 7 / 30 day /
  all-time totals, best day, current streak and a 30-day bar chart.
- **Home-screen widget** (Jetpack Glance): pick a counter when adding it, tap the
  number to add one, tap the name to open the app. Resizable; long-press →
  reconfigure to switch counters.
- Material You dynamic colour, dark theme, edge-to-edge. Android 12+.

## Permissions

| Permission | Why                                                            |
| ---------- | -------------------------------------------------------------- |
| `VIBRATE`  | The click on each count and the buzz when a target is reached. |

The app declares nothing else. The home-screen widget library (Glance) brings
in WorkManager, whose own manifest adds `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` to the built APK. All four are
normal install-time permissions that WorkManager uses to schedule widget
updates; none of them allows a network connection or reads personal data.

There is deliberately **no `INTERNET` permission**, so Android itself
guarantees your counts never leave the phone. See [PRIVACY.md](PRIVACY.md).

## Building

Requires JDK 17+ and the Android SDK (compileSdk 37).

```sh
# This Mac has no system JDK; Android Studio's bundled JBR works:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # counting-rule and history tests (JVM)
./gradlew lintDebug
./gradlew assembleRelease      # R8-minified; unsigned unless keystore.properties exists
```

Release signing is optional: put a `keystore.properties` (with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`) next to `settings.gradle.kts` and
the release build is signed with v2 + v3 signatures. Without it the release APK
is unsigned, which is what F-Droid wants. Both files are git-ignored.

## Releasing on Google Play

```sh
./gradlew lintRelease testDebugUnitTest
./scripts/archive-apk.sh            # build, verify, archive
./scripts/archive-apk.sh --force    # replace an existing archive
```

`scripts/archive-apk.sh` builds the release APK and AAB and copies them into
`local/releases/v<version>/` as `counter-v<version>.apk` and `.aab`, each with a
`.sha256`, plus the R8 `mapping.txt` that turns a minified stack trace from that
build back into names. Upload the `.aab` to Play; the `.apk` is for sideloading.
The version is read from the built APK, so the name cannot disagree with what
shipped. The script refuses an unsigned APK and a bundle signed with a different
key, archives a dirty tree as `v<version>-dirty-g<commit>/`, and never replaces
an existing archive without `--force`.

- **Signing.** The app uses Play App Signing: Google holds the key that signs what
  users install, and `keystore.properties` points at the _upload_ key. A lost
  upload key can be reset from the Play Console; a lost app signing key could not
  be, which is why Google keeps it.
- **Store listing.** Text, icon, feature graphic and screenshots are in
  [`fastlane/metadata/android/en-US/`](fastlane/metadata/android/en-US), the
  layout F-Droid also reads. Bump `versionCode` and `versionName` in
  `app/build.gradle.kts` and add `changelogs/<versionCode>.txt` for every release.
- **Screenshots** must be no taller than 2:1, so take them with the emulator's
  display set to `adb shell wm size 1080x2160` (and `adb shell wm size reset`
  afterwards). The demo counters and their 30 days of history were written
  straight into a debug build's `counter.db` with `run-as`, rather than tapped in.

## Design decisions

- **Write-through storage, never an in-memory count.** Every tap is a single
  Room transaction that updates the counter and today's history row together.
  Writes run in an application-wide coroutine scope rather than the screen's, so
  navigating away mid-write can't cancel a count, and a mutex serialises taps
  so a widget tap racing an in-app tap can't both read the same old value. If
  Android kills the process, the last committed tap is on disk. (The only gap
  is the sub-millisecond window between the touch and the transaction starting.)
- **Rules in plain Kotlin.** `core/CounterEngine.kt` and `core/History.kt` hold
  the counting, target, round and aggregation rules with no Android imports, so
  they are covered by fast JVM tests (`app/src/test`).
- **Minus is the exact inverse of plus.** Stepping back over a target also takes
  back the round (and today's total), so wobbling around 33 with plus/minus can't
  inflate the round count.
- **History records what was counted, not what the display shows.** Reset and
  "new round" change the counter but not the log. A minus tap subtracts from
  today's total, since it corrects a mistaken tap. Days use the phone's local
  calendar date at the time of the tap.
- **Count on touch-down**, like a mechanical clicker: no wait for the finger to
  lift and no double-tap delay, so fast counting never drops taps. A second
  finger landing while the first is still down does not count twice.
- **Both volume keys count up.** With the phone in a pocket or under a shawl you
  can't tell which key you're pressing; a wrong "minus" is worse than a missed
  feature. Held keys don't auto-repeat counts. Volume keys only count while the
  counting screen is visible; everywhere else they change the volume as usual.
- **Vibration uses the media usage on Android 13+**, which follows the "media
  vibration" setting rather than the ringer, so counting still buzzes in silent
  mode (mosque, temple, class). The optional beep uses the notification stream,
  so it _is_ silenced by silent mode.
- **The widget can't ask.** A widget can't show a dialog, so an "Ask" counter just
  keeps counting when tapped from the widget (the round is still recorded and
  the target still buzzes).
- **Opens to the last counter.** A tally is opened to count, so the app starts
  on the counter used last; back goes to the list.
- **No navigation or DI library.** Five screens and a back stack of sealed
  routes are simpler than the dependencies would be.
- **Schemas are exported** (`app/schemas/`) so any future database change ships
  as a real migration instead of wiping people's counts.

## License

[MIT](LICENSE) © 2026 Vivek Kumar Gupta
