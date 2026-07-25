# HARDWARE_PROFILE.md — proponowane poprawki/dodatki po v5/v6 (do wklejenia w oryginał)

## KOREKTA KRYTYCZNA 1 — sekcja "Progi termiczne" jest nieaktualna

Oryginalny plik stwierdza: "te progi nigdy nie zostały faktycznie osiągnięte w testach".
NIEPRAWDA po runie heavy_gaming (Eden, Super Mario Odyssey, tryb Gaming):
- thermal_zone26 (cpu-1-2-1) = 90700 (90.7°C) - POWYŻEJ progu HOTTER (85000/85.0°C)
- thermal_zone25 (cpu-1-2-0) = 88000 (88.0°C) - POWYŻEJ progu HOTTER
- thermal_zone27 (cpu-1-2-2) = 87200 (87.0°C) - POWYŻEJ progu HOTTER
- thermal_zone16/17 (cpu-2-1-x) = 80600-81400 (80.6-81.4°C) - POWYŻEJ progu HOT (78000)
- skin-msm-therm = 50089 (50.1°C) - realny, wysoki skin temp pod obciążeniem

Wniosek: urządzenie REALNIE się przegrzewa pod pełnym obciążeniem CPU+GPU w trybie
Gaming, konkretne rdzenie A720/X4 przekraczają nawet próg HOTTER. Progi termiczne
w dokumencie są prawdopodobnie prawidłowe jako wartości, ale trzeba usunąć zapis
"nigdy nie osiągnięte" i dodać potwierdzony przypadek + zanotować, że AYASpace
(w trybie Gaming) NIE throttluje CPU mimo przekroczenia tych progów - co jest ważnym
odkryciem samo w sobie (brak natywnej ochrony termicznej ze strony AYASpace na
poziomie zaobserwowanym, albo throttling dzieje się głębiej/wolniej niż nasze okno
próbkowania 90s wykryło).

## KOREKTA KRYTYCZNA 2 — sekcja "Odczyt FPS" jest błędna, wymaga zamiany metody

Oryginalny plik rekomenduje `dumpsys gfxinfo <package> framestats` jako metodę FPS.
TO JEST BŁĘDNE dla emulatorów (RetroArch, Eden/Yuzu-class, Dolphin, PPSSPP) - potwierdzone
empirycznie w tym wątku:
- RetroArch: `dumpsys gfxinfo` zwraca "Total frames rendered: 0" mimo aktywnej rozgrywki,
  bo emulator renderuje przez natywny SurfaceView, niewidoczny dla tego API.
- Eden (Switch emulator): `dumpsys gfxinfo` zwrócił identyczne, zamrożone statystyki
  (117 frames, ten sam histogram) w 5 próbkach rozłożonych na 15s realnej rozgrywki -
  API zwracało cache'owany snapshot z UI overlay, nie z treści gry.

NOWA REKOMENDOWANA METODA (v6): pipeline dynamiczny
1. `dumpsys window windows | grep mCurrentFocus` + `dumpsys activity activities | grep
   mResumedActivity` -> wykryj foreground pkg/activity.
2. `dumpsys SurfaceFlinger --list` -> znajdź warstwę zawierającą nazwę pakietu,
   preferuj `SurfaceView[pkg]` (natywny render gry/emulatora) nad generyczną
   `pkg/Activity` (UI Android).
3. `dumpsys SurfaceFlinger --latency "<layer>"` -> pierwsza linia = refresh period (ns),
   kolejne linie = trójki timestampów; FPS liczone z delty actualPresentTime.

Ta metoda działa uniwersalnie (UI apps, RetroArch, Eden) i NIE wymaga hardkodowania
nazwy pakietu. `dumpsys gfxinfo` pozostaje użyteczne wyłącznie jako dodatkowe źródło
danych jank/percentyle DLA APLIKACJI NATYWNIE ANDROIDOWYCH (nie emulatorów).

## KOREKTA 3 — tabela CPU cap (ECO/H_LOW/H_MID/STOCK) nie odpowiada rzeczywistym
## wartościom obserwowanym w trybach AYASpace

