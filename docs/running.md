# Running and testing Gawi

How to get the app onto a screen, and what to check by hand once it is there.

Companion to [the architecture](architecture.md) §8, which makes this necessary:
CI runs unit tests only, and *"instrumented tests are a manual, on-device
activity"*. §4 below is that activity written down. Toolchain setup for the
**build** lives in [docs/stacks/kotlin-android.md](stacks/kotlin-android.md);
this file picks up where that leaves off.

**What has actually been run**, since every heading below says so and this is
the summary. The Linux path end to end on Arch with an AVD; a physical device
over **wireless debugging** only, a Nothing A059 on Android 16 (API 36). The
macOS and Windows sections, and §3's USB path, come from Google's and
Microsoft's documentation and have not been run by anyone here. Corrections
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
  | `make lint` | `bash scripts/check-history.sh`, `bash scripts/check-citations.sh`, `bash scripts/check-tests.sh`, `bash scripts/check-docs.sh` and `bash scripts/check-names.sh` then `gradlew.bat spotlessCheck detekt lint :app:assembleDebug` |
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
because launchers and OEM battery policies differ from emulators. The widget and the reminder both ship, and
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

The phone above never enumerated, on any port or cable, while charging normally
throughout, measured 2026-08-22 — which is why the USB half of this section is
marked unverified, and why wireless debugging is the path that works.

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

**`make run` installs the debug build; `make release` builds the shippable
one.** Debug is `debuggable` and unminified and is entirely fine for daily use,
but it is not a release rehearsal: R8 rewrites the release build, and what
breaks there breaks nowhere else. Anything being verified for a tag runs on the
release APK.

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

This depends on the device holding the only copy, which is true of any real use
and true from the first habit you create: `allowBackup` is off, so there is no
second copy anywhere by design. Two ways to destroy it, both easy:

- **`make itest` uninstalls the app**, and `allowBackup=false` (architecture §6)
  means the OS has no copy. See the warning above §4's widget block — it is not
  theoretical, one run wiped an emulator holding 345 events. Point `make itest` at
  a throwaway AVD, never at a device holding data you want. `ANDROID_SERIAL` is
  how you make sure.

  **Turn the device's animations off first, or three of the eleven fail and look
  like a regression.** `WriteJourneyTest`'s three cases start on Today, and
  `Momo`'s KDoc says why: `rememberFrameClock` is a permanent awaiter on the
  frame clock, so Compose is never idle while she is on screen and every
  `waitForIdle` times out with `ComposeNotIdleException: possibly due to compose
  being busy`. The message blames "infinite re-compositions in the tested code",
  which is the wrong place to look. The suite does not set this itself and a
  fresh AVD ships them on — reinstalling the app is enough to lose the setting:

  ```bash
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
      adb shell settings put global $k 0
  done
  ```
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
Work through it for any change to the data path or the Today view; note in the
PR which parts you ran. Nothing here is deferred any longer: the widget block
is owed against the palette the widget has carried since 2026-08-28
([visual-identity.md](ux/visual-identity.md) §7.4), and the accessibility block
is live work.

**How to read a tick.** A tick earned on an emulator means "seen and correct",
not "verified on the target device" — colour, contrast and layout render the
same there and nothing else does. Each box says where it ran. **Since
2026-09-14 a tick also says which build**: PRD step 5a runs these against the
signed, shrunk release APK rather than a debug install, because R8 rewrites
what ships and what breaks there breaks nowhere else. A box needing `run-as`
is the exception and says so, since a release build refuses it (§6). Two device passes
stand behind the ticks that name them: the **Nothing A059 on 2026-09-02** —
Android 16 (API 36), 1080×2392 at 375 dpi, font scale 0.85, the Nothing
launcher, TalkBack 17.0.1, Accessibility Scanner 2.5.1, over Wi-Fi adb, against
a seeded event log of fourteen habits — and a re-hearing pass on 2026-09-03.

**TalkBack cannot be driven from `adb shell input`.** Injected taps and swipes
bypass the accessibility layer, so a tap under TalkBack *toggles* the row it
lands on — which is the "direct tap" these boxes forbid, arriving by another
door. What can be driven is focus, `input keyevent KEYCODE_DPAD_DOWN`, which
TalkBack announces for every focusable node; what can be read is TalkBack's
*Display speech output* overlay off a `screencap` taken within half a second of
the key. Anything not focusable — the panel, the chip, headings, grid cells,
trend columns, widget bodies — has to be swiped by hand and reported by ear.

**Driving the rest of it from `adb`.** `screencap` and `uiautomator dump`
inject no input, so they are what a box watched across a boundary is watched
with; anything that taps changes the thing being measured. A dump lags a tap by
a recomposition, so read a control's `enabled` flag rather than the label
beside it. Read a description untruncated or *4 weeks* arrives as *4 week* —
and where a drawn badge is cleared from the tree, the description is the only
evidence there is. A snackbar cannot be caught by racing a dump against it:
diff the text of a burst of dumps against the settled screen, and take its
duration off a recording. When nothing may pause between two actions, put both
in one `adb shell` invocation. **A negative result proves nothing on its own**
— a tap that opens no dialog has to be paired with the same tap on an idle row,
or a guard that neutralises a formula with a reader shown to evaluate one.
Three things swallow an action: the Add-habit FAB is drawn over the last
visible row's archive control; a file row's preview thumbnail carries the file
name in its own description, so matching on description opens a preview instead
of choosing the file; and a settings dialog commits on **Set**, so Back
discards the selection and leaves the old value with no word either way.

**Seeding clears the app's data.** `scripts/avd-seed.sh` wipes the log before
it imports, because an import is a merge and a second one would union with the
first. It refuses anything but an emulator for that reason — `adb` with no
`-s` takes whatever single device is attached, and §3's physical-device path
puts a real habit log in range.

**What a device is for, and `make test` is not.** No test here opens a file
picker, so the export, import and CSV boxes are the only check that a file is
written and read at all. `AppNavigationTest` launches the real `MainActivity`
under `HiltTestApplication` and covers the production Hilt graph, the navigation
graph and all four routes, but Room's `InvalidationTracker` does not deliver in
that setup, so no screen ever re-reads after a write
(docs/architecture.md §8) — which is why the create, edit and archive steps
below earn their place even though Robolectric renders the same screens. Read
the copy while you are here: the tests resolve every expected string from the
same `R.string` the composable renders, so a reword cannot fail them, by design.

**On an emulator**

- [x] The app launches and `adb logcat -d -s AndroidRuntime:E` is empty. Run
      2026-09-14 on `Small_Phone` against the **signed release APK**, which is
      where this stops being a formality: R8's failures are silent, so an empty
      log is what says no serializer and no reflected class was stripped.
- [x] From the empty state, tap **Add a habit**, name it, save — which opens the
      habit's own detail screen, so go back to see the row. It is on Today
      with no restart — one observation covering Hilt building the data
      layer, the command path, the log being folded, the projection write and
      the Room `Flow`.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      row was on Today with no restart, and `AndroidRuntime:E` was empty.
- [x] Create a **weekly** habit and check the target stepper stops at 7 and at
      1. Above 7 throws out of `Schedule.Weekly`'s `require` rather than being
      rejected, so this is a crash if it is wrong.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      stepper stopped at 7 and at 1, both buttons reporting `enabled=false` at
      their limit rather than clamping, and no `AndroidRuntime:E` appeared, so
      the `require` was never reached.
- [x] Open a habit from the list, change **only** its name, save. Its schedule
      and tag survive, and so do the icon and colour the form no longer shows —
      an update is a whole-record write, and those two are passthrough
      ([visual-identity.md](ux/visual-identity.md) §7.3), so a form that wrote
      the defaults instead of carrying them would silently restyle every habit
      on its first save. `HabitsUiMapperTest` pins the carrying; only an export
      taken before and after shows the log agreeing. **Use a habit that did not
      get the form's defaults** — on one that did, the icon and colour being
      compared *are* those defaults, so carrying and re-defaulting write
      identical bytes and the check proves only the schedule and the tag.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: one
      export either side of a rename shows the update carrying the icon, the
      colour, the tag and the schedule, with only the name changed.
- [x] Archive a habit: it leaves Today, and appears under **Archived** on the
      list with a *Bring back* action. Bring it back: it returns to Today.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: it
      left Today at once and appeared under **Archived** with *Bring back*,
      beside one the seed had archived through an event rather than a gesture,
      and bringing it back returned it to both lists.
- [x] The mascot's count follows archiving — an archived habit stops being
      outstanding.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      count went from 9 of 11 to 8 of 10 on archiving and back again, so the
      habit leaves both halves of it.
- [x] Tap a row: it ticks, and its streak appears. Tap again: it unticks. A
      daily streak reads as a count, a weekly one in weeks.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: a tap
      ticked the row and its streak appeared, a second unticked it, and the row
      descriptions carry days for a daily habit and weeks for a weekly one,
      broken forms included.
- [x] Force-stop and relaunch: completions and streaks are rebuilt from the log.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: every
      row's name, streak and ratio and the header count were identical by
      `diff` across a force-stop, including a completion made seconds earlier.
- [x] The database exists — `adb shell run-as com.gawi.app ls -l databases`. To
      inspect it, **pull the `-wal` too**, or you read a pre-checkpoint snapshot
      and will think writes were lost:

      ```sh
      adb exec-out run-as com.gawi.app cat databases/gawi.db     > /tmp/gawi.db
      adb exec-out run-as com.gawi.app cat databases/gawi.db-wal > /tmp/gawi.db-wal
      sqlite3 /tmp/gawi.db 'select type, payload from events;'
      ```

      A *missing* `-wal` is fine and means SQLite has checkpointed into the main
      file, so let that copy fail rather than chasing it.

      Run 2026-09-15 on `Small_Phone` against the **debug** build, the exception
      this box's own command names (§6). The main file pulled alone has no
      `events` table at all, so reading it without the `-wal` is an error
      rather than a low count; pulled with it, 164 events and
      `integrity_check` ok. **Querying the pulled pair checkpoints the `-wal`
      into the main file and deletes it**, so a main-file-only copy taken
      afterwards is no longer one.
- [x] Settings persist. Open **Settings** from Today's app bar — the gear, not
      the list glyph beside it — and change the day cutoff.
      `files/datastore/settings.preferences_pb` appears after the **first
      write**, not the first read, so it will not exist until you do. Then
      force-stop, relaunch and reopen the screen: it reads the stored value
      back, not the default. **Put the cutoff back to midnight before moving
      on** — the next two checks both start from it, and neither restores it.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK** for the
      value, and 2026-09-19 on the **debug** build for the file, which needs
      `run-as` (§6): `settings.preferences_pb` was absent on a fresh install
      and still absent after the screen had only been read, appeared on the
      first write, and the cutoff read back after a force-stop rather than the
      default.
- [x] **Day rollover, against a real clock.** Start from a cutoff at or before
      the current time — midnight does, which is why the check above restores it
      — and tick a habit, so there is a completion on today's logical date.
      *Then* set the cutoff a couple of minutes ahead and go back to Today:
      "today" becomes yesterday, so that row reads unticked. Leave the screen
      alone; when the boundary passes it flips back on its own. Getting the
      order wrong is what makes this pass vacuously: with the cutoff already
      ahead of now, the row is unticked before you change anything. This is
      still the cheapest way to force a boundary — `adb shell date` needs `adb
      root` and is refused on the Play images this project uses.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: with
      the cutoff moved ahead of the clock the ticked row read unticked and two
      habits completed the day before read ticked, so the whole logical day had
      moved; left untouched, it flipped back on its own at the boundary.
- [x] **The mascot follows the clock, not just the data.** With something
      outstanding, set **End-of-day reminder** to a time just past now. The
      panel changes with no habit touched and no interaction — and the habit
      rows do not reload underneath it, which is the point of the repository
      subscribing to the settings twice with different dedupes.
      `TodayMoodTest` asserts the rows are *equal* across the crossing, which an
      identical re-query satisfies too, so only a screen can answer the second
      half. **Momo has to be content going in**: a log carrying a break makes
      her regenerating instead, a different mood that this boundary does not
      move ([momo.md](ux/momo.md) §3).

      Half seen 2026-09-03 on the Nothing A059; closed 2026-09-15 on
      `Small_Phone` against the **signed release APK**: the mood line turned
      over on the boundary with nothing touched, and the rows beneath it did
      not move.
- [x] **Week start re-buckets what is already on screen.** With a weekly habit
      showing a ratio, change the week start. The ratio re-counts against the
      new week without leaving the screen. Unlike the cutoff, this is not
      prospective-only: nothing about a week is stored on an event, so it is
      recomputed on read. **Seed the completion on the day the two week starts
      disagree about**, or every ratio reads the same under both and this
      passes without touching the question.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      ratio re-counted on return while a second weekly with nothing that week
      stayed where it was.
- [x] A cancelled tap still commits: tap, immediately press Back, relaunch, and
      the completion is there.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      completion outlived the activity and was there after relaunching.
- [x] **Export writes a file you can read back.** Settings → scroll to **Data**
      → **Export a copy**. Keep the offered name, save it into Downloads,
      confirm the snackbar, then:

      ```sh
      adb shell cat /sdcard/Download/gawi-export-*.json | head -c 400
      ```

      It prints JSON with your habits in it. Note the contrast with the database
      check above: SAF wrote outside app-private storage, so this needs no `run-
      as`. Nothing in the app points at that file, so delete it when you are
      done. Run 2026-09-14 on `Small_Phone` against the release APK: the
      snackbar read *"Exported. That file is the only copy — keep it somewhere
      other than this phone."*, and the file held 83 events over ten habits with
      `HabitCreated`, `CompletionAdded` and `CompletionTombstoned` all
      round-tripping — which is what says kotlinx-serialization survived
      shrinking.
- [x] **The offered name is today's date, not yesterday's.** Set the day cutoff
      to 03:00, wait until after midnight — or simply check the name is today's
      while the cutoff is at 03:00 and the clock reads before it — and the save
      dialog still offers `gawi-export-<today>.json`, so the file name uses the
      wall clock rather than the logical date. **Put the cutoff back to midnight
      afterwards**; the rollover checks above start from it. It need not be 03:00 and you need
      not wait for midnight: **any cutoff ahead of the clock** puts the
      logical day behind the wall date, which is the only condition this
      box needs.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      save dialog offered today's wall date while the logical day sat on
      yesterday.
- [x] **Cancelling the picker does nothing and says nothing.** Tap **Export a
      copy**, then press Back out of the save dialog. No snackbar, no file, and
      the row is still tappable — the null-`Uri` path is a no-op rather than an
      error, which is the rule every Cancel on this screen follows.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**:
      `/sdcard/Download` gained nothing, the row's value line did not move, no
      snackbar appeared, and the row was still tappable.
- [x] **Importing what you just exported changes nothing.** **Import a file** →
      pick the export from above. The snackbar says nothing was new, and Today
      is unchanged — same rows, same ticks, same streaks. That is the dedupe by
      event id, and an import being a merge and not a replace. It restores
      nothing because it changes nothing, which is the point.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      snackbar said nothing was new, and a full sweep of Today was identical by
      `diff` either side.
- [x] **A file that is not an export is refused without changing anything.**
      Import → pick a photo or any text file. The snackbar says it is not a Gawi
      export, and Today is unchanged: a refusal is a message rather than a crash
      or a half-written log.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: a
      plain `.txt` was refused as not an export, `pidof` returned the same
      process, and Today was unchanged by `diff`.
- [x] **The export is visible in the import picker** without needing a "show all
      files" step. The one thing the type filter can get wrong that no test can
      see — a filter that hides someone's own backup from them is worse than one
      that shows a few extra files.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      export was listed in Downloads beside the other `.json` files with no
      *show all files* step.
- [x] **Both rows go quiet while the work runs.** With a log big enough to take
      a moment, the tapped row's explanation is replaced by *Writing the file…*
      and neither row answers a tap until it finishes. On a small log this is
      over before you can see it — expected, and `SettingsScreenTest` covers it
      instead. **Build the log from several imports**: one file cannot exceed the
      32 MB import cap, and merging 30,000 events into a log already holding
      30,000 is minutes of work, which is what makes the state visible.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: one
      dump landed inside the write and caught *Writing the file…* in place of
      the row's explanation, with all three Data rows disabled together.
