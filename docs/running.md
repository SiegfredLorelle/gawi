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
been run by anyone here — they are marked as such. A physical device joined them
on 2026-08-22 — a Nothing A059 on Android 16 (API 36) — but **over wireless
debugging**: §3's USB path was attempted on Linux and never got as far as
enumeration, so it stays unverified, and the macOS and Windows device notes are
unrun like the rest of their sections. Corrections welcome; that is what those
markers are for.

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
  | `make lint` | `bash scripts/check-citations.sh` then `gradlew.bat spotlessCheck detekt lint :app:assembleDebug` |
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

## 3. A physical device — *wireless path verified on Linux; USB unverified*

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
  `adb kill-server && adb start-server` — with the caveat about *which shell*
  starts that server below. Or skip USB altogether: wireless debugging needs
  none of this.
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
workstation. Two more things, both measured on the Nothing A059 on 2026-09-02:
`adb mdns services` lists both ports (`_adb-tls-pairing` and `_adb-tls-connect`)
once the toggle is on, so nobody has to read them off the phone; and the pairing
code dies the moment the pairing dialog is closed or reopened — two `adb pair`
attempts on a code that had been read out a minute earlier failed with
`protocol fault (couldn't read status message)`, and the third, sent within
seconds of a fresh code, paired. mDNS also opens a second transport for the same
phone, so `adb disconnect <name>._adb-tls-connect._tcp` it (or set
`ANDROID_SERIAL` to the `ip:port` one) before `make run`, which cannot pick.

**Wireless debugging needs no udev rules and no group membership**, because udev
governs USB device nodes and this path has no USB node in it. That makes it the
way past a `no permissions` phone without logging out — and on 2026-08-22 it was
the only way in at all. On Linux it is worth trying before the USB path below,
not after it.

`minSdk` is 29, so Android 10 testers are in scope and need the legacy route,
which requires one initial cable:

```sh
adb tcpip 5555
# unplug
adb connect 192.168.1.50:5555
```

### If USB never shows up at all

Before auditing udev rules or adb groups, check the phone is on the USB bus at
all. A device missing from `lsusb` is a cable, port or phone-mode problem, and no amount
of udev or adb work will touch it. **Charging proves nothing** — only that VBUS
and ground are connected, which a charge-only cable does too.

```console
$ lsusb                              # the phone should appear by name
$ ls /sys/bus/usb/devices/usb*/      # per-port detail when it does not
```

`dmesg` is the usual tool for this and it is **not available here**: the machine
runs with `kernel.dmesg_restrict = 1`, so kernel logs need root and USB
enumeration cannot be read from them. `lsusb` and `/sys` are the substitutes.

Measured on 2026-08-22: the phone above never enumerated, on any port or cable,
while charging normally throughout — and wireless debugging is what got the app
on. That is why the USB half of this section is still marked unverified.

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

**Then beware that any `adb` command silently starts a server.** With no server
running, an `adb devices` from a shell that is *not* in `adbusers` forks one
**without** the group, and that server cannot open the phone's USB node however
correct the rules are. So the `adb kill-server && adb start-server` above will
undo the fix rather than complete it if it runs before the logout, or from a
script or tool that inherited the old groups. Verify the *server's* groups, not
the shell's:

```console
$ getent group adbusers                            # note the gid
$ grep ^Groups: /proc/$(pgrep -f 'adb -L')/status  # the gid must be in this list
```

Whoever holds the group has to be the one who starts the server, and nobody else
may call `adb` while it is down.

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
Makefile documents only reaches the second command. Confirmed on 2026-08-22 with
a phone and an emulator attached at once: the install landed on the phone alone,
checked against the two targets' install timestamps.

**The reminder does nothing until you visit its settings row.**
`POST_NOTIFICATIONS` is the app's only hand-declared permission and it is a
*runtime* permission on API 33+, requested from the settings screen's reminder row
rather than at first launch (docs/ux/reminder.md §3). On a fresh install on a
modern phone, that means the end-of-day reminder is silently inert until you go
there — which reads exactly like a bug if you do not know it. Check the phone's
API level with `adb shell getprop ro.build.version.sdk`.

### If the device is one you actually use — read this

**There is no 30-day trial any more** — PRD §5's criterion was waived on
2026-08-23 — but nothing in this section ever depended on the data being a
*trial*. It depends on the device holding the only copy, which is true of any
real use and true from the first habit you create: `allowBackup` is off, so there
is no second copy anywhere by design. Two ways to destroy it, both easy:

- **`make itest` uninstalls the app**, and `allowBackup=false` (architecture §6)
  means the OS has no copy. See the warning above §4's widget block — it is not
  theoretical, one run wiped an emulator holding 345 events. Point `make itest` at
  a throwaway AVD, never at a device holding data you want. `ANDROID_SERIAL` is
  how you make sure.
- **The debug keystore is per-machine** (`~/.android/debug.keystore`). PRD §7 names
  macOS as a fallback build environment, and a build from a second machine is
  signed with that machine's key — so it cannot install over the first one. The
  only way through is an uninstall, which is the data loss above. Build the app
  you actually use from one machine, or copy the keystore deliberately.

The JSON export is the only recovery path either way. Take one when real habits
exist, which also arms the 30-day export nudge.

---

## 4. Manual verification checklist

Architecture §8 puts instrumented tests outside CI, so this is the substitute.
Work through it for any change to the data path or the Today view; note in the PR
which parts you ran.

**The widget and accessibility blocks are deferred, not dropped** (recorded
2026-08-23). PRD §5 wanted them run before Phase 1, on the reasoning that Phase 1
adds screens and replaces the three-face placeholder on both the Today view and
the widget — the surfaces these checks exist to verify. Phase 1 then turned out
to open with a whole-app restyle rather than with those screens (PRD §8, OQ-4
widened to the visual identity), which makes the argument sharper, not weaker:
a TalkBack pass, a 200% font-scale pass and a widget legibility check run *now*
would all be measuring a theme that is about to be replaced.

**Their trigger was the restyle landing, and it has fired** (2026-08-23): the
designed scheme and the retuned hues are in the code, so the theme these checks
would measure is no longer about to be replaced. **They are due**, and part of
the debt has since been paid. The accessibility block below and the widget block
are live work, and the restyle block before them is new and comes from the same
change.

**What has actually run, and where.** Everything ticked in the restyle block and
three items in the accessibility block ran on an **emulator** on 2026-08-23, and
each says so at the tick. That is the honest ceiling for those checks: colour,
contrast and layout render the same there, so a tick means "seen and correct",
not "verified on the target device". What an emulator cannot settle is left
unticked and named at the end of each block — the TalkBack items, Accessibility
Scanner, and the widget on a launcher, which is the only end-to-end
`ProjectionListener` exercise and has still never run. The Nothing A059 pass
(§3) is what would upgrade the rest.

**The Nothing A059 pass ran on 2026-09-02** — Android 16 (API 36), 1080×2392 at
375 dpi, font scale 0.85, the Nothing launcher, TalkBack 17.0.1 and
Accessibility Scanner 2.5.1, over Wi-Fi adb — against a seeded event log
(fourteen habits, two streaks broken on different days, a six-day run, tags),
since the phone's own data was a single test habit. Every box below that names
that date ran there, and each says how. Two things about how it ran are worth
more than any one tick. **TalkBack cannot be driven from `adb shell input`**:
injected taps and swipes bypass the accessibility layer entirely, so a tap under
TalkBack *toggles* the row it lands on and a swipe at a row's height does too —
which is exactly the "direct tap" these boxes forbid, arriving by another door.
What can be driven is input focus, `input keyevent KEYCODE_DPAD_DOWN`, which
TalkBack announces for every focusable node, and what can be read is TalkBack's
*Display speech output* overlay (its developer setting) off a `screencap` taken
within half a second of the key. Everything that is not focusable — the panel,
the chip, headings, grid cells, trend columns, widget bodies — was swiped by
hand and reported by ear, and the boxes say which. **And one premise fell**: on
this TalkBack a Compose node that merges its descendants and carries a
`contentDescription` is read as the description *and then* every child text, so
the retro strip, the history grid, the trend columns and the chip all say more
than their description. The accessibility block has the recordings; the shape
of the fix is one modifier (`clearAndSetSemantics`) in four places — a plain
swap in three, and on the retro strip one that has to keep the cell's checkbox
role and toggle state — and it is not made here.

That deferral has expired. The widget drew on Glance's default theme on purpose
for two phases — a Glance tree cannot consume the Compose theme (architecture
§2), so the widget takes any palette separately, and this section said the
legibility check was due against the *unchanged* widget and would be owed a
second time once it had one. **It has one since 2026-08-28**
([visual-identity.md](ux/visual-identity.md) §7.4), because the check found a
real defect and the palette turned out to be its fix rather than styling laid on
top. So the second time has arrived and is what the widget items below now ask
for. Nothing in this section is deferred any longer.

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
      that makes the gap bounded. See `docs/ux/settings.md` §8; closing it needs
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
      Robolectric shadow this project does not use (docs/ux/settings.md §8).
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
- [ ] **Momo appears only when there is room.** Place the widget at its
      smallest (one row tall): name and checkbox, no face. Resize it to two
      rows: Momo's resting frame appears above the rows, in today's mood, and
      the rows still have room beneath her. `WidgetMomoTest` proves the tree;
      it cannot see whether a launcher's two-row cell clears 170 dp, which is
      the constant this check is really measuring. Then, with no habits, two
      rows tall: the face above "No habits yet".
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
      **checkbox glyph**, not just the label. Until 2026-08-28 it was the one
      thing on this surface whose colour the app did not choose — it took
      `?android:attr/colorControlNormal` (unchecked) and `colorControlActivated`
      (checked) from Glance's own selector — and this check is what caught the
      consequence: on API 29 it resolved to the platform accent and sat at
      2.91:1 against a background this app picked, freshly rendered. The app
      chooses it now, from `WidgetPalette`, and a JVM test does see it after all
      (`WidgetTextColourTest` measures both states in both themes, via the one
      reflective hop `TodayWidget.kt` explains). Confirm by eye anyway that both
      states stand out: what no JVM test can reach is how a real launcher
      translates the colour, which is where the defect lived.
      **Unticked on purpose, and here is the split.** The emulator half has run
      twice — 2026-08-28 on API 29 and 30, by eye and by sampling the rendered
      pixels — and it is what found the 2.91:1 glyph above. What the tick waits
      on is a phone, because this block's own standard is that an emulator is not
      enough for a widget: it lives in a launcher's process, and an OEM
      launcher's is not the emulator's. Read the body as done on emulators and
      owed on hardware, not as unstarted.
- [ ] **A tap completes.** Tap an unticked row's *glyph*: it ticks at once. Tap
      its *name* instead: nothing moves until the write round-trips (a second
      or so), then the glyph ticks — only the checkbox half flips instantly, so
      do not tap twice while waiting or the second tap undoes the first. Open
      the app — Today
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
- [ ] **A write in the app moves *both* widgets.** With the Today widget and the
      streak widget both placed, complete a habit in the app and go to the home
      screen without touching either. Both change. This is the same
      `ProjectionListener` check as above and it is listed separately because the
      failure it catches is different: a provider missing from
      `GlanceProjectionListener` still *renders*, so it looks placed and working
      and simply stops following writes. `ProjectionRefreshTest` reads the
      receivers out of the merged manifest, which is as far as a JVM test reaches.

**The streak widget — built 2026-08-29** (docs/ux/widget.md §6). Its own
provider, so its own picker entry, and the first one here carrying API 31
attributes.

