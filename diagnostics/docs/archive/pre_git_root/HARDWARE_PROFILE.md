# HARDWARE_PROFILE.md — AYANEO Pocket FIT / Pocket S2 (skonsolidowane)

Zrodlo: pulse_lite_v2.2 -> v3.7 handoffy + skrypty. Wszystkie wartosci ponizej
zostaly zweryfikowane empirycznie na urzadzeniu (nie sa zgadywane).

## Identyfikacja urzadzenia (DO WERYFIKACJI PONOWNEJ)

- ro.product.name = AYANEOPocketFIT
- ro.build.flavor = AYANEOPocketS2-user
- SoC: nazywany w dokumentach "Snapdragon G3 Gen 3" (potwierdzic: adb shell getprop ro.soc.model)
- GPU: Adreno (w dokumentach nazwany "A33" - potwierdzic: adb shell getprop ro.hardware.egl / gsl)
- Android 14, firmware AYASpace

UWAGA: nazwa chipsetu w dokumentach ("Snapdragon G3 Gen 3") jest niestandardowa -
prawdopodobnie marketingowa nazwa AYANEO, nie oficjalna nazwa Qualcomm. Do potwierdzenia
przed dalsza praca: `adb shell getprop | grep -i soc`.

## CPU — 4 niezalezne policy (klastry)

| Policy | Rdzenie | Klaster        | ECO cap   | H_LOW cap | H_MID cap | STOCK cap | Turbo (pominiety) |
|--------|---------|----------------|-----------|-----------|-----------|-----------|---------------------|
| policy0 | cpu0-1 | Cortex-A520    | 1344000   | 1459200   | 1804800   | 2265600   | -                   |
| policy2 | cpu2-4 | Cortex-A720 mid| 1708800   | 2035200   | 2438400   | 3148800   | -                   |
| policy5 | cpu5-6 | Cortex-A720 high| 1708800  | 2035200   | 2438400   | 2956800   | -                   |
| policy7 | cpu7   | Cortex-X4 prime| 1824000   | 2035200   | 2438400   | 3052800   | 3302400 (SKIP)      |

Wartosci w kHz. Wszystkie zweryfikowane wzgledem `scaling_available_frequencies`.
policy7 ma dostepny "turbo bin" 3302400 kHz w OPP table, ale jest SWIADOMIE
POMIJANY jako niestabilny/za goracy — STOCK cap = 3052800, nie 3302400.

Sciezki sysfs:
```
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
```

### BRAK: pelna lista dostepnych czestotliwosci per policy

Skrypty do v3.7 uzywaly tylko 4 punktow per klaster (ECO/H_LOW/H_MID/STOCK).
Do zbudowania precyzyjnego FPS-driven step-controller potrzebna jest PELNA lista:
```sh
adb shell xsu cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies
adb shell xsu cat /sys/devices/system/cpu/cpufreq/policy2/scaling_available_frequencies
adb shell xsu cat /sys/devices/system/cpu/cpufreq/policy5/scaling_available_frequencies
adb shell xsu cat /sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies
```
To jest PIERWSZA rzecz do zrobienia przed napisaniem finalnej wersji step-controllera —
bez tego mamy tylko 4 punkty regulacji per klaster, nie prawdziwa granularnosc OPP.

## GPU — Adreno, 14 power-levels (index 0-13, ODWROTNE: wyzszy index = nizsza freq)

| Index | Freq (MHz) | Index | Freq (MHz) |
|-------|-----------|-------|-----------|
| 0     | 1050 (uncapped/stock) | 7 | 629 |
| 1     | 1000      | 8     | 578 |
| 2     | 903       | 9     | 500 |
| 3     | 834       | 10    | 422 |
| 4     | 770       | 11    | 366 |
| 5     | 720       | 12    | 310 |
| 6     | 680       | 13    | 231 (min) |

Sciezki sysfs:
```
/sys/class/kgsl/kgsl-3d0/max_pwrlevel      <- zapis: cap (indeks)
/sys/class/kgsl/kgsl-3d0/gpubusypercentage <- odczyt: busy%, world-readable, bez roota
```

Skrypty do v3.7 uzywaly tylko 4 z 14 poziomow (0=uncap, 6=high, 7/8=mid, 9=eco).
Cala reszta (1,2,3,4,5,10,11,12,13) jest niewykorzystana — realna szansa na
finiejszy step-controller.

