# AIDL Bind Spike — throwaway probe (NOT the real app)

**What this tests:** whether a completely ordinary, non-system, non-AYA-signed
app (this one) can bind to `com.ayaneo.gamewindow`'s `AyaAidlService` and
change the AYASpace performance profile — with no `xsu`, no root, no
system UID — based on the teardown findings in the sibling
`research/ayaspace-teardown/` and `research/aya-gamewindows-teardown/`
directories (see those for the full reverse-engineering trail; this app is
the empirical confirmation step both of them recommended as the next move).

If this works, it changes `apl/app/`'s architecture significantly: profile/
fan/GPU/RGB/controller changes could go through this Binder channel instead
of `xsu -c` sysfs writes — no ~100ms-per-call floor, no risk of fighting
AYASpace over the same sysfs node, and fan control (a lever `apl` couldn't
otherwise safely replicate) comes along for free.

## What it does, concretely

1. On launch, binds to `com.ayaneo.gamewindow`/`...AyaAidlService` (explicit
   component, no root involved).
2. Registers a callback (hand-rolled `Binder` subclass — see `AidlProtocol.kt`
   for why there's no `.aidl` file backing this) and waits for gamewindow to
   deliver `"msg_type_register:<id>"`, which is the real `clientId` every
   subsequent command needs.
3. Two buttons, **enabled only once a clientId is received**: "Gaming" and
   "Eco". Each sends `"<clientId>:msg_type_performance:com_set_performance_mode:<N>"`
   over the Binder connection (3=Gaming, 0=Eco).
4. After sending, waits 1.5s and reads back `scaling_governor`/
   `scaling_cur_freq` for policy0/policy2 **through the already-proven `xsu`
   channel** — this is the objective "did it actually work" check,
   independent of whether the Binder transaction itself reported success.
   A transact() that doesn't throw only means the message was delivered and
   accepted, not that the hardware changed — the xsu read-back is what
   actually confirms it.

## Install and run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **AidlSpike** on the device. You should see, within a second or two,
without touching anything:

```
bindService() returned: true
onServiceConnected: name=ComponentInfo{com.ayaneo.gamewindow/...AyaAidlService} binder=...
registerCallback() transact completed without exception -- waiting for msg_type_register callback...
callback received: "msg_type_register:<some number>"
clientId assigned: <some number>
```

...and the status line at the top should read **"READY -- connected,
clientId=..."**, with both buttons now enabled. **This alone is already the
first half of the answer** — if binding/registration fails, everything
below is moot and that failure itself is the finding (see "What a failure
looks like" below).

Tap **Gaming**, wait ~2 seconds, read the log. Tap **Eco**, wait ~2 seconds,
read the log. Alternate a few times if you want to see it flip back and
forth.

## What a SUCCESS looks like

```
--- sending Gaming (mode index 3) ---
send("7:msg_type_performance:com_set_performance_mode:3")
send() transact completed without exception
verify (via xsu, 105ms, send_ok=true): P0_GOV=performance | P0_FREQ=1248000 | P2_GOV=performance | P2_FREQ=3148800
Expected for Gaming per apl-diag/docs/HARDWARE_PROFILE.md: P2_GOV=performance, P2_FREQ near 3148800 (max)
```

Then tapping **Eco** should show `P2_GOV=powersave`, `P2_FREQ` near `729600`
(per the AYASpace-mode table in `apl-diag/docs/HARDWARE_PROFILE.md`) — a
visible, real flip between the two, driven entirely by this app, with no
`xsu` write of our own anywhere in the command path (only in the read-back
verification step).

## What a FAILURE looks like, and what each one would mean

- **`bindService() returned: false`, or no `onServiceConnected` ever
  fires** — the bind itself is rejected. Would mean `exported="true"` with
  no permission isn't actually enough at the OS level for a fully unrelated
  app (contradicts the static analysis — genuinely surprising if it
  happens, worth capturing the exact log for the teardown docs).
- **`onServiceConnected` fires but `binder=null`**, or immediately followed
  by `onServiceDisconnected` — service resolved but refused to hand back a
  live connection.
- **`registerCallback() FAILED` with a `SecurityException`** — would mean
  there IS an enforcement point we didn't see in the decompiled source
  (maybe in a part of `onTransact` jadx failed to fully decompile).
- **Callback never arrives (`clientId` never assigned, buttons never
  enable)** — registration accepted but gamewindow never replies; possibly
  needs an additional handshake step not visible in the one-pass teardown.
- **`send()` throws** — registered fine, but the actual command is
  rejected — would narrow the problem to authorization specifically on the
  command path, not the connection itself.
- **`send()` succeeds with no exception, but the `xsu` verify shows no
  change** — the message was accepted but didn't do anything — could mean
  the message format needs something the static analysis missed (unlikely
  given how directly it was traced, but possible), or this specific
  Pocket FIT unit's `IAyaDevices` implementation ignores the command for
  some device-specific reason.

Any of these is still a real, useful answer for `apl`'s architecture
decision — a clean failure narrows down exactly which layer to look at
next, same as everywhere else in this project.

## Pull results

```bash
adb pull /sdcard/aidl_spike_result.txt
adb logcat -d | grep AIDL_SPIKE > aidl_spike_logcat_dump.txt
```
(Pull the logcat dump immediately after testing, not later — a lesson
already learned the hard way in `autotdp-ab-harness`'s run1.)

File both under a `results/` subfolder here (create it, same convention as
the other probes in this repo) and report back — this is the single most
architecturally consequential probe in the project so far, worth writing
up properly in `FINDINGS.md` regardless of which way it goes.
