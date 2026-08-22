# Running and testing Gawi

How to get the app onto a screen, and what to check by hand once it is there.

Companion to [the architecture](architecture.md) §8, which makes this necessary:
CI runs unit tests only, and *"instrumented tests are a manual, on-device
activity"*. §4 below is that activity written down. Toolchain setup for the
**build** lives in [docs/stacks/kotlin-android.md](stacks/kotlin-android.md);
this file picks up where that leaves off.

**What has actually been run.** The Linux path in this document was executed end
to end on 2026-08-20 (Arch, AMD Ryzen, AVD on Android 17 x86_64). The macOS and
Windows sections come from Google's and Microsoft's documentation and have **not**
been run by anyone here — they are marked as such. **No physical device has been
attached on any platform**, so the whole of §3 is unverified. Corrections
welcome; that is what those markers are for.

---

## 1. Prerequisites

### Everyone

| Need | Notes |
|---|---|
| **JDK 17** | Pinned by `jdk` in `gradle/libs.versions.toml` and by CI. Check with `./gradlew -version`, which reports the JVM Gradle actually uses. |
| **Android SDK** | `platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`, `emulator`. See the stack doc for the one-time install. |
| **`ANDROID_HOME`** | Exported in your shell profile, pointing at the SDK. |
| **Accepted licences** | `sdkmanager --licenses`. Skipping this makes Gradle's SDK auto-download fail with an opaque error — a very common first-run blocker. |
| **`make`** | The command contract (`make help`). See the Windows note below. |
| **`pre-commit`** | A Python tool, needed by `make setup` for the git hooks. Not a JVM dependency, easy to miss. |
| **~16 GB RAM** | Google's documented figure for the emulator. Below it the emulator warns and thrashes. |

Two environment traps worth stating outright:

- **`ANDROID_SDK_ROOT` is deprecated** in favour of `ANDROID_HOME`. If both are
  set and disagree, the build complains. Unset the old one.
- **Android Studio's bundled JDK may differ from your shell's.** That is the
  classic "works in the IDE, fails in the terminal" confusion. `./gradlew
  -version` is the arbiter.

Line endings need **no** configuration: `.gitattributes` already pins `gradlew`
to LF and `*.bat` to CRLF, and a committed `.gitattributes` overrides whatever
`core.autocrlf` you have. Please do not "fix" it.

### Linux — *verified*

The emulator needs KVM. One command answers whether you have it, on every
platform:

```sh
$ANDROID_HOME/emulator/emulator -accel-check
# working: "KVM (version 12) is installed and usable."
```

If `/dev/kvm` is missing, the cause is almost always **virtualization disabled in
firmware**, not a missing kernel module. Diagnose it carefully, because the
obvious grep lies:

```sh
# WRONG: matches "svm_lock" as a substring and reports success on a machine
# where SVM is disabled.
grep -E 'vmx|svm' /proc/cpuinfo

# RIGHT: word-bounded against the flags line only.
awk -F': ' '/^flags/{print $2; exit}' /proc/cpuinfo | tr ' ' '\n' | grep -x -E 'svm|vmx'
```

Empty output with `svm_lock`, `npt` and `nrip_save` present is the signature of a
CPU that supports virtualization while the firmware has it switched off. Enable
it in the UEFI setup — **SVM Mode** on AMD, **VT-x** on Intel. On the Gigabyte
B450 this repo was developed on: *Tweaker → Advanced CPU Settings → SVM Mode →
Enabled*. `kvm_amd`/`kvm_intel` then autoload; you do not need `modprobe`.

Permissions on `/dev/kvm` differ by distro, so check rather than follow folklore:

```sh
ls -l /dev/kvm
# crw-rw-rw-  (0666, systemd's own udev rule) -> nothing more to do
# crw-rw----  (0660, common on Debian/Ubuntu) -> sudo usermod -aG kvm $USER, then re-login
```

**Wayland note.** The emulator UI is Qt and runs through XWayland. If the window
never appears or renders black, try `-gpu host`, then `-gpu software`. Forcing
XWayland with `QT_QPA_PLATFORM=xcb` also helps. (`swiftshader_indirect`, which
older guides recommend, no longer exists — `emulator -help-gpu` lists the current
modes.)

### macOS — *unverified here; from Google's documentation*

- SDK lives at `~/Library/Android/sdk`.
- **Acceleration needs no setup.** The emulator uses the built-in
  Hypervisor.framework — nothing to install, no firmware setting. Intel HAXM has
  been unavailable since macOS 11; ignore any guide that mentions it.
- `make` comes from the Xcode Command Line Tools: `xcode-select --install`.
- **A physical device needs no driver.**
- **Apple Silicon must use an `arm64-v8a` system image** — see §2.

### Windows — *unverified here; from Google's and Microsoft's documentation*

- SDK lives at `%LOCALAPPDATA%\Android\Sdk`.
- **Acceleration is WHPX** (Windows Hypervisor Platform):
  1. Enable virtualization in firmware — **SVM Mode** on AMD, **VT-x** on Intel.
  2. Start → *"Turn Windows features on or off"* → tick **Windows Hypervisor
     Platform** → OK → **reboot** (not optional).
  3. Verify with `emulator -accel-check`.
- **Two older options are on the way out.** Intel HAXM is discontinued outright.
  The Android Emulator hypervisor driver (**AEHD**) still works, but is
  deprecated with a sunset date of **2026-12-31**, so migrate rather than adopt.
  Enable WHPX and confirm `emulator -accel-check` reports it working *first* —
  only then remove AEHD (`sc stop aehd && sc delete aehd`), or you are left with
  no accelerator at all.
- **Do not disable Hyper-V.** That instruction is legacy. WHPX exists precisely
  so the emulator coexists with Hyper-V, WSL2 and Docker Desktop. The conflict
  belonged to AEHD, which required Hyper-V *off*; moving to WHPX resolves it.
  You still cannot run the emulator nested inside a VirtualBox/VMware guest, and
  some anti-cheat drivers claim the hypervisor exclusively.
- **A physical device needs a driver** — the only platform that does. Google USB
  Driver via SDK Manager for Pixel devices, the vendor's own driver otherwise.