**What `StreakWidgetHostTest` already covers, so these checks need not.** Run on
emulators at API 30 and API 37 on 2026-08-29: the provider binds to a real host,
Glance's session renders it, the rows body is what renders, its `LazyColumn`
translates to a `RemoteViews` collection with the "as of" footer pinned outside
it, and on API 31+ `targetCellWidth/Height`, `description` and `previewLayout`
resolve from `res/xml-v31` (3×2, and a non-empty description). Below 31 those
three fields **do not exist** on `AppWidgetProviderInfo` — reading one throws
`NoSuchFieldError`, which is the sharpest possible statement of why the provider
xml is split.

Two things that test cannot reach, so they stay below. The **row content**: a
`RemoteViews` collection's item views are materialised by the host when it lays
out, and an unattached `AppWidgetHostView` never lays out — the `ListView` comes
back present and childless. And anything about a **launcher's own cells, theme or
process**, which is the standard this block already holds.

**Seen by eye on a launcher, 2026-08-29** — API 37 emulator, Pixel launcher, dark
mode, both widgets placed and one daily habit; then again the same day with four
habits, one of them weekly. The boxes below stay unticked because this block's
standard is that an emulator is not an OEM launcher, but these were watched
rather than inferred and the next pass need not rediscover them:

- The rows render, which is the half `StreakWidgetHostTest` cannot see: habit
  name left, streak numeral right in `primary`, `as of Sat, Aug 29` pinned
  beneath. Dark palette correct.
- **The size gate flips on resize.** Placed at roughly 240×127dp it drew the
  compact form — a bare `1`, no header — and dragging it taller switched it to
  `Streaks` / `1 day`. That is `SizeMode.Exact` and `FULL_MIN_HEIGHT` doing what
  they claim, at density 320.
- **`1 day`, not `1 days`** — the plural, end to end, on the surface that draws
  it.

- **Days and weeks do not look alike — the second pass, with `Read` (daily,
  done today) beside `Stretch` (weekly, target 1, done today), and `Water` and
  `Journal` never completed.** Compact: `1` in `primary` and `1w` in `tertiary`,
  the two em dashes in `outline`. Full: `1 day` and `1 week` under the *Streaks*
  header. The inks were measured from the screenshot, not eyeballed, and they
  reproduce `gawiRole`'s dark-scheme numbers exactly — 10.44:1, 7.34:1 and
  5.31:1 against the widget's own surface. **The two inks are only 1.42:1
  apart from each other**, which is the whole reason the `w` and the unit word
  exist — in greyscale they are near-identical greys, so on this surface the
  suffix and the word carry the distinction alone, and they were both present.
- **The greyscale step cannot be screenshotted.** Turning on monochromacy
  (`settings put secure accessibility_display_daltonizer_enabled 1` and
  `accessibility_display_daltonizer 0`) reads back as set, and `screencap`
  returns the same colours as before — colour correction is applied on the
  display path, after the frame a capture reads. Judge it by eye on the
  device, or compute the luminance as above; a capture proves nothing here.
- **Never-completed reads as an em dash, not `0`**, on both rows that had
  no history.
- **This launcher's smallest cell is two rows, about 240×132dp**, and at that
  size four rows and the date all fit. The "one row tall, three rows and the
  date" case below needs a launcher whose cells come closer to the 110dp
  minimum; dragging the handle further up here snaps back.
- Placing it from the picker is a drag, and `input draganddrop` does it on
  API 37 — the only part of this block that had always needed a hand.

Still not seen: **three rows at the smallest size** proper, for the reason
above — the launcher, not the habits, is what this pass lacked.

- [ ] **It is offered, and the picker says what it is.** Long-press → *Widgets* →
      **Gawi**: two entries, *Today* and *Streaks*. On API 31+ the *Streaks*
      entry shows a description under the name and a preview of the rows; on 29
      and 30 it shows neither, which is correct rather than broken — those
      attributes only exist in `res/xml-v31`. The preview is in the **system
      face, not Outfit**, also correct: a picker inflates real XML and there is no
      bitmap escape there.
- [ ] **A fresh placement lands three cells by two** on API 31+, from
      `targetCellWidth/Height`. On 29 and 30 the launcher sizes it off `minWidth`
      instead, so a narrower first placement there is expected.
- [ ] **It dates its number.** The bottom line reads *as of* and then a weekday,
      a day and a month — no year, no clock time. This is the requirement
      docs/ux/visual-identity.md §7.1 makes non-negotiable, so it is the one
      element on this widget that must never be missing or clipped.
- [ ] **Three rows and the date at the smallest size.** Place it one row tall
      with four or more active habits: three habit rows, the date pinned beneath
      them, and the rows scroll. Four rows and no date is the failure — 94dp buys
      one or the other and the date is not the half that gives way.
- [ ] **The unit word appears only when there is room.** At the smallest size a
      weekly habit reads `3w` and a daily one a bare number. Resize to two rows
      and two columns wider: they become `3 weeks` and `12 days`, under a
      *Streaks* header. `StreakUiStateTest` pins the thresholds; only a launcher
      shows whether its cells clear them.
- [ ] **Days and weeks never look like the same number.** With one daily and one
      weekly habit both on a run, check all three signals are present at the
      larger size — the unit word, and two visibly different inks — and that at
      the smallest size the `w` and the ink still separate them. Then check it in
      greyscale (Settings → Accessibility → colour correction → monochromacy):
      the `w` and the word must carry it alone.
- [ ] **A break and a fresh habit read differently.** A habit whose streak has
      broken shows a muted `0` (and *was 12* at the larger size); a habit with no
      completions ever shows an em dash. If both show `0`, the two states have
      been collapsed and the widget is telling a new user they have failed.
- [ ] **It survives a rollover untouched.** Set the day cutoff a couple of
      minutes ahead, wait past it without touching anything, and a streak that
      depended on yesterday updates itself. Same mechanism as the Today widget's
      rollover check further down, and worth repeating here because this is the
      widget whose number can go stale *without any event at all* — which is why
      it carries a date in the first place.
- [ ] **TalkBack reads each row once, with the unit.** Swipe through the rows:
      each announces as *"read, 12 days"* — the **full** wording even where the
      widget is drawing `3w`, because a spoken "12" cannot say whether it counts
      days or weeks. The date is announced too. Nothing announces twice.
- [ ] **You can read it in the theme the device is in.** Both schemes, on
      hardware, sampled the way the API 29/30 pass sampled the Today widget. The
      role to watch is `tertiary` — the week-streak ink, and the only role in this
      module that no other surface draws, so nothing measured it against a real
      launcher before this widget existed.
- [ ] **200 % font scale.** Rows grow, a long habit name ellipsises inside its
      row rather than under the numeral, and the numeral is never pushed off the
      edge. The name and the streak have fixed slots, so what fails here is the
      slot width rather than the layout.

**The widget's text is in Outfit since 2026-08-25, as bitmaps** — a font
resource cannot reach a widget (measured 2026-08-24, docs/ux/visual-identity.md
§2), so each name is rasterised in our process and tinted by the host. Until
then this paragraph said the platform sans was "not a bug to file" and that no
check was owed; the reversal owed four, all on a launcher because that is where
the bitmaps are drawn and tinted. The list below has grown to six — the widget
palette added two on 2026-08-28 — and **one is still open**, the first:

- [ ] **It is Outfit.** Against the launcher's own clock and labels, the
      names' `a` and `o` are geometric and the `t` has no tail — the same test
      the typography block further down uses for the app. Both themes, and the
      text follows the theme on API 31+: force-stop after `cmd uimode night yes`
      or the running widget will not re-theme.
- [x] **API 29 or 30 emulator: every colour is resolved in our process.**
      Toggle dark mode with the widget placed. The expectation written here was
      that the text, the checkbox glyph and the background stay stale
      *together* until the next render. **Run 2026-08-28 on API 29 and 30, and
      they did not** — this check found the defect it was written for, and the
      two levels measured the same to the decimal. The background was
      resource-backed, so the host re-resolved it immediately while the name and
      the glyph kept the value baked at the last render, and the widget landed
      at 1.31:1 for the name and 1.60:1 for the glyph. The same run found a
      second defect no toggle was needed to see: the glyph was below the floor
      in dark mode even freshly rendered, at 2.91:1 checked and 1.60:1
      unchecked, taking the platform accent against a background this app chose.
      **Re-run the same day against the fix, on both levels and identical on
      each**, and the expectation this check was written with now holds. All
      three colours are day/night pairs from `WidgetPalette`, so they take one
      translation path, stay stale together, and stay readable throughout: the
      name at 16.59:1 in light and 14.82:1 in dark, the glyph at 5.56:1 checked
      and 5.18:1 unchecked in light, 10.44:1 and 5.31:1 in dark, unchanged
      across a toggle in either direction. Completing a habit repairs the
      staleness, so it lasts until the next write, rollover or 30-minute update.
      Two traps worth not rediscovering, both of which make a broken widget and
      a working one look identical. An `APPWIDGET_UPDATE` broadcast is **not** a
      render and will not repair the staleness — measure with one and you will
      conclude, wrongly, that nothing moved; a tap on the widget or a write in
      the app is a render. And on a freshly booted emulator `cmd uimode night
      yes` silently does nothing, printing `Night mode: no` back at you, until
      `adb root` has been run: a toggle that never happened looks exactly like a
      widget that correctly stayed light, so **read the setting back** before
      believing either. [ux/widget.md](ux/widget.md) has the mechanism.
- [x] **API 31 or later: the whole widget follows a toggle with no render.**
      Added 2026-08-28 with the widget palette, because the property changed
      hands. The background used to be a colour *resource* the launcher
      resolved; it is now a day/night pair the launcher picks from, so "it still
      follows" became a claim about this repo's code rather than about Glance's
      default, and the check above is the reason not to trust that reasoning
      unmeasured. Run on API 37 the same day: `cmd uimode night yes` then `no`
      with the widget placed and the app not running, and the ground, the name
      and the glyph all move together within about two seconds — 16.59:1 and
      5.18:1 in light, 14.82:1 and 5.31:1 in dark.
- [x] **200 % font scale.** Rows grow; a long name ellipsises inside the row
      rather than under the widget's edge; nothing clips vertically. The change
      lands at the next render, not on the spot — complete a habit in the app
      to force one. (Glance recomposes on locale, not on configuration.)
      **Run 2026-08-30 on `Small_Phone` (API 37), Pixel launcher, the Today
      widget at four by three.** The name bitmap for *Read* grew 75 × 40 px to
      130 × 71 px — the text is close to the 2× asked for; the *row* grew only
      64 → 71 px, because a row's height is the checkbox's floor until the text
      passes it. A 37-character name ellipsised to *Read a long …* with its
      bitmap ending at x = 469, against the rows' content edge at 501 and the
      widget's own edge at 517 — inside the row, and the full string is still
      the row's `contentDescription`. Nothing is lost vertically: the rows are
      a `ListView`, so the fourth row sits below the fold and **scrolls** into
      view rather than clipping, which is worth knowing before reading a
      part-drawn last row as a defect. One thing a re-run should expect: at
      200 % the body drops to the face-above-rows form, so the header, the mood
      line and the band are not on screen to judge here.
