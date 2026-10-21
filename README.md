# TrimAll

Root-based storage maintenance for Android. Runs `fstrim` on every block-backed mount, can run F2FS garbage collection first, and gives you a one-tap home-screen widget to do it all. Everything it does is logged verbosely.

> ## ⚠️ Highly experimental. This can break your device.
>
> This project is **experimental and unfinished**. It runs commands as **root** against your phone's storage (`fstrim` and, optionally, forced F2FS garbage collection through `/sys/fs/f2fs/*/gc_urgent`).
>
> - It **may cause data loss, instability, heavy battery drain, overheating, boot problems, or a bricked device.** Some phones have flash controllers or kernels that react badly to trim or forced GC.
> - The code was written **without being compiled or tested on real hardware and only tested on my own device**. Expect bugs.
> - There are **no safety gates**: no battery, thermal, or charging checks, and no scheduler. It runs when you tell it to, regardless of device state.
> - Forced GC adds flash wear and can run for many minutes on a full or fragmented device.
>
> **Back up your data first. Use it at your own risk. There is no warranty of any kind.** If you aren't comfortable with rooted devices and kernel sysfs nodes, don't use this.

## Features

- **Request root button.** Triggers the grant prompt from Magisk, KernelSU, or APatch and shows the current status.
- **Trim everything.** Reads `/proc/mounts`, keeps block-backed filesystems, de-duplicates bind mounts, and runs `fstrim -v` on each one. Read-only or unsupported mounts (for example erofs) are marked `UNSUPPORTED` instead of being skipped silently.
- **Optional F2FS GC.** For each F2FS mount: set `gc_urgent` to 1, wait until dirty segments drop to a threshold or a timeout hits, then set it back to 0. `gc_urgent` is always reset, even if the run is cancelled.
- **One-tap widget.** One tap runs the whole action in a foreground service without opening the app. It shows the running stage, or the last result and how long ago it ran.
- **Verbose logging and history.** Every shell command, exit code, stdout, and stderr is logged, plus device info and GC polling. Each run is saved with per-mount results. Export zips the logs and history for sharing.

## Settings

| Setting | Effect |
| --- | --- |
| Trim every mount | Runs `fstrim -v` on each mount. On by default. |
| F2FS garbage collection first | Forces GC on F2FS mounts before trimming. On by default. Turn it off for the lightest, trim-only run. |
| Stop GC at N dirty segments | GC stops waiting once dirty segments reach this value (default 512). |
| GC timeout per mount | Hard stop for the GC wait, in seconds (default 600). |

The widget uses the same settings. With GC off, a widget tap only trims. With both switches off, a run does nothing useful.

If a run is killed mid-GC, use **Settings → Reset GC urgent** to set `gc_urgent` back to 0 on every F2FS volume.

## Requirements

- A rooted device (Magisk, KernelSU, or APatch)
- Android 9 (API 28) or newer
- An `fstrim` binary. Most devices ship one (toybox). If yours doesn't, put a static build at `app/src/main/assets/fstrim/<abi>/fstrim` (for example from [EXALAB/fstrim-static](https://github.com/EXALAB/fstrim-static)).

## FAQ

Does this damage the device? F2FS GC taking a long time to process.
Not in any way that’s dangerous, but it isn’t free either. Phones use flash storage (UFS or eMMC), not a hard disk, and the two parts of Run all have different costs.

Trim (fstrim) is safe. It only tells the flash which blocks are free, so it doesn’t rewrite your data. The wear is negligible, and Android already runs it nightly on its own. The one caveat is that cheap or buggy flash controllers can misbehave under trim, which is rare on modern phones.

F2FS GC is the one that costs something. It moves live data around to consolidate free space, and every move is a flash write, so it adds wear. A single run is nothing: Android does the same thing during overnight idle maintenance. Running it many times a day for weeks could add measurable wear, and it also warms the phone and drains battery.

Why it’s slow: GC keeps going until the dirty-segment count drops to the threshold (512 by default), and on a full or heavily fragmented phone that can take many minutes. It’s the same work Android would eventually do anyway, just done all at once.

To make it faster or gentler (all in Settings):

Lower the GC timeout, for example to 120 seconds. It stops cleanly and still trims afterward.
Raise the dirty-segment threshold, for example to 2000, so it stops earlier.
Turn off “F2FS garbage collection first” and use trim only. This is the lightest option and still gets you most of the benefit.
Run it while charging, and no more than once a day or so.

## Download
Download from [Releases](../../releases)


## Build

Open the project in Android Studio (JDK 17). It will create the Gradle wrapper and sync. Or run:

```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

Stack: Kotlin, Jetpack Compose, Jetpack Glance (widget), [libsu](https://github.com/topjohnwu/libsu) for root.

## How a run works

1. Check root.
2. Scan `/proc/mounts` and pick candidate filesystems.
3. For each F2FS mount (if GC is enabled): GC, then reset `gc_urgent`.
4. `fstrim -v` on every mount.
5. Save the result to History and write the full log.

Trim sizes are shown as "passed to discard". That is what `fstrim` reports, not the amount the device actually freed.

## Logs

Logs are at `files/logs/trimall.log` (rotates at 2 MB, keeps 5 files), and the history is `history.json`. Both can be exported from the app.

## Status and roadmap

Not done yet: Shizuku backend (the `Executor` interface is ready for it), tests, CI.

Bug reports are welcome. Please attach the exported log, your device model, Android version, kernel version, and root manager.

## License

Add a `LICENSE` file before publishing (GPL-3.0 or Apache-2.0 are reasonable choices).

## Disclaimer

Provided as is, without warranty. The authors are not responsible for any damage, data loss, or other consequences of using this software.