- **`make` needs a POSIX shell.** The Makefile uses `[ -n "$CI" ]`, `grep` and
  `awk`. Git Bash supplies those but **not `make` itself**; MSYS2
  (`pacman -S make`) is the cleanest single install. Because the Makefile is a
  thin wrapper, you can skip it entirely:

  | Instead of | Run |
  |---|---|
  | `make fmt` | `gradlew.bat spotlessApply` |
  | `make lint` | `gradlew.bat spotlessCheck detekt lint` |
  | `make test` | `gradlew.bat test` |
  | `make run` | `gradlew.bat :app:installDebug` then the `adb shell am start` line from the Makefile |

  Building inside WSL2 works, but **run the emulator and `adb` on the Windows
  side**: GPU passthrough into WSL2 is a common source of rendering failure, and
  reaching a Windows-hosted emulator from WSL2 otherwise needs port forwarding
  (WSL2 mirrored networking mode is the tidy fix).

---

## 2. An emulator

### Create the AVD

**The image ABI must match your CPU**, or the AVD will not start:

| Host | Image |
|---|---|
| Intel / AMD | `…;x86_64` |
| Apple Silicon | `…;arm64-v8a` |

The package ids differ only in that last field:

```sh
# Intel/AMD hosts
sdkmanager "system-images;android-37.1;google_apis_playstore_ps16k;x86_64"
# Apple Silicon
sdkmanager "system-images;android-37.1;google_apis_playstore_ps16k;arm64-v8a"
```

Then create the device. `avdmanager list device` shows the profiles;
`medium_phone` is a reasonable default:

```sh
# Intel/AMD hosts
avdmanager create avd -n gawi -d medium_phone \
  -k "system-images;android-37.1;google_apis_playstore_ps16k;x86_64"
# Apple Silicon — same command, same ABI swap as above
avdmanager create avd -n gawi -d medium_phone \
  -k "system-images;android-37.1;google_apis_playstore_ps16k;arm64-v8a"
```

That command prints two `Could not load devices from …/devices.xml` errors. They
are **noise** — the profile is applied anyway (`hw.device.name=medium_phone`,
1080×2400, 2 GB in the generated `config.ini`) and the AVD boots. Verified, so
do not go hunting.

> An `x86_64` image is not expected to run on Apple Silicon at all — there is no
> slow-but-working fallback. Google does not document the exact failure, so if
> yours behaves differently, correct this line.

### Start it and run the app

```sh
$ANDROID_HOME/emulator/emulator -avd gawi &
adb wait-for-device
make run
```

`make run` builds the debug variant, installs it, and launches the Today view. It
resolves `adb` from `PATH`; override that if your SDK is somewhere unusual:

```sh
make run ADB=~/Library/Android/sdk/platform-tools/adb
```

**With more than one device attached**, name the target — otherwise the launch
step stops with `adb: more than one device/emulator`, and Gradle installs to
*every* attached device rather than choosing:

```sh
adb devices                              # find the serial
ANDROID_SERIAL=emulator-5556 make run
```

Useful while iterating:

```sh
adb logcat -c                                   # clear, then reproduce
adb logcat -d -s AndroidRuntime:E               # crashes only
adb exec-out screencap -p > /tmp/shot.png       # screenshot
adb shell pm clear com.gawi.app                 # wipe the database and settings
```

---

## 3. A physical device — *unverified on every platform*

PRD §7 makes a real device the primary target for widget and notification work,
because launchers and OEM battery policies differ from emulators. That stopped
being forward-looking on 2026-08-21: the widget and the reminder both shipped, and
§4's widget block **cannot be completed without a launcher** — pinning a widget
requires a user, so nothing automated in this repo can place one. A device is no
longer setup-in-advance; it is the only way to finish the checklist.

1. On the phone: **Settings → About → tap Build number seven times**, then
   **Developer options → USB debugging**.
2. Connect it and accept the RSA fingerprint prompt.
3. `adb devices` must show `device`. Anything else means the per-platform notes
   below — there is no step 4, which this line used to point at.

Per platform:

- **Linux** — needs udev rules. On Arch: `pacman -S android-udev`, then
  `sudo usermod -aG adbusers $USER` and **log out and back in** (group
  membership only refreshes on login). Debian/Ubuntu ship
  `android-sdk-platform-tools-common` and use the `plugdev` group instead. The
  symptom of missing rules is `adb devices` listing the serial with
  **`no permissions`** rather than `device`; after installing them,
  `sudo udevadm control --reload-rules && sudo udevadm trigger`, replug, then
  `adb kill-server && adb start-server`.
- **macOS** — nothing needed.
- **Windows** — install the USB driver (see §1).

### Wireless debugging

Android 11+ (pairing code, no cable):

```sh
# Phone: Developer options -> Wireless debugging -> Pair device with pairing code
adb pair 192.168.1.50:37123      # port from the pairing dialog
adb connect 192.168.1.50:5555    # port from the main Wireless debugging screen
```

The two ports differ, which is the usual stumbling block. Pairing is once per
workstation.

`minSdk` is 29, so Android 10 testers are in scope and need the legacy route,
which requires one initial cable:

```sh
adb tcpip 5555
# unplug
adb connect 192.168.1.50:5555
```

### Check the udev group *before* you plug in

On Arch, installing `android-udev` is **not** sufficient and the symptom appears
only after the cable is in. `51-android.rules` sets `GROUP="adbusers"` with
`MODE="0660"`, and the package creates that group **empty** — so a fresh Arch box
has the rules and still cannot talk to a phone. Check first:

```console
$ id                      # is adbusers in the list?
$ getent group adbusers   # who is actually in it
```

If you are missing, `sudo usermod -aG adbusers $USER` and **log out and back in**;
group membership only refreshes at login, so a new terminal is not enough and
neither is restarting `adb`. Measured on this machine on 2026-08-22: rules
present, group present, group empty.

### Installing it

`make run` is the whole story, but four things about it are not obvious.

**Only the debug variant is installable.** `release` has no signing config and R8
is deliberately deferred until the keep rules for Room, Hilt and
kotlinx-serialization can be tested against a real release build
(`build-logic/src/main/kotlin/AndroidApplicationConventionPlugin.kt`), so
`assembleRelease` produces an *unsigned* APK that no device will accept. Anything
you run on a phone is the debug build: `debuggable`, unminified, and entirely fine
for use — just not a release rehearsal.