- [x] **An RTL *system* locale** — Settings → System → Languages, Hebrew or
      Arabic first. Not a per-app locale, and not the developer toggle: measured
      on 2026-08-25 on the API 37 emulator, `cmd locale set-app-locales` flips
      our app and leaves the widget as it was, because the launcher inflates the
      `RemoteViews` in *its* configuration, and `settings put global
      development_force_rtl 1` changed nothing at all, not even the status bar.
      What to see: the glyph sits on the right, and a Hebrew or Arabic name is
      shaped and read right-to-left. `BitmapTextTest` proves the glyphs land on
      the canvas; only a launcher shows whether the row mirrors around them.

      **Run 2026-08-30 on `Small_Phone` (API 37), Pixel launcher, Hebrew first
      and a habit named קריאה.** Both halves hold, and the launcher itself went
      Hebrew with them (`יום א׳, 30 באוג׳`, *חנות Play*), which is the evidence
      that this was the host's own configuration and not ours. Measured off the
      accessibility tree, same widget and same data in both directions, the
      content spanning x 49–501 either way:

      | | LTR | RTL |
      |---|---|---|
      | row 1's checkbox | 49–113 (flush left) | 437–501 (flush right) |
      | Momo's face bitmap | 52–177 | 372–497 |
      | the mood line | 201–473 | 77–349 |

      Read the middle row as the `ImageView`, not the pill `FrameLayout` around
      it (49–181 in LTR) — review caught the first version of this table quoting
      the container on one side and its child on the other, which made the pill
      look 7 px short of a mirror when it was not. Mirroring 52–177 about the
      content span gives 373–498 against a measured 372–497, so all three rows
      mirror to within a pixel.

      A clean mirror. קריאה shapes and reads right-to-left, from the platform's
      Hebrew face — Outfit's `cmap` has no Hebrew — and it still shapes
      correctly *in the LTR pass too*, which is `FIRSTSTRONG_LTR` doing its job
      on the paragraph while the run itself stays RTL.

      **The band not mirroring was this run's finding, and it was fixed the same
      day** — it is the band's own box under *The Momo widget and the large Today
      body*, not this one, and both the finding and its re-run are recorded
      there.

      **How to set the locale, because two obvious routes are dead ends.** The
      emulator's `-prop persist.sys.locale=he-IL` is silently ignored, and a
      Play Store image (`Small_Phone` is `google_apis_playstore`) refuses
      `adb root`, so there is no `setprop` either. It has to be the Settings UI
      — and on API 37 the language **search** crashes Settings outright
      (`LocalePickerBaseListPreferenceController.getSortedSuggestedLocaleFromSearchList`,
      NPE), so scroll instead. The list sorts by native name, which puts every
      RTL script in one clump just above the CJK tail: fling to the bottom, then
      step back up and עברית is about thirteen screens short of it. Adding a
      language does **not** switch to it — tap the row's drag handle (its
      `content-desc` is *Edit system language list, …*) for a **Move up** menu,
      then confirm. **Hebrew has been left installed as the second preferred
      language on this AVD on purpose**, so a re-run is only that handle and
      *Move up*. Read it back three ways before believing it —
      `getprop persist.sys.locale`, `settings get system system_locales`, and
      `am get-config`, which is the one that actually proves direction: it reads
      `…he-rIL,en-rUS-ldrtl…` against `…en-rUS-ldltr…`.
- [x] **A non-default Display size** — Settings → Display → Display size, Large
      then Small (or `wm density 400` on the emulator, `wm density reset` after),
      then complete a habit so the widget re-renders. The name must be as crisp
      as the checkbox glyph and keep its proportion to it. A blurry, oversized
      or clipped name means the bitmap is carrying the device's default density
      rather than the one it was drawn at, and the host has scaled it twice —
      review caught exactly that in the first cut, and none of the checks above
      would have, because they all run at the default size.
      **Run 2026-08-30 on `Small_Phone` (API 37) in both directions**, `wm
      density 400` and `wm density 260` against its 320 default. At 400 (×1.25)
      the checkbox went 64 → 80 px and the *Read* bitmap 75 × 40 → 91 × 50; at
      260 (×0.8125) the checkbox went 64 → 52 px and the bitmap 75 × 40 → 60 ×
      33. The checkbox and the bitmap's *height* land on the ratio exactly; its
      **width** runs about 3 % short of it (91 against the 93.75 the ratio
      predicts, 60 against 60.9) — an earlier draft of this said "exactly" of
      both and review caught it. That 3 % is not the failure this box is for,
      and the arithmetic is what says so: a bitmap drawn at one density and
      scaled again by the host is out by the *square* of the ratio, 56 % at
      1.25, not 3 %. The name and the glyph therefore keep their proportion, and
      both stay crisp — the letterforms have clean antialiased edges at 400,
      which is where a twice-scaled bitmap would have shown as blur. Expect the
      *body* to change underneath: at 400 the widget is about 151 dp tall and
      draws rows alone, at 260 it is tall enough for the header again, so this
      check is about the row, not about which body appears.

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

### The Momo widget and the large Today body — *launcher only*

Both of docs/ux/widget.md §7's surfaces. `MomoWidgetHostTest` and
`WidgetHostTest`'s large-body case bind them to a real host, so "the provider
binds and Glance composes it" is a machine's job; what follows is what only a
launcher shows.

**Seen by eye on a launcher, 2026-08-29** — API 37 emulator, Pixel launcher, dark
mode, the same four habits as the streak pass. Boxes stay unticked because an
emulator is not an OEM launcher, the streak block's standard; these were
watched and measured, not inferred:

- **The picker offers three entries**, and the *Momo* one shows its description
  and a preview that is the ground and the word with no face, as intended.
  `input draganddrop` placed it; it landed two by two.
- **The Momo widget's colours are the derivation, to the byte.** Sampled off the
  screenshot: ground `#0F545C`, the word `#B2E7EE`, 6.37:1 — the numbers
  `gawiRole`'s KDoc promises for `onPrimaryContainer` on `primaryContainer`.
- **The large body renders at four by three** — Momo on the pill, *Momo is
  pottering about.* beside her, a four-segment band, four rows. Woven segments
  `#7FD4DC`, outstanding `#324042`, 6.34:1 apart, again the derivation exactly.
- **The band is the checkboxes.** Tapping *Water* flipped its box and its
  segment on the same write, and the Momo widget beside it kept its word (one
  habit still outstanding, so still *pottering*).
- **Three columns is 240dp on this launcher, so the header stays** — 240 is
  over the 220 gate, and the mood line fits. **Two columns is refused**: the
  launcher will not resize a 180dp-minimum provider below three cells, so the
  face-above-rows body cannot be reached here at three rows tall. Like the
  streak block's "smallest size" case, that is the launcher's cell size and
  not the widget; a launcher with narrower cells is what shows the flip.
- **At three by two the Today widget drew rows alone**, because two rows here
  are 132dp and the face needs 170. That refutes `MOMO_MIN_HEIGHT`'s old KDoc
  claim that two cells land at 220 or more on every launcher, and the "two
  cells tall" wording widget.md §2 and momo.md §4 used; all three now say 170dp
  of room and name this launcher as the one where that is three rows.

Not seen there: greyscale by eye (the capture cannot show it), TalkBack, the
light scheme, and the two-column flip. **The Nothing A059 pass of 2026-09-02
took three of the four** — greyscale by the user's eye, the light scheme by
`cmd uimode`, and TalkBack by hand, which is where the two widget bodies failed
— and left the flip owed to a launcher with other cells; each box says how.

- [ ] **The Today widget grows a header at four by three.** Place *Today* and
      resize it to four cells wide and three tall (250×200dp on the canvas):
      Momo on a teal pill at the left, the mood line beside her, and beneath the
      line a band of thin segments — one per habit, filled where today is done.
      Then narrow it, still three tall, to the first width under 220dp the
      launcher allows: the header goes and the face sits above the rows again,
      the 2026-08-25 body. Then shorten it to under 170dp: rows alone. On the
      small-phone Pixel launcher neither is reachable — three columns is 240dp
      and the floor, and two rows is 132dp — so this box needs a launcher with
      other cells. `WidgetBodyTest` pins the two gates; only a launcher shows
      which side of them its cells land on.

      **Run 2026-09-02 on the Nothing A059's own launcher** (85 dp cells, five
      columns), driven over adb: long-press the widget's padding → *Resize*,
      then drag an edge one cell at a time. **Four by three** (341×256 dp)
      **grows the header**: Momo on the pill, the mood line, and a band of
      fourteen segments with the two done habits woven in `#7FD4DC` against
      `#324042` — the derivation exactly, sampled off the screenshot — over five
      rows. Three columns (256 dp) keeps it, with the mood line wrapping to two
      lines. **Two rows is rows alone**: 3×2 spans 170.7 dp of cells, and the
      launcher reports less than 170 after its padding, so this launcher's 2-row
      widget is under the face gate even though its cells are not — the
      2026-08-22 instance had been rows-alone since install for that reason.
      **The two-column flip is unreachable here too**: two cells is 170 dp and
      the provider's minimum is 180, so the launcher refuses, as the Pixel one
      refused its two columns at 160. The arithmetic for whoever has a third
      launcher: the face-above-rows body wants a width of at least 180 dp (the
      provider's floor) and under 220 dp (the header's gate), so either three
      cells of 60 to 73.3 dp or two cells of 90 to 110 dp. Nothing's 85 dp cells
      and the Pixel's 80 dp cells both fall in the gap between those windows,
      which is why neither launcher can show it. One row is refused as well
      (85 < 110). So: header seen, rows-alone seen, the flip still owed to a
      launcher with other cells.
- [x] **The band is the checkboxes.** Count the segments against the rows and
      tap a row: its segment flips with its box, on the same write. A band that
      disagrees with the rows beneath it has been given a rule of its own, which
      it must not have.

      **Run 2026-08-30 on `Small_Phone` (API 37) in both layout directions, and
      the second one fails.** In LTR it is exactly right: four habits, four
      segments, and tapping the *first* row lit the *first* segment — woven
      `#7FD4DC` sampled at x 201–269, the band's left end, with the three
      outstanding `#324042` running rightwards. Under a Hebrew system locale the
      rows mirror and the band does not. The same first row now has its glyph at
      x 437–501, flush **right**, while its woven segment is still at x 49–117,
      the band's **left** end — so the band's first segment sits where a
      right-to-left reader finishes rather than where they start, and the band
      reads backwards against the rows it is supposed to be repeating.

      `BandBitmap.render` had no layout direction to consult: it placed every
      segment at `left = index * pitch`, unconditionally, so index 0 was at the
      bitmap's left edge in both directions. That is the whole mechanism, and it
      is why no test caught it — the geometry was correct, it was the *reading*
      that was wrong. Distinct from the `BitmapText` side, which does settle
      direction, via `FIRSTSTRONG_LTR`.

      **Re-run 2026-08-30 after the fix, same emulator, and it passes in both
      directions.** `WovenBand` now reads the app's configuration and
      `BandBitmap.render` mirrors on it; [ux/widget.md](ux/widget.md) §8 has the
      arithmetic and the caveat that survives. Sampled off `screencap` at the
      band's own `uiautomator` node — `[49,283][349,293]` under RTL,
      `[201,283][501,293]` under LTR, the same node on both sides — with four
      habits, all outstanding, and the first row tapped:

      - **LTR**: woven `#7FD4DC` at x 202–268, the band's left end, with the
        three outstanding `#324042` running rightwards. Matches the pre-fix LTR
        reading of x 201–269, so nothing moved on this side.
      - **RTL**: woven `#7FD4DC` at x 281–347, the band's **right** end, beside
        the same row's glyph at x 437–501. Before the fix it was x 49–117. The
        gap now falls at the near (left) edge, x 49–54, which is the half of the
        arithmetic the wrong form gets backwards.
      - **The tap still flips both together** in RTL: tapping the first row
        returned its right-end segment to `#324042` on the same write.

      **Two notes on the recipe above**, which was followed as written. Move up
      now raises a *Change system language to …?* confirmation on this level, so
      it is two taps rather than one. And `am get-config` is the check that
      matters — `getprop persist.sys.locale` still read `en-US` after the
      handle's menu and before the confirmation, so it lags the thing being set.
