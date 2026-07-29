# FINDINGS — KonaBess-Next-G3Gen3 (`thefiqs/KonaBess-Next-G3Gen3`) static assessment

Target: fresh clone of `https://github.com/thefiqs/KonaBess-Next-G3Gen3.git`
(app id `com.ireddragonicy.konabessnext`, "KonaBess Next"), pulled to
`research/konabess-g3gen3-upstream/` (gitignored, read-only). Static
analysis only — no build, no install, no device involved. All paths below
are relative to that clone directory.

## TL;DR

This is a **DTB-patching, reboot-required tool** — the classic KonaBess
mechanism, not a live sysfs-write path. It confirms our device's
`ro.board.platform=pineapple` codename is the same "Pineapple" the tool
recognizes for Snapdragon 8 Gen 3, but it carries **no hardcoded
frequency/voltage numbers** for that chip — only generic Qualcomm
voltage-corner *labels* (RETENTION/SVS/NOM/TURBO/...) applied to whatever
DTB the user's own device supplies at runtime. **No CPU control, no
fan/thermal control.** Bottom line: reference-only for risk-profile
contrast; nothing here is directly reusable technique or data for
`pulse-for-aya`. Full detail below.

## 1. How does it get root / apply changes?

**Magisk/KernelSU/APatch root + DTB patching + raw `dd` write to the
boot/vendor_boot/dtbo partition, reboot required.** There is no live,
no-reboot sysfs-write path anywhere in this codebase — this is a
fundamentally different, far more invasive risk class than
`pulse-for-aya`'s `xsu -c` sysfs approach.

Evidence:

- Own README states the mechanism plainly: "The application operates by
  unpacking the Boot or Vendor Boot image, decompiling and editing the
  relevant device tree binary (dtb) files, and then repackaging and
  flashing the modified image" (`README.md:27`). Prerequisites: "**Root
  Access**: Magisk, KernelSU, or APatch is **mandatory**" and "**Unlocked
  Bootloader**: Necessary for flashing modified boot images"
  (`README.md:93-94`).
- Root layer is `topjohnwu`'s `libsu` (`Shell.cmd(...)`), with a
  KernelSU-directory fallback probe (`/data/adb/ksu`) — see
  `app/src/main/java/com/ireddragonicy/konabessnext/utils/RootHelper.kt:9-25,98-111`
  and the app-wide wrapper
  `app/src/main/java/com/ireddragonicy/konabessnext/repository/ShellRepository.kt:39-77,160-175`.
  There is also a "non-root mode" (`ShellRepository.kt:87-127`, plain
  `ProcessBuilder("sh","-c",cmd)`), but it's only used for
  browsing/importing files the user supplies manually — every actual
  partition-write path below hard-checks `isRootMode &&
  shellRepository.isRootAvailable()` and fails otherwise.
- The actual read/write of physical partitions is a raw `dd` against
  `/dev/block/.../by-name/<partition><slot>`:
  - Read current partition into a working file:
    `app/src/main/java/com/ireddragonicy/konabessnext/repository/DeviceRepository.kt:620-634`
    (`"dd if=$partition of=$imagePath && chmod 644 $imagePath"`).
  - Write the *active* slot's boot/vendor_boot partition after edit+repack:
    `DeviceRepository.kt:647-687` (`writeBootImage()`), gated by root check
    at `DeviceRepository.kt:649-651`, actual write at `DeviceRepository.kt:678`
    (`"dd if=$newBootPath of=$partition"`).
  - Write the *inactive* slot (A/B devices), with an optional backup-first
    step: `DeviceRepository.kt:692-770` (`installToInactiveSlot()`),
    backup `dd` at `DeviceRepository.kt:742`, flash `dd` at
    `DeviceRepository.kt:757-758`.
  - DTBO-specific flash: `DeviceRepository.kt:800` area
    (`flashDtboImage()`), same `writeBootImage()`/`dd`-to-partition
    pattern (`DeviceRepository.kt:801-802`).