**With more than one device attached, `make run` is ambiguous.** It is
`./gradlew :app:installDebug` followed by `$(ADB) shell am start`, and neither
half takes a serial — so a running emulator makes the install fan out and the
`am start` fail on *"more than one device"*. Name the target:

```console
$ adb devices -l                          # copy the serial
$ ANDROID_SERIAL=<serial> make run
```

`ANDROID_SERIAL` is read by both AGP and `adb`, which is why it is the one knob
that steers the whole target rather than just half of it. The `ADB` variable the
Makefile documents only reaches the second command.

**The reminder does nothing until you visit its settings row.**
`POST_NOTIFICATIONS` is the app's only hand-declared permission and it is a
*runtime* permission on API 33+, requested from the settings screen's reminder row
rather than at first launch (docs/ux/reminder.md §3). On a fresh install on a
modern phone, that means the end-of-day reminder is silently inert until you go
there — which reads exactly like a bug if you do not know it. Check the phone's
API level with `adb shell getprop ro.build.version.sdk`.

### If the device is one you actually use — read this

A phone carrying the PRD §5 30-day trial holds the only copy of the trial. Two
ways to destroy it, both easy:

- **`make itest` uninstalls the app**, and `allowBackup=false` (architecture §6)
  means the OS has no copy. See the warning above §4's widget block — it is not
  theoretical, one run wiped an emulator holding 345 events. Point `make itest` at
  a throwaway AVD, never at the trial device. `ANDROID_SERIAL` is how you make
  sure.
- **The debug keystore is per-machine** (`~/.android/debug.keystore`). PRD §7 names
  macOS as a fallback build environment, and a build from a second machine is
  signed with that machine's key — so it cannot install over the first one. The
  only way through is an uninstall, which is the data loss above. Build the trial
  app from one machine, or copy the keystore deliberately.

The JSON export is the only recovery path either way. Take one when real habits
exist, which also arms the 30-day export nudge.

---

## 4. Manual verification checklist

Architecture §8 puts instrumented tests outside CI, so this is the substitute.
Work through it for any change to the data path or the Today view; note in the PR
which parts you ran.

The clock-dependent checks below used to need an `adb` call into a debug
activity. They drive the settings screen now, which is the same code path a user
takes — so what they verify is the app rather than a test fixture beside it.

**The Storage Access Framework cannot be exercised off a device.** No test in
this repo opens a file picker — the export, import and CSV checks below are the
only thing that verifies a file is actually written and read. `SettingsScreenTest`
covers the Data section's rows, their disabled state and their status copy, and
`SettingsDataViewModelTest` covers what the ViewModel does with the `Uri` the
picker returns, but nothing above those knows whether a picker appears at all.

**What `make test` now covers on its own.** `TodayScreenTest`,
`HabitListScreenTest`, `HabitEditorScreenTest` and `SettingsScreenTest` render
those screens under Robolectric, so the empty, loading and unavailable states,
the weekly target's bounds, the disabled save, the archived row's action, the
fact that a tap reports the tapped row's own date and completion, the fact
that the settings rows draw the *stored* values rather than the defaults, and
every state of the export row's nudge — silent, never, today, a day count and
overdue, including that a running export outranks the nudge — are all checked
without a device. They are still listed below because the checklist
verifies them *through the real stack* — a tap that reaches Room and comes back,
and a habit that survives a process death — which a stateless render cannot.

`AppNavigationTest` goes one layer further: it launches the real `MainActivity`
under `HiltTestApplication`, so the production Hilt graph, the navigation graph
and all four routes are covered without a device too — including that the
settings screen resolves its `SettingsSource` binding and reads the real store.
What it deliberately leaves out is anything that **writes** — Room's
`InvalidationTracker` does not deliver in that setup, so a screen never re-reads
after a write (see docs/architecture.md §8). That is the part this checklist
still owns, and why the create, edit and archive steps below earn their place.

**Read the copy anyway.** The tests resolve every expected string from the same
`R.string` the composable renders, so a reword cannot fail them — by design, so
they survive a copy edit. What they do catch is copy in the wrong *place*: the
empty state rendering the remaining-count line is a failing test now, which is
the shape the 4b bug took. Wording itself is still yours to read.

**On an emulator**

- [ ] The app launches and `adb logcat -d -s AndroidRuntime:E` is empty.
- [ ] From the empty state, tap **Add a habit**, name it, save. It appears on
      Today with no restart. This one observation covers Hilt building the data
      layer, the command path, the event log being folded, the projection write,
      and the Room `Flow`.
- [ ] Create a **weekly** habit and check the target stepper stops at 7 and at
      1. Above 7 would throw out of `Schedule.Weekly`'s `require` rather than
      being rejected, so this is a crash if it is wrong.
- [ ] Open a habit from the list, change **only** its name, save. Its icon,
      colour, schedule and tag survive — an update is a whole-record write, so
      a field the form forgot to submit would come back as a default.
- [ ] Archive a habit: it leaves Today, and appears under **Archived** on the
      list with a *Bring back* action. Bring it back: it returns to Today.
- [ ] The mascot's count follows archiving — an archived habit stops being
      outstanding.
- [ ] Tap a row: it ticks, and its streak appears. Tap again: it unticks. A
      daily streak reads as a count, a weekly one in weeks.
- [ ] Force-stop and relaunch: completions and streaks are rebuilt from the log.
- [ ] The database exists — `adb shell run-as com.gawi.app ls -l databases`.
      To inspect it, **pull the `-wal` too**:

      ```sh
      adb exec-out run-as com.gawi.app cat databases/gawi.db     > /tmp/gawi.db
      adb exec-out run-as com.gawi.app cat databases/gawi.db-wal > /tmp/gawi.db-wal
      sqlite3 /tmp/gawi.db 'select type, payload from events;'
      ```

      Without the WAL you read a pre-checkpoint snapshot and will think writes
      were lost — the main file was 4 KB against a 181 KB WAL when this was
      written. A *missing* `-wal` is fine and means SQLite has checkpointed
      into the main file, so let that copy fail rather than chasing it.
- [ ] Settings persist. Open **Settings** from Today's app bar — the gear, not
      the list glyph beside it — and change the day cutoff.
      `files/datastore/settings.preferences_pb` appears after the **first
      write**, not the first read, so it will not exist until you do. Then
      force-stop, relaunch and reopen the screen: it reads the stored value
      back, not the default. **Put the cutoff back to midnight before moving
      on** — the next two checks both start from it, and neither restores it.
