# HANDOFF: Budowa apki Android GUI dla pulse_lite (root via `xsu`/`xsud`)

## Status na dziś (2026-07-21)

Sesja robocza urwała się w trakcie przygotowywania planu budowy natywnej apki Android
zastępującej ręczny workflow "Root Script" w AYANEO Settings. Ten dokument jest
kompletnym handoffem do nowej instancji/sesji, żeby kontynuować bez utraty kontekstu.

**Cel użytkownika:** Zbudować apkę GUI na Androida (KONKR Pocket FIT, Snapdragon G3 Gen 3,
Android 14/15) z:
- Autostartem po boot (bez klikania czegokolwiek ręcznie)
- Stałym dostępem do roota (bez Magiska)
- Portem 1:1 logiki z `pulse_lite_v3.5.sh` (CPU/GPU tuning) do natywnego serwisu w tle

**Ważne:** Użytkownik deklaruje **zero wcześniejszego doświadczenia z budowaniem apek
Android**. Potrzebuje najpierw krótkiego "crash course" (tooling, architektura, jak się
buduje/testuje), zanim zaczniemy pisać cokolwiek związane z pulse_lite.

---

## Kluczowe znalezisko #1: mamy potwierdzony, działający ROOT bez Magiska

Użytkownik wykonał serię testów w `adb shell` na urządzeniu i **potwierdził w 100%
działający, operacyjny root** przez binarkę `xsu` (analog `su`, natywnie wbudowany
przez producenta AYANEO/KONKR w system, w domenie SELinux `u:r:xsud:s0`):

```
PocketFIT:/ $ xsu id
uid=0(root) gid=0(root) groups=0(root) context=u:r:xsud:s0

PocketFIT:/ $ xsu cat /data/system/users/0/settings_secure.xml
[zawartość chronionego pliku - odczyt się powiódł]

PocketFIT:/ $ xsu touch /data/local/tmp/test_root
PocketFIT:/ $ xsu stat /data/local/tmp/test_root
Uid: (    0/    root)   Gid: (    0/    root)
[plik faktycznie utworzony jako root - operacyjny dowód, nie tylko deklaratywny]
```

**Wniosek:** `xsu` daje pełny, funkcjonalny root — czytanie chronionych plików,
zapisywanie plików jako UID 0. To jest **wbudowany w firmware serwis systemowy**
(analogiczny do tego, co robi Magisk, ale bez patchowania boot image, bez ryzyka
boot loop, bez zależności od aktualizacji firmware łamiących roota).

**Nie przetestowano jeszcze:** czy `xsu`/`xsud` jest wywoływalny z wnętrza normalnej
apki Android (przez `Runtime.exec()` / `ProcessBuilder`), a nie tylko z sesji `adb shell`.
To jest **PIERWSZY krok techniczny do zweryfikowania w nowej sesji** — patrz "Następne
kroki" na końcu.

---

## Kluczowe znalezisko #2: zdekompilowany kod producenta pokazuje JAK TO ROBIĆ

Z plików w tym wątku (`RootShell.java`, `YtRootShell.java` — zdekompilowany kod
apki AYANEO Settings) wynika, że producent robi **dokładnie to samo** co musimy
zrobić — wywołuje swoją binarkę roota (`ytsu`, analog `xsu`) jako proces z Javy:

```java
// Wzorzec z YtRootShell.java (zdekompilowany kod AYANEO Settings)
Process process = Runtime.getRuntime().exec("ytsu");
OutputStream out = process.getOutputStream();
out.write(cmd.getBytes(Charsets.UTF_8));
out.write("\n".getBytes());
out.write("exit\n".getBytes());
out.flush();
// czytaj InputStream jak z normalnego procesu (BufferedReader)
process.waitFor();
```

To jest CAŁY "sekret" komunikacji z rootem z apki — nie ma AIDL, nie ma Bindera,
nie ma specjalnych uprawnień w manifeście. Dla naszej apki: zamieniamy `"ytsu"` na
`"xsu"` i mamy działający kanał do roota. Poziom trudności: kilkanaście linii kodu.

---

## Kluczowe znalezisko #3: referencyjna apka open-source PULSE

Repo: https://github.com/keiretrogaming/pulse

To jest **w pełni działająca, wydana apka** (98 stars, GPL-2.0, aktywnie rozwijana,
ostatni release 2026-07-05) robiąca bardzo zbliżoną rzecz do naszego celu — tuning
CPU/GPU na handheldach do gier — ale **innym mechanizmem no-root**:

### Architektura PULSE (do wzorowania się, NIE do 1:1 kopiowania)
- **Język/UI:** Kotlin + Jetpack Compose (99.7% Kotlin, reszta shell)
- **Mechanizm roota:** używa serwisu `PServerBinder` (analog naszego `xsud`) przez
  **reflection**, NIE przez `Runtime.exec()` jak w naszym przypadku z AYANEO. To jest
  inny, bardziej "naturalny" dla Androida mechanizm (Binder IPC) niż nasz prostszy
  `exec("xsu")`. Nasz mechanizm (exec) jest PROSTSZY do zaimplementowania jako
  pierwszy krok, ale mniej "elegancki" architektonicznie.
- **Funkcje:** AutoTDP (closed-loop kontroler FPS→moc), custom fan curve (PI
  controller), HUD/OSD overlay, RGB joysticka, profile per-app, Quick Settings tile,
  5 animowanych motywów (czysty Compose Canvas, bez shaderów/bitmap).
- **Wymagane uprawnienia (bez roota w ich przypadku):** Usage Access (do wykrywania
  aktywnej gry), Display over other apps (do OSD overlay).
- **Build:** `./gradlew testDebugUnitTest assembleDebug` → APK w
  `app/build/outputs/apk/debug/`
- **minSdk 31** (Android 12+)
- Sami deklarują, że kod pisany w dużej mierze z pomocą AI coding assistant (Claude)
  pod nadzorem i review maintainera — czyli nasz workflow (budowa z AI) jest
  dokładnie tym samym paradygmatem, jaki już się sprawdził w tym real-world projekcie.

### Przykład shell-scriptu jaki PULSE wysyła przez PServerBinder (analogiczne do
naszego pulse_lite_v3.5.sh):
```sh
# CPU cluster
chmod 666 /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo 2745600 > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
chmod 444 /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq

# GPU (Adreno power-level index, nie frequency!)
chmod 666 /sys/class/kgsl/kgsl-3d0/min_pwrlevel
echo 13 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel
chmod 444 /sys/class/kgsl/kgsl-3d0/min_pwrlevel
```

**Różnica kluczowa względem naszego planu:** PULSE działa BEZ roota (przez
PServerBinder Binder IPC) i deliberate NIE wymaga Magiska/roota — to jest ich
filozofia projektu ("no-root, or nothing"). MY mamy przewagę: potwierdzony,
działający root przez `xsu`, więc możemy pójść **prostszą** ścieżką (`exec()`)
zamiast reimplementować reflection-based Binder call do nieznanego serwisu.

---

## Pliki z tego wątku istotne do dalszej pracy

| Plik | Zawartość | Znaczenie |
|---|---|---|
| `pulse_lite_v3.5.sh` | Najnowsza wersja skryptu shell z logiką tuning CPU/GPU | To jest LOGIKA BIZNESOWA do przeportowania |
| `pulse_lite_v3.5_handoff.md` | Wcześniejszy handoff z detalami architektury pulse_lite | Kontekst dla logiki skryptu |
| `RootShell.java` / `YtRootShell.java` | Zdekompilowany kod AYANEO Settings | Wzorzec wywołania roota z Javy |
| `01_rootscript.txt` | Zrzut mechanizmu Root Script z apki AYANEO | Kontekst SELinux/xsud |
| `04_ipc.txt`, `05_auth.txt`, `06_manifest_components.txt` | Analiza IPC/auth/manifest apki AYANEO | Do sprawdzenia czy jest publiczny Binder interfejs do `xsud` (analog PServerBinder) |
| `pulse_lite.log`, `before.log`, `after.log`, `dmesg.log` | Logi z testów na urządzeniu | Dane referencyjne do debugowania |

**WAŻNE zadanie do zrobienia w nowej sesji:** sprawdzić pliki `04_ipc.txt` i
`06_manifest_components.txt` — czy `xsud` ma odsłonięty jakiś Binder/AIDL interfejs
(analog `PServerBinder` z PULSE), a nie tylko binarkę `xsu` wywoływaną przez shell.
Jeśli tak, można pójść dokładnie tą samą architekturą co PULSE (bardziej "natywną"
dla Androida) — jeśli nie, wzorzec `exec("xsu")` z `YtRootShell.java` jest
wystarczający i prostszy.

---

## Plan pracy (do rozpisania w nowej sesji)

### Faza 0: Crash course Android dev (PRIORYTET — użytkownik nie ma żadnego doświadczenia)
Potrzebne minimum do zrozumienia, zanim zaczniemy pisać kod:
1. **Tooling:** Android Studio (IDE), Gradle (build system), Kotlin (język — PULSE
   też jest w Kotlin, więc trzymajmy się tego samego)