- [x] **In greyscale the band still reads.** Monochromacy on: a woven segment
      and an outstanding one must still tell apart by lightness alone (3.78:1
      light, 6.34:1 dark, measured). `screencap` will not show you this — see
      the streak block above — so look at the device. **Looked at on the Nothing
      A059, 2026-09-02**, dark scheme, the 4×3 body with eleven woven and three
      outstanding segments, monochromacy set over adb (`settings put secure
      accessibility_display_daltonizer_enabled 1` and `…_daltonizer 0`, which
      work without root on this phone): "the two kinds of segments are clear
      even on monochrome". Restored after.
- [ ] **TalkBack reads the large body once.** The mood line, then each row with
      its state. Not the face and then the line, and nothing for the band.

      **Fails on the Nothing launcher, 2026-09-02, swiped by hand.** The header
      is not a stop at all — swiping into the 4×3 widget goes straight to the
      list, and *"Momo is regrowing a gill. Pick the thread back up."* is never
      spoken, though the mood line's `ImageView` carries it as its description.
      Each row is then **two** stops, not one: *"Read. In list, double tap to
      activate"* and *"not checked. Check box, double tap to toggle"*. The band
      and the face are silent, which is the one half that holds. Two things to
      check in the Glance body. The header sits outside the `LazyColumn` that
      the rows live in, and the rows are what TalkBack reached — see the Momo
      box below for why the list may be the mechanism. And the row's click and
      its `CheckBox` are separate nodes, so TalkBack lands on each; the box's
      stop said only *"not checked. Check box"* although `TodayWidget.kt` sets
      the habit's name as the `CheckBox`'s `contentDescription`, so a Glance
      description on a `CheckBox` is not surviving into the hosted
      `RemoteViews` — the name has to reach it another way (the checkbox's own
      text, which was deliberately dropped, or the row's description). Whether
      the Pixel launcher does the same is unknown — no AVD image has TalkBack.
      Accessibility Scanner on the same widget adds that the rows and boxes are
      75 px tall here, **32 dp**, under the 48 dp floor, and that the boxes
      share one description — the same lost name, seen from the other side.
- [x] **The Momo widget is offered, two by two, and says what it is.** Long-press
      → *Widgets* → **Gawi**: three entries. The *Momo* preview on API 31+ is her
      ground and a word with **no face** — deliberate, and widget.md §7 says
      why; the description under the name is what names her. **Seen on the
      Nothing launcher, 2026-09-02**: the picker's Gawi row reads "Momo,
      Streaks, Today"; expanded, *Momo* is "2 × 2" with "How Momo is doing, in
      one word" under the name and a preview of the ground and the word
      *pottering* with no face (a real host preview, a
      `LauncherAppWidgetHostView`, not a drawable); *Streaks* "3 × 2" with its
      line; *Today* "3 × 2". `input draganddrop` from the preview to the home
      screen placed each one.
- [x] **Her ground is the tank colour, in both schemes.** Light `#B4E9F0`, dark
      `#0F545C`, sampled off a screenshot; the word on it in `#00353A` / `#B2E7EE`.
      A flat colour, not the Today screen's gradient. **Sampled 2026-09-02 on
      the Nothing A059**, the placed 2×2 widget (400×400 px): dark ground
      `#0F545C` on 103,983 of its pixels with the word in `#B2E7EE`; light,
      after `cmd uimode night no` (which needs no root on this phone, unlike the
      AVD), ground `#B4E9F0` on the same 103,983 pixels with the word in
      `#00353A`. To the byte, both ways. Night mode was put back afterwards.
- [x] **The word follows the mood.** Complete everything → *thriving*; leave one
      → *pottering* or *worried* as the day goes; break a streak → *regrowing*,
      with the dimmer face. Same face as the Today screen at that moment.
      **Seen 2026-09-02**: *regrowing* with the faded-gill face while two
      streaks were freshly broken; *pottering* with the smiling face once both
      were
      picked back up; *thriving* with the sparkle face once all fourteen were
      done. *Worried* was not seen — it needs the clock past 21:00, and the pass
      ended before it. Each word change arrived with the app's own write (see
      the last box).
- [x] **With no habits she is still there**, under *No habits yet*. Archive every
      habit rather than `pm clear` to see it. **Seen 2026-09-02**: fourteen
      `HabitArchived` events appended to the log (the run-as recipe in §5) and
      the projection rebuilt — the Today screen showed *Momo is waiting for a
      habit.* / *No habits yet* / *Add a habit*, and the widget kept its ground
      and her smiling face with *No habits yet* in the word's place; the Today
      and Streaks widgets said the same three words. Deleting the fourteen
      events brought all fourteen habits back.
- [ ] **TalkBack reads the sentence, not the word.** Focus the widget: *"Momo is
      pottering about."* once, and never *"pottering"* as well.

      **Fails on the Nothing launcher, 2026-09-02, swiped by hand — and not in
      the way the box feared.** The widget is one stop that says *"Momo"*, then
      *"Actions available, swipe up and swipe down, double tap to activate"*:
      the launcher's own label for the widget frame and its move/resize
      actions. The next swipe leaves the widget for the neighbouring app icon.
      The sentence is **never** spoken, and neither is the word. The face
      `ImageView` carries the sentence as its description, but the frame
      (`LauncherAppWidgetHostView`,
      described "Momo" by the launcher) has no reachable child inside it, and a
      described container hides its unreachable children from TalkBack. What
      separates the bodies is the container, not clickability: the Streaks
      rows are stops of their own (*"Water 7 days"*) and carry no click action
      at all — `StreakWidget.kt` has none — and the Today rows are stops too;
      both bodies are Glance `LazyColumn`s, which land as a real list in the
      `RemoteViews` tree (the Today rows say *"In list"*), while Momo's body is
      a plain `Box` and `Column`. So the experiment for the second launcher is
      a one-item `LazyColumn` around the face, not a clickable. The user's
      *"double tap to activate"* on a Streaks row has no callback behind it and
      is itself a reason to re-listen. Whether the Pixel launcher labels its
      frame the same way is unknown.
- [x] **A write in the app moves all three widgets**, on the same commit.
      **Seen 2026-09-02**, all three placed and dumped before and after each
      write: ticking *Stretch* checked its box on the Today widget and moved its
      Streaks row 0 → 1 while Momo, still regenerating over *Read*, kept her
      sentence — as she should; ticking *Read* then checked its box, moved its
      row 0 → 1 **and** changed Momo's description to *"Momo is pottering
      about."* with the word *pottering*. One write, three widgets, all changed
      by the next dump about five seconds later.

### The reminder

Built 2026-08-21 (docs/ux/reminder.md). PRD §7 makes a **physical device** the
primary target for this as well as for the widget — OEM battery policies are the
whole risk and an emulator has none.

- [ ] **The status-bar icon is Momo.** When a reminder posts, the small icon
      is her silhouette — body, two fronds a side, eyes punched out — tinted by
      the system, not a bell and not a blob. `LauncherIconTest` proves the
      vector has fills; only the shade shows whether the eyes survive at 24 dp.

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

### The history grid

New with `:feature:insights` (2026-08-24) — PRD §5's per-habit heatmap,
docs/ux/insights.md §8. `make test` covers what the grid draws from a given month
and what the steppers report; what it cannot cover is the colour distinction
being *visible*, which is the point of the screen, and a month query reaching Room
and coming back.

Reach it from habit detail: **See full history**, under the five-cell strip.

- [ ] **Done and not-done are obviously different, in both themes.** Tick a few
      days, open the grid, and look at it from arm's length in light and in dark.
      The pair is measured at 4.41 and 6.94, so this is not really in doubt — what
      is worth confirming by eye is the other half of §8.1's claim: that a
      not-done cell is *quiet* against the page rather than invisible, and that
      you can still read its number.
- [ ] **Today is findable without hunting.** The ring, not a different fill. Do
      it on a day you have **not** ticked as well as one you have: the not-done
      case is the one that fails if the ring is ever replaced by a
      `secondaryContainer` ground, which measures 1.04 against the cell it would
      sit next to.
- [ ] **Nothing after today is drawn.** In the current month, the cells past
      today are empty — no ground, no number. A grid that drew them as not-done
      would read as a month already half lost.
- [ ] **A tap does nothing at all.** Tap cells: done ones, empty ones, today.
      No ripple, no prompt, no tick, no snackbar. Read-only is
      docs/ux/insights.md §3, and the absence of a *refusal* is the check —
      a message would mean the cell is being tapped and turned down.
- [ ] **The columns line up with the week start.** Change **Week starts on** in
      Settings from Monday to Sunday and come back. The header letters rotate and
      the whole grid shifts by a column. It must not need reopening.
- [ ] **Stepping back reads real months.** Step back past a month you have
      history in, then back again into one you do not: the second draws an empty
      month rather than repeating the first's cells. Then step forward to the
      current month — the forward arrow disappears there and nowhere else.
- [ ] **The month follows the day rollover.** With the grid open, set the **day
      cutoff** a couple of minutes ahead and wait past it. Today's ring moves a
      day, with nothing tapped. Worth doing at least once near a month end, where
      the whole grid should change month. **Put the cutoff back to midnight
      afterwards.**
- [ ] **200% font scale.** Six rows of cells at 200% overflow the screen: the
      column scrolls, and no cell clips its own number. Two-digit days are where
      this shows first.
- [ ] **A habit with no history at all.** Create a habit, open its history
      immediately. A month of not-done cells, no crash, and nothing that reads as
      an error — the habit is new, not failing.

---

### The Insights screen, and the rate trend

New 2026-08-24 with the other two Insights surfaces (docs/ux/insights.md §§8.7,
8.8). `make test` covers what each draws from given numbers; what it cannot cover
is a colour distinction being *visible*, a glyph existing in the device's font,
and an upgrade not losing anything.

- [x] **The upgrade, before anything else.** Install the *previous* build, make a
      habit or two and tick a few days, then install this one over it. Every
      habit and every completion must survive and the new start dates must appear
      — this is the first schema migration in the repo, `fallbackToDestructive`
      is deliberately absent, and a failure here is the one that costs real data.
      **Run on an emulator 2026-08-24**: a v1 database with 57 events and two
      habits came through with both versions at 2 and `created_on` populated from
      the log.
- [x] **Every tag bar is the same colour, and Untagged is still obvious.** Reach
      Insights from Today's app bar, pick Tags. The bars are all `primary` — a
      grey one would measure 1.07 against it, which is why the distinction is the
      *label* instead. Check at arm's length in both themes that "Untagged" reads
      as quieter than a tag name without reading as disabled.
- [x] **The bar track is visible where a bar is short.** Needs two tags with
      different totals; with one tag the bar is full width and the track is
      covered, so this check is silently vacuous otherwise. **Run on an emulator
      2026-08-24** with two: the track sampled `#D3E3E6` light / `#2C3A3D` dark.
- [x] **A habit created today reads a dash, not a low number.** Make a habit,
      open Insights, and look at its row under Habits — and at its rate card on
      the history screen. Five dashes is correct: it has failed nothing. A
      percentage here means the creation date is not reaching the clip, which is
      the whole point of projecting it. **Run on an emulator 2026-08-24**: a
      habit made that day read a dash in its row and across all five months.