- [x] **A file far too large to be an export is refused, not fatal.** The picker
      shows essentially everything by design, so this is the likeliest wrong
      tap:

      ```sh
      adb shell 'dd if=/dev/zero of=/sdcard/Download/toobig.json bs=1048576 count=40'
      ```

      Import it. The snackbar says it is not a Gawi export, the app is still
      running (`adb shell pidof com.gawi.app` returns the same pid) and the log
      is untouched. Without the ceiling this is an `OutOfMemoryError`, which is
      an `Error` and so slips past the guard around every other failure here —
      process death on the recovery screen with nothing said. Delete the file
      afterwards.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      40 MB file was refused as not an export, `pidof` returned the same
      process, and Today was unchanged by `diff`.
- [x] **An export you do not interrupt ends in a closing brace.** Export into
      Downloads, then:

      ```sh
      adb shell 'cat /sdcard/Download/gawi-export-*.json | tail -c 120'
      ```

      It ends `}` rather than mid-token, and the `event_count` near the top
      matches what the log holds — the check that encoding first and opening the
      document last did not break the ordinary path.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      last bytes are a closing brace after the final payload rather than a cut
      token, and the declared `event_count` matched the events carried.
- [x] **Leaving the screen the instant you tap Save can leave an empty file, and
      that is a known gap.** Tap **Export a copy**, save, and press Back out of
      Settings immediately. Two outcomes are both correct: no file at all (Back
      beat the picker, so nothing was ever created), or a **zero-byte** file
      (the picker created the document and the screen died before the export
      started). What must *not* appear is a partial file — and if you import
      whichever file you got, it is refused as damaged, which is the property
      that makes the gap bounded. See `docs/ux/settings.md` §8; closing it needs
      an application-scoped coroutine, which is a decision rather than a patch.
      Delete the file afterwards so the next check starts clean.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**, on a
      log large enough that a small one does not finish first: it left a
      **zero-byte** file, the second of the two outcomes this box allows and
      not a partial one, and importing it was refused as damaged.
- [x] **Process death mid-export is not survived, and the file is refused rather
      than half-restored.** Repeat the check above but run `adb shell am force-
      stop com.gawi.app` instead of pressing Back. The file is empty or
      truncated — expected; `NonCancellable` survives cancellation, not a killed
      process. Now import it: the snackbar says it is damaged. That is what
      bounds the residual gap, because truncated JSON does not parse and
      `event_count` would not match, so a half-written backup can never be
      silently restored as a partial one.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**:
      force-stopping also left a zero-byte file, which does not parse, and
      importing it was refused as damaged.
- [x] **The count snackbar is readable before it goes.** Import an export
      holding habits this install does not have and read the whole line without
      hurrying; it uses the default short duration, and if that is too fast that
      is a real finding. The habits it adds cannot be deleted afterwards, only
      archived, so do this on a scratch install or be ready to archive them.
      **Give the file its own id range**: two seeds minted from the same
      deterministic sequence dedupe against each other by event id and add
      almost nothing, so the habits this box needs never arrive.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**, and
      it is **not** too fast: the line stayed up **4.0 seconds**, which is
      Compose's `SnackbarDuration.Short` at 4000 ms rather than the View
      system's 1500 ms.
- [x] **The whole recovery claim, end to end.** Export, then `adb shell pm clear
      com.gawi.app`, relaunch to the empty state, and import the file. Every
      habit, completion and streak comes back. This is the promise architecture
      §6 makes on behalf of `allowBackup="false"`, and the only check that tests
      it as a user would need it. **Compare the exported events, not the rendered
      rows** — names, streaks and ratios are derived, and two different logs
      can render the same ones.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**:
      exports taken either side of the clear hold **identical event sets** —
      every id, instant, offset, type, schema version and payload, 148 events
      over twelve habits.

**The 30-day nudge** (PRD §5). Run these in order from a cleared install — they
build on each other, and the third is the one with no JVM test behind it.

- [x] **A fresh install is not nudged about losing nothing.** After `adb shell
      pm clear com.gawi.app`, open Settings → **Data**. The export row has *no*
      value line and the ordinary help underneath it. The stamp is absent here
      exactly as it is on a log full of events, and only the log tells the two
      apart.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: after
      `pm clear` the row ran straight from its title into the ordinary help
      line, with no value line between them.
- [x] **A log with something in it and no backup says so.** Create one habit,
      then reopen Settings. The export row reads **Never exported** and the help
      line has become the nudge — the same split in the other direction, and
      "never" is overdue immediately rather than in thirty days.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: one
      habit on an otherwise empty log turned the row to **Never exported** over
      the nudge rather than the ordinary help.
- [x] **An import moves the row without leaving the screen.** From a cleared
      install again — `adb shell pm clear com.gawi.app` — open Settings while
      the log is empty, confirm the row is silent, then **without navigating
      away** tap **Import a file** and pick an export saved earlier. The row
      must switch to **Never exported** with the nudge *immediately*. **Watch
      the five seconds specifically**: importing into an *empty* log once left
      the row silent for up to five seconds, and a row that only updates after
      you leave and come back is the defect, not a pass. The import check
      further down runs after an export, so the log already has events and the
      row is already saying something — which is why the obvious ordering hides
      this. Creating a habit on Today and returning to Settings within five
      seconds checks the same mechanism from the other side.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**, and
      the five seconds were watched: from a cleared install the row was silent,
      and importing without leaving Settings put **Never exported** and the
      nudge up on the first poll after the snackbar.
- [x] **A finished export records itself, and only a finished one.** Export a
      copy, keep the offered name, and return to Settings: the row reads **Last
      exported today** and the ordinary help is back. **This is the only check
      of the ordering** — the stamp is written after the output stream closes,
      so that it means "a file landed" rather than "a write was attempted", and
      substituting a `ContentResolver` to test that needs a Robolectric shadow
      this project does not use (docs/ux/settings.md §8).

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      row went from *Never exported* to **Last exported today**, and the help
      line under it went back to the ordinary one from the no-copy nudge.
- [x] **A cancelled export does not count as a backup.** Tap **Export a copy**
      and press Back out of the save dialog. The row still reads whatever it
      read before: the stamp follows the write and not the tap.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**:
      backing out of the save dialog left the row reading what it read before.
- [x] **An import does not count as a backup either.** Import the file from
      above. The row still says today and the value does not move. Deliberate:
      an imported file proves a copy was readable, not that it is recent, so
      importing a backup from March must not silence the nudge for a month.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: after
      importing the file just exported, the value line had not moved.
- [x] **The stamp survives a restart.** `adb shell am force-stop com.gawi.app`,
      relaunch, reopen Settings: still **Last exported today**, so it is in the
      preferences file rather than in memory.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: still
      **Last exported today** after a force-stop and relaunch.
- [x] **A month later, the nudge comes back.** Settings → **Date & time**, turn
      off automatic time and move the date forward 31 days — the device UI, not
      `adb shell date`, which needs root and is refused on a Play image. Reopen
      Gawi's settings: **Last exported 31 days ago**, with the nudge underneath.
      **Put the date back and re-enable automatic time afterwards**; a 31-day
      jump also sweeps every streak, so any check above this one has to be re-
      run from a clean state rather than after this. **Do not export while the
      date is forward.** That leaves a stamp dated in the future, which the
      journal deliberately reads as no stamp at all — so the row goes back to
      **Never exported** once the date is restored, which is correct behaviour
      and looks like a bug if you were not expecting it.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: with
      a stamp reading *Last exported today*, moving the device date on 31 days
      through the Settings app turned the row to **Last exported 31 days ago**
      over the nudge. Automatic time was restored afterwards and nothing was
      exported while the date was forward.
- [x] **A settings edit does not reset the clock.** With a stamp in place,
      change the week start and come back. The value line has not moved: the
      export stamp shares a preferences file with the three settings and
      survives a write that assigns all three of their keys.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      week start was changed and changed back, and the export stamp did not
      move.

**The CSV of completions** (PRD §5, docs/ux/settings.md §6). Its correctness is
mostly covered on the JVM — `CompletionCsvTest` pins every byte of the format
and `CompletionExportDaoTest` pins the query — so what is left here is the two
things no test in this repo can reach: the picker, and what a real spreadsheet
does with the file.

- [x] **The CSV is written, and Excel will read it as UTF-8.** Settings →
      **Data** → **Export completions**. Keep the offered name — it should be
      `gawi-completions-<today>.csv` and not the JSON stem. Then:

      ```bash
      adb shell 'head -c 3 /sdcard/Download/gawi-completions-*.csv' | xxd | head -1
      # expect: efbb bf   -- the byte order mark, without which Excel mangles
      #                      any non-ASCII habit name
      adb shell 'head -2 /sdcard/Download/gawi-completions-*.csv'
      # expect: habit,logical_date,note   then the oldest logged day
      ```

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      offered name carried today's date, the first three bytes were the byte
      order mark, the header row was right, and the first data row was the
      oldest logged day.

- [x] **The row count matches the projection.** Pull the database **with its
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

      Run 2026-09-15 on `Small_Phone` against the **debug** build, for the
      `run-as` reason the database box above gives: the projection and the
      parsed CSV agreed exactly, the difference from `CompletionAdded` being
      the one tombstoned day.

- [x] **A formula in a habit name stays text in a spreadsheet.** The security
      check, and the reason the file is not written naively. Create three habits
      named `=1+1`, `Read, daily` and `say "yes"`, complete each one today,
      export, then open the file in LibreOffice on the host (`localc /tmp/gawi-
      completions-*.csv`, comma-separated, UTF-8). The first cell must
      **display** `=1+1` and compute nothing; the other two must each be a
      single cell. In the raw file the first field reads `"'=1+1"` — the
      apostrophe is the guard and a spreadsheet does not show it. Include a name
      with a **leading space before the sigil** — ` =1+1` — in the same pass; it
      must also come out as text. Archive the three habits afterwards. What this
      does not show: LibreOffice leaves ` =1+1` as text whether or not leading-
      space removal is on, measured 2026-08-21, so the check pins the guard's
      rule rather than reproducing an exploit. The case that genuinely evaluates
      is a **bare** `=1+1` with no apostrophe — convert a hand-made file holding
      one and confirm the cell really does compute, or this whole check can pass
      because the reader never evaluates anything.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**, with
      the control taken first: the guard lands ahead of a leading space, all
      four names came back as text computing nothing, and the same reader on
      the same settings turned a hand-made bare `=1+1` into `2`.

- [x] **Know what a `;`-locale Excel does with it.** Not a defect and not
      fixable in the bytes without breaking every other reader, so it is a check
      that you have seen it rather than one that can fail: on a German, French,
      Spanish or Dutch install, Excel splits CSV on `;` and puts every record of
      this file in column A. The fix for a user is the import dialog. See
      `CompletionCsv`'s KDoc for why no `sep=,` line is written.

      Seen 2026-09-15 on the host, importing one CSV twice into LibreOffice,
      once with `,` as the separator and once with `;`. Under `;` every record
      lands in one column, and a record whose note carries a line break is
      split as well: the field starts at column 0, so its quoting is never
      honoured and the embedded newline ends the record.

- [x] **Cancelling the picker does nothing and says nothing.** Tap **Export
      completions** and press Back out of the save dialog. No snackbar, no
      change, and `/sdcard/Download` gains nothing.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**:
      `/sdcard/Download` held exactly what it had held before, and no snackbar
      appeared.

- [x] **A CSV export does not touch the nudge.** The load-bearing negative, and
      the one worth running even when nothing else is. Note what the export row
      says, write a CSV, and return: the value line and the help line are both
      unchanged. A CSV holds no events, so treating one as a backup would
      silence the warning for a month over a file that could not restore
      anything. `CompletionCsvArchiveTest` reads a real journal either side of a
      real export and `SettingsDataViewModelTest` asserts what the row says;
      this confirms it through the real graph. **Start from a real stamp**, not
      from *Never exported*, where the value cannot move backwards and a
      pass proves nothing.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: a CSV
      export left both the value line and the help line as they were.

- [x] **All three Data rows go dead together.** Start a CSV export of a large
      log and, while it runs, confirm **Export a copy** and **Import a file**
      are both unavailable and that only the CSV row says it is working. Hard to
      catch by hand on a small log; the JVM tests own this and this is a sanity
      check.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**, the
      tap halves re-run 2026-09-19: only the CSV row said it was working, and
      a tap at **Export a copy** and one at **Import a file**, each fired in
      the same invocation as save, opened nothing — where the same tap on the
      same coordinates opens the dialog on an idle row.

- [x] **An empty log still writes a usable file.** After `adb shell pm clear
      com.gawi.app`, export completions before creating anything. The snackbar
      says the file holds only its column headings, and the file is the header
      line and nothing else. Re-run the recovery check above afterwards, since
      this clears the app.

      Run 2026-09-15 on `Small_Phone` against the **signed release APK**: the
      file is the byte order mark, the header and a CRLF — 25 bytes after the
      mark and 28 in the file, with no data row.

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

The widget's logic is JVM-tested (`:widget`) and the write journey is covered by
`make itest`, so what is left here is the part that needs a real launcher:
**pinning a widget requires the user**, so no test can place one. Decisions and
reasoning are in [docs/ux/widget.md](ux/widget.md).

**What an emulator earns here, and what it cannot**, because most of this block
is not the thing a launcher's process decides. Behaviour, geometry and
typography reach the host as the same `RemoteViews` an OEM launcher would
inflate, so a box about those names the emulator it ran on and that is enough —
which is what the already-ticked RTL, display-size and font-scale boxes below
have always been. Three kinds of claim it cannot settle, and the restyle block
gives the reason for the first: a launcher's **colour translation**, because a
widget is drawn against a background it does not own; a launcher's **cell
geometry**, which is what decides whether a size gate is reachable at all; and
**TalkBack**, which `adb` cannot drive. Those boxes say so and wait for a phone.

- [x] **It is offered at all.** Long-press the home screen → *Widgets* →
      **Gawi** → *Today*. If it is missing, the provider did not merge: read
      `app/build/intermediates/packaged_manifests/debug/.../AndroidManifest.xml`
      for `com.gawi.widget.TodayWidgetReceiver` (needs `--rerun-tasks`; a stale
      merged manifest reports the old answer). `WidgetHostTest` covers provider
      binding and that Glance renders, *not* launcher discovery — so if that
      test passes and this step fails, suspect the launcher; if it fails too,
      the problem is below the launcher.

      Run 2026-09-19 on `Small_Phone` (API 37, Pixel launcher) against the
      **signed release APK**: *Browse* lists **Gawi, 3 widgets**, and
      `dumpsys appwidget` names all three receivers as registered providers.
      That reading is against the installed bytes, so it is better evidence
      than the path above, which names the *debug* merged manifest while this
      pass runs the release build.
- [ ] **Momo appears only when there is room.** Place the widget at its smallest
      (one row tall): name and checkbox, no face. Resize it to two rows: Momo's
      resting frame appears above the rows, in today's mood, and the rows still
      have room beneath her. Then, with no habits, two rows tall: the face above
      "No habits yet". `WidgetMomoTest` proves the tree; it cannot see whether a
      launcher's two-row cell clears **170 dp**, which is the constant this
      check is really measuring.

      **Not earned 2026-09-20 on `Small_Phone`**, and the constant is why. The
      Pixel launcher here floors a placement at two rows and its rows are
      65 dp, so the smallest reachable Today widget is 242 × 133 dp: name and
      mark, no face, which is this box's first clause arriving at the wrong
      size. The gate itself does fire — at 242 × 203 dp the Today body draws
      Momo's resting frame above the rows, and above *No habits yet* when
      every habit is archived — but only in the *other* host, the merge defect
      below having put it there, and this launcher refused every attempt to
      grow the Today widget itself past two rows.
- [x] **It draws today's habits** — each active habit's name with a checkbox,
      ticked to match the Today screen. **No streak**, deliberately (PRD OQ-5).

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**: the
      widget's own subtree, read off its host-view bounds so it is provably the
      widget and not the app, carries *Read*, *Stretch* and *Meditate*, each
      with a mark and none with a numeral. Their marks agreed with Today row
      for row, and the header count moved with them, nine outstanding before
      the widget's own tap and eight after.
