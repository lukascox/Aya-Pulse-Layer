# PulseFit / pulse_lite / pulse_lite_diag — Master Summary (do sesji `chat`)

Data sporządzenia: 2026-07-22.
Cel tego pliku: pozwolić sesji bez dostępu do plików (np. zwykły chat) zrozumieć
w pełni stan dwóch powiązanych projektów i pomóc w rozkminianiu "co dalej", bez
konieczności ponownego czytania kodu. Jeśli trzeba coś faktycznie zmienić w
plikach/skryptach/kodzie — to wraca do sesji CLI (Claude Code) z dostępem do
repo.

---

## 0. Kontekst ogólny

- **Autor nie jest programistą.** Wszystko budowane iteracyjnie z pomocą AI.
- **Urządzenie:** KONKR Pocket FIT / AYANEO Pocket FIT (branding pokrywa się,
  to ten sam sprzęt) — handheld Androidowy do gier, Snapdragon 8 Gen 3-class
  ("Snapdragon G3 Gen 3" w UI producenta), GPU Adreno (oznaczany "A33"/750 w
  różnych miejscach — nazwa niepotwierdzona oficjalnie), Android 14, firmware
  AYASpace.
- **Cel końcowy:** natywna apka Android (GUI) o nazwie roboczej **PulseFit**,
  realizująca:
  1. **AutoTDP** — kontroler w tle, autostart po boocie, dynamicznie
     ograniczający limity CPU/GPU tak, by trzymać docelowe FPS przy minimalnym
     poborze mocy/cieple/hałasie wentylatora (styl Steam Deck/ROG Ally).
  2. **Key remapping** (odłożone) — przemapowanie fizycznych przycisków,
     w tym tryb "wyłącz pada całkowicie" (czego brakuje w konkurencyjnym AYA
     Space).
  3. **Fan curve control** (odłożone, ale priorytet rośnie — patrz sekcja 3).
- **Dlaczego własny kod, a nie fork `pulse`:** oryginalny projekt `pulse`
  (github.com/keiretrogaming/pulse) używa reflection do serwisu
  `PServerBinder`, dostępnego tylko na AYN Odin 3 / Thor / Retroid Pocket 6.
  KONKR Pocket FIT go nie ma — potrzebna własna warstwa roota przez `xsu`.
- **Istnieją DWA katalogi projektu**, chronologicznie powiązane:
  - `pulse_lite/` (v3.2 → v3.7) — **pierwsza próba, prawdziwy kontroler
    AutoTDP** (pisze do sysfs, reguluje CPU/GPU na żywo).
  - `pulse_lite_diag/` (v3 → v8, ten katalog) — **druga faza, czysta
    diagnostyka** (read-only), powstała żeby naprawić fundamentalny problem
    znaleziony w pierwszej próbie (patrz sekcja 2).

---

## 1. `pulse_lite` (v3.2–v3.7) — pierwszy, działający kontroler

**Status: DZIAŁAJĄCY, zwalidowany na sprzęcie, ale oparty o sygnał, który
okazał się mieć fundamentalną wadę (patrz sekcja 2).**

### Model wykonania (ważne, historyczne ograniczenie)

- W tamtym czasie: **brak** `adb root`, **brak** Magiska/su. Jedyna
  uprzywilejowana ścieżka: funkcja **"Root Script"** w apce AYA Settings
  (System Settings → Custom → Root Script) — użytkownik wkleja ścieżkę do
  pliku `.sh` (wgranego wcześniej przez `adb push`) w pole tekstowe, klika
  "Run", apka producenta wykonuje skrypt jako `u:r:xsud:s0` (root, dedykowana
  domena SELinux).
- **Brak trwałej interaktywnej powłoki roota** — dlatego architektura to
  demon (`while true; sleep N; done`) sterowany plikami-sentinelami
  (`/sdcard/pulse_lite.stop`, `.force`, `.force_cpu`, `.force_gpu`), bo nie ma
  jak wysłać żywego sygnału do procesu działającego jako root z poziomu
  zwykłego adb (uid=2000).
- Ten model **zmienił się później** w wątku `pulse_lite_diag` — patrz sekcja
  4 (odkrycie `xsu` bezpośrednio z adb shell).

### Architektura kontrolera (sprawdzona, warta ponownego użycia)

Dwie niezależne maszyny stanów (CPU i GPU), każda z trzema tierami
(`idle` / `medium` / `heavy`):

