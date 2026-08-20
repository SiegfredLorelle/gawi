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
because launchers and OEM battery policies differ from emulators. None of that
exists yet, so nothing in this repo yet *requires* a device — but the setup is
here so it is not discovered later.

1. On the phone: **Settings → About → tap Build number seven times**, then
   **Developer options → USB debugging**.
2. Connect it and accept the RSA fingerprint prompt.
3. `adb devices` must show `device`. Anything else means step 4.

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

---

## 4. Manual verification checklist

Architecture §8 puts instrumented tests outside CI, so this is the substitute.
Work through it for any change to the data path or the Today view; note in the PR
which parts you ran.

The clock-dependent checks below used to need an `adb` call into a debug
activity. They drive the settings screen now, which is the same code path a user
takes — so what they verify is the app rather than a test fixture beside it.

**The Storage Access Framework cannot be exercised off a device.** No test in
this repo opens a file picker — the export and import checks below are the only
thing that verifies a file is actually written and read. `SettingsScreenTest`
covers the Data section's rows, their disabled state and their status copy, and
`SettingsDataViewModelTest` covers what the ViewModel does with the `Uri` the
picker returns, but nothing above those knows whether a picker appears at all.

**What `make test` now covers on its own.** `TodayScreenTest`,
`HabitListScreenTest`, `HabitEditorScreenTest` and `SettingsScreenTest` render
those screens under Robolectric, so the empty, loading and unavailable states,
the weekly target's bounds, the disabled save, the archived row's action, the
fact that a tap reports the tapped row's own date and completion, and the fact
that the settings rows draw the *stored* values rather than the defaults, are
all checked without a device. They are still listed below because the checklist
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

**Physical device only** — nothing here is built yet, so these are placeholders
that come alive with the widget and the reminder (architecture §8, PRD §7):

- [ ] Home-screen widget: renders, taps complete, refreshes after an in-app change.
- [ ] End-of-day reminder fires, and stays silent when everything is done.
- [ ] Survives doze and the vendor's battery optimiser.

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