- [ ] **You can read it, in the theme the device is actually in.** Toggle the
      system dark-mode setting and look at the widget in both, checking the
      **checkbox glyph** and not just the label. This is where a shipped defect
      lived: Glance's default is not theme-aware, so a dark-themed device drew
      near-black on near-black at 1.59:1 while every JVM test stayed green,
      because it *rendered* the whole time. Every colour now comes from
      `WidgetPalette` and `WidgetPaletteTest` holds both glyph states in both
      themes to the 4.5:1 floor — but no JVM test reads what the tree drew the
      glyph with, and none can see how a real launcher translates a colour,
      which is where the defect lived. Done on emulators, owed on hardware: the
      emulator half ran twice on 2026-08-28 (API 29 and 30, by eye and by
      sampling the rendered pixels) and found the 2.91:1 glyph, but this block's
      standard is that a widget lives in a launcher's process and an OEM
      launcher's is not the emulator's.
- [x] **A tap completes.** Tap an unticked row's *glyph*, then another row's
      *name*: **neither moves under the finger**. Both wait for the write to
      round-trip (a second or so), and the mark turns over when the widget
      redraws — it is an `Image` and no Glance `CheckBox` is emitted
      (`GlyphBitmap.kt`), so no half of the row flips optimistically. Do not tap
      twice while waiting, or the second tap undoes the first. Open the app:
      Today agrees, and the mascot has reacted if that was the last one.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**, both
      taps recorded at 4 fps: each burst shows the row's pressed highlight with
      the mark unchanged, then the mark turned over one frame later for the name
      and two frames later for the glyph. Neither is instant, which is what
      `GlyphBitmap`'s own KDoc says to expect.
- [x] **A tap again undoes.** Tap the ticked row: it unticks, and Today agrees.
      This is the half that separates the widget from a complete-only one.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**: the
      row a name tap had ticked was unticked by a glyph tap on the same row, and
      the app then read *8 of 10 left today* with that habit not done — eight
      rather than seven being the proof the undo landed.
- [x] **A write in the app moves the widget.** The only check that exercises
      `ProjectionListener`, and nothing else can: complete a habit *in the app*,
      then go to the home screen **without tapping the widget**. It shows the
      tick. If it does not, the push is broken even though every JVM test passes
      — `ProjectionListenerTest` proves the call happens, not that Glance acted
      on it.

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**: a
      habit completed in the app, then `KEYCODE_HOME` and a dump of this
      widget's own subtree with nothing touched, showed its mark turned over.
      The push reaches Glance. What it reaches *beyond* this widget is the
      box below.
- [x] **An empty install says so.** With no active habits the widget reads *"No
      habits yet"*, not a blank box. (Archive every habit rather than using `pm
      clear`, which destroys the log.)

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**: all
      ten archived through the habit list, never `pm clear`, and the widget
      drew *No habits yet* rather than an empty ground. All ten were brought
      back afterwards and the log came through it — the completion made
      earlier that day was still there.
- [ ] **Resizing keeps it usable.** Drag the handles: rows reflow and the list
      scrolls rather than clipping.

      **Not earned 2026-09-20.** This launcher would not grow the Today widget
      past its two-row placement, for the reason the Momo box above gives, so
      the only host that resized was the one bound to `StreakWidgetReceiver` —
      which the merge defect below means was not drawing its own body either.
      A reflow and a scrolling list were both seen there; neither is evidence
      about this widget.
- [ ] **A write in the app moves *both* widgets.** With the Today widget and the
      streak widget both placed, complete a habit in the app and go to the home
      screen without touching either. Both change. Listed separately from the
      check above because the failure it catches is different: a provider
      missing from `GlanceProjectionListener` still *renders*, so it looks
      placed and working and simply stops following writes.
      `ProjectionRefreshTest` reads the receivers out of the merged manifest,
      which is as far as a JVM test reaches.

      **Failed 2026-09-20 on `Small_Phone`**, and not in the way above: the
      write reaches both, but the widget bound to `StreakWidgetReceiver` then
      draws the *Today* body — habit rows, no numerals, no *as of* line — and
      both host views report the same `views_bitmap_memory`. R8 merges the
      three `GlanceAppWidget` subclasses into one class, so `updateAll` cannot
      tell them apart; the shipped dex holds no `Lcom/gawi/widget/StreakWidget;`
      at all. Release only, so no JVM test can see it. A provider-initiated
      update draws the right body again, and the next write undoes that.
      widget.md §8 holds it; this box is owed a re-run against the fix.

**The streak widget** (docs/ux/widget.md §6). Its own provider, so its own
picker entry, and the first one here carrying API 31 attributes.
`StreakWidgetHostTest` already covers provider binding, Glance's session, the
rows body, its `LazyColumn` translating to a `RemoteViews` collection with the
"as of" footer pinned outside it, and `targetCellWidth/Height`, `description`
and `previewLayout` resolving from `res/xml-v31` on API 31+. Below 31 those
three fields **do not exist** on `AppWidgetProviderInfo` — reading one throws
`NoSuchFieldError`, which is the sharpest statement of why the provider xml is
split. Two things it cannot reach, so they stay below: the **row content**,
because a `RemoteViews` collection's items are materialised by the host when it
lays out and an unattached `AppWidgetHostView` never does (the `ListView` comes
back present and childless), and anything about a **launcher's own cells, theme
or process**.

- [x] **It is offered, and the picker says what it is.** Long-press → *Widgets*
      → **Gawi**: three entries, *Today*, *Streaks* and *Momo*. On API 31+ the
      *Streaks* entry shows a description under the name and a preview of the
      rows; on 29 and 30 it shows neither, which is correct rather than broken
      — those attributes only exist in `res/xml-v31`. The preview is in the
      **system face, not Outfit**, also correct: a picker inflates real XML and
      there is no bitmap escape there.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**:
      *Streaks* carries `3 × 2` and *Every habit's current run, dated*, and
      *Momo* `2 × 2` and its own line, while *Today* — which has no
      `res/xml-v31` — carries the size alone and no description. That pairing
      inside one group is the contrast, and the preview drew *Reading 12* and
      *Drink water 5* over *as of today* in the system face.
- [x] **A fresh placement lands three cells by two** on API 31+, from
      `targetCellWidth/Height`. On 29 and 30 the launcher sizes it off
      `minWidth` instead, so a narrower first placement there is expected.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**: the
      first placement measured **242 × 133 dp** from its host-view bounds at
      density 320 — three cells by two rows. What this launcher cannot separate:
      *Today* declares no `targetCellWidth` and lands on the same 3 × 2, because
      `minWidth` 180 dp and `minHeight` 110 dp round to it here. The span agrees
      with the attribute without proving it was the attribute that was read.
- [x] **It dates its number.** The bottom line reads *as of* and then a weekday,
      a day and a month — no year, no clock time. docs/ux/visual-identity.md
      §7.1 makes this non-negotiable, so it is the one element on this widget
      that must never be missing or clipped.

      Run 2026-09-19 on `Small_Phone` against the **signed release APK**: *as of
      Sat, Sep 19* — weekday, day, month, no year, no clock time — read in the
      dump and seen drawn in full in the screenshot. Both halves are needed: a
      dump returns the whole string whatever the pixels did with it.
- [ ] **Three rows and the date at the smallest size.** Place it one row tall
      with four or more active habits: three habit rows, the date pinned beneath
      them, and the rows scroll. Four rows and no date is the failure — 94 dp
      buys one or the other and the date is not the half that gives way. Still
      unseen: this needs a launcher whose cells come closer to the 110 dp
      minimum. The Pixel launcher's smallest cell is two rows, about 240×132 dp,
      where four rows and the date all fit and dragging the handle further up
      snaps back.
- [x] **The unit word appears only when there is room.** At the smallest size a
      weekly habit reads `3w` and a daily one a bare number. Resize to two rows
      and two columns wider: they become `3 weeks` and `12 days`, under a
      *Streaks* header. `StreakUiStateTest` pins the thresholds; only a launcher
      shows whether its cells clear them. Seen on an API 37 emulator on
      2026-08-29: the gate flipped on resize at roughly 240×127 dp, compact to
      full, at density 320.

      Re-run 2026-09-20 on `Small_Phone` against the **signed release APK**:
      at 242 × 133 dp the drawn text is `12`, `6` and `12w`; dragging the
      bottom handle to 242 × 203 dp turns them into *13 days*, *7 days* and
      *12 weeks* under a *Streaks* header. Read the drawn text, never the
      description, which carries the full wording at either size.
- [ ] **Days and weeks never look like the same number.** With one daily and one
      weekly habit both on a run, check all three signals at the larger size —
      the unit word and two visibly different inks — and that at the smallest
      size the `w` and the ink still separate them. Then check it in greyscale
      (Settings → Accessibility → colour correction → monochromacy): the `w` and
      the word must carry it alone, because **the two inks are only 1.42:1 apart
      from each other** and are near-identical greys. Judge greyscale by eye on
      the device: colour correction is applied on the display path, after the
      frame a capture reads, so `screencap` returns the uncorrected colours and
      proves nothing here.
- [x] **A break and a fresh habit read differently.** A habit whose streak has
      broken shows a muted `0` (and *was 12* at the larger size); a habit with
      no completions ever shows an em dash. If both show `0`, the two states
      have been collapsed and the widget is telling a new user they have failed.

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**: with
      five habits left active, *Walk* and *Tidy* drew a muted *was 1*,
      *Journal* *was 21*, *Sleep log* a live *49 days*, and *Draw*, which has
      no completion in the log at all, drew **—**. The two states are not
      collapsed, and the descriptions separate them too: *was 1 day* against
      *no streak yet*.
- [ ] **It survives a rollover untouched.** Set the day cutoff a couple of
      minutes ahead, wait past it without touching anything, and a streak that
      depended on yesterday updates itself. Same mechanism as the Today widget's
      rollover check below, and worth repeating here because this is the widget
      whose number can go stale *without any event at all* — which is why it
      carries a date in the first place.

      **Not earned 2026-09-20.** Across a real midnight, untouched, this widget
      did not update itself: the rollover push replaced it with the *Today*
      body, for the reason recorded against the both-widgets box above. Its
      footer did reach *as of Sun, Sep 20*, but only after a resize made its
      own receiver redraw, which is the opposite of what this box asks.
- [ ] **TalkBack reads each row once, with the unit.** Swipe through the rows:
      each announces as *"read, 12 days"* — the **full** wording even where the
      widget is drawing `3w`, because a spoken "12" cannot say whether it counts
      days or weeks. The date is announced too. Nothing announces twice.
- [ ] **You can read it in the theme the device is in.** Both schemes, on
      hardware, sampled the way the API 29/30 pass sampled the Today widget. The
      role to watch is `tertiary` — the week-streak ink, and the only role in
      this module that no other surface draws, so nothing measured it against a
      real launcher before this widget existed.
- [x] **200 % font scale.** Rows grow, a long habit name ellipsises inside its
      row rather than under the numeral, and the numeral is never pushed off the
      edge. The name and the streak have fixed slots, so what fails here is the
      slot width rather than the layout.

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**, a
      habit renamed to 37 characters through the form: at `font_scale` 2.0 the
      rows grow, the name draws as *Read a cha…* and ellipsises inside its own
      row, the numeral *14* stays whole against the right edge, and the *as of*
      line is still drawn. Read the drawn text — the description keeps the
      whole name. The change needs a render; a force-stop and relaunch is one.

**The widget's text is Outfit, as bitmaps.** A font resource cannot reach a
widget (measured 2026-08-24, docs/ux/visual-identity.md §2), so each name is
rasterised in our process and tinted by the host — which is why these six run on
a launcher, where the bitmaps are drawn and tinted.

- [x] **It is Outfit.** Against the launcher's own clock and labels, the names'
      `a` and `o` are geometric and the `t` has no tail — the same test the
      typography block below uses for the app. Both themes, and the text follows
      the theme on API 31+: force-stop after `cmd uimode night yes` or the
      running widget will not re-theme.

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**, both
      schemes, judged against the launcher's own *Play Store* label in the same
      frame: the widget's `a` is a single-storey circle and stem where the
      launcher's is double-storey, and its `t` ends without a tail. One thing
      to expect from the force-stop — it kills the Glance session, so both
      widgets sit on the loading layout until something starts a new one.
- [x] **API 29 or 30 emulator: every colour is resolved in our process.** Toggle
      dark mode with the widget placed. The text, the checkbox glyph and the
      background must go stale *together* until the next render, because all
      three are day/night pairs from `WidgetPalette` taking one translation
      path. Completing a habit repairs the staleness, so it lasts until the next
      write, rollover or 30-minute update. Run 2026-08-28 on API 29 and 30,
      identical to the decimal on both: this check found the defect it was
      written for — a resource-backed background the host re-resolved
      immediately while the name and glyph kept the value baked at the last
      render, landing at 1.31:1 and 1.60:1 — plus a second the toggle was not
      needed to see, the glyph below the floor in dark mode even freshly
      rendered at 2.91:1 checked. Re-run the same day against the fix and the
      expectation holds: the name at 16.59:1 light and 14.82:1 dark, the glyph
      at 5.56:1 and 5.18:1 light, 10.44:1 and 5.31:1 dark. **Two traps that make
      a broken widget and a working one look identical.** An `APPWIDGET_UPDATE`
      broadcast is **not** a render and will not repair the staleness — a tap on
      the widget or a write in the app is. And on a freshly booted emulator `cmd
      uimode night yes` silently does nothing, printing `Night mode: no` back,
      until `adb root` has been run, so **read the setting back** before
      believing either result.
- [x] **API 31 or later: the whole widget follows a toggle with no render.** The
      background is a day/night pair the launcher picks from, not a colour
      resource it resolves, so "it still follows" is a claim about this repo's
      code rather than Glance's default — and the check above is the reason not
      to trust that reasoning unmeasured. Run 2026-08-28 on API 37: `cmd uimode
      night yes` then `no` with the widget placed and the app not running, and
      the ground, the name and the glyph all move together within about two
      seconds — 16.59:1 and 5.18:1 in light, 14.82:1 and 5.31:1 in dark.
- [x] **200 % font scale.** Rows grow; a long name ellipsises inside the row
      rather than under the widget's edge; nothing clips vertically. The change
      lands at the next render, not on the spot — complete a habit in the app to
      force one, because Glance recomposes on locale and not on configuration.
      Run 2026-08-30 on `Small_Phone` (API 37), Pixel launcher, the Today widget
      at four by three: the *Read* name bitmap grew 75×40 px to 130×71 px, close
      to the 2× asked for, while the *row* grew only 64 → 71 px, because a row's
      height is the checkbox's floor until the text passes it. A 37-character
      name ellipsised inside the row, its bitmap ending at x = 469 against the
      rows' content edge at 501, with the full string still the row's
      `contentDescription`. Nothing is lost vertically: the rows are a
      `ListView`, so a fourth row **scrolls** into view rather than clipping —
      worth knowing before reading a part-drawn last row as a defect. At 200 %
      the body drops to the face-above-rows form, so the header, the mood line
      and the band are not on screen to judge here.
- [x] **An RTL *system* locale** — Settings → System → Languages, Hebrew or
      Arabic first. Not a per-app locale and not the developer toggle: `cmd
      locale set-app-locales` flips our app and leaves the widget as it was,
      because the launcher inflates the `RemoteViews` in *its* configuration,
      and `settings put global development_force_rtl 1` changes nothing at all.
      The recipe below the block's boxes is how to set one. What to see: the
      glyph sits on the right, and a Hebrew or Arabic name is shaped and read
      right-to-left. `BitmapTextTest` proves the glyphs land on the canvas; only
      a launcher shows whether the row mirrors around them. Run 2026-08-30 on
      `Small_Phone` (API 37), Pixel launcher, Hebrew first and a habit named
      קריאה. A clean mirror: the checkbox, Momo's face bitmap and the mood line
      all mirror about the content span to within a pixel, and קריאה shapes
      right-to-left from the platform's Hebrew face — Outfit's `cmap` has no
      Hebrew — while still shaping correctly in the LTR pass, which is
      `FIRSTSTRONG_LTR` doing its job on the paragraph. Read the face as the
      `ImageView`, not the pill `FrameLayout` around it, or the pill looks 7 px
      short of a mirror when it is not.
- [x] **A non-default Display size** — Settings → Display → Display size, Large
      then Small (or `wm density 400` on the emulator, `wm density reset`
      after), then complete a habit so the widget re-renders. The name must be
      as crisp as the checkbox glyph and keep its proportion to it. A blurry,
      oversized or clipped name means the bitmap is carrying the device's
      default density rather than the one it was drawn at, and the host has
      scaled it twice — which none of the checks above would catch, because they
      all run at the default size. Run 2026-08-30 on `Small_Phone` (API 37) in
      both directions, `wm density 400` and `260` against its 320 default. The
      checkbox and the bitmap's *height* land on the ratio exactly; its
      **width** runs about 3 % short (91 px against the 93.75 the ratio
      predicts). That 3 % is not the failure this box is for, and the arithmetic
      is what says so: a bitmap drawn at one density and scaled again by the
      host is out by the *square* of the ratio, 56 % at 1.25, not 3 %. Both stay
      crisp, with clean antialiased edges at 400 where a twice-scaled bitmap
      would have blurred. Expect the *body* to change underneath — about 151 dp
      tall at 400, drawing rows alone — so this check is about the row, not
      about which body appears.

**A stale render across the day cutoff is known and expected, not a bug**, and
much narrower than it was: a widget left on the launcher is refreshed by a
scheduled wake (`RolloverWorker`, docs/ux/reminder.md §2), so it normally clears
by itself. The wake is best-effort and not a deadline, though, so a device deep
in Doze can defer it and leave the periodic update as the fallback. The
behaviour is worth knowing because it is why the tap path is built the way it
is: a tap always writes to the *right* date, because it re-reads rather than
trusting the drawn one, but the visible semantics invert while a render is
stale — tapping a row drawn as **ticked** finds today incomplete and therefore
**adds** a completion, so the box stays checked and nothing looks undone. The
log is correct; the render was not (docs/ux/widget.md §4).

**Provoking it takes deliberately stopping the wake.** Moving the cutoff ahead
and waiting will normally show the widget *refreshing*, which validates the fix
rather than reproducing the fault. To see the stale render, stop the worker from
running across the boundary with `adb shell dumpsys deviceidle force-idle`, then
move the cutoff ahead and wait past it; `unforce` afterwards.

A lag *without* forcing Doze is worth investigating rather than expected — but it
does **not** on its own mean the wake was never armed. Three things produce the
same symptom: armed and deferred anyway (App Standby, an OEM battery policy,
ordinary idle), the wake ran and the push failed silently
(`GlanceProjectionListener` catches `Throwable` and only logs), or the push
succeeded and Glance's own update did not. Tell them apart before concluding
anything:

```console
adb shell dumpsys jobscheduler | grep -A5 com.gawi.app
adb logcat -s ReminderScheduler ReminderWorker RolloverWorker GlanceProjection
```

Pending work under `gawi.reminder.day-rollover` and no log line means armed and
deferred. No pending work means never armed, and `ReminderScheduler` will have
logged why. A `GlanceProjection` warning means it ran and the redraw is what
failed — the failure shape that looks identical to nobody having placed a widget.

**Put the cutoff back to midnight afterwards**, for the reason §4's own rollover
check gives: those steps start from midnight and this section sits below them,
so leaving it moved is how a later run passes vacuously.

**How to set a system RTL locale, because two obvious routes are dead ends.**
The emulator's `-prop persist.sys.locale=he-IL` is silently ignored, and a Play
Store image (`Small_Phone` is `google_apis_playstore`) refuses `adb root`, so
there is no `setprop` either. It has to be the Settings UI, and on API 37 the
language **search** crashes Settings outright, so scroll instead. Adding a
language does **not** switch to it — tap the row's drag handle for a **Move
up** menu, then confirm. **Hebrew is left installed as the second preferred
language on the `Small_Phone` AVD on purpose**, so a re-run is only that handle
and *Move up*. Read it back with `am get-config`, the one that proves direction
and does not lag: `…he-rIL,en-rUS-ldrtl…` against `…en-rUS-ldltr…`.

### The Momo widget and the large Today body — *launcher only*

Both of docs/ux/widget.md §7's surfaces. `MomoWidgetHostTest` and
`WidgetHostTest`'s large-body case bind them to a real host, so "the provider
binds and Glance composes it" is a machine's job; what follows is what only a
launcher shows. An emulator does not tick a box here, for the streak block's
reason: a widget lives in a launcher's process, and an OEM launcher's is not
the emulator's.

**The arithmetic for whoever has a third launcher**, and the reason neither
phone here can show the middle body: the face-above-rows form wants a width of
at least 180 dp (the provider's floor) and under 220 dp (the header's gate),
so either three cells of 60 to 73.3 dp or two cells of 90 to 110 dp. Nothing's
85 dp cells and the Pixel's 80 dp cells both fall in the gap between those
windows. The flip was reached on 2026-09-03 on a throwaway AVD — `gawi-flip`,
the API 37 Play image with `hw.lcd.width` 1260, `hw.lcd.height` 1800 and
`hw.lcd.density` 480, giving 420×600 dp and four columns — at 183×188 dp,
which shows the gates are right and the body draws, not that a phone
launcher's cells land in the window. To make it again anywhere:

```sh
avdmanager create avd -n gawi-flip -d small_phone \
  -k "system-images;android-37.1;google_apis_playstore_ps16k;x86_64"
