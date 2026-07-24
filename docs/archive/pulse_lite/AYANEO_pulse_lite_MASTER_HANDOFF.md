# KONKR Pocket FIT / AYANEO Root Script Environment — Master Handoff

**Date:** 2026-07-06
**Purpose:** Onboarding substrate for a new conversation/session. Covers device identity, execution model constraints, everything established so far via pulse_lite (v3.2 -> v3.7), and the newly proposed next project (controller/button remapping tool). Written so a new assistant instance can be productive within 1-2 messages without re-discovering basics.

---

## 1. Device & Platform Identity

- **Device:** KONKR Pocket FIT — an Android gaming handheld (Switch/Steam Deck-style form factor with built-in gamepad controls: ABXY, D-pad, dual sticks, shoulder triggers/bumpers, plus extra buttons: two extra buttons near the triggers, two on the back, and several on the front that are currently underused).
- **SoC:** Snapdragon 8 Gen 3-class chip (marketed/labeled "Snapdragon G3 Gen 3" in device UI). Adreno 750 GPU.
- **CPU topology:** 8 cores across 4 cpufreq policies (confirmed via `/sys/devices/system/cpu/cpufreq/policyN/scaling_max_freq`):
  - `policy0` = cpu0-1, Cortex-A520 (efficiency cluster)
  - `policy2` = cpu2-4, Cortex-A720-mid cluster
  - `policy5` = cpu5-6, Cortex-A720-high cluster
  - `policy7` = cpu7, Cortex-X4 (prime core)
- **GPU control node:** `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` (inverted scale: 0=fastest/1050MHz ... 10=slowest/422MHz). Busy% at `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage`.
- **Settings app:** AYANEO Settings app (package `com.ayaneo.settings`), built with Hilt/Dagger DI, Kotlin. This is the vendor app that exposes System Settings -> Performance / Controller / Device / Fan Settings / Root Script, etc. (confirmed via decompiled sources, see Section 3).
- **OS:** Android, no Google Play-certified root (no Magisk installed, device is NOT rooted in the traditional adb-root or su sense).

---

## 2. Execution Model & Hard Constraints (READ THIS FIRST)

This is the most important section for any new session — **do not assume standard adb root or Magisk-style root access.**

- **No `adb root`.** ADB is available (unrooted/user mode) but cannot get a root shell via adb directly.
- **No Magisk / no su binary available to the user directly.**
- **The only privileged execution path is the AYANEO Settings app's built-in "Root Script" feature** (System Settings -> Custom -> Root Script; confirmed via screenshots, IMG_0191/0192/0194 this session).
  - The UI (`RootScriptFragment`, part of `com.ayaneo.settings.ui.optimization`, confirmed via jadx decompile in `01_rootscript.txt`) presents a dialog with a file path field (e.g. `/storage/emulated/0/rootcheck.sh`) and a "Run" button.
  - The user writes a `.sh` script, saves it to shared storage (e.g. via `adb push` to `/sdcard/...`), enters the full path in the Root Script dialog, and taps Run.
  - The vendor app then executes that script file with elevated privileges via its own internal mechanism — confirmed from decompiled classes `RootShell.java` and `YtRootShell.java` (both attached as user files this session) which appear to be the actual privilege-broker classes the vendor app calls into (likely a signed system service or vendor-granted `su`-like binary bundled with the OS image, NOT standard Magisk).
  - **Confirmed empirically:** scripts launched this way run with `uid=0(root) gid=0(root) groups=0(root) context=u:r:xsud:s0` (directly observed in pulse_lite startup log lines across v3.4-v3.7, e.g. `id=uid=0(root) gid=0(root) groups=0(root) context=u:r:xsud:s0`). The SELinux context `u:r:xsud:s0` is notable — suggests a custom/vendor SELinux domain (`xsud`) specifically carved out for this script-runner mechanism, not the standard `su` domain.
- **What this means practically:**
  - We can write and deploy arbitrary `sh`/`bash` scripts that run as root ONCE per invocation, but there is no persistent root shell to interactively poke around in real time.
  - Everything must be scripted in advance: write the script, push it via adb, run it via the Root Script UI, then pull back results/logs via adb (files written to `/sdcard/...` by the script, since the script runs as root and can write anywhere, but we as the user only have unrooted adb pull/push access to shared storage).
  - There is no live TTY/interactive root shell equivalent to `adb shell su -c ...` — every root action must be pre-written into the `.sh` file that gets executed.
  - Iteration loop: (1) write script locally with the assistant, (2) `adb push script.sh /sdcard/`, (3) enter path in Root Script dialog + tap Run, (4) `adb pull /sdcard/output.log` (or similar) to retrieve results, (5) paste log content back into the conversation for analysis.
  - The daemon pattern used by pulse_lite (an infinite `while true; do ... sleep N; done` loop with a sentinel file `/sdcard/pulse_lite.stop` to signal shutdown) exists specifically because there's no persistent shell to send a kill signal to interactively — the script has to poll for its own stop condition.
  - Screenshots (IMG_0191.jpg, IMG_0194.jpg, IMG_0192.jpg, this session) show the actual Root Script dialog: a text field for the script path, Cancel/Run buttons, and a result dialog afterward showing `Command: echo ... > /sys/...` / `Result: readback=...` style output — confirming the vendor app itself does simple sysfs read/write testing as a built-in diagnostic feature (separate from user-supplied scripts), but the general "Root Script" mechanism is what we exploit to run our own arbitrary shell scripts.