- [ ] **Day rollover, against a real clock.** Start from a cutoff at or before
      the current time — midnight does, which is why the check above restores it
      — and tick a habit, so there is a completion on today's logical date.
      *Then* set the cutoff a couple of minutes ahead and go back to Today:
      "today" becomes yesterday, so that row reads unticked. Leave the screen
      alone; when the boundary passes it flips back on its own. Getting the
      order wrong is what makes this pass vacuously: with the cutoff already
      ahead of now, the row is unticked before you change anything. This is
      still the cheapest way to force a boundary — `adb shell date` needs
      `adb root` and is refused on the Play images this project uses.
- [ ] **The mascot follows the clock, not just the data.** With something
      outstanding, set *Day is nearly over at* to a time just past now. The
      panel changes with no habit touched and no interaction — and the habit
      rows do not reload underneath it, which is the point of the repository
      subscribing to the settings twice with different dedupes.
- [ ] **Week start re-buckets what is already on screen.** With a weekly habit
      showing a ratio, change the week start. The ratio re-counts against the
      new week without leaving the screen. Unlike the cutoff, this is not
      prospective-only: nothing about a week is stored on an event, so it is
      recomputed on read.
- [ ] A cancelled tap still commits: tap, immediately press Back, relaunch, and
      the completion is there.
- [ ] **Export writes a file you can read back.** Settings → scroll to
      **Data** → **Export a copy**. Keep the offered name, save it into
      Downloads, confirm the snackbar, then:

      ```sh
      adb shell cat /sdcard/Download/gawi-export-*.json | head -c 400
      ```

      It prints JSON with your habits in it. Note the contrast with the
      database check above: SAF wrote outside app-private storage, so this
      needs no `run-as`. Nothing in the app points at that file, so delete it
      when you are done. This one observation covers the whole Storage Access
      Framework path — the picker, the grant, the `ContentResolver` stream and
      the serializer — none of which any test touches, because no test in this
      repo can open a picker.
- [ ] **The offered name is today's date, not yesterday's.** Set the day cutoff
      to 03:00, wait until after midnight — or simply check the name is today's
      while the cutoff is at 03:00 and the clock reads before it — and the save
      dialog still offers `gawi-export-<today>.json`. Proves the file name uses
      the wall clock rather than the logical date. **Put the cutoff back to
      midnight afterwards**; the rollover checks above start from it.
- [ ] **Cancelling the picker does nothing and says nothing.** Tap **Export a
      copy**, then press Back out of the save dialog. No snackbar, no file, and
      the row is still tappable. Proves the null-`Uri` path is a no-op rather
      than an error, which is the rule every Cancel on this screen follows.
- [ ] **Importing what you just exported changes nothing.** **Import a file** →
      pick the export from above. The snackbar says nothing was new, and Today
      is unchanged — same rows, same ticks, same streaks. Proves the dedupe by
      event id, and that an import is a merge and not a replace. It restores
      nothing because it changes nothing, which is the point.
- [ ] **A file that is not an export is refused without changing anything.**
      Import → pick a photo or any text file. The snackbar says it is not a
      Gawi export, and Today is unchanged. Proves a refusal is a message rather
      than a crash or a half-written log.
- [ ] **The export is visible in the import picker** without needing a "show
      all files" step. The one thing the type filter can get wrong that no test
      can see: a filter that hides someone's own backup from them is worse than
      one that shows a few extra files.
- [ ] **Both rows go quiet while the work runs.** With a log big enough to take
      a moment, the tapped row's explanation is replaced by *Writing the file…*
      and neither row answers a tap until it finishes. On a small log this is
      over before you can see it — that is expected, and `SettingsScreenTest`
      covers it instead.
- [ ] **A file far too large to be an export is refused, not fatal.** The picker
      shows essentially everything by design, so this is the likeliest wrong tap:

      ```sh
      adb shell 'dd if=/dev/zero of=/sdcard/Download/toobig.json bs=1048576 count=40'
      ```

      Import it. The snackbar says it is not a Gawi export, the app is still
      running (`adb shell pidof com.gawi.app` returns the same pid) and the log
      is untouched. Before the ceiling this was an `OutOfMemoryError`, which is
      an `Error` and so slipped past the guard around every other failure here —
      process death on the recovery screen with nothing said. Delete the file
      afterwards.
- [ ] **An export you do not interrupt ends in a closing brace.** Export into
      Downloads, then:

      ```sh
      adb shell 'cat /sdcard/Download/gawi-export-*.json | tail -c 120'
      ```

      It ends `}` rather than mid-token, and the `event_count` near the top
      matches what the log holds. That is the check that the reordering — encode
      first, open the document last — did not break the ordinary path.
- [ ] **Leaving the screen the instant you tap Save can leave an empty file, and
      that is a known gap.** Tap **Export a copy**, save, and press Back out of
      Settings immediately. Two outcomes are both correct: no file at all (Back
      beat the picker, so nothing was ever created), or a **zero-byte** file
      (the picker created the document and the screen died before the export
      started). What must *not* appear is a partial file — and if you import
      whichever file you got, it is refused as damaged, which is the property
      that makes the gap bounded. See `docs/ux/settings.md` §7; closing it needs
      an application-scoped coroutine, which is a decision rather than a patch.
      Delete the file afterwards so the next check starts clean.
- [ ] **Process death mid-export is not survived, and the file is refused rather
      than half-restored.** Repeat the check above but run
      `adb shell am force-stop com.gawi.app` instead of pressing Back. The file
      is empty or truncated — expected; `NonCancellable` survives cancellation,
      not a killed process. Now import it: the snackbar says it is damaged.
      Proves the residual gap is bounded, because truncated JSON does not parse
      and `event_count` would not match, so a half-written backup can never be
      silently restored as a partial one.
- [ ] **The count snackbar is readable before it goes.** Import an export
      holding habits this install does not have and read the whole line without
      hurrying; it uses the default short duration, and if that is too fast
      that is a real finding. The habits it adds cannot be deleted afterwards,
      only archived, so do this on a scratch install or be ready to archive
      them.