- **Reboot is required to apply anything** — changes only take effect once
  the kernel re-reads the patched DTB at boot. The app has its own explicit
  reboot action (`svc power reboot`):
  `DeviceRepository.kt:443-444`, wired to a UI button at
  `app/src/main/java/com/ireddragonicy/konabessnext/ui/MainActivity.kt:147-150`
  and a view-model method at
  `app/src/main/java/com/ireddragonicy/konabessnext/viewmodel/DeviceViewModel.kt:743-744`.

**Contrast with `pulse-for-aya`, stated clearly**: this tool touches the
boot/vendor_boot/dtbo partition directly with `dd`, requires an unlocked
bootloader per its own README, and requires a reboot before any change is
observable — a real risk of bootloop if the DTB is malformed (the app's own
UI warns about exactly this, see Q3). `pulse-for-aya`'s design (plain
`xsu -c` writes to already-existing, already-writable `kgsl`/`cpufreq`
sysfs nodes, reversible instantly by writing the value back, no boot
partition touched, no reboot) is not a smaller version of what this tool
does — it's a categorically different, much safer mechanism. Nothing here
suggests we should reconsider that choice.

## 2. Does it expose or hardcode a GPU frequency/voltage OPP table specific to "G3 Gen 3"?

**No concrete numbers — only a generic voltage-corner label scheme,
applied to whichever DTB the user's own device supplies at runtime.** The
app does not ship a static "SD8Gen3 GPU table" of MHz/mV values anywhere
in source, assets, or test fixtures.

- The tool does confirm our device's platform codename independently: its
  chip-inference table matches the string **"Pineapple"** (case-insensitive,
  word-boundary regex `\bPineappleP?\b`) to Snapdragon 8 Gen 3, at
  `app/src/main/java/com/ireddragonicy/konabessnext/model/LevelPresets.kt:188-190`
  ("Pineapple — shifted MIN_SVS" comment) and the preset-selection catalog
  comment at `LevelPresets.kt:86-87` ("Pineapple 480-level preset (21
  named levels). Used by: SD8Gen3"). This is the *exact same* codename our
  own `diagnostics/docs/HARDWARE_PROFILE.md` records from live `getprop`
  (`ro.vendor.qti.soc_model=SG8350P`, `ro.board.platform=pineapple`) — a
  genuinely useful independent confirmation that "Pineapple" = Snapdragon 8
  Gen 3 platform family, not something we'd invented from the marketing
  name alone.
- But the `PINEAPPLE_480` map (`LevelPresets.kt:89-110`) contains **only
  index → label pairs** like `31 to "32 - MIN_SVS"`, `255 to "256 - NOM"`,
  `415 to "416 - TURBO_L1"` — these are Qualcomm's internal RPMh
  voltage-corner *names* (a 480-entry level index used by the PMIC/RPMh
  hardware), not frequencies in MHz or voltages in mV. The actual
  frequency-to-level and voltage-to-level mappings for a given device are
  parsed live out of *that specific device's* DTB at runtime — the model
  class that holds a real number is
  `app/src/main/java/com/ireddragonicy/konabessnext/model/Opp.kt:3-6`
  (`data class Opp(var frequency: Long = 0, var volt: Long = 0)`), which is
  empty until the app parses a user-supplied boot image; nothing populates
  it from a bundled table.
- Confirmed no hardcoded Pineapple/SD8Gen3 numeric values exist anywhere in
  the repo: neither the bundled test DTS fixtures
  (`app/src/test/sd660.dts`, `app/src/test/sd860.txt`,
  `app/src/test/Tuna0.txt` — none of which are Pineapple/SD8Gen3) nor any
  `.kt` source file contain frequency constants matching our own measured
  GPU power levels (checked against
  `diagnostics/docs/HARDWARE_PROFILE.md`'s 14-entry
  `kgsl` `available_frequencies` list, e.g. `1050000000`, `903000000`,
  `834000000` — no matches found anywhere in `app/src/`).

**Conclusion**: no conflicting numbers to reconcile (good — nothing to
flag as a discrepancy), but also nothing new to extract. The one genuinely
useful fact is the "Pineapple" codename cross-confirmation above, which
belongs as a footnote in `HARDWARE_PROFILE.md` if not already implied
there, not a data source for the GPU table itself.

## 3. What safety rails does it have?

**UI-level warnings and confirmation dialogs, plus a backup-before-flash
option — but no programmatic bounds-checking on frequency or voltage
values.** The one automated check found is structural DTS syntax
validity (a parser/lint pass), not a safety/range check.

