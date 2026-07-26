# Komendy do sesji testowej — instalacja, zbieranie wyników, sprzątanie

Ściąga z komendami do wklejenia w terminal dla jednej sesji A/B
(`pulse-for-aya` vs natywny AyaSettings). Sama procedura "co robić na
telefonie w grze" jest w [`TESTING.md`](TESTING.md) (po polsku, do oddania
testerowi) — ten plik jest tylko dla strony technicznej: build/install,
ściąganie CSV, sprzątanie.

> Przeczytaj `STATUS.md` (sekcja "INCIDENT #2") przed sesją — ostatnia
> pełna sesja PULSE+ab-logger+Eden skończyła się pełnym restartem
> urządzenia. Zalecane: najpierw sama sesja z `ab-logger` (bez
> `pulse-for-aya`), pod obserwacją.

Wszystkie komendy zakładają `cd` do katalogu głównego repo
(`apl/`) i podłączone przez `adb` urządzenie.

## 1. Instalacja (build już zrobiony — tylko install)

```bash
adb install -r research/pulse-for-aya/app/build/outputs/apk/debug/app-debug.apk
adb install -r research/ab-logger/app/build/outputs/apk/debug/app-debug.apk
```

Jeśli trzeba przebudować od zera (np. po zmianach w kodzie):

```bash
(cd research/pulse-for-aya && ./gradlew assembleDebug)
(cd research/ab-logger && ./gradlew assembleDebug)
```

## 2. Czyszczenie katalogu z logami przed sesją

Żeby nowa sesja nie mieszała się ze starymi plikami z poprzedniego testu:

```bash
adb shell rm -rf /sdcard/apl_ab_logs/*
```

Bezpieczne do puszczenia zawsze przed startem nowej sesji — `ab-logger`
sam odtwarza katalog przy kolejnym "Start log".

## 3. Sama sesja testowa

Wykonaj procedurę z [`TESTING.md`](TESTING.md) na urządzeniu (dwa
przebiegi: natywny i PULSE, po 10 min tej samej gry, z notatkami godzin).
Nic do wklejenia w tym kroku — to dzieje się na telefonie.

## 4. Ściągnięcie plików po sesji

```bash
adb pull /sdcard/apl_ab_logs/ ./pulled_logs/
```

Ściąga wszystkie CSV z sesji naraz do `./pulled_logs/` w repo (folder
roboczy, nie commituj go w tym miejscu — patrz krok 5).

## 5. Wgranie do właściwego miejsca w repo

Zmień nazwy plików wg notatek (który plik to `native`, który `pulse`) i
przenieś do `diagnostics/logs/ab-comparison/<gra>_<scena>/` — podmień
`<gra>_<scena>` i `<timestamp-*>` na rzeczywiste wartości:

```bash
mkdir -p diagnostics/logs/ab-comparison/<gra>_<scena>
mv pulled_logs/session_<timestamp-native>.csv diagnostics/logs/ab-comparison/<gra>_<scena>/native_run1.csv
mv pulled_logs/session_<timestamp-pulse>.csv diagnostics/logs/ab-comparison/<gra>_<scena>/pulse_run1.csv
rm -rf pulled_logs/
```

(Przy powtórce z zamienioną kolejnością: `native_run2.csv` / `pulse_run2.csv`.)

Layout i konwencja nazw opisane w
[`diagnostics/logs/ab-comparison/README.md`](../../diagnostics/logs/ab-comparison/README.md).

## 6. Sprzątanie urządzenia na koniec sesji

Odinstaluj obie apki testowe i zrestartuj urządzenie, żeby pozbyć się
cache/procesów (`xsud` worker state, zawieszone foreground service, itp.)
przed kolejną sesją:

```bash
adb uninstall com.kei.pulse
adb uninstall pl.ablogger.app
adb reboot
```

Po restarcie urządzenie wraca do stanu "czystego" — przy kolejnej sesji
zacznij od kroku 1 (install od zera).