```

then the three `hw.lcd.*` edits above in its `config.ini`.

**The clickable is what settles the Momo body; the one-item `LazyColumn` stays
the fallback.** Its root takes `actionStartActivity` to the app and carries the
mood sentence itself, because the folding rule the Today checkbox taught —
TalkBack folds a described, unfocusable view into its nearest *focusable*
ancestor — makes a focusable root the cheapest thing that can be reached: no
adapter, no service, and no "in list" in every announcement. The Streaks rows
being reached *without* a click shows a list suffices, not that a focusable view
would fail. The list was built and withdrawn on four counts, every one of which
still stands if a phone shows the clickable is not enough.
`MomoWidgetHostTest` waits for the mood sentence from a host view never attached
to a window, and such a host never asks a collection's adapter for its items, so
the instrumented test would time out with the whole body inside an item. On
API 29–31 Glance serves a list through `RemoteViewsService`, so the face would
arrive a beat after the ground where the `Column` paints in one pass — Today and
Streaks keep their headers outside their lists for that reason and Momo would
have had nothing outside. A `ListView` claims vertical drags that start on it,
so a home-screen swipe over a static tile would go dead.

- [ ] **The Today widget grows a header at four by three.** Place *Today* and
      resize it to four cells wide and three tall: Momo on a teal pill at the
      left, the mood line beside her, and beneath the line a band of thin
      segments — one per habit, filled where today is done. Then narrow it,
      still three tall, to the first width under **220 dp** the launcher allows:
      the header goes and the face sits above the rows again. Then shorten it to
      under **170 dp**: rows alone. `WidgetBodyTest` pins the two gates; only a
      launcher shows which side of them its cells land on. Run 2026-09-02 on the
      Nothing A059's own launcher (85 dp cells): four by three grew the header,
      three columns kept it with the mood line wrapping, and two rows was rows
      alone, because 3×2 spans 170.7 dp of cells and the launcher reports less
      than 170 after its padding. The middle body is still owed to a phone
      launcher whose cells land in the window the preamble gives.
- [x] **The band is the checkboxes.** Count the segments against the rows and
      tap a row: its segment flips with its box, on the same write. A band that
      disagrees with the rows beneath it has been given a rule of its own, which
      it must not have. Check it under an RTL system locale too. Run 2026-08-30
      on `Small_Phone` (API 37), where the RTL half failed and was fixed the
      same day. `BandBitmap.render` had no layout direction to consult and
      placed every segment at `left = index * pitch`, so index 0 sat at the
      bitmap's left edge in both directions and the band read backwards against
      the rows it repeats — the geometry was correct and the *reading* was
      wrong, which is why no test caught it. `WovenBand` now reads the app's
      configuration and `BandBitmap.render` mirrors on it
      ([ux/widget.md](ux/widget.md) §8 has the arithmetic). After the fix, both
      directions pass: sampled at the band's own `uiautomator` node,
      `[49,283][349,293]` under RTL against `[201,283][501,293]` under LTR, the
      woven segment sits at the band's right end in RTL beside the same row's
      glyph, and the tap still flips both together. Two notes on the locale
      recipe above: *Move up* raises a confirmation on this level, so it is two
      taps, and `am get-config` is the check that matters — `getprop
      persist.sys.locale` still reads the old value between the menu and the
      confirmation, so it lags the thing being set.
- [x] **In greyscale the band still reads.** Monochromacy on: a woven segment
      and an outstanding one must still tell apart by lightness alone (3.78:1
      light, 6.34:1 dark). `screencap` will not show you this — see the streak
      block above — so look at the device. Looked at on the Nothing A059,
      2026-09-02, dark scheme, the 4×3 body with eleven woven and three
      outstanding segments, monochromacy set over adb (`settings put secure
      accessibility_display_daltonizer_enabled 1` and `…_daltonizer 0`, which
      need no root on this phone): the two kinds of segment were clear on
      monochrome. Restored after.
- [ ] **TalkBack reads the large body once.** The mood line, then each row with
      its state. Not the face and then the line, and nothing for the band. Heard
      2026-09-03 on the Nothing launcher, a fresh 4×3 placement: the row is one
      named stop with its state, *"Read. Not done"*, and the name is not read
      twice. **The second stop is gone**: no control is emitted any more, so a
      row is the only stop it has and the mark beside the name is a decorative
      image (widget.md §8). **One half is still owed** — the header is not a
      stop at all, so the mood line is never spoken even though its `ImageView`
      carries it as a description. The rows reached 48 dp (`ROW_HEIGHT`) and
      took the streak widget's pattern, a description of name and state on the
      row; `WidgetRowTest` pins what the tree asks for, including that nothing
      inside a row is described. Whether the Pixel launcher behaves the same is
      unknown: TalkBack runs on an AVD, but its speech overlay is a preference
      no unrooted shell can set and the Play-store image refuses `adb root`, so
      an emulator shows what focuses and not what is said.
- [x] **The Momo widget is offered, two by two, and says what it is.** Long-
      press → *Widgets* → **Gawi**: three entries. The *Momo* preview on API 31+
      is her ground and a word with **no face** — deliberate, and widget.md §7
      says why; the description under the name is what names her. Seen on the
      Nothing launcher, 2026-09-02: *Momo* is "2 × 2" with "How Momo is doing,
      in one word" under the name and a preview of the ground and the word
      *pottering* with no face — a real host preview, a
      `LauncherAppWidgetHostView`, not a drawable. `input draganddrop` from the
      preview to the home screen placed each one.
- [x] **Her ground is the tank colour, in both schemes.** Light `#B4E9F0`, dark
      `#0F545C`, with the word on it in `#00353A` / `#B2E7EE`. A flat colour,
      not the Today screen's gradient. Sampled 2026-09-02 on the Nothing A059,
      the placed 2×2 widget: to the byte in both schemes, over the same 103,983
      pixels each way. `cmd uimode night no` needs no root on this phone, unlike
      the AVD; night mode was put back afterwards.
- [x] **The word follows the mood.** Complete everything → *thriving*; leave one
      → *pottering* or *worried* as the day goes; break a streak → *regrowing*,
      with the dimmer face. Same face as the Today screen at that moment. Seen
      2026-09-02 for *regrowing*, *pottering* and *thriving*, each arriving with
      the app's own write. *Worried* was seen 2026-09-03 by moving the hour
      rather than waiting for it, and it is the box's real finding: at a minute
      past the new hour the Today panel already read *Momo is getting worried.*
      while **both widgets still said *pottering***, because nothing had been
      written and their only clock is the 30-minute `updatePeriodMillis`
      (widget.md §4, "shortened, not bounded"). One write in the app and both
      caught up. So the word follows the mood and the mood follows the clock,
      but a widget learns of the clock only on the next write or period — the
      documented trade, now a seen one.
- [x] **With no habits she is still there**, under *No habits yet*. Archive
      every habit rather than `pm clear` to see it. Seen 2026-09-02 by appending
      fourteen `HabitArchived` events to the log (the run-as recipe in §5) and
      rebuilding the projection: she kept her ground and her smiling face with
      *No habits yet* in the word's place, and the Today and Streaks widgets
      said the same three words. Deleting the fourteen events brought all
      fourteen habits back.
- [ ] **TalkBack reads the sentence, not the word.** Focus the widget: *"Momo is
      pottering about."* once, and never *"pottering"* as well. A tap on it
      opens the app. It failed on the Nothing launcher for a reason the box had
      not feared: the widget was one stop saying *"Momo"*, the launcher's own
      label for the frame, because the face carried the sentence on a view
      nothing could focus and a described container hides its unreachable
      children. **A description is only read if something can focus the node
      carrying it** — the half the Streaks rows could not show, since they are
      reached without a click and so prove only that a list suffices. The body
      is now a clickable root that describes itself, the cheaper of the two
      experiments the block above names; the one-item `LazyColumn` stays the
      fallback. Heard 2026-09-03 in its old shape, and owed again on a phone in
      this one.
- [x] **A write in the app moves all three widgets**, on the same commit. Seen
      2026-09-02, all three placed and dumped before and after each write:
      ticking one habit checked its box on the Today widget and moved its
      Streaks row 0 → 1 while Momo, still regenerating over another, kept her
      sentence — as she should; ticking that other one then moved its row and
      changed Momo's description too. One write, three widgets, all changed by
      the next dump about five seconds later.

### The reminder

docs/ux/reminder.md. PRD §7 makes a **physical device** the primary target for
this as well as for the widget — OEM battery policies are the whole risk and an
emulator has none.

- [ ] **The status-bar icon is Momo's mark.** When a reminder posts, the small
      icon is the gill cluster — three lobes, no face — tinted by the system,
      not a bell and not a blob. `LauncherIconTest` proves the vector has fills
      and cuts nothing out of itself, and the shipped path data rasterised at
      24 dp reads as three lobes; only the shade shows it drawn by the platform.
      A trap for whoever runs it: a **full-colour** face in the notification
      header is not this icon at all. A small icon is alpha-only, so anything in
      colour there is SystemUI's cached app icon, which survives an AVD reboot
      and will show a build's worth of stale.

Every check below needs the reminder time moved to a couple of minutes ahead, in
Settings. **Put it back to 21:00 afterwards**, for the reason §4's rollover check
gives about itself: leaving it moved is how a later run passes vacuously.

**The button checks each need a set number of habits outstanding**, so seed once
rather than per box: create four habits and complete or un-complete them on
Today until the count the box names is left. The count in the notification's
body is what confirms you got it right before the buttons are worth reading —
they are the same fact twice (docs/ux/reminder.md §4), so a body disagreeing
with the buttons is itself the failure.

**How to see whether anything was posted, and why it matters here.** Two of the
checks below assert an *absence* — silent when everything is done, and one per
day — so a command that cannot see a notification makes both pass without
proving anything.

```console
adb shell cmd notification list | grep com.gawi.app
adb shell dumpsys notification --noredact | grep -A30 "pkg=com.gawi.app" \
  | grep -E "android.title|android.text|when="
```

A posted reminder is one line from the first, `0|com.gawi.app|1|null|<uid>`; the
second gives the copy and the time it fired. Note **`dumpsys notification`**,
not `dumpsys notification_manager`: the latter exists, exits zero, and contains
no `NotificationRecord` section at all, so grepping it for one reports "nothing
posted" for every app on the device including the ones that certainly did post.

- [ ] **It fires.** With at least one habit outstanding, set the reminder a
      couple of minutes ahead and lock the screen. A notification arrives saying
      *"N of M left today"*. Tapping it opens the app on Today.
- [ ] **It is silent when everything is done.** Complete every habit, set the
      time ahead again. Nothing arrives. This is PRD §6.1.5's second half, and
      the failure it guards against looks identical to success from the outside,
      so check it deliberately rather than assuming.
- [ ] **One per day, and this is the one only a device can show.** After a
      reminder has fired, `adb shell am force-stop com.gawi.app`. Then reopen
      the app, set the reminder time a couple of minutes ahead in Settings, and
      wait for it. **No second notification arrives** — the journal already
      stamped today, and it was read back in a process that did not write it.
      **The force-stop is the point of the check.** Skip it and nothing has been
      shown about the journal surviving a process ending: the app is alive
      throughout, because moving the reminder time means opening Settings, so
      the read could come from the same in-memory `DataStore` that wrote the
      stamp — and that is the one thing this adds over `ReminderCheckTest`.
      Reopening also re-arms both wakes through `ReminderScheduler.start()`,
      which is the documented repair path, so this exercises that for free and
      does not depend on whether `force-stop` cancels pending jobs by itself. Do
      **not** try it by forcing the job out of `dumpsys jobscheduler`: once
      `ReminderWorker` has succeeded its unique work is finished and the only
      thing pending is the *rollover*, so the job you would find and force is
      the wrong one — no second notification appears, the check looks green, and
      nothing about the once-a-day rule was exercised.