2. **Podstawowa architektura apki Android:**
   - `Activity` = jeden "ekran" (dla prostego GUI wystarczy jedna)
   - `Service` / `ForegroundService` = proces w tle (do naszego daemon-a pulse_lite)
   - `BroadcastReceiver` = nasłuchiwanie zdarzeń systemowych (np. `BOOT_COMPLETED`
     do autostartu)
   - `AndroidManifest.xml` = deklaracja komponentów i uprawnień apki
3. **Cykl budowania:** edytuj kod → `./gradlew assembleDebug` → `adb install app.apk`
   → testuj na urządzeniu → powtórz
4. **Najprostsze możliwe "Hello World":** apka z jedną Activity wyświetlającą tekst,
   żeby użytkownik zobaczył cały pipeline (edycja→build→install→uruchomienie) raz,
   zanim dojdziemy do czegokolwiek związanego z rootem.

### Faza 1: Weryfikacja `xsu` z wnętrza apki (nie z adb shell)
Minimalna apka testowa z jednym przyciskiem "Test Root", która wywołuje wzorzec z
`YtRootShell.java` (zamieniając `"ytsu"` na `"xsu"`) i wyświetla wynik `xsu id` na
ekranie. To potwierdzi, że apka (a nie tylko sesja adb) ma dostęp do roota.

### Faza 2: Warstwa RootShell + prosty GUI status
Klasa `RootShell.kt` (wzorowana na `YtRootShell.java`, ale w Kotlinie) + Activity
pokazująca żywe odczyty (np. aktualna częstotliwość CPU/GPU) przez `xsu cat`.

### Faza 3: Port logiki pulse_lite_v3.5.sh
Przepisanie logiki tuningu CPU/GPU cluster + GPU pwrlevel z shell scriptu na
wywołania Kotlin, budujące komendy shell wysyłane przez `xsu` (analogicznie do
sekcji "How the no-root mechanism works" w README PULSE, tylko przez `xsu` a nie
`PServerBinder`).

### Faza 4: ForegroundService + autostart
`BootReceiver` na `BOOT_COMPLETED` + `ForegroundService` startujący logikę z Fazy 3
automatycznie, bez interakcji użytkownika. Trzeba też obsłużyć battery optimization
exemption (Android 14/15 restrykcje dla serwisów w tle).

### Faza 5 (opcjonalnie, dużo później): GUI feature parity z PULSE
AutoTDP-style closed-loop, HUD/OSD, profile per-app — tylko jeśli po Fazach 1-4
użytkownik zechce iść dalej. NIE jest to wymagane na start.

---

## Rzeczy, o których NIE zapominać w nowej sesji

1. Root przez `xsu` już jest **potwierdzony i działający** — nie trzeba nikogo
   przekonywać ani ponownie testować w adb, tylko przenieść to do apki.
2. Magisk **nie jest potrzebny** — cała wcześniejsza dyskusja o rootowaniu przez
   Magisk/boot image patching jest **zbędna** dzięki odkryciu `xsu`. Nie wracać do
   tego tematu, chyba że `xsu` z jakiegoś powodu przestanie działać w apce.
   Wcześniej rozważano root przez Magisk (backup boot image, patch, flash) jako
   plan B — trzymać to jako informację w tle, ale nie jako główną ścieżkę.
3. Użytkownik ma zerowe doświadczenie Android dev — **zaczynać zawsze od najprostszej
   możliwej wersji** każdego kroku, z pełnym wyjaśnieniem "co to jest" i "dlaczego
   to robimy", zanim przejdziemy do kolejnego kroku.
4. Urządzenie: **KONKR Pocket FIT**, Snapdragon G3 Gen 3, Android 14/15,
   Root Script mechanizm dostępny natywnie w AYANEO Settings jako fallback/plan B
   testowania komend przed przeniesieniem ich do apki.
5. Traktować repo PULSE (github.com/keiretrogaming/pulse) jako **wzorzec
   architektoniczny i inspirację**, nie jako kod do bezpośredniego forka — nasz
   mechanizm roota (`exec("xsu")`) jest inny (prostszy) niż ich Binder-reflection
   do `PServerBinder`, i nasz cel (autostart + ciągły root) różni się od ich
   filozofii "no-root, permission-based".

---

## Pierwsza wiadomość do wysłania w nowej sesji (proponowana)

> "Kontynuujemy projekt budowy apki Android do pulse_lite na KONKR Pocket FIT.
> Załączam handoff z pełnym kontekstem. Chcę zacząć od Fazy 0 — crash course
> budowania apek Android (tooling, architektura, jak buduje się i testuje), bo
> nigdy wcześniej tego nie robiłem. Zacznijmy od najprostszej możliwej apki
> 'Hello World', żebym zobaczył cały pipeline raz, zanim przejdziemy do
> czegokolwiek związanego z rootem."
