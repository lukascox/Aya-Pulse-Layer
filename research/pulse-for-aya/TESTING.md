# Procedura testowa — porównanie PULSE vs natywny AyaSettings

Napisane po polsku celowo (w odróżnieniu od reszty dokumentacji w tym
repo) — to instrukcja operacyjna do fizycznego wykonania na urządzeniu,
możliwa do oddania osobie bez wiedzy technicznej, nie zapis badawczy.

## Cel, w jednym zdaniu

Sprawdzamy, czy nakładka PULSE (eksperymentalna, nasza) daje podobne albo
lepsze FPS/temperatury/zużycie baterii niż zwykły, natywny tryb wydajności
AYANEO — grając w tę samą grę dwa razy, raz z każdą opcją.

## Czego potrzeba

- Urządzenie AYANEO Pocket FIT / Konkr Pocket, naładowane (najlepiej >50%).
- Obie aplikacje testowe już zainstalowane: **PULSE** i **AutoTdpAbHarness**
  (jeśli `AutoTdpAbHarness` nie jest jeszcze zainstalowana, to jest to
  jeden dodatkowy krok do zrobienia przed testem — zapytaj osobę, która
  przygotowała telefon).
- Jedna gra, w którą można pograć bez przerwy ok. 10 minut, dwa razy pod
  rząd tego samego dnia (albo w dwóch bliskich sesjach).
- Kartka/notatnik (albo apka Notatki w telefonie) do zapisania kilku liczb
  i uwag — bez tego test nie będzie się dało porównać.

## Jednorazowe przygotowanie (raz na urządzenie, nie przed każdym testem)

1. Otwórz **PULSE**.
2. Włącz przełącznik **AUTOTDP** na górnym ekranie.
3. System pokaże ekran **"Usage access"** (dostęp do użycia aplikacji) —
   to normalne, PULSE musi wiedzieć w co aktualnie grasz. Znajdź na liście
   **Pulse**, wejdź w nią, zaznacz **Allowed / Zezwól**.
4. Wróć do PULSE (przycisk wstecz). Jeśli przełącznik AUTOTDP nie jest już
   włączony, włącz go ponownie.
5. Ten krok robi się **raz** — przy kolejnych testach nie trzeba tego
   powtarzać, chyba że apka zostanie odinstalowana i zainstalowana od nowa.

## Jedna sesja testowa — dokładne kroki

Test składa się z dwóch "przebiegów" (**A** i **B**), tej samej gry, w tym
samym dniu. Kolejność ma znaczenie — patrz niżej.

### Przebieg "natywny" (AyaSettings)

1. Zamknij PULSE całkowicie (przesuń z listy ostatnich aplikacji) albo
   wyłącz w nim przełącznik AUTOTDP.
2. Otwórz natywne ustawienia wydajności AYANEO (AYA Space / AyaSettings) i
   ustaw taki tryb, jakiego normalnie używasz do grania (np. Gaming albo
   Balanced) — zapamiętaj który.
3. Otwórz **AutoTdpAbHarness**, naciśnij **"Start Baseline"**.
4. Odpal grę, graj spokojnie **dokładnie 10 minut** (ustaw stoper w
   telefonie/zegarku).
5. Wróć do AutoTdpAbHarness, naciśnij **"Stop"**.
6. Zapisz w notatniku: godzina startu, nazwa gry, tryb AYANEO użyty w
   punkcie 2, i słowem: czy coś dziwnego się działo (przycięcia,
   gorący telefon, dziwne dźwięki wentylatora, zawieszenie).

### Przebieg "PULSE"

1. W natywnych ustawieniach AYANEO przełącz na tryb **odkorkowany/Max**
   (żeby PULSE, a nie AYANEO, decydował o zegarach — inaczej będą się o
   to "kłócić").
2. Otwórz PULSE, włącz przełącznik **AUTOTDP** (jeśli nie jest już
   włączony).
3. Otwórz AutoTdpAbHarness, naciśnij **"Start AutoTDP"**.
4. Odpal **tę samą grę**, w tym samym miejscu/scenie co poprzednio, graj
   spokojnie **dokładnie 10 minut**.
5. Wróć do AutoTdpAbHarness, naciśnij **"Stop"**.
6. Zapisz to samo co wyżej (godzina, gra, uwagi) + dopisz "PULSE" jako
   użyty tryb.

### Ważne: zamień kolejność przy drugiej sesji

Jeśli robisz to więcej niż raz (a warto, dla pewności wyniku) — **drugiego
dnia zacznij od przebiegu PULSE, a dopiero potem natywny**, czyli
odwrotnie niż za pierwszym razem. Granie zawsze w tej samej kolejności
(najpierw natywny, potem PULSE) zafałszowałoby wynik — telefon może się
np. bardziej nagrzać za drugim razem niezależnie od tego, który tryb jest
lepszy.

## Kiedy przerwać test i zgłoić problem

Przerwij natychmiast (naciśnij "Stop", zamknij grę) i zgłoś, jeśli:
- telefon robi się wyraźnie gorętszy niż zwykle przy tej samej grze,
- wentylator zachowuje się nienaturalnie (bardzo głośno, albo w ogóle
  cicho tam gdzie zwykle chodzi),
- gra się zawiesza/wyłącza, telefon się restartuje,
- cokolwiek wygląda/brzmi niepokojąco.

To normalne narzędzie testowe na sprzęcie, na którym nam zależy —
ostrożność ważniejsza niż dokończenie sesji.

## Co się dzieje potem

Dane (pliki CSV) zostają na telefonie — osoba techniczna (Lukasz) ściągnie
je później przez kabel/komputer. Tester nie musi nic wysyłać ani
eksportować — wystarczy notatka z godzinami i obserwacjami, żeby dało się
dopasować, który plik do którego przebiegu należy.
