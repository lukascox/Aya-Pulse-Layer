// Verbatim excerpt from com/ayaneo/devices/ar03/AR03.java (full class is
// 1091 lines of mostly-unrelated device config -- these are the only
// fan-PWM-relevant lines, extracted as-is from jadx output, package/
// import statements omitted). Field `o` (java.io.File) holds the
// resolved pwm1 path; `b(cmd)` is this device class's own root-shell
// exec helper (same pattern as RootShell in the other teardown).
// AR13 (evidence/fan/AR13_fan_excerpt.java) extends this class and
// overrides the fan-speed entry point.

// --- constructor: default pwm1 path guess ---
        this.o = new File("/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1");

// --- probes hwmon0..hwmon8 for the first one with a pwm1 file ---
        for (int i = 0; i < 9; i++) {
            File file = new File(a.a.h("/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon", i, "/pwm1"));
            if (file.exists()) {
                return file;
            }
        }
        return new File("/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1");
    }


// --- writes a raw PWM duty value (0-255) to the resolved pwm1 file ---
    public final void t1(int i) {
        Object objM15constructorimpl;
        if (!this.o.exists()) {
            this.o = r1();
        }
        if (!this.o.canRead()) {
            b("chmod 777 " + this.o);
        }
        try {
            Result.Companion companion = Result.INSTANCE;
            FilesKt.c(this.o, String.valueOf(i));
            objM15constructorimpl = Result.m15constructorimpl(Unit.f8334a);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(ResultKt.a(th));
        }
        if (Result.m18exceptionOrNullimpl(objM15constructorimpl) != null) {
            b("echo " + i + " > " + this.o.getAbsolutePath());
        }
    }