- [ ] **Notifications off is admitted, not hidden.** Turn the app's
      notifications off in system settings and come back to Settings. The
      reminder row shows *"Notifications are off, so this reminder will not
      arrive"* with a target that leads somewhere — the permission dialog, or
      the system page if the dialog can no longer appear. The row must update
      **on resume**, without re-navigating.
- [ ] **The time still edits while notifications are off.** Tapping the row
      itself opens the time picker, not the permission. The time drives Momo's
      worried face whether or not a notification can arrive.
- [ ] **Survives doze and the vendor's battery optimiser.** With `adb shell
      dumpsys deviceidle force-idle`, the reminder still arrives, late. It is
      *expected* to be late: architecture §7 makes delivery deliberately inexact
      and there is **no ceiling to quote**, because WorkManager will not wake a
      device to deliver this. What must not happen is the failure below.
- [ ] **A very late wake stays quiet rather than lying.** Let a deferred
      reminder land after the day cutoff — force-idle through midnight, or move
      the cutoff close. It must post **nothing**. A reminder at 00:30 saying *"5
      of 5 left today"* is the bug: it describes a brand-new day, and it would
      consume that day's one reminder so the real 21:00 one never comes.
- [ ] **Three outstanding gets three buttons; four gets none.** With three left,
      the notification carries a button per habit, labelled with the name alone.
      Add a fourth and post again: the buttons go entirely and the tap opens
      Today. Four is the case OQ-2 exists for, so check it deliberately — three
      buttons chosen out of four looks like success from the outside.
      Passed on an emulator (API 37): three drew *Read · Water · Journal*, and
      four drew none. Unticked because this block's target is a phone.
- [ ] **A tap writes, and moves the count with it.** Press one button. The habit
      is ticked on Today, the body drops by one, and that button is gone while
      the others remain. `adb shell cmd notification list | grep com.gawi.app`
      shows **one** row, not two — the fixed id is what makes a re-post replace.
      Passed on an emulator: *"3 of 4"* with three buttons became *"2 of 4"*
      with two, one row throughout, and Today showed the habit ticked.
- [ ] **A tap does not make a second sound.** The same press as above, with the
      device unmuted and the shade closed. The first post of the evening sounds;
      the re-post after a tap must not. This is the one a unit test cannot
      reach, and the flag behind it is one a reader would set for every post.
      Half-answered on an emulator: `dumpsys notification` showed the first post
      at `flags=AUTO_CANCEL` and the re-post at `ONLY_ALERT_ONCE|AUTO_CANCEL`,
      which is what the platform reads. Nobody has yet **heard** it.
- [ ] **The last habit takes the notification away.** Press the remaining
      buttons. When none is left the notification is gone rather than showing
      *"0 of N left today"*. Passed on an emulator: the shade emptied and Today
      read *"Nothing left today"*.
- [ ] **A tap the next morning writes to the night before.** Post a reminder,
      then let the day cutoff pass without tapping — force-idle through
      midnight, or move the cutoff close as the check above does. Tap a button
      *after* the rollover. The completion must land on the day the notification
      was posted for, which is now **yesterday** on habit detail. This is §4's
      one correctness rule, and its failure is silent: the three-day retro
      window accepts a write to today, so nothing refuses it. **Not runnable on
      a Play-store emulator image**: `adb root` is refused so the clock cannot
      be moved, and raising the cutoff moves the logical day *backwards*, not
      forwards. It needs a `google_apis` AVD that allows root, or a phone
      carried past its own midnight.
- [ ] **Two taps in quick succession settle on one answer.** With three buttons
      up, press two of them back to back — `adb shell "input tap X1 Y1; input
      tap X2 Y2"`, one invocation, no pause. Both habits are ticked in the app
      and the shade shows **one** button with a matching count, never a button
      for a habit just completed. Each button's intent was filled in when the
      notification was posted, so the second one carries a list that predates
      the first tap's write; the tap reading the day back is what settles it.
      Passed on an emulator (API 37): *"3 of 5"* with three buttons became
      *"1 of 5"* with one, and Today agreed.
- [ ] **A refused write leaves the notification alone.** The other side of the
      carried date, and reachable in a minute: with a reminder posted, raise
      **Day starts at** past now, which moves the logical day back so the
      carried date is in the *future*, and tap a button. Nothing is written, the
      notification does not change, and logcat says
      `a quick-complete tap was refused: FutureLogicalDate`. Dropping the button
      here would report a completion that never happened. Passed on an emulator
      (API 37) exactly so; put the cutoff back to 12:00 AM afterwards.
- [ ] **TalkBack speaks the verb, not just the name.** With TalkBack on, focus a
      button. It must announce *"Complete Read"*, not *"Read"* — a notification
      action has no content description, so the spoken form rides in the title
      as a `TtsSpan` and only a real screen reader shows whether the platform
      kept it. If it reads the bare name, the span is being stripped: drop to
      the plain name and say so in docs/ux/reminder.md §4. **This one needs an
      ear or the speech overlay.** On an emulator TalkBack focused each button
      and spoke, but it logs no text, `uiautomator` flattens spans out of the
      dump, and the overlay that would show the words is a TalkBack preference
      no unrooted shell can set — so nothing was learnt. Either half is safe to
      ship: a stripped span leaves the bare name, which is the fallback.

### Habit detail

PRD §5's retro window and per-completion note, reachable by hand
(docs/ux/habits.md §7). `make test` covers what the screen draws and what a tap
reports; what it cannot cover is a write going all the way to Room and coming
back, which is most of this list.

Open a habit from the **Habits** list — the row's name, not the Archive button.

**What the ticks below were run against.** `Small_Phone`, the **signed
release APK**, its installed bytes hashed against the build on disk rather
than read off a version number, on the log `scripts/avd-seed.sh baseline`
writes. No one habit carries every condition this list needs and the seed
does not try: *Read* has an unbroken run and completed cells to long-press,
*Meditate* stands at six days and unticked, *Stretch* has the three open past
cells, and *Yoga* is the weekly one.

**Prove a long-press is a long-press before reading any absence here.** The
same gesture on a *completed* cell opens the note sheet, and without that
control a press too short to register looks exactly like a screen that
ignores the gesture — an absence that passes two of these boxes for the
wrong reason.

- [x] **The streak matches the Today row's.** Tick a habit on Today, open it,
      and the number agrees. A daily habit reads as a count and a weekly one in
      weeks with a `w`. Two screens drawing one habit's streak differently is
      the failure docs/ux/today-view.md §5 exists to prevent, and the shared
      `StreakUi` is what should make it impossible — this is the check that the
      sharing actually reaches both.

      Run 2026-09-19: ticking *Read* on Today moved both screens to
      `streak of 13 days`, and *Yoga* drew **12w** above "week streak"
      against the row's `streak of 12 weeks`.
- [x] **An unfinished daily habit still shows its live streak.** Open a habit
      with a run going, before ticking it today. It must not read `0`.

      Run 2026-09-19: *Meditate*, headed "Not done yet today", read
      `streak of 6 days`.
- [x] **The oldest cell is drawn shut, and does nothing.** The leftmost of the
      five cells is struck through and dimmed. Tap it: nothing happens — no
      snackbar, no prompt, no tick. **The absence of a snackbar is the check**;
      a refusal message would mean the cell is being tapped and refused, which
      is exactly what §5 says not to do.

      Run 2026-09-19: tapping the struck-through *Day 15* added nothing to a
      five-second burst of dumps, where the same burst caught the prompt in
      all three passes on the cell beside it.
- [x] **A past day asks first, and cancelling changes nothing.** Tap one of the
      three open past cells. The honesty prompt appears. Cancel, and the cell is
      unchanged. Force-stop and reopen: still unchanged. Cancelling has to leave
      the log untouched rather than defer a write, and only a restart proves the
      event was never appended.

      Run 2026-09-19: the prompt appeared on *Stretch*'s Day 16, Cancel left
      it `not done`, and a force-stop and reopen left it there.
- [x] **Confirming writes to that day, not to today.** Tap a past cell, confirm,
      and the tick lands on *that* cell. Then go back to Today: the habit is
      **not** ticked there. This is the one worth running slowly — the 3-day
      window *accepts* a date one day off rather than refusing it, so a wrong
      date here looks like success and is only visible by checking which day
      moved.

      Run 2026-09-19: confirming on *Stretch*'s Day 17 moved Day 17 and
      nothing else — 16, 18 and 19 unchanged, the header still "Not done yet
      today", and Today still "8 of 10 left today".
- [x] **Un-ticking a past day prompts too.** Tap a completed past cell: the same
      prompt. Confirm, and it clears.

      Run 2026-09-19: the completed Day 17 raised the same prompt, and
      confirming cleared it.
- [x] **Today's cell writes with no prompt.** Tap the rightmost cell: it ticks
      immediately. PRD §6.4 wants same-day logging and undo frictionless, so a
      prompt here is a bug.

      Run 2026-09-19: the rightmost cell ticked on the tap, the burst catching
      only the result and no prompt — against the same burst catching one on
      every past cell minutes earlier.
- [x] **A note survives a restart.** Long-press a completed cell, type a note,
      Save. Force-stop and reopen the habit, then long-press that cell again:
      the note is in the field. Force-stop matters — an in-memory projection
      would hold the note without it ever reaching the log.

      Run 2026-09-19: a note typed over the seeded one read back from the
      field after a force-stop.
- [x] **Clear removes it, and that also survives.** Long-press the same cell,
      **Clear note**, force-stop, reopen: the field is empty. An empty note is a
      real write, so a clear that was skipped as a no-op would let the old note
      come back on the next read.

      Run 2026-09-19: after **Clear note** and a force-stop the sheet reopened
      on an empty field, with **Clear note** itself no longer offered.
- [x] **Long-press offers nothing on a day with no tick.** Long-press an empty
      open cell, and on the shut cell. Neither opens the sheet.

      Run 2026-09-19: neither did, the gesture proved by the sheet opening on a
      completed cell. **An empty open cell is not inert**, which this box does
      not say: the long-press falls through to the tap and raises the honesty
      prompt, so read the absence as the sheet's, not the screen's.
- [x] **Creating a habit opens it.** Add a habit and save: you land on its
      detail screen, not back on the list. Press Back **once** — you reach the
      habit list, not the create form you just filled in.

      Run 2026-09-19: saving *Sketching* landed on its own detail screen, and
      one Back reached the list with it in place.
- [x] **The strip follows the day rollover.** With detail open, set the **day
      cutoff** a couple of minutes ahead and wait past it. The strip shifts by
      one day and today's cell moves with it, with nothing tapped. **Put the
      cutoff back to midnight afterwards.**

      Run 2026-09-19: the strip read Days 14 to 18 while the clock stood before
      a 6:16 PM cutoff and Days 15 to 19 at 6:17, today's cell moving with it
      and nothing tapped between the two reads. **Setting the cutoff ahead
      moves the strip at once**, before any boundary is crossed, because the
      logical day has not started yet — so the shift to watch for is the
      second one. Put back to midnight.
- [x] **An archived habit still opens.** Archive a habit, then open it from the
      Archived section: it shows, and says it is archived. Unarchiving has to
      stay reachable, so a detail screen that refused to show one would be a
      trap.

      Run 2026-09-19: *Yoga* archived, opened from **Archived**, and said
      *Archived* under its schedule. It draws no streak badge and no tappable
      cells there, which is the screen refusing to log to an archived habit
      rather than refusing to show it.

---

### The history grid

PRD §5's per-habit heatmap, docs/ux/insights.md §8. `make test` covers what the
grid draws from a given month and what the steppers report; what it cannot cover
is the colour distinction being *visible*, which is the point of the screen, and
a month query reaching Room and coming back.

Reach it from habit detail: **See full history**, under the five-cell strip.

**What the ticks below were run against.** `Small_Phone`, the **signed
release APK** hashed against the build on disk, on `scripts/avd-seed.sh
baseline`. Three habits carry the conditions: *Read* is completed this month
and nowhere else, *Sleep log* runs through August into September and is
un-ticked today, and *Sketching* is made on the spot with nothing logged.

**The cells are in the tree, described.** `uiautomator dump` reads them as
*"Saturday 19, today, not done yet"*, so which day is which, which are done
and which one is today are read rather than counted off a screenshot — and
their bounds are what says the grid moved a column. The screenshot is still
what answers the two boxes about *looking* right, which is the half no dump
reaches.

- [x] **Done and not-done are obviously different, in both themes.** Tick a few
      days, open the grid, and look at it from arm's length in light and in
      dark. The pair is measured at 4.41 and 6.94, so this is not really in
      doubt — what is worth confirming by eye is the other half of §8.1's claim:
      that a not-done cell is *quiet* against the page rather than invisible,
      and that you can still read its number.

      Run 2026-09-19: *Read*'s September, both themes. Dark draws done as a
      bright teal with dark numerals against a quiet slate not-done; light
      inverts it to deep teal on pale grey-blue. The not-done numbers stay
      legible in both, which is the half §8.1 asks the eye for.
- [x] **Today is findable without hunting.** The ring, not a different fill. Do
      it on a day you have **not** ticked as well as one you have: the not-done
      case is the one that fails if the ring is ever replaced by a
      `secondaryContainer` ground, which measures 1.04 against the cell it would
      sit next to.

      Run 2026-09-19: both cases. *Read* is ticked today and *Sleep log* is
      not, and the un-ticked one keeps the ordinary not-done ground with only
      the ring to find it by — no second fill.
- [x] **Nothing after today is drawn.** In the current month, the cells past
      today are empty — no ground, no number. A grid that drew them as not-done
      would read as a month already half lost.

      Run 2026-09-19: September stops at 19. The tree holds nineteen cells and
      the screenshot shows nothing drawn past them.
- [x] **A tap does nothing at all.** Tap cells: done ones, empty ones, today. No
      ripple, no prompt, no tick, no snackbar. Read-only is docs/ux/insights.md
      §3, and the absence of a *refusal* is the check — a message would mean the
      cell is being tapped and turned down.

      Run 2026-09-19: a done cell, an empty cell and today, tapped in one
      pass, added nothing to a five-second burst of dumps — where the same
      burst on the month stepper beside them added thirty-three nodes.
- [x] **The columns line up with the week start.** Change **Week starts on** in
      Settings from Monday to Sunday and come back. The header letters rotate
      and the whole grid shifts by a column. **What this can show is that the
      next composition is already right** — Settings is reachable only from
      Today, which pops the grid, so no hand can watch a live screen update
      and the check is that nothing needs restarting for it to take.

      Run 2026-09-19: the header went M T W T F S S to S M T W T F S, and
      *Tuesday 1* moved one column, 169 px to 264 px, with *Monday 7* behind
      it.
- [x] **Stepping back reads real months.** Step back past a month you have
      history in, then back again into one you do not: the second draws an empty
      month rather than repeating the first's cells. Then step forward to the
      current month — the forward arrow disappears there and nowhere else.

      Run 2026-09-19: *Sleep log* back through August, 31 done, into July, 31
      not done — an empty month drawn rather than August repeated. Stepping
      forward, **Later month** is there in July and August and gone in
      September, with **Earlier month** present throughout.
- [x] **The month follows the day rollover.** With the grid open, set the **day
      cutoff** a couple of minutes ahead and wait past it. Today's ring moves a
      day, with nothing tapped. Worth doing at least once near a month end,
      where the whole grid should change month. **Put the cutoff back to
      midnight afterwards.**

      Run 2026-09-19: with a 6:39 PM cutoff the ring sat on *Friday 18* and at
      6:39 it sat on *Saturday 19*, one column along, with nothing tapped
      between the reads. The month-end half is **not run and cannot be today**
      — it needs a cutoff crossing the 1st, so it wants either a month end or
      the device clock moved. Put back to midnight.
- [x] **200 % font scale.** Six rows of cells at 200 %: no cell clips its own
      number, and two-digit days are where that shows first. **The cells hold
      their size and only the numeral grows**, which is what keeps them whole —
      so what overflows and scrolls is the page, the rate card below the grid
      going off-screen, rather than the column of cells.

      Run 2026-09-19: August on *Sleep log*, six rows, every two-digit day
      whole inside its cell, the row pitch 88 px at both 100 % and 200 %.