- [ ] **Each period chip changes the window.** With history in more than one
      month, Month and Quarter must differ. With everything inside one month they
      will agree, and that is correct rather than broken — worth knowing before
      it looks like a bug. **Not yet run against data spanning two months**; the
      query window moving is pinned by `InsightsViewModelTest` instead, which is
      not the same claim.
- [x] **Every icon draws, in both themes.** Fifteen controls, all vectors since
      2026-08-24. The old check here was for tofu, which a vector cannot draw;
      the failure that replaced it is the opposite and worse. A `<path>` missing
      `strokeColor` inflates without complaint and draws *nothing*, and because
      each button is named through `contentDescription`, every semantics test
      passes on a control that renders empty. `GawiIconsTest` pins the XML, so
      what is left for the eye is that the strokes read at a glance and that
      `Icon`'s tint carries them in dark mode as well as light. **Run on an
      emulator 2026-08-24, in both themes**: all ten drawables drew and the
      strokes read at 24dp — the three app-bar icons, back, pencil, close, both
      chevrons, the stepper's pair and the FAB. Light and dark were walked
      separately rather than reasoned about from the one shared tint mechanism,
      because "they all go through `LocalContentColor`" is a reason to expect
      them to follow, not evidence that they did.

      Three things that cost time here and will again. This emulator **does not
      re-theme a running activity**, so `cmd uimode night yes` needs a
      `force-stop` after it or the screenshot lies — it silently returned the
      light screen once. Screen coordinates **are not stable between themes or
      between data states**: *See full history* sits about 96px lower once the
      habit has a live streak, because the `displaySmall` numeral appears above
      it, so a replayed tap script lands on a retro-strip cell instead. And a
      stray tap in that strip **writes a completion**, which is how this pass
      silently ticked the test habit; it was reverted, but the log keeps both
      events, which is what an event-sourced app is supposed to do. Drive this
      by screenshot-then-tap, not by a fixed script.
- [x] **The app-bar icons hold 24dp at 200% font scale, and that looks
      deliberate.** A behaviour change, not a regression: the characters these
      replaced were `titleLarge` and grew with `fontScale`; a 24dp `Icon` does
      not. Material-correct, and touch targets are 48dp either way — but the
      titles beside them still grow, so the thing to check is that the result
      reads as a decision rather than as clipping. **Run on an emulator
      2026-08-24**: the title grew about double, the three icons held their size,
      and there was no clipping and no collision. It reads as a row of controls
      beside a large title, which is the intended result.
- [x] **The directional icons flip under RTL, and the pager still reads
      forwards.** The three glyphs replaced — `←` (U+2190), `‹` (U+2039), `›`
      (U+203A) — are all `Bidi_Mirrored`, so the text shaper flipped them and
      the app got RTL correctness for free. A `VectorDrawable` does not: it
      needs `android:autoMirrored`, and the first cut of the set omitted it.
      Found in review, not by any of the checks above, because every one of them
      looks at a light-mode English screen. `GawiIconsTest` now pins the
      attribute in both directions, and this is the other half — that the
      framework honours it.

      **`debug.force_rtl` did not work on this emulator** and silently returned
      an LTR screen, which is the trap worth recording. What does work, without
      touching system settings, is a per-app locale:

          adb shell cmd locale set-app-locales com.gawi.app --user 0 --locales ar-EG
          adb shell cmd locale set-app-locales com.gawi.app --user 0 --locales

      **Run on an emulator 2026-08-24**: the Up arrow points right, toward the
      edge it now sits on; `list-checks` leads with its marks on the right; and
      in the month pager the *earlier* chevron sits on the leading right edge
      pointing right, which is backwards in RTL and therefore correct. Restore
      the locale afterwards — the second command above clears it.
- [x] **Each sparkline dot sits above its own month label.** The plot's x
      positions are coupled to the label row's column centres and nothing in the
      suite can see that — an earlier edge-to-edge spacing put the outer two
      dots about 27dp off, which a screenshot shows at a glance and a test never
      will. Look at a habit with two or more months of history. **Run on an
      emulator 2026-08-24**: the dot's centre and its label's centre both landed
      on the same pixel column.
- [ ] **200% font scale on both new surfaces.** The chips wrap rather than clip,
      the bar rows stay readable, and the rate card's five month labels do not
      collide. **Not yet run** — 200% was checked on the month grid, which is a
      different layout from either of these.
- [x] **An empty period says so, and says *which* empty.** Copy, not an empty
      list, and the pickers stay reachable so there is a way out of it. **Three
      different notices**, and which one appears is the check: no habits at all,
      every habit archived, and a period with no completions each say their own
      thing (docs/ux/insights.md §8.8). **Run on an emulator 2026-08-24**, all
      three — the first from cleared app data, and the archived one showing "1
      active day · 1 completion" above "Every habit is archived", which is the
      contradiction the three-way split was made to remove.

**The retrospective** (built 2026-08-29, docs/ux/insights.md §9) is the same
screen one period back, and its owed looks are the ones no JVM test can take:

- [x] **Step back to a quarter that holds real data.** On `gawi-api30` — the
      rootable image, so the clock can be walked (`adb root`, `settings put
      global auto_time 0`, `date @<epoch>`) — seed two quarters with a tagged
      habit each, then open Insights → Quarter → ◀. The label reads "Q2 2026",
      the headline changes, the trend has three columns with a dot centred over
      each, and ▶ is greyed only on the current quarter. Then flip to Year: the
      offset resets to now, so the label reads "2026" and ▶ is greyed.
      **Run on `gawi-api30` 2026-08-29**: Run tagged `health` logged on 10 and
      11 May and 3 June with the clock walked, Read re-tagged `career` today.
      Q3 → ◀ read "Q2 2026 · 3 active days", three columns (Apr 0, May 2, Jun 1)
      with each dot on its label, "best 2 days" on Run, ▶ live; Year reset to
      2026 with ▶ greyed. **Second pass, after the PR review**: Q3 read "So
      far, mostly career." and Q2 — whose Q1 held nothing — no sentence; ◀ was
      already greyed on Q2, since Q2 starts before Run's 10 May creation and
      nothing earlier can hold a habit, and a further tap did not move.
- [ ] **The focus sentence flips when the top tag does.** With `health` leading
      last quarter and `career` this one: "Focus shifted from health to career."
      Re-tag the leading habit so both quarters agree: "Still mostly career."
      Remove every tag: no sentence at all, not "Untagged".
      **Half run 2026-08-29**: the shifted sentence appeared on Q3 exactly as
      written, and Q2 — whose Q1 held nothing — drew no sentence. The "still
      mostly" and untagged flips were not exercised on a device; the mapper
      test pins them.
- [x] **200 % font scale on Year.** The eight-to-twelve trend columns are the
      densest row in the app: the counts stay on one line each, the initials
      under them do not collide, and the stepper label between its two arrows
      does not wrap. The initials are what bought the room, so this is the look
      that decides whether they were enough. **Run 2026-08-29** on eight
      columns: every count and initial on one line, "2026" between its arrows,
      the rows' "Every day · best 2 days" unwrapped. Also seen in dark.
- [x] **A row's best run reads as one line.** "Daily · best 31 days" beside the
      percentage, and a habit made this period with no run shows the schedule
      alone — no "best 0 days" anywhere. **Run 2026-08-29**: "Every day · best
      1 day" beside a dash on Read — the habit was created on the 29th and
      back-filled, so the creation clip leaves one day for the run and no
      finished day for the rate, which is the two rules agreeing. The no-run
      half was seen on the same pass: on Q3, Run — created in May, not yet done
      this quarter — read "Every day" alone beside "0%", no "best" anywhere.

---

### The restyle — both themes, once

New with the designed scheme (2026-08-23). Everything here is a thing the tests
cannot see: `GawiColorSchemeTest` asserts every contrast ratio the app draws in
both themes, which is the part a number can answer. Whether it *looks* like one
app is not.

**Ticked items ran on an emulator, not on a phone, and are labelled so.** That
is enough for this block: everything in it is colour, contrast and layout, which
an emulator renders with the same Compose and the same resource qualifiers a
device would. It is *not* enough for the widget or for TalkBack below, and those
stay unticked — a widget lives in a launcher's process against a background it
does not own, and TalkBack cannot be driven from `adb` at all. A tick here means
"seen and correct", never "shipped and verified on the target device"; the
Nothing A059 pass in §3 is still owed and is what would upgrade these.

- [x] **Every screen, in both system themes.** Settings → Display → Dark theme,
      and walk Today, the habit list, habit detail, the editor and settings in
      each. What the ratio test cannot catch: two roles that both pass and still
      look wrong together, and any surface that reads as a different app.
- [x] **A day streak next to a week streak.** `StreakBadge` distinguishes them by
      `primary` versus `tertiary` and a trailing `w`
      ([visual-identity.md](ux/visual-identity.md) §4.1). Both roles are measured
      to be a lightness step apart, so this check is the other half: that the two
      are *tellable apart at a glance*, in both themes, on a real row rather than
      in a swatch. Light mode's `tertiary` is a dark bronze rather than the gold
      the drawings showed — this is where that either reads as deliberate or does
      not.
- [x] **Habit detail's retro strip.** Three marker states, not two: a completed
      day is `primary`, an open day not yet done is `onSurfaceVariant`, and a
      shut day is `outline` — which should be the quietest of the three. The
      strip is the densest use of the scheme and the place a recessive role that
      is *too* recessive shows up. Look at today's cell especially: it is the one
      with a filled ground, and the ground is what made the first version of
      this fail (`visual-identity.md` §3).
- [x] **The editor's colour swatches, and the tick on every one.** All eight
      retuned hues take a black glyph by design. Look at the tick on each: the
      old palette drew six of the eight below the contrast floor and the ring
      around the selection hid it (§4.2), so a swatch that looks fine at a glance
      is exactly the failure mode here.
- [x] **Cold start in dark mode, watching for a flash.** Force-stop the app, then
      launch it. The window is painted from `values-night/themes.xml` before
      Compose runs, and its `windowBackground` was pointed at the scheme's dark
      surface for this reason. Any visible flip from a lighter grey to the app's
      background means the two have drifted apart.
- [x] **The theme setting, on a phone whose system theme is the opposite.**
      New 2026-08-26 ([ux/settings.md](ux/settings.md) §7). Settings →
      Appearance → Theme → Dark on a light phone: every screen flips
      immediately, and the app does not leave Settings while doing it — the
      Activity is recreated underneath on API 31+, so a lost scroll position or
      a reopened dialog is what a failure looks like. Then Light on a dark
      phone, then back to *Follow the system* and toggle dark from quick
      settings, which must move the app again.
- [x] **Cold start with a forced theme, on API 31 or later.** The check above
      this one, run with Dark chosen and the *system* in light mode.
      Force-stop, launch, watch the first frame. There must be no flash at all:
      `setApplicationNightMode` puts the choice in the configuration, so the
      window resolves from `values-night/` before Compose runs. **Run
      2026-08-26 on API 37** — the launch window came up `#0E1A1C` with the
      system in light mode, measured frame by frame off a `screenrecord`, so
      the light `#F4FBFA` never appeared.
- [x] **The same cold start on API 29 or 30, where it is a different check.**
      Its own item rather than a second half of the one above, because those
      two versions have no `setApplicationNightMode`. **Run 2026-08-28 on
      both**, `google_apis` x86_64 `medium_phone` emulators, Dark chosen with
      the system in light: nine cold starts each, sampled frame by frame off a
      `screenrecord`. The launch window came up light `#F4FBFA` and held it
      before the dark app replaced it — 66–331 ms on API 30, and 317–448 ms on
      API 29, which is the one place the two levels measurably differ. That is
      the flash API 31 and up does not have. Backgrounding the app and opening
      Recents was part of the check, and is where the doc turned out to be
      wrong: the thumbnail is a screenshot of the window and measured
      `#0E1A1C` on both.
      [ux/settings.md](ux/settings.md) §8 carries the numbers and the two
      claims that did not survive them.