- [ ] **The whole recovery claim, end to end.** Export, then
      `adb shell pm clear com.gawi.app`, relaunch to the empty state, and
      import the file. Every habit, completion and streak comes back. This is
      the promise architecture §6 makes on behalf of `allowBackup="false"`, and
      it is the only check that tests it as a user would need it.

**The 30-day nudge** (PRD §5). Run these in order from a cleared install — they
build on each other, and the third is the one that has no JVM test behind it.

- [ ] **A fresh install is not nudged about losing nothing.** After
      `adb shell pm clear com.gawi.app`, open Settings → **Data**. The export
      row has *no* value line and the ordinary help underneath it. Proves the
      empty-log case: the stamp is absent here exactly as it is on a log full
      of events, and only the log tells the two apart.
- [ ] **A log with something in it and no backup says so.** Create one habit,
      then reopen Settings. The export row reads **Never exported** and the
      help line has become the nudge. Proves the split above, in the other
      direction, and that "never" is overdue immediately rather than in thirty
      days.
- [ ] **An import moves the row without leaving the screen.** From a cleared
      install again — `adb shell pm clear com.gawi.app` — open Settings while
      the log is empty, confirm the row is silent, then **without navigating
      away** tap **Import a file** and pick an export saved earlier. The row must
      switch to **Never exported** with the nudge *immediately*.

      This step exists because the obvious ordering hides the bug. The import
      check further down runs after an export, so the log already has events and
      the row is already saying something — which is why a reviewer, not this
      checklist, found that importing into an *empty* log left the row silent
      for up to five seconds. Watch for the five seconds specifically: a row that
      only updates after you leave and come back is the defect, not a pass.
      Creating a habit on Today and returning to Settings within five seconds
      checks the same mechanism from the other side.
- [ ] **A finished export records itself, and only a finished one.** Export a
      copy, keep the offered name, and return to Settings: the row reads
      **Last exported today** and the ordinary help is back. **This is the only
      check of the ordering** — the stamp is written after the output stream
      closes, so that it means "a file landed" rather than "a write was
      attempted", and substituting a `ContentResolver` to test that needs a
      Robolectric shadow this project does not use (docs/ux/settings.md §7).
- [ ] **A cancelled export does not count as a backup.** Tap **Export a copy**
      and press Back out of the save dialog. The row still reads whatever it
      read before. Proves the stamp follows the write and not the tap.
- [ ] **An import does not count as a backup either.** Import the file from
      above. The row still says today and the value does not move. Deliberate:
      an imported file proves a copy was readable, not that it is recent, so
      importing a backup from March must not silence the nudge for a month.
- [ ] **The stamp survives a restart.** `adb shell am force-stop com.gawi.app`,
      relaunch, reopen Settings: still **Last exported today**. Proves it is in
      the preferences file rather than in memory.
- [ ] **A month later, the nudge comes back.** Settings → **Date & time**, turn
      off automatic time and move the date forward 31 days — the device UI, not
      `adb shell date`, which needs root and is refused on a Play image. Reopen
      Gawi's settings: **Last exported 31 days ago**, with the nudge underneath.
      **Put the date back and re-enable automatic time afterwards**; a 31-day
      jump also sweeps every streak, so any check above this one has to be
      re-run from a clean state rather than after this. **Do not export while
      the date is forward.** That leaves a stamp dated in the future, which the
      journal deliberately reads as no stamp at all — so the row goes back to
      **Never exported** once the date is restored, which is correct behaviour
      and looks like a bug if you were not expecting it.
- [ ] **A settings edit does not reset the clock.** With a stamp in place,
      change the week start and come back. The value line has not moved. Proves
      the export stamp shares a preferences file with the three settings and
      survives a write that assigns all three of their keys.

**The CSV of completions** (PRD §5, docs/ux/settings.md §6). Its correctness is
mostly covered on the JVM — `CompletionCsvTest` pins every byte of the format
and `CompletionExportDaoTest` pins the query — so what is left here is the two
things no test in this repo can reach: the picker, and what a real spreadsheet
does with the file.

- [ ] **The CSV is written, and Excel will read it as UTF-8.** Settings →
      **Data** → **Export completions**. Keep the offered name — it should be
      `gawi-completions-<today>.csv` and not the JSON stem. Then:

      ```bash
      adb shell 'head -c 3 /sdcard/Download/gawi-completions-*.csv' | xxd | head -1
      # expect: efbb bf   -- the byte order mark, without which Excel mangles
      #                      any non-ASCII habit name
      adb shell 'head -2 /sdcard/Download/gawi-completions-*.csv'
      # expect: habit,logical_date,note   then the oldest logged day
      ```

- [ ] **The row count matches the projection.** Pull the database **with its
      `-wal`**, or the count lies in either direction (a pre-checkpoint snapshot
      under-reports; a stale main file over-reports):

      ```bash
      adb exec-out run-as com.gawi.app cat databases/gawi.db     > /tmp/gawi.db
      adb exec-out run-as com.gawi.app cat databases/gawi.db-wal > /tmp/gawi.db-wal
      sqlite3 /tmp/gawi.db 'SELECT COUNT(*) FROM completions;'

      # Count records with a parser, not with wc -l. A note may contain a line
      # break -- written through verbatim, see docs/ux/settings.md 6 -- so wc
      # counts newlines and over-reports. Name the file rather than globbing it:
      # `wc -l < ...*.csv` also fails outright once two exports are in the folder.
      adb pull /sdcard/Download/gawi-completions-$(date +%F).csv /tmp/ >/dev/null
      python3 - "/tmp/gawi-completions-$(date +%F).csv" <<'EOF'
      import csv, sys
      with open(sys.argv[1], newline="", encoding="utf-8-sig") as handle:
          rows = [r for r in csv.reader(handle) if r]
      print(f"{len(rows) - 1} data rows (header excluded)")
      EOF
      ```