- [x] **A habit with no history at all.** Create a habit, open its history
      immediately. Not-done cells up to today and none past it — "nothing after
      today is drawn" holds here too — no crash, and nothing that reads as an
      error. The habit is new, not failing.

      Run 2026-09-19: *Sketching*, made and opened straight away, drew nineteen
      not-done cells and no done ones, with no error copy and an empty
      `AndroidRuntime:E`.

---

### The Insights screen, and the rate trend

docs/ux/insights.md §§8.7, 8.8. `make test` covers what each surface draws from
given numbers; what it cannot cover is a colour distinction being *visible*, a
glyph existing in the device's font, and an upgrade not losing anything.

- [x] **The upgrade, before anything else.** Install the *previous* build, make
      a habit or two and tick a few days, then install this one over it. Every
      habit and every completion must survive and the new start dates must
      appear — this is the first schema migration in the repo,
      `fallbackToDestructive` is deliberately absent, and a failure here is the
      one that costs real data. Run on an emulator 2026-08-24: a v1 database
      with 57 events and two habits came through with both versions at 2 and
      `created_on` populated from the log.
- [x] **Every tag bar is the same colour, and Untagged is still obvious.** Reach
      Insights from Today's app bar, pick Tags. The bars are all `primary` — a
      grey one would measure 1.07 against it, which is why the distinction is
      the *label* instead. Check at arm's length in both themes that "Untagged"
      reads as quieter than a tag name without reading as disabled.
- [x] **The bar track is visible where a bar is short.** Needs two tags with
      different totals; with one tag the bar is full width and the track is
      covered, so this check is silently vacuous otherwise. Run on an emulator
      2026-08-24 with two: the track sampled `#D3E3E6` light and `#2C3A3D` dark.
- [x] **A habit created today reads a dash, not a low number.** Make a habit,
      open Insights, and look at its row under Habits — and at its rate card on
      the history screen. Five dashes is correct: it has failed nothing. A
      percentage here means the creation date is not reaching the clip, which is
      the whole point of projecting it. Run on an emulator 2026-08-24: a habit
      made that day read a dash in its row and across all five months.
- [x] **Each period chip changes the window.** With history in more than one
      month, Month and Quarter must differ. With everything inside one month
      they will agree, and that is correct rather than broken — worth knowing
      before it looks like a bug.

      Run 2026-09-19 on `scripts/avd-seed.sh baseline`, which spans April to
      September: Month read 19 active days and 59 completions, Quarter 66 and
      158, Year 157 and 300. The focus sentence moves with them — career for
      the month and the quarter, health for the year, the previous quarter's
      weight outvoting this one's over a whole year.
- [x] **Every icon draws, in both themes.** Fifteen controls, all vectors. The
      failure to look for is not tofu, which a vector cannot draw, but its
      opposite: a `<path>` missing `strokeColor` inflates without complaint and
      draws *nothing*, and because each button is named through
      `contentDescription`, every semantics test passes on a control that
      renders empty. `GawiIconsTest` pins the XML, so what is left for the eye
      is that the strokes read at a glance and that `Icon`'s tint carries them
      in dark mode as well as light — walk the two themes separately rather than
      reasoning from the one shared tint mechanism. Run on an emulator
      2026-08-24 in both themes: all ten drawables drew and the strokes read at
      24 dp. **Three things that cost time here and will again.** This emulator
      **does not re-theme a running activity**, so `cmd uimode night yes` needs
      a `force-stop` after it or the screenshot lies. Screen coordinates **are
      not stable between themes or between data states**: *See full history*
      sits about 96 px lower once the habit has a live streak, because the
      `displaySmall` numeral appears above it, so a replayed tap script lands on
      a retro-strip cell instead. And a stray tap in that strip **writes a
      completion**. Drive this by screenshot-then-tap, not by a fixed script.
- [x] **The app-bar icons hold 24 dp at 200 % font scale, and that looks
      deliberate.** A behaviour change, not a regression: the characters these
      replaced were `titleLarge` and grew with `fontScale`; a 24 dp `Icon` does
      not. Material-correct, and touch targets are 48 dp either way — but the
      titles beside them still grow, so the thing to check is that the result
      reads as a decision rather than as clipping. Run on an emulator
      2026-08-24: the title grew about double, the three icons held their size,
      and there was no clipping and no collision. It reads as a row of controls
      beside a large title, which is intended.
- [x] **The directional icons flip under RTL, and the pager still reads
      forwards.** The three glyphs replaced — `←` (U+2190), `‹` (U+2039), `›`
      (U+203A) — are all `Bidi_Mirrored`, so the text shaper flipped them and
      the app got RTL correctness for free. A `VectorDrawable` does not: it
      needs `android:autoMirrored`. `GawiIconsTest` pins the attribute in both
      directions, and this is the other half — that the framework honours it.
      **`debug.force_rtl` does not work on this emulator** and silently returns
      an LTR screen, which is the trap worth recording. What works, without
      touching system settings, is a per-app locale:

      adb shell cmd locale set-app-locales com.gawi.app --user 0 --locales ar-EG
      adb shell cmd locale set-app-locales com.gawi.app --user 0 --locales

      Run on an emulator 2026-08-24: the Up arrow points right, toward the edge
      it now sits on; `list-checks` leads with its marks on the right; and in
      the month pager the *earlier* chevron sits on the leading right edge
      pointing right, which is backwards in RTL and therefore correct. Restore
      the locale afterwards — the second command above clears it.
- [x] **Each sparkline dot sits above its own month label.** The plot's x
      positions are coupled to the label row's column centres and nothing in the
      suite can see that — an earlier edge-to-edge spacing put the outer two
      dots about 27 dp off, which a screenshot shows at a glance and a test
      never will. Look at a habit with two or more months of history. Run on an
      emulator 2026-08-24: the dot's centre and its label's centre both landed
      on the same pixel column.
- [x] **200 % font scale on both new surfaces.** The chips wrap rather than
      clip, the bar rows stay readable, and the rate card's five month labels do
      not collide.

      Run 2026-09-19: nothing clips and nothing collides, but **at 200 % this
      build breaks a label mid-word rather than wrapping it whole** — the Year
      chip reads *Ye/ar*, and the rate card's last two labels *Augus/t* and
      *Septe/mber*. The percentages above them stay clear of each other and the
      bar rows keep their value on one line. A finding for
      [insights.md](ux/insights.md) rather than for a test: no assertion can
      see where a line broke.
- [x] **An empty period says so, and says *which* empty.** Copy, not an empty
      list, and the pickers stay reachable so there is a way out of it. **Three
      different notices**, and which one appears is the check: no habits at all,
      every habit archived, and a period with no completions each say their own
      thing (docs/ux/insights.md §8.8). Run on an emulator 2026-08-24, all three
      — the first from cleared app data, and the archived one showing "1 active
      day · 1 completion" above "Every habit is archived", which is the
      contradiction the three-way split was made to remove.

**The retrospective** (docs/ux/insights.md §9) is the same screen one period
back, and its owed looks are the ones no JVM test can take.

- [x] **Step back to a quarter that holds real data.** On `gawi-api30` — the
      rootable image, so the clock can be walked (`adb root`, `settings put
      global auto_time 0`, `date @<epoch>`) — seed two quarters with a tagged
      habit each, then open Insights → Quarter → ◀. The label reads "Q2 2026",
      the headline changes, the trend has three columns with a dot centred over
      each, and ▶ is greyed only on the current quarter. Then flip to Year: the
      offset resets to now, so the label reads "2026" and ▶ is greyed. Run on
      `gawi-api30` 2026-08-29: Q3 → ◀ read "Q2 2026 · 3 active days", three
      columns (Apr 0, May 2, Jun 1) with each dot on its label, "best 2 days" on
      the tagged habit, ▶ live; Year reset to 2026 with ▶ greyed. A second pass
      added that ◀ is already greyed on Q2, since Q2 starts before that habit's
      creation and nothing earlier can hold one, and a further tap did not move.
- [x] **The focus sentence flips when the top tag does.** With `health` leading
      last quarter and `career` this one: "Focus shifted from health to career."
      Re-tag the leading habit so both quarters agree: "Still mostly career."
      Remove every tag: no sentence at all, not "Untagged".

      Run 2026-08-29 and again 2026-09-19, the second time on
      `scripts/avd-seed.sh focus`, which is built for this box: health through
      June, career through July and August. July read "Focus shifted from
      health to career.", August "Still mostly career.", and June nothing at
      all, its own predecessor being empty. Clearing both tags left July with
      no sentence while the Tags view still drew an **Untagged** bar. **A
      finished period is the whole condition**: the sentence is claimed only
      against the period before, so the current one always reads "so far" and
      a period whose predecessor is empty reads nothing — which is why
      `baseline` cannot answer this box and a second log exists.
- [x] **200 % font scale on Year.** The eight-to-twelve trend columns are the
      densest row in the app: the counts stay on one line each, the initials
      under them do not collide, and the stepper label between its two arrows
      does not wrap. The initials are what bought the room, so this is the look
      that decides whether they were enough. Run 2026-08-29 on eight columns:
      every count and initial on one line, "2026" between its arrows, the rows'
      "Every day · best 2 days" unwrapped. Also seen in dark.
- [x] **A row's best run reads as one line.** "Daily · best 31 days" beside the
      percentage, and a habit made this period with no run shows the schedule
      alone — no "best 0 days" anywhere. Run 2026-08-29: "Every day · best 1
      day" beside a dash on a habit created that day and back-filled, so the
      creation clip leaves one day for the run and no finished day for the rate,
      which is the two rules agreeing. The no-run half was seen on the same
      pass: a habit created in May and not yet done that quarter read "Every
      day" alone beside "0%", no "best" anywhere.

---

### The restyle — both themes, once

Everything here is a thing the tests cannot see: `GawiColorSchemeTest` asserts
every contrast ratio the app draws in both themes, which is the part a number
can answer. Whether it *looks* like one app is not.

**Ticked items ran on an emulator, not on a phone, and are labelled so.** That
is enough for this block — everything in it is colour, contrast and layout,
which an emulator renders with the same Compose and the same resource
qualifiers a device would. It is *not* enough for the widget or for TalkBack,
so those stay unticked: a widget lives in a launcher's process against a
background it does not own, and TalkBack cannot be driven from `adb` at all.

- [x] **Every screen, in both system themes.** Settings → Display → Dark theme,
      and walk Today, the habit list, habit detail, the editor and settings in
      each. What the ratio test cannot catch: two roles that both pass and still
      look wrong together, and any surface that reads as a different app.
- [x] **A day streak next to a week streak.** `StreakBadge` distinguishes them
      by `primary` versus `tertiary` and a trailing `w` ([visual-
      identity.md](ux/visual-identity.md) §4.1). Both roles are measured to be a
      lightness step apart, so this check is the other half: that the two are
      *tellable apart at a glance*, in both themes, on a real row rather than in
      a swatch. Light mode's `tertiary` is a dark bronze rather than the gold
      the drawings showed — this is where that either reads as deliberate or
      does not.
- [x] **Habit detail's retro strip.** Three marker states, not two: a completed
      day is `primary`, an open day not yet done is `onSurfaceVariant`, and a
      shut day is `outline` — which should be the quietest of the three. The
      strip is the densest use of the scheme and the place a recessive role that
      is *too* recessive shows up. Look at today's cell especially: it is the
      one with a filled ground, and the ground is what made the first version of
      this fail (`visual-identity.md` §3).
- [x] **Cold start in dark mode, watching for a flash.** Force-stop the app,
      then launch it. The window is painted from `values-night/themes.xml`
      before Compose runs, and its `windowBackground` is pointed at the scheme's
      dark surface for this reason. Any visible flip from a lighter grey to the
      app's background means the two have drifted apart.
- [x] **The theme setting, on a phone whose system theme is the opposite**
      ([ux/settings.md](ux/settings.md) §7). Settings → Appearance → Theme →
      Dark on a light phone: every screen flips immediately, and the app does
      not leave Settings while doing it — the Activity is recreated underneath
      on API 31+, so a lost scroll position or a reopened dialog is what a
      failure looks like. Then Light on a dark phone, then back to *Follow the
      system* and toggle dark from quick settings, which must move the app
      again.
- [x] **Cold start with a forced theme, on API 31 or later.** The check above
      this one, run with Dark chosen and the *system* in light mode. Force-stop,
      launch, watch the first frame. There must be no flash at all:
      `setApplicationNightMode` puts the choice in the configuration, so the
      window resolves from `values-night/` before Compose runs. Run 2026-08-26
      on API 37: the launch window came up `#0E1A1C` with the system in light
      mode, measured frame by frame off a `screenrecord`, so the light `#F4FBFA`
      never appeared.
- [x] **The same cold start on API 29 or 30, where it is a different check.**
      Its own item rather than a second half of the one above, because those two
      versions have no `setApplicationNightMode`. Run 2026-08-28 on both,
      `google_apis` x86_64 `medium_phone` emulators, Dark chosen with the system
      in light: nine cold starts each, sampled frame by frame off a
      `screenrecord`. The launch window came up light `#F4FBFA` and held it
      before the dark app replaced it — **66–331 ms on API 30 and 317–448 ms on
      API 29**, the one place the two levels measurably differ. That is the
      flash API 31 and up does not have. Backgrounding the app and opening
      Recents was part of the check and is where the document turned out to be
      wrong: the thumbnail is a screenshot of the window and measured `#0E1A1C`
      on both. [ux/settings.md](ux/settings.md) §8 carries the numbers and the
      two claims that did not survive them.
- [x] **The status bar's icons in both forced modes.** The bars follow the
      resolved theme rather than the device's, so this is where that either
      holds or produces white-on-white — the exact failure `enableEdgeToEdge`
      was added for.
- [x] **The widget after switching**, on the home screen. It must stay in the
      launcher's theme and change nothing. That is correct behaviour and the row
      says so; the check is that the *row* said so, not that the widget moved.
- [x] **The app draws in Outfit, at the right weight.** Two things. **The
      face**: Outfit is geometric, so its `o`, `a` and `0` are visibly circular
      against the system sans, and the status bar clock stays Roboto and gives a
      free side-by-side. **The weight**: the `wght` axis is named explicitly on
      each entry, and if that were ever dropped as redundant the whole app would
      render at the file's `fvar` default of 100 — hairline, everywhere. That is
      loud rather than subtle, and a unit test pins it, so this check is a
      second line and not the only one. Also worth a look with the system *Bold
      text* setting on, where the roles ask for W700/W800 and should hit real
      instances rather than fake bold. Seen on an emulator 2026-08-24: face
      correct, weights correct. That pass also covered a third thing a re-run
      should **skip** — five characters the screens drew as text (`☰`, `◔`, `⚙`,
      `✎`, `✕`) falling back to the platform face, which is why every character-
      as-icon is a vector now ([visual-identity.md](ux/visual-identity.md) §7.5)
      and has its own check above. Two glyphs are present and were checked
      rather than assumed: `−` (U+2212) and `·` (U+00B7), which matter most for
      the weekly-target stepper, since it draws `−` beside an ASCII `+` at one
      size.
- [x] **200 % font scale, a second time, because the face changed.** The pass in
      the accessibility block ran against Roboto, and Outfit has its own metrics
      — wider, different x-height — so every clipping and overflow judgement
      there was made about a face the app no longer draws. Not a doubt about the
      old run; it answered a different question. The `displaySmall` streak
      numeral and Settings' longest body paragraph are where a wider face would
      show first. Re-run on an emulator 2026-08-24 and clean: Today's empty
      state wraps to two lines and keeps its button; Settings' longest body
      paragraph wraps to six lines and the notification notice wraps rather than
      clipping; habit detail wraps a three-word name to two lines, still draws
      the `displaySmall` numeral, and scrolls far enough that all five retro-
      strip cells and *See full history* are reachable — checked by scrolling to
      the end rather than by assuming the screen scrolls.

### Momo — the four faces, both themes

The character, the habitat, the transition and the celebration
([momo.md](ux/momo.md)). `MomoRenderTest`, `HabitatRenderTest` and
`CelebrationRenderTest` prove each mood draws, differs from the others and
moves; what they cannot see is whether the motion reads as a character rather
than a screensaver, the things that are Settings reads, and the one sequence
that only plays while the frame loop runs.