---

## 3. AYANEO Settings App — Reverse Engineering Notes

- The app has been partially decompiled via jadx (files `01_rootscript.txt` through `06_manifest_components.txt`, `RootShell.java`, `YtRootShell.java` attached across sessions).
- Confirmed package structure: `com.ayaneo.settings.ui.optimization.RootScriptFragment` (the dialog UI), `RootScriptResultFragment` (shows execution results), backed by `RootShell` / `YtRootShell` classes that appear to be the actual native/system bridge for privileged execution.
- The app also has a `com.ayaneo.settings.ui.controller` package with `KeyLayoutFragment` variants per device model (`ar02`, `ar05`, `ar06`, `ar07`, `ar14`, `ar16` — likely internal AYANEO hardware revision codes), plus a `ControllerFragment` and `ControllerViewModel` — **this is almost certainly where the Xbox/Nintendo button-style switching logic lives** (per the user's new question this session about replicating that functionality). Not yet deep-dived — flagged as the starting point for the next project (see Section 5).
- `SwitchPerformanceModeFragment` and `CpuFragment` under `com.ayaneo.settings.ui.performance` / `com.ayaneo.settings.ui.personalization` are likely related to the vendor's own built-in performance-mode presets (separate from our custom pulse_lite daemon).
- Full manifest component list available in `06_manifest_components.txt` if deeper jadx analysis is needed in a future session.

---

## 4. pulse_lite — AutoTDP Daemon (Prior Work Summary)

This is the completed/ongoing side-project from prior sessions. Full technical detail lives in `pulse_lite_v3.5_handoff.md` (already produced as an artifact) and the versioned `.sh` files themselves (v3.2 through v3.7 all exist as artifacts/attachments). Summary for context:

- **What it is:** A root-script shell daemon that dynamically caps CPU (`scaling_max_freq` per policy) and GPU (`kgsl-3d0/max_pwrlevel`) clocks based on live busy%/thermal signals, to reduce heat/fan noise/power draw without sacrificing FPS in GPU-fps-capped games.
- **Deployment pattern:** `adb push pulse_lite_vX.sh /sdcard/pulse_lite.sh`, then run via the Root Script UI (path `/sdcard/pulse_lite.sh`), which launches it as the infinite-loop root daemon described in Section 2. Stopped via sentinel file: `adb shell 'touch /sdcard/pulse_lite.stop'`.
- **Version history (brief):**
  - v3.2-v3.4: Single shared TIER state machine for both CPU and GPU, driven by GPU busy% + thermal zones. Worked, but caused false-positive escalations (GPU-idle/CPU-heavy Switch emulation forced to max unnecessarily; GPU spikes in menus wrongly escalated CPU).
  - v3.5: Split into two independent state machines (`CPU_TIER`/`CPU_HTIER` and `GPU_TIER`/`GPU_HTIER`), added an aggregate `/proc/stat`-based CPU busy signal. Decoupling worked for the intended cases, but full decoupling created a NEW failure mode: CPU could stay under-clocked (idle) even while GPU was heavy, causing FPS regression (observed: FPS=39 instead of 60 in a GPU-heavy scene).
  - v3.6: Replaced aggregate CPU busy% with **per-core max** busy% (`read_cpu_busy_max`, mirrors the existing worst-case-wins pattern used for temperature). Added a GPU-driven "floor" but it only covered the `idle -> medium` transition. Soak test showed this was STILL insufficient — CPU got stuck at `medium` for an entire session while GPU repeatedly hit `heavy` (same underlying regression, one tier lower).
  - v3.7 (current, latest artifact delivered): Replaced the idle-only floor with a **full tier-rank floor** — CPU_TIER can never be ranked lower than GPU_TIER (idle=0 < medium=1 < heavy=2), applied via `tier_rank()`/`rank_to_tier()` helper functions and a post-resolution clamp each tick. CPU can still independently escalate ABOVE GPU's rank on its own busy signal (preserves the Switch-emulation case: GPU light, CPU heavy). Log field `floor_rank=` shows when/how strongly the floor fires.
  - **v3.7 test results (this session, confirmed):** Floor fired correctly once (`floor_rank=2`) exactly when GPU jumped to heavy, immediately pulling CPU up to heavy in the same tick — no more stuck-at-medium regression. A side-by-side stock-vs-pulse_lite comparison on a third game showed **identical FPS=60 in both cases, but with pulse_lite: CPU temp 74C vs 81C stock, GPU temp 73C vs 77C stock, skin temp 71C vs 78C stock, fan RPM 2070 vs 2381 stock** — i.e. same performance at meaningfully lower thermals/noise. This is considered the target outcome for an AutoTDP daemon.
  - **Known open item:** Slow heavy->medium descent observed due to per-core busy% oscillating right around the `CPU_HEAVY_DOWN=45%` threshold (values seen: 47/46/44 in immediate succession) combined with the 5-tick debounce resetting on every re-crossing. Proposed but not yet implemented fix: widen the debounce dead-zone or raise `CPU_HEAVY_DOWN_TICKS`/threshold gap. Not urgent — cosmetic/comfort issue, not a correctness bug.
- **Full source code of v3.5-v3.7 and the v3.5 handoff document already exist as file attachments/artifacts in this thread's history** — a new session should request them from conversation history/attachments rather than having them re-typed, given plan character limits on pasting full script bodies.

---

## 5. New Project (Scope for Next Session/Conversation): Controller Profile Switcher + Button Remapper

The user wants to build a **new, separate tool** (potentially with its own light GUI, aiming to eventually merge with pulse_lite into one combined utility) that replicates and extends functionality seen in competing vendor tools (AYA Space) and the built-in Retroid Android skin:

### 5a. Controller style switcher (Xbox-layout / Nintendo-layout / **None/Disabled**)
- AYA Space (a competing handheld vendor's app) offers switching the ABXY button semantic mapping between "Xbox style" and "Nintendo style" (i.e. swapping which physical button reports as A/B and X/Y to the OS/games) but has **no "disable controller entirely" option**.
- Retroid's Android skin has a working three-way toggle (Xbox / Nintendo / disabled) that the user wants replicated — particularly useful when the handheld is docked to an external monitor with its own controller and the built-in pad should be inert.
- **Hypothesis (not yet confirmed):** the AYANEO Settings app's own `com.ayaneo.settings.ui.controller.KeyLayoutFragment` (and per-model variants `ar02`/`ar05`/`ar06`/`ar07`/`ar14`/`ar16`) is very likely where this vendor's own version of this exact feature lives, since a `ControllerFragment` + `ControllerViewModel` package already exists in the decompiled app (see Section 3). This should be the first place to look in a deep jadx dive next session, rather than starting from zero.
- **Technical approaches to evaluate (not yet started):**
  1. **Key Character Map (`.kl`) remapping** — Android's standard mechanism for translating raw scancodes to logical key/button codes per input device; likely how the vendor's Xbox/Nintendo toggle works (swap `BTN_SOUTH`/`BTN_EAST` <-> logical A/B assignments). Would need root-script write access to the relevant `.kl` file plus a way to force-reload the input device mapping (possibly via `adb shell input` commands or triggering an input device rescan).
  2. **`EVIOCGRAB` ioctl** — a small root-run program/script that grabs exclusive access to the gamepad's `/dev/input/eventN` node, effectively muting it system-wide (no other process, including Android's input dispatcher, sees events) until released. This is the most promising approach for the "Disable" state specifically, since it doesn't require understanding the OEM's exact remapping mechanism — it's a generic input-grab technique.
  3. **HID driver unbind/bind via sysfs** (`/sys/bus/hid/drivers/.../unbind` and `.../bind`) — a harder "unplug" of the device at the driver level, more invasive/harder to reverse cleanly than option 2, likely not the first choice.
  4. **`cmd input` / `InputManager.disableDevice()` system calls** — worth testing whether the constrained root-script execution model (Section 2) has access to the `cmd` binary or equivalent Binder calls; would be the cleanest API-level solution if reachable from a shell script context.
- **Immediate next step agreed:** identify device event nodes and raw button codes first (see 5b), since that step is a prerequisite for any remapping/disabling approach regardless of which technical route is chosen.

### 5b. Reading raw button/input codes (xev equivalent) — CONFIRMED FEASIBLE
- Android ships `getevent` and `sendevent` as standard AOSP toolbox binaries — these are the direct functional equivalent of Linux's `xev`, and should be accessible from our root-script execution context since they're just binaries invoked from a shell script (no special permissions beyond what root-script already grants).
  - `getevent -lp` — lists all input devices with their reported capabilities (key/axis codes they support). First step: run this to identify which `/dev/input/eventN` corresponds to the built-in gamepad (as opposed to touchscreen, volume keys, etc.).
  - `getevent -lt /dev/input/eventN` — live-streams labeled events (e.g. `BTN_SOUTH`, `KEY_VOLUMEUP`) as buttons are pressed; this is the direct `xev` equivalent. Since there's no interactive shell, the practical pattern is: have the root script run `getevent -lt /dev/input/eventN > /sdcard/button_capture.log &` for a fixed duration (e.g. 30-60s) while the user physically presses every button of interest (the two extra buttons near the triggers, two back buttons, and the underused front buttons), then `adb pull` the log afterward for analysis.
  - `sendevent /dev/input/eventN <type> <code> <value>` — can synthesize arbitrary input events; useful later for testing whether a remap actually produces the intended logical button in-game/in-OS.
- **This is the concrete, low-risk first deliverable for the new session:** a capture script that runs `getevent -lp` once (device enumeration) and then `getevent -lt` for a timed window into a log file on `/sdcard/`, given the constraint that we cannot watch output live and must capture-then-pull.

### 5c. GUI aspiration (secondary, deferred)
- The user mentioned wanting "possibly a GUI" for the eventual tool, and ideally merging this with pulse_lite into one combined app/utility down the line.
- **Not yet scoped or started.** Given the Section 2 constraint (no persistent root shell, only script-per-invocation execution via the vendor's Root Script UI), a real always-on GUI app would likely require either (a) a companion Android app with its own UI that shells out to root-script-launched helper scripts for the privileged parts, or (b) continuing to rely on the vendor's own Root Script entry point as the trigger mechanism with results surfaced via log files read by a separate always-running (non-privileged) monitoring app. This architectural question is unresolved and should be revisited once 5a/5b produce concrete findings about what's actually controllable.

---

## 6. Open Questions For Next Session

- Which exact `/dev/input/eventN` node corresponds to the built-in gamepad, and what raw codes does each of the extra/underused physical buttons (2 near triggers, 2 on back, several on front) actually emit today? (Section 5b — first concrete task.)
- Does the AYANEO Settings app's own `KeyLayoutFragment`/`ControllerFragment` package (Section 3) already implement an Xbox/Nintendo swap, and if so, via `.kl` file remap, a sysfs write, or a Binder/system-service call? Needs a focused jadx read of that specific package (not yet done this session — prior jadx work targeted `RootScriptFragment` only).
- Is `EVIOCGRAB` achievable from a plain `sh` script under the `u:r:xsud:s0` SELinux context, or does it require a small compiled helper binary (C) pushed alongside the script? This determines whether the "disable" feature is a quick shell one-liner or needs a small native tool built and cross-compiled for the device's arch first.
- Does the `xsud` root-script context have access to the `cmd` binary (for `cmd input ...` style calls), or is it sandboxed away from normal system Binder services? Untested.
- What CPU architecture/ABI does the device use, in case a small native helper binary (for ioctl-based grab, or for a proper GUI companion app) needs to be compiled? (Snapdragon 8 Gen 3-class implies arm64-v8a, but not explicitly confirmed via `getprop ro.product.cpu.abi` yet this session.)

---

## 7. Files/Artifacts Referenced (available in conversation history, not re-attached here due to plan limits)

- `pulse_lite_v3.2.sh` through `pulse_lite_v3.7.sh` (all versions, full source)
- `pulse_lite_v3.5_handoff.md` (detailed handoff for the v3.5 CPU/GPU decoupling session)
- `01_rootscript.txt`, `02_exec.txt`, `03_ytsu.txt`, `04_ipc.txt`, `05_auth.txt`, `06_manifest_components.txt` — jadx decompile dumps of AYANEO Settings app, focused on the Root Script mechanism
- `RootShell.java`, `YtRootShell.java` — decompiled privilege-broker classes referenced by RootScriptFragment
- `dmesg.log`, `wake_lock.log`, `before.log`/`after.log` — earlier diagnostic captures from prior pulse_lite sessions
- Screenshots: IMG_0191/0192/0194 (Root Script UI walkthrough, this session), IMG_0206/0207 through IMG_0217 (various pulse_lite in-game overlay test captures across sessions)

---

## 8. Recommended First Actions For a New Session

1. Confirm device ABI: request the user run a trivial root-script capturing `getprop ro.product.cpu.abi` and `getprop ro.build.version.release` to `/sdcard/device_info.txt`, pull and review.
2. Build and hand off the `getevent` capture script (Section 5b) as the first concrete deliverable — this unblocks everything else in Section 5.
3. Once button codes are known, request the AYA Space APK or the AYANEO Settings APK (already partially available via jadx dumps) specifically for the `com.ayaneo.settings.ui.controller` package, to determine the existing Xbox/Nintendo swap mechanism before attempting to reimplement "Disable" from scratch.
4. Keep pulse_lite and this new controller project as separate script files/artifacts for now (per user's own framing: "może to potem połączyć w jedną całość") — do not attempt premature merging until both are independently stable.