- Histereza góra/dół (osobne progi wejścia i wyjścia z tieru, np.
  `HEAVY_UP=70%` / `HEAVY_DOWN=45%`).
- Debounce (N ticków sustained-low zanim tier realnie spadnie — zapobiega
  oscylacji przy wartościach blisko progu).
- Termiczne sub-tiery (`heavy_high/mid/low`) reagujące **tylko** na własną
  domenę (CPU temp nie wpływa na GPU sub-tier i odwrotnie).
- **"Floor" (v3.7, kluczowy mechanizm):** `CPU_TIER` nigdy nie może mieć
  rangi niższej niż `GPU_TIER` (idle=0 < medium=1 < heavy=2). CPU może wciąż
  samodzielnie eskalować WYŻEJ niż GPU na własnym sygnale (przypadek: gra na
  emulatorze Switcha, GPU lekkie, CPU ciężkie). Floor tylko podnosi, nigdy
  nie obniża.
- 4 niezależne polityki CPU (`policy0/2/5/7` = A520 / A720-mid / A720-high /
  X4), każda ze swoimi punktami ECO/H_LOW/H_MID/STOCK (Hz).
- GPU: 1 węzeł `kgsl-3d0/max_pwrlevel` (indeks odwrócony: 0=najszybszy).

### Wyniki (zmierzone, v3.7)

Ta sama gra, to samo FPS=60 w obu przypadkach, ale z `pulse_lite`:
CPU 74°C vs 81°C stock, GPU 73°C vs 77°C stock, skóra 71°C vs 78°C stock,
wentylator 2070 vs 2381 RPM. **To jest dokładnie pożądany efekt AutoTDP** —
już osiągnięty raz, na starszej architekturze sygnału.

### Fundamentalny problem, który wypłynął (uzasadnienie dla `pulse_lite_diag`)

- Sygnał sterujący to było **busy%** (CPU z `/proc/stat` per-core max, GPU z
  `gpu_busy_percentage`), NIE realny pomiar FPS.
- v3.5/v3.6 próbowały różnych wariantów busy%-based sygnału CPU i za każdym
  razem łapały ten sam błąd: **busy% nie potrafi odróżnić "CPU potrzebuje
  wyższego zegara" od "CPU czeka bezczynnie na fence GPU"** — oba wyglądają
  jak umiarkowane, nie-saturujące zajęcie w `/proc/stat`. Efekt: CPU utykał
  na `medium` przez całą sesję mimo GPU regularnie w `heavy`, FPS spadało do
  ~39 zamiast ~60.