## Progi termiczne (heavy sub-tier, zweryfikowane, nigdy nie osiagniete w testach)

| Sygnal | HOT (next tier down) | HOTTER (kolejny down) | COOL_HOT (powrot) | COOL_HOTTER (powrot) |
|--------|----------------------|------------------------|--------------------|------------------------|
| CPU temp | 78000 (78.0 C) | 85000 (85.0 C) | 74000 (74.0 C) | 81000 (81.0 C) |
| GPU temp | 75000 (75.0 C) | 82000 (82.0 C) | 71000 (71.0 C) | 78000 (78.0 C) |

Wartosci w millidegrees C. Strefy termiczne wykrywane dynamicznie przy starcie
(prefix "cpu" i "gpu" w /sys/class/thermal/thermal_zone*/type), nie hardkodowane
numery zon (mogą się różnić między rewizjami firmware).

UWAGA: te progi nigdy nie zostały faktycznie osiągnięte w dotychczasowych testach —
nie mamy dowodu empirycznego, że urządzenie realnie się przegrzewa przy pełnym CPU+GPU
capie. Do potwierdzenia przy dłuższej sesji gamingowej.

## Kanał roota (potwierdzony, dwa warianty historyczne)

**Historyczny (v2.2-v3.7, AYASpace Root Script):** brak bezposredniego roota
z adb shell (uid=2000). Kanal: AYASpace -> "Performance -> Root Script" ->
pole tekstowe wykonywane jako `u:r:xsud:s0` (dedykowana domena SELinux z prawami
zapisu do sysfs). Wymaga recznego wklejenia po kazdym reboocie — brak autostartu
bez Magiska.

**Aktualny (potwierdzony w tym wątku):** `xsu` jako pełny, operacyjny binarny
odpowiednik `su`, dający `uid=0` bezpośrednio z `adb shell` — bez potrzeby
przechodzenia przez UI AYASpace. Wzorzec wywołania z apki Android: identyczny do
`YtRootShell.java` z AYA Settings (`Runtime.exec("xsu")`, stdin/stdout pipe).

NIE ZWERYFIKOWANE: czy `xsu` jest wywolywalne z wnetrza normalnej apki (Runtime.exec)
a nie tylko z sesji adb shell — patrz plik handoff_pulse_android_app.md.

## Odczyt FPS (nowy element, nie byl w skryptach do v3.7)

Rekomendowana metoda: `dumpsys gfxinfo <package> framestats` (Android 6.0+, stabilne
API, nie zmienialo formatu na Androidzie 14/15 w odroznieniu od SurfaceFlinger --latency).
Aktywny pakiet: `dumpsys activity | grep mResumedActivity`.

NIE ZWERYFIKOWANE: czy `gfxinfo framestats` zwraca sensowne dane dla emulatorow
renderujacych przez natywny OpenGL/Vulkan surface (RetroArch, Dolphin) — wymaga
testu na urzadzeniu przed budowa finalnej logiki.

## Sentinel/force pattern (mechanizm kontroli bez sygnalow miedzyprocesowych)

- `/sdcard/pulselite.stop` — plik obecnosci = zatrzymaj demona (bo uid=2000 nie
  moze zasygnalizowac procesu uid=0 przez pkill/kill)
- `/sdcard/pulselite.force` — wpisana wartosc (idle/medium/heavy) = wymuszenie
  tieru, do testow manualnych
- `/sdcard/pulselite.force_cpu`, `/sdcard/pulselite.force_gpu` — wymuszenie
  niezaleznie per domena (v3.7)

## Otwarte pytania przed napisaniem finalnej wersji FPS-driven

1. Pelna lista scaling_available_frequencies per policy (patrz wyzej) — BLOKUJACE.
2. Weryfikacja gfxinfo framestats na realnym emulatorze — BLOKUJACE dla FPS-loop.
3. Weryfikacja xsu z Runtime.exec() w apce (a nie tylko adb shell) — BLOKUJACE dla
   przyszlej apki, NIE blokujace dla POC skryptu shell (ktory i tak odpalamy z adb).
4. Potwierdzenie realnej nazwy SoC/GPU (ro.soc.model) — kosmetyczne, nieblokujace.