- Confirmation dialogs exist before every destructive action: flashing
  (`confirm_flash` string, referenced at
  `app/src/main/java/com/ireddragonicy/konabessnext/ui/compose/DtsDiffViewer.kt:69`,
  defined `app/src/main/res/values/strings.xml:470`), applying a config
  (`confirm_apply_config`, `strings.xml:244`), switching chipset
  (`switch_chipset_confirm`, `strings.xml:294`), clearing/deleting history
  (`strings.xml:226-229`).
- Explicit backup-before-flash affordance: `backup_old_image` /
  `will_backup_to` / `backup_boot_image` strings
  (`strings.xml:6-7,170-171,336`), and `installToInactiveSlot(shouldBackup:
  Boolean)` performs a `dd` backup of the target partition before
  overwriting it, when requested (`DeviceRepository.kt:733-748`) — backup
  is optional (a boolean flag), not forced.
- Two escalating warning strings for the riskiest surfaces: the raw DTS
  text editor ("⚠️ Warning: Advanced Feature... can cause boot issues if
  done incorrectly... Always backup your boot image first!",
  `strings.xml:255-256`) and RPMh regulator/PMIC bound editing ("⚠ DANGER
  ZONE... Modifying PMIC bounds bypasses hardware safety limits. Extreme
  overvolting can permanently damage your SoC, PMIC, or battery
  circuitry", `strings.xml:763-764`).
- The only found automated validation is `DtsLintResult`
  (`app/src/main/java/com/ireddragonicy/konabessnext/model/dts/DtsLintResult.kt:5-19`)
  — an `isValid` flag plus a list of `DtsError{line,column,message,
  severity}` — used by the raw text editor
  (`app/src/main/java/com/ireddragonicy/konabessnext/viewmodel/editor/TextEditorViewModel.kt`)
  and the GUI editor toolbar/level operations
  (`app/src/main/java/com/ireddragonicy/konabessnext/ui/compose/GpuEditorToolbar.kt`,
  `app/src/main/java/com/ireddragonicy/konabessnext/core/editor/LevelOperations.kt`).
  This is a **parse-correctness check** (does the DTS text still parse as
  valid device-tree syntax after edits), not a value-range/safety check —
  grepping the level-editing and RPMh-regulator code
  (`core/editor/LevelOperations.kt`,
  `domain/RpmhDomainManager.kt`,
  `model/power/RpmhRegulator.kt`,
  `viewmodel/gpu/GpuVoltViewModel.kt`) for bounds-checking idioms
  (`coerceIn`/`coerceAtMost`/min-max constants/`require()`) turns up
  nothing beyond `LevelOperations.kt`'s own `coerceAtLeast(0)` clamp on
  pwrlevel *offsets* (`core/editor/LevelOperations.kt:127`, preventing a
  negative array index, not a hardware-safety bound). A user can set a
  frequency/voltage pairing the hardware cannot actually support and the
  app will let them flash it (with warnings, not a block).

## 4. Any CPU-side control at all?

**No — confirmed GPU-only** (plus several non-CPU peripheral domains this
fork added beyond classic KonaBess's GPU-only scope). The `domain/`
package — the closest thing to a feature-area map — has managers for
`CamIspDomainManager`, `DdrDomainManager` (memory/DDR frequency),
`GmuDomainManager` (GPU management unit), `RpmhDomainManager` (PMIC
voltage rails), `SdeDomainManager` (display), `SpeakerDomainManager`,
`TouchDomainManager`, `UfsDomainManager` (storage) — listed in full at
`app/src/main/java/com/ireddragonicy/konabessnext/domain/` (`ls` output).
**There is no `CpuDomainManager` or any CPU-frequency-table editor
screen/view-model.** Searching the whole source tree for
`cpufreq`/`CpuFreq`/`cpu-vdd`/`cpu_opp` finds exactly one hit outside GPU
code: `ui/compose/DtsNodeIcon.kt:360`
(`PrefixRule("qcom,cpufreq", Icons.Rounded.Speed)`) — this is purely a
tree-view icon lookup for the generic Raw DTS tree browser (any node name
prefix gets an icon), not a dedicated CPU-editing feature. A sufficiently
determined user could hand-edit a `qcom,cpufreq` node via the raw-text DTS
editor (same "Advanced Feature" warning as Q3), but that's incidental to
the raw-editor being general-purpose, not a supported CPU-control
capability. This confirms the assumption in the task brief: this fork,
like classic KonaBess, is GPU-focused; its added domains (DDR/camera/
display/speaker/touch/UFS) extend breadth within "peripheral clock/voltage
tables editable via DTB," not into CPU governor/frequency territory.

## 5. Any mention of fan/thermal control?

**No.** Grepping the entire `app/src/main/java/com/ireddragonicy/konabessnext`
tree (case-insensitive) for `fan`/`thermal`/`cooling` turns up exactly one
file, `ui/compose/DtsNodeIcon.kt`, and only as generic icon-matching rules
for whatever node names happen to appear in a raw DTS tree — e.g.
`PrefixRule("thermal", Icons.Rounded.Thermostat)`
(`DtsNodeIcon.kt:422`), `PrefixRule("cooling", Icons.Rounded.AcUnit)`
(`DtsNodeIcon.kt:423`), `KeywordRule("thermal", ...)`
(`DtsNodeIcon.kt:761`). This is the same passive tree-browser behavior as
the `qcom,cpufreq` icon in Q4 — a thermal-zone or cooling-device node from
the DTB would render with a thermometer/snowflake icon if a user browses
to it in the raw tree view, but there is no dedicated fan-curve or
thermal-trip-point editor, domain manager, or apply path anywhere in the
app. Confirmed, not assumed.

## 6. Bottom-line verdict

**(c) Neither reusable technique nor reusable data — reference only, and
the risk profile is fundamentally different from `pulse-for-aya`'s.** With
one narrow exception: the "Pineapple" codename cross-confirmation (Q2) is
a small, genuine, free piece of corroborating evidence for
`diagnostics/docs/HARDWARE_PROFILE.md`.

Specifics:

- **Not a source of technique.** This is a DTB-patch-and-reboot tool,
  requiring root + (per its own README) an unlocked bootloader, writing
  directly to the boot/vendor_boot/dtbo partition via raw `dd` (Q1). It
  has no live sysfs-write path we could learn from or adapt — the entire
  mechanism `pulse-for-aya` already uses (plain `xsu -c` writes to
  existing, instantly-reversible `kgsl`/`cpufreq` sysfs nodes, no reboot,
  no boot-partition access) is simply outside what this class of tool
  does. It solves a different problem: changing the GPU's factory OPP
  table permanently (persists across reboots, until re-flashed), versus
  `pulse-for-aya`'s runtime cap within the existing table (resets on
  reboot, zero persistence risk).
- **Not a source of data.** No hardcoded frequency/voltage numbers for
  "G3 Gen 3"/Pineapple exist in this codebase (Q2) — only generic
  Qualcomm voltage-corner index labels that apply to any device's own DTB,
  populated at runtime from whatever the user's actual device provides.
  Nothing to add to or reconcile against `HARDWARE_PROFILE.md`'s GPU
  power-level table.
- **Meaningfully higher risk class than what we do.** Bootloader unlock,
  boot-partition writes, and a real bootloop risk if the edited DTB is
  malformed (mitigated only by UI warnings and an optional backup, not by
  any value-range safety check — Q3). Worth keeping in mind precisely
  *because* it's the road not taken: it's a concrete illustration of why
  `pulse-for-aya`'s conservative, reversible, no-flash sysfs-cap approach
  (per `CLAUDE.md`'s hard rules and `research/pulse-glue-assessment/FINDINGS.md`)
  was the right call for this project, not an alternative to reconsider.
- **GPU-only, no CPU, no fan/thermal** (Q4, Q5) — confirmed, not assumed,
  matching the task brief's expectation for a KonaBess-family tool.

No follow-up action recommended for `pulse-for-aya` or
`diagnostics/docs/HARDWARE_PROFILE.md` beyond optionally noting the
"Pineapple" codename cross-confirmation from Q2 the next time that file is
touched for an unrelated reason — not urgent enough to justify a
dedicated edit on its own.