- [x] **The status bar's icons in both forced modes.** The bars follow the
      resolved theme rather than the device's, so this is where that either
      holds or produces white-on-white — the exact failure `enableEdgeToEdge`
      was added for.
- [x] **The widget after switching**, on the home screen. It must stay in the
      launcher's theme and change nothing. That is correct behaviour and the row
      says so; the check is that the *row* said so, not that the widget moved.
- [ ] **The habit hues against a real photo wallpaper**, on the Today list. Not
      because anything composites against the wallpaper — nothing does — but
      because the eight are tuned to one lightness and the failure to look for is
      the set reading as muddy rather than as eight distinguishable colours.
- [x] **The app draws in Outfit, at the right weight, and knows which glyphs it
      cannot draw.** New with the type scale (2026-08-24). Three things, and only
      the first is easy. **The face**: Outfit is geometric, so its `o`, `a` and
      `0` are visibly circular against the system sans, and the status bar clock
      stays Roboto and gives a free side-by-side. **The weight**: the `wght` axis
      is named explicitly on each entry, and if that were ever dropped as
      redundant the whole app would render at the file's `fvar` default of 100 —
      hairline, everywhere. That is loud rather than subtle, which is the good
      news; a unit test pins it, so this check is a second line and not the only
      one. Also worth a look with the system *Bold text* setting on, where the
      roles ask for W700/W800 and should hit real instances rather than fake
      bold. **The glyphs**: five characters the screens draw as text — `☰`, `◔`,
      `⚙`, `✎`, `✕` — are not in this font and fall back to the platform face, so
      an app bar mixes faces at one size. Look at habit detail's bar, where `←`
      is Outfit and `✎` is not. Expected, documented, and an argument for icons
      rather than a bug to file. **Not** in that set, checked rather than assumed
      after review asked: `−` (U+2212) and `·` (U+00B7) are both present, which
      matters most for the weekly-target stepper — it draws `−` beside an ASCII
      `+` at one size, so an absent `−` would have been the most visible
      mismatch in the app. The habit-icon emoji are a different thing again and
      not worth checking here: colour emoji always come from the system's emoji
      font ([visual-identity.md](ux/visual-identity.md) §4.2). Seen on an
      emulator on 2026-08-24: face correct, weights correct, the five do fall
      back, and the stepper pair is wholly Outfit.

      **The glyph third of this is now history, and a re-run should skip it.**
      Those five characters stopped being drawn later the same day — every
      character-as-icon is a vector now
      ([visual-identity.md](ux/visual-identity.md) §7.5) — so the face and the
      weight are all that is left to look at here, and the icons have their own
      check in §4. The record above stays as run rather than being edited: it
      was true, and the fallback it describes is why the icons exist.
- [x] **200% font scale, a second time, because the face changed.** The pass
      below is ticked and was run on 2026-08-23 against Roboto. Outfit has its
      own metrics — wider, different x-height — so every clipping and overflow
      judgement in that pass was made about a face the app no longer draws. This
      is not a doubt about the old run; it is that the old run answered a
      different question. The `displaySmall` streak numeral and Settings' longest
      body paragraph are where a wider face would show first. **Re-run on an
      emulator on 2026-08-24 and clean.** Today's empty state wraps to two lines
      and keeps its button; Settings' longest body paragraph wraps to six lines
      and the notification notice wraps rather than clipping; habit detail wraps a
      three-word habit name to two lines, still draws the `displaySmall` streak
      numeral, and scrolls far enough that all five retro-strip cells and *See
      full history* are reachable — checked by scrolling to the end rather than
      by assuming the screen scrolls.

### Momo — the four faces, both themes

New with the character (2026-08-25, [momo.md](ux/momo.md)); the habitat, the
transition and the celebration joined on 2026-08-26. `MomoRenderTest`,
`HabitatRenderTest` and `CelebrationRenderTest` prove each mood draws, differs
from the others, and moves; what they cannot see is whether the motion reads as
a character rather than a screensaver, the things that are Settings reads, and
the one sequence that only plays while the frame loop runs.

- [ ] **All four moods on the tank, in both themes.** Content is the default
      with habits added and nothing done late in the day; tick everything for
      thriving; let the reminder hour pass with one habit open for worried;
      break a streak (a habit completed yesterday, skipped the day before) and
      open the app inside the three-day window for regenerating — the tank
      drains and one right-hand gill is short and pulsing. If any two are hard
      to tell apart with the app held at arm's length, that is a finding for
      momo.md §3, not for the tests.
- [ ] **The tank keeps the mood's tempo.** Behind Momo, four weeds sway and
      four bubbles rise: briskly while thriving, at the canvas's own pace while
      content, slower while worried. Regenerating drains the water, leans the
      weeds outward and greys them, and no bubble rises. If the weeds and
      bubbles ever look out of step with each other, that is a finding for
      momo.md §4 — they share one tempo by design.
- [ ] **A mood change is one Momo.** Tick a habit so the mood changes and
      watch the change: the body should glide from one float to the other with
      the face crossfading on it, never two bodies at different heights. The
      water should drain or refill on the same beat when regenerating is one
      end of the change.
- [ ] **Finishing the day plays once.** With one habit left, tick it: Momo
      hops, bubbles rush up from under the tail and the water brightens for a
      beat, then the thriving loop continues. Untick and re-tick: it plays
      again, because the mood left thriving and came back. Now background the
      app and return, and rotate the phone: nothing plays — a finished day is
      not re-celebrated (momo.md §6). TalkBack says nothing extra either: the
      line changing to "All done. Momo is thriving." is the whole announcement.
- [ ] **The pastel body on the light tank.** momo.md §2 calls this the softest
      edge on purpose. Look at whether the silhouette reads from the gills and
      eyes alone; if the body vanishes into the water, the fix is the tank's
      gradient, not Momo's colour.
- [ ] **A milestone plays bigger.** With a habit at six days, tick it: Momo
      hops twice, a wider burst rises, a ring of gold sparkles opens out around
      her and the water brightens harder; the line under the tank reads "7 days
      in a row. Momo is dazzled." for two seconds and the row's streak badge
      swells on a teal pill, then everything returns. Untick and re-tick: it
      plays again. If that habit was also the last one of the day, only the
      milestone plays, and the thriving line follows the milestone line.
      Background the app and return, and rotate: nothing plays (momo.md §6).
      A weekly habit reaching its fourth week does the same with "4 weeks" and
      a gold pill.
- [ ] **TalkBack on a milestone.** With TalkBack on and focus on the row,
      tick the six-day habit: after "checked", the panel's live region reads
      the milestone line once, and the mood line once more when it returns two
      seconds later; the badge announces nothing extra. Tick and untick any
      other row: each is followed by one sentence — the mood line and the
      remaining count — and nothing else.
- [ ] **Animator duration scale off** (Developer options → *Animator duration
      scale* → *Animation off*), then reopen Today. Momo must be still, at the
      resting frame, the weeds upright and the bubbles frozen, and the mood
      change on a tick must cut rather than glide; ticking the last habit must
      not play the celebration, and a habit reaching seven must not move
      anything — but its line still swaps and its badge still takes the pill,
      for the same two seconds. Turn it back on and restart the app; the float
      resumes. (Read once per composition, by design — `Animations.kt` says why
      it is not observed.) Nothing on the JVM can see this: the tests set the
      same switch to get a still frame, so they prove the still frame, not the
      switch.
- [ ] **200 % font scale.** The tank stays 250 dp; the copy under it grows and
      wraps and pushes the list down rather than clipping. The character must
      not shrink.
- [x] **TalkBack, once.** Swipe onto the panel: it is one node and should
      announce the mood's line once — "Momo is pottering about." followed by
      the remaining count — and never "image" or "unlabelled". If the tank and
      the caption land as two stops, the merge has been lost. **Heard on the
      Nothing A059, 2026-09-02**, TalkBack 17: one stop, the sentence and the
      count together, no "image". The overlay spelled it *"Momo is pottering
      about.. 3 of 14 left today"* — the copy's own full stop plus TalkBack's
      joiner, a nit to hear rather than a defect. The same line was read
      unprompted on a cold launch, and again each time the panel scrolled back
      into composition, which is the live region firing on appearance that
      today-view §1 predicted.
- [x] **The regenerating line names a habit** (2026-08-31, today-view §6).
      **Half seen 2026-08-31** and the box stays open for the other half. With
      three streaks broken on the same day the line read "Momo is regrowing a
      gill. Pick Read back up" — a real name, in the sentence, at API 37. That
      is the tie case, where the rule is "keep the user's own order", and it
      cannot be told apart from the ordering rule. **Still owed:** break one
      streak, then break a second a day later, and check the line moves to the
      newer break. Most recently broken is the rule and two breaks *on different
      days* are the only way to see it. `TodayUiMapperTest` pins which name
      reaches the state and `TodayScreenTest` pins that the panel draws it;
      neither can see whether a long habit name still reads as a sentence.

      **The other half, seen 2026-09-02 on the Nothing A059.** Seeded: *Read*
      completed 20–29 August (broken on the 31st), *Stretch* completed 24–30
      August (broken on 1 September), *Read* first in the list. The line read
      *"Momo is regrowing a gill. Pick Stretch back up."* — the newer break,
      over the one that sorts first. Ticking *Stretch* moved it to *"Pick Read
      back up."*, and ticking *Read* ended it: *"Momo is pottering about."*
- [x] **The app-bar chip** (2026-08-31, today-view §1). Seen on API 37: the
      title "Today" gives way to Momo's face and "4 left", and scrolling back
      restores the title. The face is the current mood's — the drained
      regenerating one, which is what made it obvious the mood really reaches
      it — and the swap is a crossfade, caught half-done in one screenshot.
      Re-checked after the review widened the gap between face and count from
      2 dp to 12 dp, since that is the axis a full bar runs out of room on.

      **Read the box below before trusting this tick.** It was seen at
      `wm size 720x820`, not at the AVD's own 720x1280, for the reason that box
      gives.
- [ ] **The chip on a full-height screen with a realistic habit count.** The
      trigger is `firstVisibleItemIndex > 0` — the panel has to leave the
      viewport *entirely* — and a short list cannot scroll that far. Measured
      on this AVD at 720x1280 / 320 dpi: the list viewport is 1056 px, the
      panel is 628 px and a row is 128 px, so the chip needs **nine habits**
      before it can appear at all. With four, the panel scrolls to a sliver and
      stops, and the title never changes. Whether that is right — the chip is
      for long lists, and with four habits Momo never really leaves — or
      whether the trigger should fire on "mostly gone" instead, is open in
      today-view §1.

      **Measured again 2026-09-02 on the Nothing A059**, 1080×2392 at 375 dpi:
      viewport 2060 px (879 dp), panel 722 px (308 dp), row 150 px (64 dp), so
      the chip needs **fourteen** habits here — it appeared with fourteen and
      forty pixels to spare, reading *"10 left"*. Nine on a small emulator,
      fourteen on a tall phone: the count is the screen's, not the design's,
      which is one more reason the question in today-view §1 is a design call.
- [x] **The chip at 200 % font scale** (2026-08-31). The face, "4 left" and all
      three action icons on one bar, nothing truncated. This is the case the
      chip replaces the title *for*, so it is the one that would have justified
      undoing that decision, and it did not. Same short-screen caveat as above.