- [ ] **A formula in a habit name stays text in a spreadsheet.** This is the
      security check and it is the reason the file is not written naively.
      Create three habits named `=1+1`, `Read, daily` and `say "yes"`, complete
      each one today, export, then open the file in LibreOffice on the host
      (`localc /tmp/gawi-completions-*.csv`, comma-separated, UTF-8). The first
      cell must **display** `=1+1` and compute nothing; the other two must each
      be a single cell. In the raw file the first field reads `"'=1+1"` — the
      apostrophe is the guard and a spreadsheet does not show it. Archive the
      three habits afterwards.

      Include a name with a **leading space before the sigil** — ` =1+1` — in
      the same pass. It must also come out as text. Note what this check does
      and does not show: measured 2026-08-21, LibreOffice leaves ` =1+1` as text
      whether or not leading-space removal is on, so this is not a reproduction
      of an exploit — it pins the guard's rule for readers nobody has measured.
      The case that genuinely evaluates, and the one worth keeping an eye on, is
      a **bare** `=1+1` with no apostrophe: convert a hand-made file holding one
      and confirm the cell really does compute, or this whole check can pass
      because the reader never evaluates anything.

- [ ] **Know what a `;`-locale Excel does with it.** Not a defect and not
      fixable in the bytes without breaking every other reader, so it is a check
      that you have seen it rather than one that can fail: on a German, French,
      Spanish or Dutch install, Excel splits CSV on `;` and puts every record of
      this file in column A. The fix for a user is the import dialog. See
      `CompletionCsv`'s KDoc for why no `sep=,` line is written.

- [ ] **Cancelling the picker does nothing and says nothing.** Tap **Export
      completions** and press Back out of the save dialog. No snackbar, no
      change, and `/sdcard/Download` gains nothing.

- [ ] **A CSV export does not touch the nudge.** The load-bearing negative, and
      the one worth running even when nothing else is. Note what the export row
      says, write a CSV, and return: the value line and the help line are both
      unchanged. A CSV holds no events, so treating one as a backup would
      silence the warning for a month over a file that could not restore
      anything. `CompletionCsvArchiveWiringTest` asserts this against the
      constructor and `SettingsDataViewModelTest` from the other end; this
      confirms it through the real graph.

- [ ] **All three Data rows go dead together.** Start a CSV export of a large
      log and, while it runs, confirm **Export a copy** and **Import a file**
      are both unavailable and that only the CSV row says it is working. Hard to
      catch by hand on a small log; the JVM tests own this and this is a
      sanity check.

- [ ] **An empty log still writes a usable file.** After `adb shell pm clear
      com.gawi.app`, export completions before creating anything. The snackbar
      says the file holds only its column headings, and the file is the header
      line and nothing else. Re-run the recovery check above afterwards, since
      this clears the app.

Clean up with `adb shell 'rm -f /sdcard/Download/*.csv'` — **quote the glob**,
or zsh expands it on the host first and the command looks like it ran while the
files stay put.

### Before running `make itest` — read this

**`make itest` destroys the app's data on the device it runs against.**
`connectedAndroidTest` uninstalls the app when it finishes, and an uninstall
deletes `/data/data`: the event log, every habit, the settings and the export
stamp. `allowBackup` is off (architecture §6), so there is no OS copy — the JSON
export is the only way back. **Export first, or use a throwaway AVD.** This was
measured rather than predicted: one run wiped an emulator holding 345 events and
30 habits.

What it buys is real, so this is a warning and not a discouragement: the write
journey it runs (create a habit, complete it, undo it, read back through the
real database) is the one Robolectric cannot do, and the widget-host check is
the only automated proof that Glance renders at all.

### The widget — *launcher only, and mostly not automatable*

**Built 2026-08-21.** The widget's logic is JVM-tested (`:widget`) and the write
journey is covered by `make itest`, so what is left here is the part that needs a
real launcher: **pinning a widget requires the user**, so no test can place one.
Decisions and reasoning are in [docs/ux/widget.md](ux/widget.md).

- [ ] **It is offered at all.** (`WidgetHostTest` covers provider binding and
      that Glance renders — *not* launcher discovery, which nothing automated
      reaches. So if that test passes and this step still fails, the launcher is
      the thing to suspect; if it fails too, the problem is below the launcher.)
      Long-press the home screen → *Widgets* → **Gawi**
      → *Today*. If it is missing, the provider did not merge: read
      `app/build/intermediates/packaged_manifests/debug/.../AndroidManifest.xml`
      for `com.gawi.widget.TodayWidgetReceiver` (needs `--rerun-tasks`; a stale
      merged manifest reports the old answer).
- [ ] **It draws today's habits** — each active habit's name with a checkbox,
      ticked to match the Today screen. **No streak**, deliberately (PRD OQ-5).
- [ ] **You can read it, in the theme the device is actually in.** Added
      2026-08-22, because this is where a shipped defect was found and this block
      had nothing that would have caught it: the widget set its background from
      `GlanceTheme` and never set a text colour, and Glance's default is not
      theme-aware, so a dark-themed device drew near-black text on a near-black
      surface at a contrast ratio of 1.59. It *rendered* the whole time, so every
      JVM test was green. `WidgetTextColourDarkTest` and its light-mode twin now
      measure the ratio of every text the widget emits, in both themes — so the
      part worth a human's eyes is what those cannot reach: **toggle the system
      dark-mode setting and look at the widget in both**. Specifically check the
      **checkbox glyph**, not just the label — it is the one thing on this
      surface whose colour the app does not choose. It takes
      `?android:attr/colorControlNormal` (unchecked) and `colorControlActivated`
      (checked) from Glance's own selector, which ships no `-night` variant, so
      it resolves in the **launcher's** theme against a background this app
      picked. Confirm by eye that both states stand out.
      `TodayWidget.kt` records why it is not simply pinned: handing a
      `GlanceTheme` colour to `CheckBox(colors = …)` throws at runtime, because
      every theme colour is resource-backed and `CheckBoxColors` refuses those,
      so pinning would mean inventing hardcoded literals while PRD OQ-4 is open.
      No test sees this colour either way.
- [ ] **A tap completes.** Tap an unticked row: it ticks. Open the app — Today
      agrees, and the mascot has reacted if that was the last one.
- [ ] **A tap again undoes.** Tap the ticked row: it unticks, and Today agrees.
      This is the half that separates the widget from a complete-only one.
- [ ] **A write in the app moves the widget.** This is the only check that
      exercises `ProjectionListener`, and nothing else can: complete a habit *in
      the app*, then go to the home screen **without tapping the widget**. It
      shows the tick. If it does not, the push is broken even though every JVM
      test passes — `ProjectionListenerTest` proves the call happens, not that
      Glance acted on it.
- [ ] **An empty install says so.** With no active habits the widget reads *"No
      habits yet"*, not a blank box. (Archive every habit rather than using
      `pm clear`, which destroys the log.)
