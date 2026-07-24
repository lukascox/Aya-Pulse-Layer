# AyaPulseLite (APL) — AutoTDP + key remap + fan curve dla KONKR Pocket FIT

## Cel projektu

Zbudowac natywna apke Android GUI dla KONKR Pocket FIT (Snapdragon 8 Gen 3, Android
14/15), zastepujaca reczny workflow "Root Script" z AYA Settings. Fork/inspiracja
logiki AutoTDP z projektu `pulse` (https://github.com/keiretrogaming/pulse), ale
z warstwa roota oparta na natywnym `xsu`/`xsud`, nie na reflection do `PServerBinder`
(ktory nie jest dostepny na tym urzadzeniu).

Autor nie jest programista. Projekt budowany iteracyjnie z pomoca AI (Claude Code /
inny agent) + reczne buildy/testy na sprzecie. Zobacz `docs/HANDOFF_TEMPLATE.md` przy
koncu kazdej sesji.

## Zakres (w kolejnosci priorytetow)

1. **Modul 1 — AutoTDP (w budowie)**
   Serwis w tle, autostart po boocie, odczyt FPS aktywnej aplikacji, step-controller
   z histereza dobierajacy limity CPU/GPU tak, aby utrzymac target FPS (domyslnie 60)
   przy minimalnej mozliwej mocy. Zapis do sysfs przez `xsu`.

2. **Modul 2 — Key remapping (odlozony)**
   Dowolne bindowanie fizycznych przyciskow (np. tylny przycisk -> ESCAPE/H).
   Prawdopodobnie Accessibility Service + InputManager, lub root-level przez
   `getevent`/`sendevent` jesli przyciski sa zarezerwowane przez firmware.

3. **Modul 3 — Fan curve (odlozony)**
   PI-controller trzymajacy minimalny RPM wzgledem temperatury, wzorem AutoTDP z
   `pulse`. Docelowo wspolzyje z Modulem 1 (ta sama petla sterowania?).

4. **Poza zakresem na razie:** przycisk "streaming mode" w AYA Settings (blokada
   przypadkowego wejscia dzieci w tryb streaming) — wymaga dekompilacji AYA Settings,
   osobna sesja analityczna.

## Zasady projektowe

- **KISS.** Kazdy modul to osobna, malenka, testowalna klasa. Zaden "boga-obiekt".
- **Modularnosc.** Warstwa transportu (`xsu`), warstwa odczytu danych (FPS/temp),
  warstwa logiki (kontrolery) i warstwa UI sa rozdzielone i nie znaja szczegolow
  implementacji siebie nawzajem (patrz `root/`, `fps/`, `tdp/`, `ui/`).
- **Male kroki.** Kazda iteracja = jeden maly, weryfikowalny przyrost (np. "serwis
  startuje po boocie i pisze log", potem "czyta FPS i loguje", potem "pisze do sysfs").
- **Zero magii producenta.** Nie kopiujemy kodu `pulse` 1:1 (inny mechanizm roota),
  tylko wzorujemy sie na architekturze i logice kontrolerow.

## Struktura repo

```
apl/
├── app/
│   ├── src/main/java/pl/ayapulselite/app/
│   │   ├── root/      -> XsuShell.kt (kanal do roota, exec("xsu"))
│   │   ├── fps/        -> FpsReader.kt (dumpsys SurfaceFlinger)
│   │   ├── tdp/        -> AutoTdpController.kt (logika), SysfsWriter.kt (I/O)
│   │   ├── service/    -> TuningService.kt (foreground service, glowna petla)
│   │   ├── boot/        -> BootReceiver.kt (autostart)
│   │   └── ui/          -> MainActivity.kt (minimalne UI, status/wlacz-wylacz)
│   ├── src/main/res/    -> zasoby (layout, values)
│   ├── src/main/AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/wrapper/       -> gradle wrapper (do dogenerowania lokalnie)
├── docs/
│   └── HANDOFF_TEMPLATE.md  -> szablon do zamykania sesji
├── build.gradle.kts       -> konfiguracja root projektu
├── settings.gradle.kts
└── README.md              -> ten plik
```

## Znane fakty techniczne (z poprzednich sesji)

- `xsu` dziala jako pelny root z `adb shell` (potwierdzone: uid=0, odczyt chronionych
  plikow, zapis jako root). NIE zweryfikowano jeszcze wywolania z wnetrza normalnej
  apki przez `Runtime.exec()` — to jest PIERWSZY test do wykonania w Module 1.
- Wzorzec komunikacji z rootem: `Runtime.getRuntime().exec("xsu")`, zapis komend na
  stdin, `waitFor()`, odczyt stdout (patrz zdekompilowany `YtRootShell.java` z AYA
  Settings — analogiczny mechanizm, inna nazwa binarki).
- `pulse` uzywa reflection do serwisu `PServerBinder`, dostepnego tylko na AYN Odin 3,
  AYN Thor i Retroid Pocket 6 — KONKR Pocket FIT tego serwisu NIE ma, stad potrzeba
  wlasnej warstwy transportu przez `xsu`.
- Przykladowe wezly sysfs uzywane przez `pulse` (analogiczne cele dla nas):
  `/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq` (CPU),
  `/sys/class/kgsl/kgsl-3d0/min_pwrlevel` (GPU, indeks power-level, nie czestotliwosc).
  Dokladne wezly/zakresy dla KONKR Pocket FIT trzeba zweryfikowac na urzadzeniu.
- FPS mozna czytac bez roota przez `dumpsys SurfaceFlinger --latency` lub
  `--timestats` — ale nazwa warstwy/okna moze sie roznic per emulator, wymaga testow.

## Workflow iteracji (bez Claude Code / bez live agenta)

1. Zmiany w kodzie generowane w sesji (Perplexity / inny asystent) jako pliki lub
   archiwum .zip.
2. Rozpakowanie na maszynie z zainstalowanym JDK + Android SDK cmdline-tools.
3. `./gradlew assembleDebug` -> APK w `app/build/outputs/apk/debug/`.
4. `adb install -r <apk>` na KONKR Pocket FIT, test recznny.
5. Zbieranie logow (`adb logcat`) i obserwacji, raport do nastepnej sesji.
6. Na koniec sesji: wypelnic `docs/HANDOFF_TEMPLATE.md` i zapisac w repo.

## Status

Repo to szkielet (pliki placeholder z komentarzami TODO). Zaden modul nie ma jeszcze
dzialajacej implementacji. Pierwszy cel: Modul 1, krok 1 — serwis startujacy po
boocie i piszacy log do logcat, zeby zweryfikowac ze `xsu` jest wywolywalne z
wnetrza apki.