**Dump the description before trusting any chip or panel copy.** `uiautomator
dump` reads the same node a screen reader consumes, and the first chip build's
said only the mood: a node with a `contentDescription` has its `text` ignored
*in the dump*, so the drawn count was silently unspoken while every test
passed. A milestone run has a second description to read, the milestone line
followed by the count with the mood line dropping out.

**What the ticks below were run against**, and the trap that nearly cost the
block: `Small_Phone` ships with **all three animation scales at zero**, so
the first motion run recorded a still screen and read as four boxes failing.
Check `settings get global animator_duration_scale` before trusting anything
here. Motion is read off `screenrecord` rather than `screencap`, and the
cheapest reading is the clip itself: four seconds of a still tank encodes to
**one frame and 33 kB**, the same four seconds moving to forty frames and
2 MB.

**Tempo is measured on the weed strip, not the whole tank.** Momo's own float
is the larger part of the tank's frame-to-frame change and it does not follow
the weeds — measured over the whole tank, worried comes out *busier* than
content and the ordering this block asks about inverts. The strip below her
body holds the weed tips and little else.

- [x] **All four moods on the tank, in both themes.** Content is the default
      with habits added and nothing done late in the day; tick everything for
      thriving; let the reminder hour pass with one habit open for worried;
      break a streak (a habit completed yesterday, skipped the day before) and
      open the app inside the three-day window for regenerating — the tank
      drains and one right-hand gill is short and pulsing. If any two are hard
      to tell apart with the app held at arm's length, that is a finding for
      momo.md §3, not for the tests.

      Run 2026-09-19, all four and both themes, from `scripts/avd-seed.sh`:
      content and thriving and worried off `baseline`, worried by moving the
      reminder behind the clock, regenerating off its own log. No two are
      close — worried swaps the smile for a wavy mouth, thriving closes the
      eyes to arcs and adds two drifting stars, and regenerating drains the
      tank to grey with the right gill cluster visibly short.
- [x] **The tank keeps the mood's tempo.** Behind Momo, four weeds sway and four
      bubbles rise: briskly while thriving, at the canvas's own pace while
      content, slower while worried. Regenerating drains the water, leans the
      weeds outward and greys them, and no bubble rises. If the weeds and
      bubbles ever look out of step with each other, that is a finding for
      momo.md §4 — they share one tempo by design.

      Run 2026-09-19. Mean per-pixel change per frame across the weed strip,
      same clip length and frame rate throughout: thriving 0.61, content 0.37,
      worried 0.32, regenerating 0.11. The order the doc asks for, and
      regenerating is all but still — its water drained, its weeds leaned out
      and greyed, and no bubble rising.
- [x] **A mood change is one Momo.** Tick a habit so the mood changes and watch
      the change: the body should glide from one float to the other with the
      face crossfading on it, never two bodies at different heights. The water
      should drain or refill on the same beat when regenerating is one end of
      the change.

      Run 2026-09-19, both halves. Ticking out of thriving caught the two
      faces mid-crossfade **on one body at one height**, the stars fading with
      the old face. Ticking out of regenerating refilled the water, returned
      her colour and opened her eyes across two frames together — one beat,
      and the body never moved to meet it.
- [x] **Finishing the day plays once.** With one habit left, tick it: Momo hops,
      bubbles rush up from under the tail and the water brightens for a beat,
      then the thriving loop continues. Untick and re-tick: it plays again,
      because the mood left thriving and came back. Now background the app and
      return, and rotate the phone: nothing plays — a finished day is not re-
      celebrated (momo.md §6). TalkBack says nothing extra either: the line
      changing to "All done. Momo is thriving." is the whole announcement.

      Run 2026-09-19: the last tick played the hop, the bubble rush and the
      brightening, and un-ticking and re-ticking played it again. Backgrounding
      and returning, and then rotating, played **nothing** — read against the
      same recording method that had just caught the celebration, and against
      the resting thriving tank, **which carries two drifting stars of its
      own**. Those stars are the idle loop, not a replay, and they are what
      this box would otherwise be misread by.
- [x] **The pastel body on the light tank.** momo.md §2 calls this the softest
      edge on purpose. Look at whether the silhouette reads from the gills and
      eyes alone; if the body vanishes into the water, the fix is the tank's
      gradient, not Momo's colour.

      Run 2026-09-19: it does not vanish. The silhouette reads from the body
      itself on the light tank, not from the gills and eyes alone.
- [x] **A milestone plays bigger.** With a habit at six days, tick it: Momo hops
      twice, a wider burst rises, a ring of gold sparkles opens out around her
      and the water brightens harder; the line under the tank reads "7 days in a
      row. Momo is dazzled." for two seconds and the row's streak badge swells
      on a teal pill, then everything returns. Untick and re-tick: it plays
      again. If that habit was also the last one of the day, only the milestone
      plays, and the thriving line follows the milestone line. Background the
      app and return, and rotate: nothing plays (momo.md §6). A weekly habit
      reaching its fourth week does the same with "4 weeks" and a gold pill.

      Run 2026-09-19 on the daily rung: "7 days in a row. Momo is dazzled."
      held for one dump in four — about the two seconds it claims — with the
      row's `7` on a teal pill, and the recording shows the double hop, the
      wider burst and a ring of gold sparkles opening around her. Un-ticking
      and re-ticking played it again. **The weekly rung is not run**: the
      ladders are 7/30/100 and 4/12/52, so a weekly habit has to sit one week
      below its rung and the seeded one is at thirteen.
- [ ] **TalkBack on a milestone.** With TalkBack on and focus on the row, tick
      the six-day habit: after "checked", the panel's live region reads the
      milestone line once, and the mood line once more when it returns two
      seconds later; the badge announces nothing extra. Tick and untick any
      other row: each is followed by one sentence — the mood line and the
      remaining count — and nothing else.

      *Device only, and the layer no test reaches* — this one belongs with the
      **Accessibility** block below and runs when that does, not with the rest
      of this one.