- [ ] **Resizing keeps it usable.** Drag the handles: rows reflow and the list
      scrolls rather than clipping.

Known and expected, not a bug — but **much narrower since 2026-08-21**: a widget
left on the launcher across the day cutoff is now refreshed by a scheduled wake
(`RolloverWorker`, docs/ux/reminder.md §2), so it normally clears by itself. What
remains is that the wake is best-effort and not a deadline: a device deep in Doze
can still defer it, and then the periodic update is the fallback it always was.

So the stale-render behaviour below is still reachable and still worth knowing,
because it is the reason the tap path is built the way it is. A tap always writes
to the *right* date, because it re-reads rather than trusting the drawn one — but
the visible semantics invert while a render is stale: tapping a row drawn as
**ticked** finds today incomplete and therefore **adds** a completion, so the box
stays checked and nothing looks undone. The log is correct; the render was not
(docs/ux/widget.md §4).

**Provoking it now takes deliberately stopping the wake, and that is the point of
the change.** Moving the cutoff a couple of minutes ahead and waiting will
normally show the widget *refreshing*, because `RolloverWorker` runs. That
validates the fix; it does not
reproduce the fault. To see the stale render, stop the worker from running across
the boundary:

```console
adb shell dumpsys deviceidle force-idle
```

Then move the cutoff ahead and wait past it. With the wake deferred, the widget
keeps yesterday's ticks and the periodic update is the only thing left, which is
the pre-2026-08-21 behaviour. `adb shell dumpsys deviceidle unforce` afterwards.

A lag *without* forcing Doze is worth investigating rather than expected — but it
does **not** on its own mean the wake was never armed. At least three things
produce the same symptom: the wake armed and
deferred anyway (App Standby, an OEM battery policy, ordinary idle), the wake ran
and the push failed silently — `GlanceProjectionListener` catches `Throwable` and
only logs — or the push succeeded and Glance's own update did not. Raised in PR
review.

Tell them apart before concluding anything:

```console
adb shell dumpsys jobscheduler | grep -A5 com.gawi.app
adb logcat -s ReminderScheduler ReminderWorker RolloverWorker GlanceProjection
```

Pending work under `gawi.reminder.day-rollover` and no log line means armed and
deferred. No pending work means never armed, and `ReminderScheduler` will have
logged why. A `GlanceProjection` warning means it ran and the redraw is what
failed — which is the failure shape that looks identical to nobody having placed a
widget.

**Put the cutoff back to midnight afterwards**, for the reason §4's own rollover
check gives: those steps start from midnight, and this section sits below them, so
leaving it moved is how a later run passes vacuously.

### The reminder

Built 2026-08-21 (docs/ux/reminder.md). PRD §7 makes a **physical device** the
primary target for this as well as for the widget — OEM battery policies are the
whole risk and an emulator has none.

Every check here needs the reminder time moved to a couple of minutes ahead,
in Settings. **Put it back to 21:00 afterwards**, for the reason §4's rollover
check gives about itself: leaving it moved is how a later run passes vacuously.

**How to see whether anything was posted, and why it matters here.** Two of the
checks below assert an *absence* — silent when everything is done, and one per
day — so a command that cannot see a notification makes both pass without
proving anything. Use:

```console
adb shell cmd notification list | grep com.gawi.app
```

A posted reminder is one line, `0|com.gawi.app|1|null|<uid>`. For the copy and
the time it fired:

```console
adb shell dumpsys notification --noredact | grep -A30 "pkg=com.gawi.app" \
  | grep -E "android.title|android.text|when="
```

Note **`dumpsys notification`**, not `dumpsys notification_manager`: the latter
exists, exits zero, and contains no `NotificationRecord` section at all, so
grepping it for one reports "nothing posted" for every app on the device
including the ones that certainly did post. Measured 2026-08-21, after it turned
a reminder that had fired exactly on time into ten minutes of looking for a bug
that was not there.


- [ ] **It fires.** With at least one habit outstanding, set the reminder a
      couple of minutes ahead and lock the screen. A notification arrives saying
      *"N of M left today"*. Tapping it opens the app on Today.
- [ ] **It is silent when everything is done.** Complete every habit, set the
      time ahead again. Nothing arrives. This is PRD §6.1.5's second half, and
      the failure it guards against looks identical to success from the outside
      — so check it deliberately rather than assuming.
- [ ] **One per day, and this is the one only a device can show.** After a
      reminder has fired:

      ```console
      adb shell am force-stop com.gawi.app
      ```

      Then reopen the app, set the reminder time a couple of minutes ahead in
      Settings, and wait for it. **No second notification arrives** — the journal
      already stamped today, and it was read back in a process that did not write
      it.

      **The force-stop is the point of the check.** Skip it and nothing has been
      shown about the journal surviving a process ending: the app is alive
      throughout — moving the reminder time means opening Settings — so the read
      could come from the same in-memory `DataStore` that wrote the stamp, and
      the one thing this adds over `ReminderCheckTest` is the one thing it would
      not have done.

      Reopening also re-arms both wakes through `ReminderScheduler.start()`, which
      is the documented repair path, so this check exercises that for free and does
      not depend on whether `force-stop` cancels pending jobs by itself.

      Do **not** try this by forcing the job out of `dumpsys jobscheduler`. Once
      `ReminderWorker` has succeeded its unique work is finished and the only thing
      pending is the *rollover*, so the job you would find and force is the wrong
      one: no second notification appears, the check looks green, and nothing about
      the once-a-day rule was exercised.
- [ ] **Notifications off is admitted, not hidden.** Turn the app's
      notifications off in system settings and come back to Settings. The reminder
      row shows *"Notifications are off, so this reminder will not arrive"* with a
      target that leads somewhere — the permission dialog, or the system page if
      the dialog can no longer appear. The row must update **on resume**, without
      re-navigating.
- [ ] **The time still edits while notifications are off.** Tapping the row
      itself opens the time picker, not the permission. The time drives Momo's
      worried face whether or not a notification can arrive.
- [ ] **Survives doze and the vendor's battery optimiser.**

      ```console
      adb shell dumpsys deviceidle force-idle
      ```

      The reminder still arrives, late. It is *expected* to be late: architecture
      §7 makes delivery deliberately inexact and there is **no ceiling to quote**
      — WorkManager will not wake a device to deliver this. What must not happen
      is the failure below.