- v3.7 "załatał" to floor-em rangowym (opisanym wyżej) — zadziałało
  empirycznie, ale **dokumentacja v3.7 sama otwarcie przyznaje**: nie
  wiadomo, czy floor stał się w praktyce jedynym realnym sterownikiem CPU
  (czyniąc per-core busy% sygnał w większości bezużytecznym dla gier z
  ciasno sprzężonym render pipeline'em) — to nigdy nie zostało domknięte
  testami.
- **Wniosek, który uruchomił `pulse_lite_diag`:** zamiast łatać coraz
  bardziej złożonym heurystykami wokół busy%, lepiej sterować wprost po
  **zmierzonym FPS vs cel** — usuwa niejednoznaczność u źródła. Ale to
  wymagało najpierw solidnego, zweryfikowanego sposobu mierzenia FPS na tym
  urządzeniu (czego `dumpsys gfxinfo` nie dawało dla emulatorów renderujących
  przez natywny SurfaceView — RetroArch, Eden/yuzu, Dolphin).

### Side-quest (odłożony, ale udokumentowany): Controller remapper

`AYANEO_pulse_lite_MASTER_HANDOFF.md` opisuje też odrębny, nierozpoczęty
projekt: przełącznik układu przycisków Xbox/Nintendo + tryb "wyłącz pada
całkowicie" (czego brakuje w AYA Space). Ustalenia:
- `getevent -lp` / `getevent -lt /dev/input/eventN` — potwierdzone jako
  dostępne z poziomu root-scriptu, odpowiednik `xev` na Linuksie.
- Kandydaci na mechanizm "disable": `EVIOCGRAB` ioctl (najbardziej
  obiecujący, generyczny), `.kl` keymap remap, HID unbind/bind (bardziej
  inwazyjne), `cmd input`/`InputManager.disableDevice()` (nieprzetestowane
  czy dostępne z `xsud`).
- AYANEO Settings ma własny `com.ayaneo.settings.ui.controller` z
  `KeyLayoutFragment`/`ControllerFragment` — prawdopodobnie tam już
  zaimplementowany jest producencki Xbox/Nintendo swap; niezdekompilowane
  jeszcze w szczegółach.
- To odpowiada **Modułowi 2** w obecnym README `pulse_lite_diag` — świadomie
  odłożone, nie blokuje AutoTDP.

---

## 2. `pulse_lite_diag` (v3–v8) — faza diagnostyczna, READ-ONLY

**Status: v8 zwalidowany end-to-end na sprzęcie (RetroArch + Eden/yuzu).
Cel: dać `pulse_lite`-owi (a docelowo apce PulseFit) prawidłowy sygnał FPS,
zamiast busy%.**

### Cel

Zbudować niezawodny sposób pomiaru FPS/GPU busy%/CPU freq/temperatur, który
działa jednolicie dla: systemowego UI, natywnych apek Android, ORAZ
emulatorów (RetroArch, Eden/yuzu-class, Dolphin) renderujących przez natywny
SurfaceView — niewidocznych dla standardowego `dumpsys gfxinfo`.

### Historia bugów (v4 → v8), skrótowo

- **v4-v5:** `dumpsys gfxinfo` zwracał 0 klatek dla RetroArch (niewidoczny
  render przez SurfaceView) i zamrożone/cache'owane dane dla ciężkiego
  gamingu (Eden, Super Mario Odyssey — legalnie posiadany kartridż).
- **v6:** zamiana na dynamiczny pipeline: `topResumedActivity=` (wykrycie
  appki na pierwszym planie) → dopasowanie warstwy SurfaceFlinger → FPS z
  `dumpsys SurfaceFlinger --latency`. Też naprawiono przepełnienie licznika
  `gpu_busy_pct`.
- **v7:** naprawiono wykrywanie foreground-app (`topResumedActivity=`
  zamiast niedziałającego `mResumedActivity`/`mCurrentFocus` na tym
  Androidzie 14). Ale **warstwa SurfaceFlinger nadal źle dobierana** — dla
  Edena łapano wrapper "Background for SurfaceView[...]" zamiast realnej
  warstwy `(BLAST)`; dla RetroArch łapano `ActivityRecordInputSink` zamiast
  realnej (nieprefiksowanej) warstwy renderującej. Efekt: `frame_count=0`
  we WSZYSTKICH próbkach obu aplikacji, mimo potwierdzonego aktywnego
  renderowania (gpu_busy 60-88%).
- **v8 (obecny, FINAL):** priorytetowe wyszukiwanie warstwy: BLAST > plain
  SurfaceView (bez wrapperów) > ostatni non-helper match > stary fallback.
  **Zwalidowane na sprzęcie:** RetroArch 30/30 próbek poprawnych
  (FPS 59.8-60.7), Eden 25/30 próbek poprawnych FPS (58.8-60.0), pozostałe
  5/30 to bezpieczny edge-case "n/a (zero span)" (pierwszy=ostatni
  timestamp w oknie próbki → dzielenie przez zero, poprawnie zabezpieczone,
  nie crashuje, nie zwraca złej wartości).

### Co jest CONFIRMED WORKING (można na tym budować)

- Wykrycie foreground-app: `dumpsys activity activities | grep
  topResumedActivity=`.
- Dobór warstwy SurfaceFlinger: priorytetowy grep opisany wyżej (v8).
- `dumpsys SurfaceFlinger --latency <layer>` jako źródło prawdziwego FPS.
- `gpu_busy_percentage`/`gpubusy` (kgsl) z zabezpieczeniem przed
  przepełnieniem licznika.
- `dumpsys gfxinfo` — potwierdzone jako NIENADAJĄCE SIĘ dla natywnych
  rendererów SurfaceView, nie używać.

### Ważne, niepokojące znalezisko (thermal)

CPU zaobserwowane bezpośrednio na **93.8°C** (cpu-1-2-1) i 86.1°C
(cpu-1-0-1) **bez jakiejkolwiek reakcji throttlingowej** ze strony AYASpace.
Nie zakładać, że firmware chroni termicznie samo z siebie. **To podniosło
priorytet Modułu 3 (fan curve)** względem pierwotnego planu — może trzeba
zrobić go wcześniej niż zakładano.

### Co JESZCZE nie jest zrobione / otwarte

1. **Pełna lista `scaling_available_frequencies` per policy** — skrypty
   do teraz używają tylko 4 punktów (ECO/H_LOW/H_MID/STOCK) na klaster.
   GPU podobnie: tylko 4 z 14 dostępnych power-levels wykorzystane.
   Blokujące dla precyzyjnego step-controllera.
2. **Series 4 (próba konfliktu zapisu z AYASpace)** — nigdy nie wykonana,
   od v4. Pytanie: co się dzieje, gdy nasz kontroler i AYASpace próbują
   pisać do tych samych węzłów sysfs jednocześnie.
3. **Walidacja tylko na 2 aplikacjach** (RetroArch, Eden/yuzu) — wnioski o
   ogólności heurystyki warstwy SurfaceFlinger są jak na razie
   przedwczesne; warto przetestować więcej gier/emulatorów (Dolphin,
   RetroHrai launcher, natywne apki Android) przed uznaniem za w pełni
   ogólne.
4. **"Zero span" FPS edge case** (Eden, ~17% próbek) — nieblokujące, fail
   -safe, kandydat na v8.1.
5. Zero logiki sterującej istnieje w `pulse_lite_diag` — to CELOWO czysta
   diagnostyka. Logika kontrolera ma zostać przeniesiona/zaadaptowana z
   `pulse_lite` v3.7 (patrz sekcja 1), ale sterowana teraz FPS-em zamiast
   busy%.

---

## 3. Fakty sprzętowe (skonsolidowane, obowiązujące dla obu projektów)

- **CPU, 4 niezależne policy:**
  | Policy | Rdzenie | Klaster | ECO | H_LOW | H_MID | STOCK |
  |---|---|---|---|---|---|---|
  | policy0 | cpu0-1 | A520 | 1344000 | 1459200 | 1804800 | 2265600 |
  | policy2 | cpu2-4 | A720-mid | 1708800 | 2035200 | 2438400 | 3148800 |
  | policy5 | cpu5-6 | A720-high | 1708800 | 2035200 | 2438400 | 2956800 |
  | policy7 | cpu7 | X4 prime | 1824000 | 2035200 | 2438400 | 3052800 |
  (kHz; policy7 ma dostępny turbo bin 3302400 kHz, świadomie pomijany jako
  niestabilny/za gorący.)
- **GPU (Adreno, kgsl):** 14 power-levels, indeks odwrócony (0=1050MHz
  uncapped ... 13=231MHz min). Węzły: `max_pwrlevel` (zapis),
  `gpubusypercentage` (odczyt, world-readable, bez roota — ale ten konkretny
  sysfs node jest CONFIRMED BROKEN na tym kernelu, używać `gpubusy` raw
  cycles zamiast).
- **Progi termiczne (nigdy realnie nie osiągnięte w kontrolowanych testach,
  ale przekroczone w warunkach bez kontrolera — patrz sekcja 2):** CPU
  HOT=78°C/HOTTER=85°C, GPU HOT=75°C/HOTTER=82°C.
- **Kanał roota — DWA warianty, chronologicznie:**
  1. Historyczny (`pulse_lite` v2.2-v3.7): tylko przez UI AYA Settings
     "Root Script" (wklej ścieżkę .sh, kliknij Run). Brak roota z gołego
     `adb shell` (uid=2000).
  2. Aktualny (`pulse_lite_diag`): **`xsu` jako pełny operacyjny odpowiednik
     `su`, dający uid=0 bezpośrednio z `adb shell`**, bez przechodzenia przez
     UI. Wzorzec z apki: `Runtime.exec("xsu")` + stdin/stdout pipe —
     analogiczny do zdekompilowanego `YtRootShell.java` z AYA Settings.
  - **KLUCZOWE, NIEZWERYFIKOWANE:** czy `xsu` działa via `Runtime.exec()` z
    wnętrza ZWYKŁEJ zainstalowanej apki (nie adb shell, nie AYA Settings).
    `adb shell` ma UID=2000 (`shell`), specjalną domenę z większymi
    uprawnieniami niż zwykła apka (`untrusted_app`). AYA Settings
    prawdopodobnie jest priv-app producenta (inna domena/podpis), więc jej
    sukces z tym mechanizmem **nie dowodzi**, że zwykła zainstalowana apka
    dostanie ten sam dostęp. **To jest test #0, blokujący dla całej apki
    PulseFit z GUI** — musi być zrobiony jako pierwszy, zanim zacznie się
    pisać cokolwiek więcej.
  - Jeśli test #0 wypadnie negatywnie: rozważyć **Shizuku**
    (rikka.shizuku) — framework specjalnie do sytuacji "adb ma więcej
    uprawnień niż apka, ale nie ma tradycyjnego roota/Magiska". Uruchamia
    trwały proces jako `shell` (jednorazowe parowanie przez adb/wireless
    debugging), do którego apki dogadują się przez Bindera. Jeśli `xsu`
    ufa wywołaniom z domeny `shell`, to może ominąć ograniczenie bez
    reverse-engineeringu.
  - Ostatnia deska ratunku (najbardziej krucha): odtworzenie historycznego
    mechanizmu Root Script z automatyzacją kliknięcia (Accessibility
    Service lub sprawdzenie czy `RootScriptFragment` ma eksportowany
    Intent).

---

## 4. Planowana architektura apki PulseFit (z README, jeszcze nieistniejąca)

```
pulsefit/app/src/main/java/pl/pulsefit/app/
├── root/    -> XsuShell.kt (kanał do roota — potencjalnie oprzeć o
                wzorzec biblioteki libsu, podmieniając binarkę su->xsu,
                zamiast pisać od zera)
├── fps/     -> FpsReader.kt (dumpsys SurfaceFlinger, logika z v8)
├── tdp/     -> AutoTdpController.kt (logika — PRZENIEŚĆ tier/histereza/
                floor z pulse_lite v3.7, ale sygnał = delta FPS vs cel,
                nie busy%), SysfsWriter.kt (I/O)
├── service/ -> TuningService.kt (foreground service, główna pętla)
├── boot/    -> BootReceiver.kt (autostart)
└── ui/      -> MainActivity.kt (minimalne UI: on/off, status live)
```

Zasady projektowe (obowiązujące): KISS, każdy moduł mały i testowalny,
warstwy (root/fps/tdp/ui) nie znają swoich szczegółów implementacji, małe
weryfikowalne kroki, zero kopiowania kodu `pulse` 1:1 (inny mechanizm
roota).

---

## 5. Zbiorcza lista "co dalej" (posortowane grubo wg priorytetu)

1. **Test #0 (blokujący):** czy zwykła zainstalowana apka może wywołać
   `xsu` przez `Runtime.exec()` i dostać uid=0. Bez tego cały plan apki z
   GUI stoi pod znakiem zapytania.
2. Jeśli test #0 negatywny → zbadać Shizuku jako most.
3. Zebrać pełną listę `scaling_available_frequencies` per policy (4x) i
   pełną listę GPU pwrlevels (już znana z tabeli w sekcji 3, ale nie
   wszystkie 14 wykorzystywane) — potrzebne do prawdziwego step-controllera
   zamiast 4 punktów na klaster.
4. Zaprojektować `AutoTdpController.kt`: port architektury tier/histereza/
   floor z `pulse_lite` v3.7, zamieniając sygnał busy% na deltę
   zmierzonego FPS (z `pulse_lite_diag` v8) względem celu.
5. Uruchomić Series 4 (próba konfliktu zapisu z AYASpace) — wciąż nigdy
   nie wykonana.
6. Rozważyć podniesienie priorytetu Modułu 3 (fan curve) z uwagi na
   znalezisko termiczne (93.8°C bez throttlingu).
7. Rozszerzyć walidację warstwy SurfaceFlinger na więcej aplikacji niż
   RetroArch/Eden, zanim uzna się heurystykę za w pełni ogólną.
8. Moduł 2 (controller remapper, Xbox/Nintendo/disable) — odłożony,
   ustalenia z `pulse_lite` master handoff czekają, gdy przyjdzie kolej.

---

## 6. Zakres tej notatki vs sesja CLI

Ten plik jest do **rozkminiania koncepcyjnego** (architektura, priorytety,
decyzje projektowe, pytania otwarte) w tańszej sesji `chat`. Gdy pojawi się
potrzeba:
- realnej edycji/tworzenia plików (kod, skrypty, projekt Gradle),
- odpalania komend (`adb`, `./gradlew`, itp.),
- odczytu pełnej zawartości konkretnych logów/skryptów spoza tego
  podsumowania,

— to wraca do sesji CLI (Claude Code) z dostępem do repo, wskazując
konkretnie czego dotyczy (np. "wróćmy do configu XsuShell.kt na bazie
ustaleń z sekcji 3").