Tabela w dokumencie wygląda jak wcześniejsza koncepcja kontrolera (własne, projektowane
capy), NIE jak zmierzone zachowanie natywnych trybów AYASpace. Rzeczywiste wartości
scaling_max_freq zaobserwowane w 5 trybach AYASpace (v5 logi, policy0/2/5/7 w Hz):

| Tryb AYASpace | Governor    | policy0 max | policy2 max | policy5 max | policy7 max |
|---|---|---|---|---|---|
| Eco       | powersave   | 1248000 | 729600  | 729600  | 480000  |
| Balanced  | schedutil   | 1248000 | 3148800 | 2956800 | 3302400 |
| Streaming | performance | 1248000 | 2131200 | 2035200 | 2112000 |
| Gaming    | performance | 1248000 | 3148800 | 2956800 | 3302400 |
| Max       | performance | 1248000 | 3148800 | 2956800 | 3302400 |

WAŻNE: dokument opisuje policy7 turbo bin (3302400 kHz) jako "SWIADOMIE POMIJANY jako
niestabilny/za goracy". To jest NIEZGODNE z rzeczywistym zachowaniem AYASpace - tryby
Balanced, Gaming i Max WSZYSTKIE ustawiają scaling_max_freq policy7 na 3302400, czyli
producent sam używa tego "turbo bin" w trzech z pięciu swoich profili, włącznie z
Balanced (czyli nie tylko w ekstremalnym Max). To podważa założenie, że jest to
niestabilny/pomijany punkt - do ponownej weryfikacji czy faktycznie jest problematyczny,
czy poprzednie ustalenie było błędne/przestarzałe.

Uwaga: Gaming i Max są IDENTYCZNE na poziomie CPU governor+freq we wszystkich 4 policy.
Różnica między nimi (jeśli istnieje) leży poza CPU - prawdopodobnie w limitach GPU
power-level, thermal, lub power budget niewidocznym w tych sysfs ścieżkach.

## KOREKTA 4 — GPU busy% odczyt: gpubusypercentage vs gpubusy (raw)

Dokument wskazuje `/sys/class/kgsl/kgsl-3d0/gpubusypercentage` jako world-readable,
bez roota. W praktyce skrypty v5/v6 używają `/sys/class/kgsl/kgsl-3d0/gpubusy` (raw
busy_cycles/total_cycles), bo `gpubusypercentage` jest oznaczone w naszych logach jako
"known broken on this kernel". Raw gpubusy ma WŁASNY problem: cykliczny licznik
zawija/resetuje się między odczytami, dający fałszywe wartości (nawet -2718%) - v6
dodaje sanity-check (wynik poza 0-100% -> "n/a counter_reset"), ale nie naprawia
przyczyny (nie do naprawienia z userspace). Do dokumentu należy dopisać: ŻADNA z dwóch
dostępnych ścieżek GPU busy% nie jest w pełni wiarygodna na tym kernelu.

## POTWIERDZENIE — kanał roota `xsu`

Dokument oznacza jako "NIE ZWERYFIKOWANE" czy xsu działa z `adb shell`. TO JEST TERAZ
ZWERYFIKOWANE: wszystkie 7 przebiegów diagnostycznych (v5, v6) używały
`adb shell xsu sh /sdcard/pulse_lite_diag.sh <suffix>` z potwierdzonym `uid=0(root)`
w sekcji IDENTITY każdego logu. Pozostaje nadal NIEZWERYFIKOWANE, czy xsu jest
wywoływalny z wnętrza normalnej apki Android (Runtime.exec) - to pytanie jest wciąż
otwarte, dokument poprawnie to zaznacza.

## DO DODANIA — nowe pole: per-sample governor/freq tracking (nie było w v2.2-v3.7)

v6 wprowadza capture CPU governor + scaling_cur_freq PER SAMPLE (co ~3s w 90s oknie),
nie tylko jednorazowy odczyt na starcie skryptu. Dokument powinien wspomnieć, że
Balanced (schedutil) wykazuje realne, ciągłe bounce'owanie częstotliwości w czasie
(np. policy2 skakał 960000-3148800 w ciągu kilku próbek podczas RetroArch), co jest
zgodne z oczekiwanym zachowaniem schedutil, ale nie było wcześniej udokumentowane
empirycznie w tym pliku.
