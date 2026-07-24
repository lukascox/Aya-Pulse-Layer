// FpsReader.kt
// Cykliczny odczyt FPS przez `dumpsys SurfaceFlinger --latency` / `--timestats`.
// Odpowiedzialnosc: zwracac aktualny FPS dla aktywnego okna/aplikacji.
// TODO: implementacja + obsluga edge-case dla emulatorow renderujacych na innym layerze.
