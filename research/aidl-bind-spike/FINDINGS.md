# FINDINGS — AIDL bind spike, run1 (2026-07-24)

**Confirmed on-device: a completely ordinary, non-system app can drive
AYASpace's performance profile with no `xsu`, no root, over a plain Binder
connection.** This is the empirical confirmation both static-analysis
teardowns (`research/ayaspace-teardown/`, `research/aya-gamewindows-teardown/`)
predicted. Raw log: `results/aidl_spike_result.txt`.

## What happened, in order

1. `bindService()` returned `true`, `onServiceConnected` fired with a live
   `BinderProxy` — no exception, no rejection.
2. `registerCallback()` transact completed; gamewindow called back with
   `"msg_type_register:175316599"` — real clientId assigned.
3. Sent Gaming (mode 3) → 102ms later, `xsu` read-back:
   `P0_GOV=performance P0_FREQ=2265600 P2_GOV=performance P2_FREQ=3148800`.
   Matches `HARDWARE_PROFILE.md`'s Gaming row exactly.
4. Sent Eco (mode 0) → `xsu` read-back:
   `P0_GOV=powersave P0_FREQ=672000 P2_GOV=powersave P2_FREQ=499200`.
   Governor flip confirmed; `cur_freq` below the mode's cap (729600) is
   expected (idle core, governor free to go lower than the cap).
5. Sent Gaming a third time (after Eco) → identical result to step 3 —
   confirms this is repeatable, not a one-off fluke.

## Bonus finding: the callback payload is far richer than expected

Every `com_set_performance_mode` call triggers an unsolicited callback
message containing a full JSON dump: `currentMode` plus **all 5 modes'
complete `ModeConfiguration`** (per-core `cpuFrequencies`, `cpuSchedulerMode`,
`fanMode`, `gpuFrequency` min/max/isFixed, `lastFanMode`). This was not
anticipated by either teardown pass — `ayaspace-teardown/FINDINGS.md`
flagged the per-device mode table as "not yet extracted"; **it doesn't need
static extraction at all, the device hands it over live on every mode
change.**

### Full per-mode table, extracted from the live JSON response

| Mode | cpu0/1 | cpu2-4 | cpu5/6 | cpu7 | scheduler | fanMode | GPU max (MHz) | GPU min (MHz) |
|---|---|---|---|---|---|---|---|---|
| 0 Eco | 787200 | 729600 | 729600 | 480000 | POWER_SAVING | OFF | 310 | 231 |
| 1 Balanced | 2265600 | 3148800/2956800* | 2956800 | 3302400 | BALANCED | CUSTOM | 903 | 231 |
| 2 Streaming | 2265600 | 2131200 | 2035200 | 2112000 | HIGH_PERFORMANCE | MUTE | 680 | 231 |
| 3 Gaming | 2265600 | 3148800/2956800* | 2956800 | 3302400 | HIGH_PERFORMANCE | CUSTOM | **834** | 231 |
| 4 Max | 2265600 | 3148800/2956800* | 2956800 | 3302400 | HIGH_PERFORMANCE | CUSTOM | **1050 (uncapped)** | 231 |

\* cpu2/3/4 all report 3148800 as their per-core `selectedFrequency` in the
raw JSON for modes 1/3/4 — table collapses cpu2-4 since they're identical;
policy5 (cpu5/6) is a separate cluster reported as 2956800 for these same
modes, kept as its own column above (the raw per-`cpuId` JSON doesn't
group by cpufreq policy, this table re-groups it to match
`HARDWARE_PROFILE.md`'s existing per-policy convention).

**This resolves a question flagged in `HARDWARE_PROFILE.md` since v6:**
*"Gaming and Max are functionally identical at the CPU governor/freq level
— whatever difference exists lives outside CPU frequency caps."* Confirmed
exactly right: **the entire Gaming-vs-Max difference is the GPU max
frequency cap** (834MHz capped vs 1050MHz/uncapped) — CPU-side config is
byte-for-byte identical between the two modes.

## Implications for `apl`

- **The AIDL-bind path is no longer just "should work per static analysis"
  — it works, confirmed, repeatably, on this exact device.** `apl`'s
  profile-mimic feature and (for the actuation side) `AutoTdpController`
  should use this instead of `xsu` sysfs writes for anything this command
  surface covers.
- **Fan mode per profile is now fully known** (not just "exists", the exact
  value per mode) — was previously an unconfirmable lever if going the
  sysfs-replication route; now trivially available via the same AIDL call
  that already changes CPU/GPU.
- **The full per-mode config table above should be folded into
  `apl-diag/docs/HARDWARE_PROFILE.md`**, replacing/extending its older
  per-policy-only table — this is genuinely more complete data (per-core,
  not just per-policy; includes fan mode and GPU frequency range, which the
  old table didn't have at all).
- Not yet tested: `com_set_performance_fan`, `com_set_performance_cpu`
  (fine-grained, not whole-mode), `com_set_performance_gpu`,
  `com_set_abxy_mode`/key-remapping commands — this spike only exercised
  the whole-mode switch. Worth a follow-up pass if `apl` needs finer
  control than "pick one of the 5 named modes."