- [x] **Animator duration scale off** (Developer options → *Animator duration
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

      Run 2026-09-19. Four seconds of the tank encoded to **a single frame**,
      which is the compositor saying nothing changed at all. The same
      worried-to-thriving tick that ran to 42 frames and 4.6 MB with the switch
      on ran to 6 frames and 67 kB with it off — a cut, and no celebration.
      Crossing the seven-day rung still swapped the line and still put the `7`
      on its pill while nothing moved. Turning it back on resumed the float.
- [x] **200 % font scale.** The tank stays 250 dp; the copy under it grows and
      wraps and pushes the list down rather than clipping. The character must
      not shrink.

      Run 2026-09-19: the tank measured `[32,176][688,676]` — 500 px, 250 dp —
      at both 100 % and 200 %, so the character does not shrink. The panel
      around it grew 628 px to 696 px on the short mood line and to 780 px on
      the one that names a habit, which is the copy wrapping: a clipped line
      would not have grown it.
- [x] **TalkBack, once.** Swipe onto the panel: it is one node and should
      announce the mood's line once — "Momo is pottering about." followed by the
      remaining count — and never "image" or "unlabelled". If the tank and the
      caption land as two stops, the merge has been lost. Heard on the Nothing
      A059, 2026-09-02: one stop, the sentence and the count together, no
      "image". The overlay spelled it *"Momo is pottering about.. 3 of 14 left
      today"* — the copy's own full stop plus TalkBack's joiner, a nit to hear
      rather than a defect. The line was also read unprompted on a cold launch
      and again each time the panel scrolled back into composition, which is the
      live region firing on appearance that today-view §1 predicted.
- [x] **The regenerating line names a habit** (today-view §6). Break one streak,
      then break a second a day later, and check the line moves to the newer
      break: most recently broken is the rule, and two breaks *on different
      days* are the only way to see it — breaks on the same day are the tie
      case, where the rule is "keep the user's own order" and cannot be told
      apart from the ordering rule. `TodayUiMapperTest` pins which name reaches
      the state and `TodayScreenTest` pins that the panel draws it; neither can
      see whether a long habit name still reads as a sentence. Seen 2026-09-02
      on the Nothing A059, seeded so that *Read* broke on 31 August and
      *Stretch* on 1 September with *Read* first in the list: the line read
      *"Momo is regrowing a gill. Pick Stretch back up."* — the newer break over
      the one that sorts first. Ticking *Stretch* moved it to *"Pick Read back
      up."*, and ticking *Read* ended it.
- [x] **The app-bar chip** (today-view §1). The title "Today" gives way to
      Momo's face and "4 left", and scrolling back restores the title. The face
      is the current mood's, and the swap is a crossfade. Seen on API 37, and
      re-checked after the gap between face and count widened from 2 dp to 12
      dp, since that is the axis a full bar runs out of room on. **Read the box
      below before trusting this tick**: it was seen at `wm size 720x820`, not
      at the AVD's own 720x1280, for the reason that box gives.
- [x] **The chip on a full-height screen with a realistic habit count.** The
      trigger is `firstVisibleItemIndex > 0` — the panel has to leave the
      viewport *entirely* — and a short list cannot scroll that far, so with
      four the panel scrolls to a sliver and the title never changes. Whether
      that is right, or whether the trigger should fire on "mostly gone"
      instead, is open in today-view §1. **There is no habit count that answers
      this**: the threshold is the panel's height against the rows above it,
      and a row grows a second line when it carries a weekly ratio or a
      `was 3`, so the same number of habits raises the chip on one screen and
      not another. Measure it where you are, and read the figure as the
      screen's rather than the design's — one more reason §1's question is a
      design call.

      Run 2026-09-19 on this AVD, 720×1280 / 320 dpi, a 628 px panel over 96 px
      rows: ten habits did not raise it, eleven did, the bar reading *"Momo is
      getting worried. 1 of 11 left today"* as one node. Measured 2026-09-02 on
      the Nothing A059, 1080×2392 / 375 dpi, a 722 px panel over 150 px rows: it
      needed fourteen, and appeared with forty pixels to spare, reading
      *"10 left"*.
- [x] **The chip at 200 % font scale.** The face, "4 left" and all three action
      icons on one bar, nothing truncated. This is the case the chip replaces
      the title *for*, so it is the one that would have justified undoing that
      decision, and it did not. Same short-screen caveat as above.
- [x] **The milestone line in the chip** (today-view §1 and §6). Record it with
      `screenrecord` rather than a screenshot, because the line holds for two
      seconds and a screencap round-trip is most of that. Done on the AVD at
      720×1280, font scale 1.0, with ten habits, one holding a six-day run so a
      tick crossed the seven-day rung: the frames show "9 left", then the
      milestone line for about two seconds, then "8 left", with the row's badge
      swelling to a `7` on its pill at the same time. **This is the box that
      earned its place.** The first build drew the *panel's* line here and it
      truncated to "7 days in a row. Mom…", crowding the first action icon, at
      font scale 1.0 rather than only at 200 % — and every JVM assertion was
      green and would stay green, because **a Compose text assertion passes on a
      node that draws its string clipped**. The fix is a chip-length plural of
      its own (`today_chip_milestone_days`), the way `today_chip_remaining`
      already works for the count. The drawn label and the spoken sentence are
      deliberately different strings, so re-check that divergence if either is
      ever touched. It is still not *announced*, because the node is not a live
      region, which is the box below.
- [x] **TalkBack: the chip is one stop, and a tick under it is silent.** Swipe
      onto the chip with the list scrolled down: **one** stop, announcing the
      mood and the count together, never a bare "image" or two stops for the
      face and the label. Scroll back to the top and swipe into the panel: it
      announces once when reached. Then tick a habit while the chip is up — the
      only thing that should speak is **the row's own checkbox**, because the
      chip is not a live region and the panel is not composed. Silence from the
      chip is the expected result, not a failure; today-view §1 says why it is
      accepted and leaves making it a live region open, and this is the check
      that would settle that. `chip_isNotALiveRegion` pins the property on the
      JVM, but only a screen reader can say what is spoken. Heard 2026-09-02 on
      the Nothing A059 and re-heard 2026-09-03, reading *"Momo is pottering
      about. 12 of 14 left today."* and nothing after it. The first hearing
      retired a premise: today-view §1 built the description on the premise that
      a described node's text is not read, and on this TalkBack **it is**, so
      the count was spoken twice in two forms — the same leak the retro strip,
      the history grid and the trend columns had. `clearAndSetSemantics` fixed
      it and `chip_doesNotAlsoReadItsLabel` pins that the label is now only in
      the unmerged tree.

### The launcher icon

[visual-identity.md](ux/visual-identity.md) §7.1, §8. `LauncherIconTest` proves
the three layers exist, draw and are wired; every launcher masks and scales them
differently, which is what is left.

**Wallpaper & style does not apply on `Small_Phone`**, which costs two of these
three boxes their subject. Neither a **Shape** nor *Style → Minimal* takes on
that image: **Apply** enables on a selection and the tap lands, and neither
Gawi's icon nor the system's own changes, with
`theme_customization_overlay_packages` still `null` afterwards. So the mask
variants and the themed layer are both out of reach there, and the boxes below
record only what that costs them.

- [ ] **In the app drawer and on the home screen.** One gill cluster — three
      frond dots around a paler body circle, no face — on the darkest teal the
      palette holds, under whatever mask the launcher uses (circle, squircle,
      rounded square). Nothing that carries meaning is clipped. The mark reaches
      31.1 of the 33 units a launcher guarantees, so it needs no corrective
      scale and nothing should be clipped at all. Wallpaper & style → Icons →
      **Shape** makes the mask a setting here rather than something to hunt a
      launcher for; Circle, Square and Arch are the three worth trying, Arch
      being the most aggressive of the five.

      **Not earned 2026-09-20**, with half of it read. In the app drawer: one
      cluster — three frond-pink lobes around the paler body circle, no face —
      on the dark teal ground, nothing clipped at the mask's edge. What is
      missing is the home screen, where Gawi does not sit here, and four of the
      five masks, for the reason the preamble gives.
- [ ] **Small.** Drop it in a folder and look at it at the drawer's smallest
      size: **three lobes still read as three**, not as a pink smudge. That is
      the claim §7.1 makes for this mark where the retired one claimed a
      readable face, and it is the whole reason the face went — the full
      character measured as mush at 40 px. A note for whoever runs it: `input
      draganddrop` makes the folder only over a *short* hop between two dock
      icons; over a longer one the launcher displaces the target or flips the
      page instead, and chained `input motionevent` with a dwell does not merge
      at all.

      **Not earned 2026-09-20.** Gawi sits in the drawer and not the dock here,
      so the short hop this note requires was not available from the shell, and
      the drawer's own rendering is not the smaller size the box is about.
- [ ] **Themed, API 33+.** The icon becomes the same cluster in the system
      tint, not a second mark: flattened to one colour the three lobes are still
      three lobes, which is the argument §7.1 makes for one geometry across all
      three layers. The paler body circle merges into the cluster by design.
      Below API 33 the coloured icon stays and there is nothing to check. **The
      setting is not called *Themed icons*** on this level: it is Wallpaper &
      style → Home screen → **Icons** → *Style* → **Minimal**, against
      *Default*, and it needs an explicit **Apply**.

      **Not earned 2026-09-20.** Nothing themed was ever drawn, for the reason
      the preamble gives, so there was nothing to judge.

### Accessibility — *device only, and the layer no test reaches*

The automated half is already in `make test`: WCAG contrast ratios in
`WidgetTextColourTest` and `TankContrastTest`, the 48 dp touch-target floor in
three screen tests, and semantics — roles, content descriptions, disabled
state — throughout. Architecture §8 records why the one automated ruleset worth
wanting is not wired up yet. What is left is what a ruleset cannot judge:
whether the app is usable without sight, and whether it survives a reader who
needs it larger.

**One thing here is checkable rather than audible, and that is better than
listening.** `adb shell uiautomator dump` gives a node's `content-desc`
together with its `bounds`; a screenshot gives the pixel inside those bounds.
Pair them and an announced name is checked against the colour actually drawn,
which is the defect visual-identity §4.3 describes — and it needs no TalkBack.

- [ ] **A TalkBack pass over the three core flows.** Turn TalkBack on, then add
      a habit, complete one from the Today view, and change the day cutoff —
      using **swipe navigation only, never a direct tap**. Direct tapping is
      what hides the failure: focus order and announcement are only observable
      when you are forced through the tree in order. Watch for a control that is
      reachable but unnamed, two targets that say the same thing, and a state
      change that happens silently (WCAG 2.4.3 and 4.1.3). Re-heard 2026-09-03
      on the Nothing A059 by D-pad and the speech overlay: a Today row reads
      *"Read. Streak broken, was 10 days. Check box"* — the name first, no emoji
      name, the streak in words, the badge last — settings rows read title,
      value and helper in order, and *Add a habit* was swiped end to end with
      nothing unnamed or silent. Two things the overlay showed that no test
      predicted: an unchecked row carries **no state word when landed on**,
      because this TalkBack says *checked* for a Compose checkbox and nothing
      for the other state, and the weekly ratio was read **as drawn**, *"1/3
      this week"*, `today_week_progress` having had no spoken twin. It has one
      now — `:core:ui`'s `spokenWeekProgress`, *"1 of 3 this week"* — and this
      box is where it is heard; the icon picker's was retired with the picker
      (visual-identity §7.3). Open for the day-cutoff **picker** itself, the
      one part of the three flows no pass has driven.
- [x] **A TalkBack pass over the Insights screen.** Two pickers and a list, and
      the thing to listen for is whether a bar row makes sense read aloud: the
      label, the total, and nothing announcing the bar itself. The bars carry no
      text, so a row is its label and its number — if that is not enough to know
      which tag is which, the row needs a spoken description of its own. Then
      the trend on Year: each column should be one stop reading "March, 15
      active days" in full, never a bare "M", since the initials are hidden
      behind the column's own description (insights.md §9.4). And the disabled ▶
      on the current period should still be announced, as disabled, not skipped.
      Heard 2026-09-02 and re-heard 2026-09-03 on the Nothing A059: a bar row is
      **three** stops — name, percentage, schedule — which is the recorded shape
      rather than a leak; the disabled ▶ is a stop, *"Later period. Button.
      Disabled"*; and the August column reads *"August, 30 active days"* and
      nothing after it, once `LabelledColumns` cleared the column it describes
      (`a trend column speaks its month once, not its texts as well`). The rate
      card's undescribed columns are untouched.
- [ ] **A TalkBack pass over the history grid, swipe-only.** Its own item
      because it is the one screen in this app that **hides content from a
      screen reader** — the seven column letters carry `clearAndSetSemantics`,
      since `T` and `S` each name two days and are noise read aloud
      ([insights.md](ux/insights.md) §8.4). That is only defensible if the trade
      holds, so check both halves: **swipe through a full month** and confirm
      you never land on a bare letter, and that every cell says its weekday
      spelled out, its date and its state — *"Friday 14, done"*. Then the two
      that are easy to get wrong: today announces itself as today and as *not
      done yet* rather than *not done*, and a day after today is not a focus
      stop at all. Thirty-one stops is a lot of swiping and that is the point —
      a calendar is read day by day, and if this is tedious rather than usable
      it is worth knowing before the trends screen copies the pattern. Re-heard
      2026-09-03 on the Nothing A059: today's cell reads *"Thursday, 3, today,
      not done yet"* and nothing after it, so the trailing day number is gone
      since `DayCell` clears rather than merges. The letters were proven absent
      from the tree the day before — 31 cell nodes for August, no letter nodes
      anywhere — and days after today are not nodes at all. Open on the
      **tedium** of the full month, which is a judgement no pass has made.
- [ ] **The retro strip, specifically.** The densest thing here: five cells —
      four writable and one drawn shut — each carrying a day, a done state, a
      note marker and up to two gestures. Every one of those is in the spoken
      label by design (`RetroStrip`'s `cellAction`), so this is the check that
      the label is *legible as speech* rather than merely complete. A shut day
      is the one to listen to hardest: it must announce as unavailable, not as
      an unchecked box. The modifier order this rests on, and what breaks if it
      moves, is [habits.md](ux/habits.md) §7's. Re-heard 2026-09-03 on the
      Nothing A059. An open cell: *"Day 2, not done. Mark done. Check box"*; the
      done cell adds *"Add or edit note"*. No letter, no number and no *"Check
      mark"* after either, so the cell's four child texts are gone from what is
      spoken, and the role and the toggle state survived the clearing. The
      user's swipe the same day heard the shut day as *"Day 30, too old to
      change. Disabled"* — unavailable rather than an unchecked box, the hardest
      thing this box asked to hear. Open because the **note marker** —
      `cellAction` appends *"has a note"* only when the cell has one — was on
      neither quoted cell, so a noted day is the one sentence still to hear.
- [x] **200 % font scale.** Settings → Display → Font size, at maximum. Three
      screens carry reasoning about this in comments — `TodayScreen`,
      `HabitDetailScreen` and `SettingsScreen` all scroll or floor a dimension
      because of it — and nothing else verifies any of it. Check that no text is
      clipped, that the strip is still tappable, and that the streak's
      `displaySmall` has not pushed the strip off a short screen. Run on an
      emulator on 2026-08-23 including the `displaySmall` case, which needs a
      habit with a live streak to draw at all: nothing clipped and the whole
      strip still on screen. Re-run on 2026-08-24 when the app moved from Roboto
      to Outfit, whose metrics differ; the restyle block has what the second
      pass measured.
- [ ] **Accessibility Scanner**, as a pre-release sweep rather than routine.
      Install Google's Accessibility Scanner, run it over each screen, and read
      the report the way you would a Lighthouse audit: the touch-target and
      contrast items are already asserted, so what it earns its place for is
      unlabelled controls and text-contrast cases the theme tests do not reach.
      Enable its service over adb — `appops set … SYSTEM_ALERT_WINDOW allow`
      plus the `enabled_accessibility_services` setting — then tap its floating
      button on each screen.
      Run 2026-09-02 and re-scanned 2026-09-03 with Scanner 2.5.1. **The habit
      list and Settings: no suggestions at all**, no unlabelled control anywhere
      and no touch-target hit in the app itself. The home screen with both
      widgets returns six, none of them a row: the three 32 dp checkboxes are
      each a *Touch target*, the first also a duplicate description, and the two
      widget frames are *Unsupported item type*, the Scanner declining a
      `LauncherAppWidgetHostView` rather than a finding. **All four app-side
      items are closed in the code** — no control is emitted and the mark is a
      decorative image (widget.md §8) — so this sweep is owed again, on the
      release build, to confirm them gone. Two classes the first
      scan raised are decided rather than open: the icon badge's *text contrast*
      went with the badge, which a habit no longer has
      ([visual-identity.md](ux/visual-identity.md) §7.3); and the repeated
      Insights row texts are the unmerged-row shape that box records. Today,
      detail, the editor and Insights are inferred clear from the same badge
      change, not re-scanned — and the editor has two fewer controls to scan
      than when that was written.

**Still owed, and an emulator discharges none of it.** Five open items, each
with its blocker: the day-cutoff **picker** under TalkBack, undriven; the
**tedium** of a full month on the history grid, a judgement rather than a
sentence; a **noted** strip cell, whose *has a note* word no quoted cell
carried; the Today widget's body, whose header is still not a stop, so the mood
line its `ImageView` carries is never spoken; and the Momo widget's body, whose
clickable root is built and owed a hearing on a real launcher.
The widget's three device checks in its own block are owed against the widget
as it now stands, palette included (visual-identity.md §7.4).

Not in CI and not automatable: TalkBack cannot be driven from the instrumented
source set, so §8's line that CI runs unit tests only is unaffected here.

### The About section and the Licences screen

docs/ux/settings.md §9. The JVM tests prove the two notices are packaged and
rendered; what only a device can show is how the section reads.

- [x] Settings scrolls to a fourth header, **About**, below Data. The Version
      row shows the build's `versionName` in its small grey line, has no primary
      middle line, and does nothing when tapped. Seen 2026-09-02 on the Nothing
      A059: a tap on the row changed nothing in the tree, dumped before and
      after, and there is no clickable wrapper around it to have taken the tap.
- [x] **Licences** opens a screen titled Licences with two headings, Outfit then
      Lucide, each with a one-line role and the full notice text under it. Both
      texts scroll; the OFL runs to its section 5 and the Lucide notice to its
      MIT block. Back returns to Settings at the same scroll position. Seen
      2026-09-02: the OFL node is 4,387 characters and reaches TERMINATION, the
      Lucide node 3,207 and reaches the last line of the Feather MIT block, and
      Back returned with *About* at the same y as before.
- [x] TalkBack: the Version row is read as one item with no "double-tap to
      activate"; each heading on the Licences screen is a stop of its own. Heard
      2026-09-02 on the Nothing A059, swiped by hand, and both hold.

### The day-rollover refresh

The same mechanism as the reminder (docs/ux/reminder.md §2).

**The wake is readable directly.** The rollover is unique
`OneTimeWorkRequest` work, so `dumpsys jobscheduler` names
`#RolloverWorker#` and prints its *Minimum latency*. That is the arming
itself rather than something downstream of it, and it costs a command
instead of a launcher and a wait.

- [x] **The widget follows the rollover without being tapped.** With the widget
      on the home screen and a habit ticked, set the **day cutoff** a couple of
      minutes ahead and wait past it without touching anything. The tick clears
      by itself.

      *Launcher only.* A widget lives in a launcher's process, so this runs with
      **The widget** block rather than here; the half of the mechanism that is
      not the widget is the box below.

      Run 2026-09-20 on `Small_Phone` against the **signed release APK**, on a
      **real** midnight rather than a moved cutoff — the stronger route, a
      cutoff moved ahead taking the logical day *backwards*. With two habits
      ticked and nothing touched, the marks were still set at 00:00:21 and
      00:01:24 and were clear by 00:03:27: the wake landing, not the boundary
      itself.
- [x] **A cutoff edit re-arms it.** Change the cutoff again; the wake moves with
      it. A settings edit writes nothing to the log, so nothing pushes it — the
      scheduler's `SettingsSource` collector is the only thing that can. Read
      the wake off the scheduler either side of the edit.

      Run 2026-09-19: `#RolloverWorker#` stood at a minimum latency of 4h38m
      with the cutoff at midnight and the clock at 19:25. Moving the cutoff to
      11:00 PM took it to 3h33m, and moving it back took it to 4h33m, each
      re-read within a minute of the edit and nothing written to the log in
      between.
- [x] **Put the cutoff back to midnight afterwards.** Same reason as above.

      Run 2026-09-19: put back, and the wake followed it back — which is the
      second half of the observation above.

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

---

## 6. The release build

`make release` is the only target that needs a secret. It reads four
environment variables, writes a signed and shrunk APK, and ends in
`apksigner verify` — because **nothing in AGP fails an unsigned release
build**: it names the output `app-release-unsigned.apk`, exits 0, and that APK
installs nowhere.

Make the keystore once, never inside the repository, and keep a second copy
somewhere you would still have it if this machine died. Losing it means nothing
can ever update an installed Gawi again.

```console
$ keytool -genkeypair -v -keystore ~/keys/gawi-release.jks \
    -alias gawi -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` writes a **PKCS12** keystore by default, and PKCS12 cannot hold a key
password that differs from the store's — it says *"Different store and key
passwords not supported"* and ignores the one you gave. So `GAWI_KEY_PASSWORD`
holds the same value as `GAWI_KEYSTORE_PASSWORD` unless the keystore was made
with `-storetype JKS`.

The build reads the *environment* and not the file, so export them first, and
check the password against the keystore before paying for a build:

```console
$ set -a; . ./.env; set +a        # .env.example is the template
$ printf '%s\n' "$GAWI_KEYSTORE_PASSWORD" | keytool -list \
    -keystore "$GAWI_KEYSTORE_PATH" -alias "$GAWI_KEY_ALIAS"
$ make release
Verifies
V2 Signer: certificate DN: CN=Gawi, O=Gawi, C=PH
V2 Signer: certificate SHA-256 digest: 786135f69a3b…a0a62c6a
APK:     app/build/outputs/apk/release/app-release.apk
mapping: app/build/outputs/mapping/release/mapping.txt
```

**Read the DN, not just `Verifies`.** An assemble that is already up to date
prints success in a second or two without repackaging anything, so `Verifies`
on its own can be perfectly true of an APK signed by a key from some earlier
experiment. The certificate is what ties the artifact to the keystore: its
SHA-256 has to be the digest `keytool -list` printed above, which is why the
target prints certificates rather than only the verdict.

**The two tools print that digest differently**, which reads as a mismatch when
it is not one: `keytool` groups it in colon-separated uppercase pairs,
`apksigner` runs it together in lowercase. Strip and fold before comparing —
`tr -d : | tr 'A-F' 'a-f'` — or compare the first and last groups by eye.

**The `./` is load-bearing, and that is the shell's doing rather than a typo.**
`.` takes a bare name as something to find on `$PATH`, so `. .env` fails with
*"no such file or directory"* in zsh even standing in the directory that holds
it — bash falls back to the working directory and zsh does not. `. ./.env`
works in both.

**What the `keytool -list` line buys.** A wrong password otherwise surfaces
minutes later inside `packageRelease`, in a message naming the keystore rather
than the `.env` that is actually wrong. This settles it in two seconds: a
`PrivateKeyEntry` line and a SHA-256 fingerprint mean the file and the password
agree, and *"keystore password was incorrect"* means the password is wrong and
the keystore is fine. Piped rather than handed over as `-storepass`, so the
value stays out of shell history — only the variable name is recorded. Keep the
fingerprint it prints beside the password and the alias, since it is how an APK
is later proved to have come from this key.

`mapping.txt` travels with every release (PRD §5). Without it a stack trace off
a shrunk build names `a.b.c` and nothing more, and it is per-build — the copy
that can read an APK is the one written beside it.

**Two things the release build takes away**, both of which shape §4's device
work. It cannot install over a debug build, because the keys differ, so it wants
an uninstall first and that destroys the event log. And `run-as` refuses on it
outright — *"package not debuggable"* — so the adb route into `/data/data` is
gone and the JSON export is the only way to read back what the app holds.

**Putting it on a device that already has the debug build.** The paragraph above
says what the release build takes away; this is the order that works around it.
Export first — the uninstall is what destroys the log, and the JSON is the only
way back.

```console
$ export ANDROID_SERIAL=emulator-5554      # adb devices -l
                                           # in the app: Settings -> Data -> Export a copy
$ adb pull /sdcard/Download/gawi-export-$(date +%F).json /tmp/
$ adb uninstall com.gawi.app
$ set -a; . ./.env; set +a
$ printf '%s\n' "$GAWI_KEYSTORE_PASSWORD" | keytool -list \
    -keystore "$GAWI_KEYSTORE_PATH" -alias "$GAWI_KEY_ALIAS"
$ make release
$ adb install app/build/outputs/apk/release/app-release.apk
$ adb shell run-as com.gawi.app true       # expect: package not debuggable
$ adb pull "$(adb shell pm path com.gawi.app | cut -d: -f2 | tr -d '\r')" /tmp/installed.apk
$ apksigner verify --print-certs /tmp/installed.apk
$ for k in window_animation_scale transition_animation_scale animator_duration_scale; do
      adb shell settings get global $k
  done
$ adb push /tmp/gawi-export-$(date +%F).json /sdcard/Download/
$ adb shell am start -n com.gawi.app/.MainActivity
                                           # Settings -> Data -> Import a file
                                           # Settings -> the reminder row -> allow notifications
```

**`run-as` failing is the install check, not a nuisance.** *"package not
debuggable"* is the two-second proof that what is on the device is the release
APK and not a debug build that happened to survive. `adb install` succeeding
proves only that *something* installed. The `pm path` → `pull` → `apksigner`
pair is the stronger form of the same question: `make release` proves the DN of
the file on disk, and only this proves it of the bytes the device is running.

**Read the animation scales back rather than setting them.** A reinstall loses
them (§4's `make itest` bullet), so after this the three may be anything; which
value they should hold depends on the box being run, and §4's Momo block turns
the animator scale off on purpose and back on afterwards.

**Do not `pm grant` the notification permission.** `POST_NOTIFICATIONS` is
requested from the settings reminder row rather than at first launch, so
visiting that row once is itself a check that the request path survived R8 —
granting it from the shell skips the only observation a fresh install buys.
Keep `adb shell pm grant com.gawi.app android.permission.POST_NOTIFICATIONS` for
repairing a permanent denial afterwards.

**`make itest` cannot run against this, and that sets the order.** `Makefile`'s
`itest` is `connectedDebugAndroidTest`: it installs the *debug* APK, which the
release key refuses with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. So the
instrumented run and anything else needing `run-as` come first, on debug, and
its uninstall is this recipe's third line for free.