- [x] **The milestone line in the chip** (2026-09-01, today-view §1 and §6).
      Done on the AVD at its own 720x1280, font scale 1.0, with ten habits — the
      four real ones plus six seeded — one of them holding a six-day run so a
      tick crossed the seven-day rung. Recorded with `screenrecord` rather than
      screenshotted, because the line holds for two seconds and a screencap
      round-trip is most of that: the frames show "9 left", then the milestone
      line for ~2 s, then "8 left". The row's badge swelled to a `7` on its pill
      at the same time, so the two treatments agree.

      **This is the box that earned its place.** The first build drew the
      *panel's* line here — "7 days in a row. Momo is dazzled." — and on the bar
      it truncated to "7 days in a row. Mom…", crowding the first action icon, at
      font scale 1.0 rather than only at 200 %. Every JVM assertion was green,
      and would stay green: a Compose text assertion passes on a node that draws
      its string clipped, so no test at any font scale could have caught this.
      The fix is a chip-length plural of its own
      (`today_chip_milestone_days`, "7 days!"), which is what
      `today_chip_remaining` already does for the count; re-checked on the device
      after, and it now fits with room before the icons.

      **The spoken half, by `uiautomator dump` mid-run**, which reads the node a
      screen reader consumes: `"7 days in a row. Momo is dazzled. 8 of 10 left
      today"` — the panel's full sentence *and* the count, while the drawn label
      is the short form. That divergence is deliberate and is the thing to
      re-check if either string is ever touched. Still not *announced*: the node
      is not a live region, which is the box below.

      Seeded habits and the tick were removed afterwards by restoring the
      database file pulled before any of it, so the four real habits and their
      history are untouched — verified by event counts and `integrity_check`.
- [ ] **TalkBack: the chip is one stop, and a tick under it is silent.** The
      first version of this box asked for something that cannot happen — "the
      announcement should come from the panel's live region only" — when the
      panel is a list item and is disposed the moment the chip appears. A
      by-hand pass would have "succeeded" without checking anything. What to
      check instead:

      Swipe onto the chip with the list scrolled down: **one** stop, announcing
      the mood and the count together, never a bare "image" or two stops for the
      face and the label. Scroll back to the top and swipe into the panel: it
      announces once when reached. Then tick a habit while the chip is up — the
      only thing that should speak is **the row's own checkbox**, because the
      chip is not a live region and the panel is not composed. Silence from the
      chip is the expected result here, not a failure; today-view §1 says why it
      is accepted and leaves making it a live region open. This is the check
      that would settle that, so run it before deciding.
      `chip_isNotALiveRegion` pins the property on the JVM, but only a screen
      reader can say what is spoken, and neither emulator image here has one.

      **Run 2026-09-02 on the Nothing A059, swiped by hand, and both halves
      hold — with one thing the box did not ask for.** The chip is one stop.
      Ticking *Guitar* under it spoke *"checked"* from the row and nothing from
      the chip; unticking spoke *"not checked"*. But the stop read *"Momo is
      pottering about. 3 of 14 left today. **3 left**"* — the description and
      then the drawn label. today-view §1 built the description on the premise
      that a described node's text is not read; on this TalkBack it is, so the
      count is spoken twice in two forms. The same shape leaks on the retro
      strip, the history grid and the trend columns (the accessibility block).
      The shape of the fix is `clearAndSetSemantics` where the description is
      set — a plain swap here, on the grid cell and on the trend column, but
      not on the retro strip, whose open cell chains its description onto a
      `combinedClickable` with a checkbox role and toggle state that the
      transcript shows being announced; clearing there must keep both, which a
      Robolectric assertion should pin first. The test that asserted the
      description was complete passes either side of it, so
      `chip_doesNotAlsoReadItsLabel` was added: the chip's node carries no
      text, and the label is found only in the unmerged tree. **Changed
      2026-09-02**; not re-heard, which is why the box is open.

      **What *was* checked, 2026-08-31, and how:** `uiautomator dump` reads the
      same node description a screen reader consumes, and on API 37 the chip's
      is `"Momo is regrowing a gill. Pick Read back up. 3 of 4 left today"` —
      the mood and the full count, in one node. That is worth doing before
      trusting any chip description, because the first build's said only the
      mood: a node with a `contentDescription` has its `text` ignored, so the
      drawn count was silently unspoken and every test still passed. What the
      dump cannot answer is the double-read, which needs the real thing.

      **Since 2026-09-01 there is a second description to dump**: during a
      milestone run the chip's is the milestone line followed by the count, the
      mood line dropping out. Worth reading mid-run for the same reason as
      above — the drawn label and the spoken sentence are different strings
      here, and only one of them is the one TalkBack reads. It is still not
      *announced*, because the node is not a live region; that is the decision
      this box exists to settle.

### The launcher icon

Built 2026-08-25 ([visual-identity.md](ux/visual-identity.md) §7.1, §8).
`LauncherIconTest` proves the three layers exist, draw and are wired; every
launcher masks and scales them differently, which is what is left.

- [x] **In the app drawer and on the home screen.** Momo's mark — face and two
      fronds a side on teal — under whatever mask the launcher uses (circle,
      squircle, rounded square). Nothing that carries meaning is clipped; a
      sliver of the lower fronds may be, by design.
      **Run 2026-08-30 on `Small_Phone` (API 37), Pixel launcher, under three
      masks rather than one** — Wallpaper & style → Icons → **Shape** offers
      Circle, Square, 4-sided cookie, 7-sided cookie and Arch, so the mask is a
      setting here rather than something to hunt a launcher for. Checked Circle
      (the default), Square and Arch, which is the most aggressive of the five:
      face, both eyes, mouth and all four frond lobes sit inside the mask in
      every one, and **nothing was clipped at all** — not even the sliver of
      lower fronds this item allows for. The ground sampled `#B4E9F0` off the
      drawer icon, which is `Color.kt`'s light `primaryContainer` exactly, the
      value `LauncherIconTest` pins.
- [x] **Small.** Drop it in a folder and look at it at the drawer's smallest
      size: the eyes and mouth still read as a face. That was the canvas's
      test for two fronds over three.
      **Run 2026-08-30 on `Small_Phone` (API 37)**, in a two-item dock folder.
      The preview draws the mark at about 42 × 42 px — 21 dp at this device's
      320 dpi, under half the 48 dp it gets in the drawer — and it still reads:
      two eyes and the mouth are separately legible and the fronds are still two
      lobes a side rather than a pink smudge. So the canvas's two-over-three
      call survives the smallest size the launcher draws. A note for whoever
      re-runs it: `input draganddrop` makes the folder only over a *short* hop
      between two dock icons; over a longer one the launcher displaces the
      target or flips the page instead, and chained `input motionevent` with a
      dwell does not merge at all.
- [x] **Themed, API 33+.** Wallpaper & style → *Themed icons* on. The icon
      becomes the woven thread — three warps and a weft — in the system tint,
      not a tinted face. Below API 33 the coloured icon stays; there is nothing
      to check there.
      **Run 2026-08-30 on `Small_Phone` (API 37)**, and correct: three warps
      crossed by one weft, drawn in the system tint on the themed ring, with no
      trace of the face. **The setting is not called *Themed icons* any more**
      on this level — it is Wallpaper & style → Home screen → **Icons** →
      *Style* → **Minimal**, against *Default*, and it needs an explicit
      **Apply**. Same monochrome layer underneath, so the item's expectation is
      unchanged; only the path to it moved.

      **What ships is three warps and one weft, and the drawable expected to draw
      four elements plus that weft** — noticed by review off the back of this
      run, not by the eye. `ic_launcher_monochrome.xml`'s first path carried a
      fourth subpath, `M54,45 V63`, commented as "the short weft under the
      centre one". It is *vertical* at x = 54, so it lay wholly inside the
      centre warp `M54,28 V80` at the same 9-unit stroke and painted nothing; a
      weft would be horizontal. So this box's expectation was met by an icon one
      element shy of what its own file described, and the two readings — dead
      subpath to delete, or a short weft mis-transcribed from the canvas — needed
      the artboard to tell apart.

      **Resolved 2026-08-30 against the artboard: three warps and one weft, so
      the subpath was dead and is deleted.** Nothing rendered changes, which is
      why this tick stands without a re-run — the file now describes what it
      already drew. `LauncherIconTest` could not see it either way, because it
      inspected `<path>` elements for colour and stroke width and never looked
      inside `pathData`; it now has one case that does, asserting three vertical
      warps and one horizontal weft. A subpath hidden inside another is still
      more than a test can see, but a weft that is not horizontal is not.

### Accessibility — *device only, and the layer no test reaches*

The automated half of this is already in `make test`: WCAG contrast ratios in
`WidgetTextColourTest` and `HabitColorTest`, the 48dp touch-target floor in three
screen tests, and semantics — roles, content descriptions, disabled state —
throughout the screen tests. Architecture §8 records why the one automated
ruleset worth wanting is not wired up yet.

What is left is what a ruleset cannot judge: whether the app is actually usable
without sight, and whether it survives a reader who needs it larger.