- [ ] **A very late wake stays quiet rather than lying.** Let a deferred reminder
      land after the day cutoff (force-idle through midnight, or move the cutoff
      close). It must post **nothing**. A reminder at 00:30 saying *"5 of 5 left
      today"* is the bug: it describes a brand-new day, and it would consume that
      day's one reminder so the real 21:00 one never comes.

### Habit detail

Built 2026-08-21, and the first time PRD §5's retro window and per-completion
note are reachable by hand at all (docs/ux/habits.md §7). `make test` covers what
the screen draws and what a tap reports; what it cannot cover is a write going
all the way to Room and coming back, which is most of this list.

Open a habit from the **Habits** list — the row's name, not the Archive button.

- [ ] **The streak matches the Today row's.** Tick a habit on Today, open it, and
      the number agrees. A daily habit reads as a count and a weekly one in weeks
      with a `w`. Two screens drawing one habit's streak differently is the
      failure docs/ux/today-view.md §5 exists to prevent, and the shared
      `StreakUi` is what should make it impossible — this is the check that the
      sharing actually reaches both.
- [ ] **An unfinished daily habit still shows its live streak.** Open a habit with
      a run going, before ticking it today. It must not read `0`.
- [ ] **The oldest cell is drawn shut, and does nothing.** The leftmost of the
      five cells is struck through and dimmed. Tap it: nothing happens — no
      snackbar, no prompt, no tick. **The absence of a snackbar is the check**;
      a refusal message would mean the cell is being tapped and refused, which is
      exactly what §5 says not to do.
- [ ] **A past day asks first, and cancelling changes nothing.** Tap one of the
      three open past cells. The honesty prompt appears. Cancel, and the cell is
      unchanged. Force-stop and reopen: still unchanged. Cancelling has to leave
      the log untouched rather than defer a write, and only a restart proves the
      event was never appended.
- [ ] **Confirming writes to that day, not to today.** Tap a past cell, confirm,
      and the tick lands on *that* cell. Then go back to Today: the habit is
      **not** ticked there. This is the one worth running slowly — the 3-day
      window *accepts* a date one day off rather than refusing it, so a wrong date
      here looks like success and is only visible by checking which day moved.
- [ ] **Un-ticking a past day prompts too.** Tap a completed past cell: the same
      prompt. Confirm, and it clears.
- [ ] **Today's cell writes with no prompt.** Tap the rightmost cell: it ticks
      immediately. PRD §6.4 wants same-day logging and undo frictionless, so a
      prompt here is a bug.
- [ ] **A note survives a restart.** Long-press a completed cell, type a note,
      Save. Force-stop and reopen the habit, then long-press that cell again: the
      note is in the field. Force-stop matters — an in-memory projection would
      hold the note without it ever reaching the log.
- [ ] **Clear removes it, and that also survives.** Long-press the same cell,
      **Clear note**, force-stop, reopen: the field is empty. An empty note is a
      real write, so a clear that was skipped as a no-op would let the old note
      come back on the next read.
- [ ] **Long-press offers nothing on a day with no tick.** Long-press an empty
      open cell, and on the shut cell. Neither opens the sheet.
- [ ] **Creating a habit opens it.** Add a habit and save: you land on its detail
      screen, not back on the list. Press Back **once** — you reach the habit
      list, not the create form you just filled in.
- [ ] **The strip follows the day rollover.** With detail open, set the **day
      cutoff** a couple of minutes ahead and wait past it. The strip shifts by one
      day and today's cell moves with it, with nothing tapped. **Put the cutoff
      back to midnight afterwards.**
- [ ] **An archived habit still opens.** Archive a habit, then open it from the
      Archived section: it shows, and says it is archived. Unarchiving has to stay
      reachable, so a detail screen that refused to show one would be a trap.

---

### The day-rollover refresh

Also built 2026-08-21, and the same mechanism (docs/ux/reminder.md §2). This is
what §4 of this document and docs/ux/widget.md §4 previously listed as a known
widget limitation.

- [ ] **The widget follows the rollover without being tapped.** With the widget on
      the home screen and a habit ticked, set the **day cutoff** a couple of
      minutes ahead and wait past it without touching anything. The tick clears by
      itself. Before this worker existed, the widget kept yesterday's ticks until
      the provider's periodic update got through.
- [ ] **A cutoff edit re-arms it.** Change the cutoff again; the wake moves with
      it. A settings edit writes nothing to the log, so nothing pushes it — the
      scheduler's `SettingsSource` collector is the only thing that can, and this
      is the only way to see it working.
- [ ] **Put the cutoff back to midnight afterwards.** Same reason as above.

---

## 5. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `/dev/kvm is not found` (Android Studio adds *"Enable VT-x"*) | Virtualization off in firmware. See §1 Linux. On AMD the setting is **SVM**, not VT-x — Studio's wording is Intel-only. |
| `x86_64 emulation currently requires hardware acceleration!` | Same cause. `emulator -accel-check` confirms. |
| Emulator window black, or never appears | GPU renderer. Try `-gpu host`, then `-gpu software`. On Wayland also `QT_QPA_PLATFORM=xcb`. |
| AVD refuses to start on a Mac | Wrong ABI. Apple Silicon needs `arm64-v8a` (§2). |
| `adb devices` shows `unauthorized` | The RSA prompt was not accepted. Replug and confirm on the phone; `adb kill-server` to re-offer it. |
| `adb devices` shows `no permissions` | Linux udev rules or group membership (§3). |
| `adb devices` empty with a cable attached | On Windows, the USB driver. Everywhere, check the cable is data-capable and the phone is not in charge-only mode. |
| Gradle fails on a missing SDK package | Licences: `sdkmanager --licenses`. |
| `make run` fails with `adb: device '…' not found` | Nothing attached, or `ANDROID_SERIAL` points at something that has gone away. |
| `make run` fails with `adb: more than one device/emulator` | Two or more targets attached. Set `ANDROID_SERIAL` (§2). |
| `avdmanager create` prints `Could not load devices from …/devices.xml` | Harmless. The device profile is still applied and the AVD boots (§2). |
| Works in Android Studio, fails in the terminal | Two different JDKs. Compare `./gradlew -version` with Studio's Gradle JDK setting. |
