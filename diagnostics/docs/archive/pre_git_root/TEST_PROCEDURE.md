# PROCEDURA TESTOWA — sesja diagnostyczna przed PoC FPS + busy%

Cel: wrocic do sesji z Perplexity z 3-4 plikami logow, ktore razem daja
kompletny obraz: zakresy sprzetowe, jakosc danych FPS per typ gry, oraz
czy jest konflikt z AYASpace. Na tej podstawie mozliwe bedzie napisanie
PoC skryptu FPS+busy% bez dalszych "domyslen".

## Wymagania wstepne

- Skrypt `pulse_lite_diag.sh` wgrany: `adb push pulse_lite_diag.sh /sdcard/`
- Kabel USB podlaczony przez cala sesje (to jest diagnostyka, nie demon dlugozyjacy)
- ok. 20-25 minut czasu, w 4 krotkich seriach

## Seria 1 — baseline (system w spoczynku, ekran startowy/launcher)

1. Wroc do ekranu glownego (nie odpalaj nic).
2. `adb shell xsu sh /sdcard/pulse_lite_diag.sh baseline`
3. Skrypt sam probkuje 90s (sekcja 7) — nie robic nic w tym czasie.
4. Po zakonczeniu: `adb pull /sdcard/pulse_lite_diag_baseline.log`

Ten log daje: pelny fingerprint SoC, kompletne tabele CPU/GPU freq, baseline
temperatur w spoczynku, potwierdzenie ze zapisy sysfs dzialaja (sekcje 8-9).

## Seria 2 — lekki emulator (np. GBA / SNES na RetroArch)

1. Odpal RetroArch, wejdz w gre GBA/SNES, poczekaj az gra sie faktycznie
   uruchomi i bedzie plynna (kilkanascie sekund gry, nie tylko menu).
2. Trzymajac gre dzialajaca w tle/na ekranie: `adb shell xsu sh /sdcard/pulse_lite_diag.sh gba_retroarch`
3. Skrypt probkuje 90s — w tym czasie GRAJ (nie zostawiaj na pauzie/menu).
4. `adb pull /sdcard/pulse_lite_diag_gba_retroarch.log`

To daje: czy `gfxinfo framestats` faktycznie zwraca dane dla RetroArch, jaki
jest realny busy% CPU/GPU przy lekkim tytule (oczekiwanie: niski).

## Seria 3 — ciezki emulator (np. Switch/PS2/GameCube, najciezszy jaki masz)

1. Odpal najbardziej wymagajacy tytul jaki masz (to co realnie przycieplo
   Ci wczesniej i zmusilo do myslenia o TDP).
2. Analogicznie: `adb shell xsu sh /sdcard/pulse_lite_diag.sh heavy_emulator`
3. GRAJ aktywnie 90s (najlepiej w scenie akcji, nie w menu/loading).
4. `adb pull /sdcard/pulse_lite_diag_heavy_emulator.log`

To daje: dane FPS/busy% przy realnym obciazeniu, punkt odniesienia "gorny"
kontrastujacy z lekkim tytulem z serii 2. Jesli temperatury z sekcji 4 tego
loga sa zauwazalnie wyzsze niz w baseline — masz pierwszy realny sygnal
termiczny do kalibracji progow HOT/HOTTER.

## Seria 4 — konflikt z AYASpace (opcjonalna, ale wazna)

1. Wroc do ekranu glownego lub zostaw cokolwiek dzialac w tle.
2. `adb shell xsu sh /sdcard/pulse_lite_diag.sh ayaspace_conflict`
3. Skrypt ustawi policy0 na tryb ECO i będzie go trzymac 20s, wypisujac co 2s
   aktualna wartosc. W TYM OKNIE 20 sekund: recznie przelacz w AYASpace
   Performance -> tryb Gaming, potem Eco, potem Max (szybko, jeden po drugim).
4. `adb pull /sdcard/pulse_lite_diag_ayaspace_conflict.log`

To daje odpowiedz: czy AYASpace nadpisuje wartosc mimo `chmod 444` (log
pokaze skok wartosci w polowie 20s), czy nasz zapis trzyma sie niezaleznie
od przelacznikow UI.

## Co przyniesc do nastepnej sesji

- `pulse_lite_diag_baseline.log`
- `pulse_lite_diag_gba_retroarch.log`
- `pulse_lite_diag_heavy_emulator.log`
- `pulse_lite_diag_ayaspace_conflict.log` (jesli zrobiona seria 4)

Cztery pliki, kazdy z innego scenariusza — to jest kompletny zestaw danych
do napisania PoC FPS+busy% bez zgadywania czegokolwiek po drodze.