- [ ] **A TalkBack pass over the three core flows.** Turn TalkBack on, then add a
      habit, complete one from the Today view, and change the day cutoff — using
      **swipe navigation only, never a direct tap**. Direct tapping is what hides
      the failure: focus order and announcement are only observable when you are
      forced through the tree in order. Watch for a control that is reachable but
      unnamed, two targets that say the same thing, and a state change that
      happens silently (WCAG 2.4.3 and 4.1.3).

      **Partly run 2026-09-02 on the Nothing A059.** *Complete one from Today*,
      by hand: each row is one stop and speaks its state on toggle. What it
      speaks is *"checked. 📚. Read. 1. Check box. In list. 15 items"* — the
      **icon emoji is read by name** before the habit, and the streak is a
      **bare number** (*"1"*, *"7"*, and *"1w"* for a weekly row), because
      `HabitIcon` draws the emoji as plain text and `StreakBadge` draws
      `today_streak_days` = `%d` as plain text. Both heard by the user, not only
      read off the overlay. **Fixed 2026-09-02, unheard**: `HabitIcon` clears
      its semantics wherever it sits beside a name (`row_doesNotSpeakTheIcon`,
      `iconBadge_isDecorative`); the badge speaks `today_streak_*_spoken` —
      *"3 days in a row"*, *"1 week in a row"*, *"Streak broken, was 12 days"*
      — via `clearAndSetSemantics` after the milestone tag
      (`streak_speaksItsUnit`, `brokenStreak_speaksWhatWasLost`). *Change the
      day cutoff*, by D-pad: every settings
      row is one stop reading title, value and helper in order (*"Day is nearly
      over at. 21:00. When Momo starts…"*), so the row is reachable and named;
      the picker itself was not driven. *Add a habit* was not swiped. One
      "two targets that say the same thing" came from the Scanner instead: the
      fourteen *Archive* buttons on the habit list all announce *"Archive"* with
      no habit name.
- [ ] **A TalkBack pass over the Insights screen.** Two pickers and a list, and
      the thing to listen for is whether a bar row makes sense read aloud: the
      label, the total, and nothing announcing the bar itself. The bars carry no
      text, so a row is its label and its number — if that is not enough to know
      which tag is which, the row needs a spoken description of its own.
      Then the trend on Year: each column should be one stop reading "March, 15
      active days" in full, never a bare "M" — the initials are hidden behind
      the column's own description (insights.md §9.4). And the disabled ▶ on the
      current period should still be announced, as disabled, not skipped.

      **Run 2026-09-02 on the Nothing A059, swiped by hand.** A bar row is
      **three** stops — name, percentage, schedule, in that order — and the bar
      itself is never one. The disabled ▶ is a stop: *"Later period. Button.
      Disabled"*. The August column read *"August, 30 active days. **30. capital
      A**"* — the full form first, so no bare "M", but the number and the
      initial follow it, because the column merges its children under a
      description and this TalkBack reads both (`LabelledColumns`). The
      Scanner adds that the repeated *"0%"* and *"Every day · best 1 day"*
      texts across rows count as duplicate descriptions, which is the same
      unmerged-row structure seen from the other side. **Changed 2026-09-02**:
      `LabelledColumns` clears the column where it describes it; `a trend
      column speaks its month once, not its texts as well` pins it. The rate
      card's undescribed columns are untouched. Not re-heard.
- [ ] **A TalkBack pass over the history grid, swipe-only.** Its own item because
      it is the one screen in this app that **hides content from a screen
      reader** — the seven column letters carry `clearAndSetSemantics`, since `T`
      and `S` each name two days and are noise read aloud
      ([insights.md](ux/insights.md) §8.4). That is only defensible if the trade
      it was made for actually holds, so check both halves: **swipe through a
      full month** and confirm you never land on a bare letter, and that every
      cell says its weekday spelled out, its date and its state — *"Friday 14,
      done"*. Then check the two that are easy to get wrong: today announces
      itself as today and as *not done yet* rather than *not done*, and a day
      after today is not a focus stop at all. Thirty-one stops is a lot of
      swiping and that is the point — a calendar is read day by day, and if this
      is tedious rather than usable it is worth knowing before the trends screen
      copies the pattern.

      **Run 2026-09-02 on the Nothing A059.** Every cell of August carries
      *"<Weekday> <n>, done|not done"* — 31 nodes, no letter nodes anywhere in
      the tree, so the `clearAndSetSemantics` trade holds. Today is *"Wednesday
      2, today, not done yet"* on a habit not yet done and *"…, today, done"* on
      one that is; 3 September onwards are not nodes at all. Swiped by hand, a
      cell read *"Thursday 20, not done. **20**"* — the bare day number trails
      the description, the same leak as the strip and the columns
      (`HistoryGrid.kt` merges under a description). Tedium was not judged.
      **Changed 2026-09-02**: `DayCell` clears rather than merges; `a day cell
      speaks its label and not its number after it` pins it, and `the days up
      to today are drawn` now reads the numbers off the unmerged tree, where
      they still are. Not re-heard.
- [x] **The colour picker's swatch names.** Every swatch announces a name rather
      than a hex, and after the retune one of those names moved: the seventh is
      "Gold", not "Yellow", because the hue at that slot is `#9C851F` and calling
      it yellow would be a false description
      ([visual-identity.md](ux/visual-identity.md) §6.2). No *unit* test can check
      this — `HabitsUiMapperTest` pins only that the labels and the hues are the
      same length, and a name is not a checkable property of a hex (§4.3).

      **But it is checkable on a device, and that is better than listening.**
      `adb shell uiautomator dump` gives every swatch's `content-desc` together
      with its `bounds`; a screenshot gives the pixel inside those bounds. Pair
      them and the announced name is checked against the colour actually drawn,
      which is the whole defect §4.3 describes. **Run on an emulator on
      2026-08-23: all nine — the eight hues plus "Current colour" — matched the
      colour drawn at their own bounds.** Worth re-running rather than re-reading
      whenever a hue or a label moves, and it needs no TalkBack. Ticked on that
      basis; *focus order* is the TalkBack item above and is still owed, because
      this check cannot see it.
- [x] **"Current colour", on a habit older than the restyle.** A habit created
      before the retune keeps its hex, and the editor offers it as a leading
      ninth swatch (§6.3). It is the one swatch whose name describes a role
      rather than a hue. Check it announces as selected, and that tapping a real
      hue moves the selection off it **without taking it off screen** — that last
      clause is the bug review caught, where the row reflowed under the finger.
      Run on an emulator on 2026-08-23 against a habit holding the pre-retune
      red: nine swatches before and after the tap, no bounds moved.
- [ ] **The retro strip, specifically.** It is the densest thing here: five cells
      — four writable and one drawn shut — each carrying a day, a done state, a
      note marker and up to two gestures. Every one of those is in the spoken
      label by design (`RetroStrip`'s `cellAction`), so this is the check that the
      label is *legible as speech* rather than merely complete. A shut day is the
      one to listen to hardest: it must announce as unavailable, not as an
      unchecked box.

      **Fails on legibility, 2026-09-02 on the Nothing A059**, by D-pad and by
      hand alike: *"Day 30, not done. Mark done. **capital S. 30. Middle dot.**
      Check box"*, and the done cell *"checked. Day 2, done. Mark not done. Add
      or edit note. **capital W. 2. Check mark.** Check box"*. The label is
      right and complete; the cell's four child texts — the weekday letter, the
      day number, the ✓/· glyph and the note glyph — are read after it, because
      `cellAction` sets the description on a merged node and this TalkBack reads
      the merged text too. The shut day's own words were not isolated by ear;
      structurally it has no role and is disabled, so it cannot announce as a
      box. The shape of the fix is `clearAndSetSemantics` on the cell, but this
      is the one site where it is not a plain swap: the description is chained
      onto the `combinedClickable` that gives the cell its role and toggle
      state, and clearing the subtree must leave both announced or the cell
      stops being a checkbox to a screen reader. Pin that with a Robolectric
      assertion before the change lands. The JVM test that pins the description
      would not notice either way.

      **Pinned, then changed, 2026-09-02.**
      `anOpenCell_isACheckboxThatReportsItsState`,
      `theNoteAction_isALongClickOnlyWhereItIsOffered` and
      `theShutCell_isDisabledAndNotABox` were green before `cellAction`
      changed and after; `aCell_speaksItsLabelAndNothingElse` holds the four
      texts out of the merged tree. The `combinedClickable` stays *ahead* of
      `clearAndSetSemantics` in the chain — Compose clears everything after
      the clearing modifier, not before it — and that order is what the first
      two pins hold: reversing it turned both red. Not re-heard, so the box
      stays open.
- [x] **200% font scale.** Settings → Display → Font size, at maximum. Three
      screens already carry reasoning about this in comments — `TodayScreen`,
      `HabitDetailScreen` and `SettingsScreen` all scroll or floor a dimension
      because of it — and **nothing verifies any of it**. Check that no text is
      clipped, that the strip is still tappable, and that the streak's
      `displaySmall` has not pushed the strip off a short screen. Run on an
      emulator on 2026-08-23, including the `displaySmall` case, which needs a
      habit with a live streak to draw at all: nothing clipped and the whole
      strip still on screen. **Was owed again from 2026-08-24**, since this ran
      against Roboto and the app now draws in Outfit, whose metrics differ — and
      it was re-run the same day. The restyle block has what the second pass
      measured.
- [x] **Accessibility Scanner**, as a pre-release sweep rather than routine.
      Install Google's Accessibility Scanner, run it over each screen, and read
      the report the way you would a Lighthouse audit: the touch-target and
      contrast items are already asserted, so what it earns its place for is
      unlabelled controls and text-contrast cases the theme tests do not reach.

      **Run 2026-09-02 on the Nothing A059**, Scanner 2.5.1, its service enabled
      over adb (`appops set … SYSTEM_ALERT_WINDOW allow` and the
      `enabled_accessibility_services` setting) and its floating button tapped
      on each screen. **Settings and Licences: no suggestions.** Everything
      else fell into four classes. (1) *Text contrast* on every emoji icon badge
      — Today ×14, habit list ×14, detail ×1, editor's icon picker ×4 — measured
      as the glyph colour `glyphColorOn` declares against the badge tint
      (`#F5F5F5` on `#427FF6` = 3.44:1, wanted 4.5): the colour never paints a
      colour emoji, so this is noise while icons are emoji and would be real the
      day a plain character is allowed; a decision for visual-identity, not a
      bug. (2) *Multiple items have the same description*: the fourteen
      **Archive** buttons on the habit list (real — no habit name), the repeated
      rate and schedule texts across Insights bar rows (the unmerged rows
      again), and the "—" placeholders under the history's rate columns (noise).
      (3) On
      the 4×3 Today widget, *Touch target* on every row and checkbox — 75 px,
      **32 dp** here — and duplicate descriptions on the nameless checkboxes
      (real, both). (4) The disabled *Save* label in an empty editor, which is
      Material's disabled-text contrast. No unlabelled control anywhere, no
      touch-target hit in the app itself.

**Still owed, and an emulator does not discharge any of them:** the TalkBack
pass over *adding a habit* (the other two flows ran on 2026-09-02 and are
written up above), and the two widget bodies under TalkBack, which failed on the
Nothing launcher and want a second launcher before anything is changed. The
retro strip's spoken labels and Accessibility Scanner both ran on 2026-09-02;
what they found is in their boxes, not fixed. The widget's three device checks
in its own block are owed twice over: once against the widget as it stands and
again when it takes the palette (visual-identity.md §7.4).

Not in CI, and not automatable: TalkBack cannot be driven from the instrumented
source set, and §8's line that CI runs unit tests only is unaffected by this
block.

### The About section and the Licences screen

Built 2026-08-30 (docs/ux/settings.md §9). The JVM tests prove the two notices
are packaged and rendered; what only a device can show is how the section reads.

- [x] Settings scrolls to a fourth header, **About**, below Data. The Version row
      shows the build's `versionName` in its small grey line, has no primary
      middle line, and does nothing when tapped. **Seen 2026-09-02 on the
      Nothing A059**: *About* at the bottom under *Data*; *Version* with `0.1.0`
      in the
      small line and no middle one; a tap on the row changed nothing in the tree
      (dumped before and after, identical), and there is no clickable wrapper
      around it to have taken the tap.
- [x] **Licences** opens a screen titled Licences with two headings, Outfit then
      Lucide, each with a one-line role and the full notice text under it. Both
      texts scroll; the OFL runs to its section 5 and the Lucide notice to its
      MIT block. Back returns to Settings at the same scroll position. **Seen
      2026-09-02**: the OFL node is 4,387 characters and contains `5)` and
      TERMINATION; the Lucide node is 3,207 and contains the Feather MIT block
      down to its last line; three swipes scrolled the OFL to a 100 px sliver
      at the top with Lucide's text ending at the bottom edge. Back returned to
      Settings with *About* at the same y as before.
- [x] TalkBack: the Version row is read as one item with no "double-tap to
      activate"; each heading on the Licences screen is a stop of its own.
      **Heard 2026-09-02 on the Nothing A059**, swiped by hand: the Version row
      is one stop with no activate hint, and *Outfit* and *Lucide* are each a
      stop of their own.

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
| `adb devices` shows `no permissions` | Linux udev rules or group membership — and check the *server's* groups, not the shell's (§3). |
| `adb devices` empty with a cable attached | Check `lsusb` first — absent from the bus is a cable, port or charge-only problem, not udev or adb (§3). On Windows, the USB driver. Wireless debugging sidesteps the whole USB path. |
| Gradle fails on a missing SDK package | Licences: `sdkmanager --licenses`. |
| `make run` fails with `adb: device '…' not found` | Nothing attached, or `ANDROID_SERIAL` points at something that has gone away. |
| `make run` fails with `adb: more than one device/emulator` | Two or more targets attached. Set `ANDROID_SERIAL` (§2). |
| `avdmanager create` prints `Could not load devices from …/devices.xml` | Harmless. The device profile is still applied and the AVD boots (§2). |
| Works in Android Studio, fails in the terminal | Two different JDKs. Compare `./gradlew -version` with Studio's Gradle JDK setting. |
