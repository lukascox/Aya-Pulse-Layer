// Verbatim excerpt from com/ayaneo/devices/ar13/AR13.java (extends AR03,
// full class is 360 lines -- these are the only fan-PWM-relevant lines).
// n1() is the setFanSpeed(percent 0-100) override AR13 uses instead of
// AR03's default; c1() is the fan-RPM-read override. Both confirmed
// live on our AYANEO Pocket FIT (2026-07-25):
//   fan_power_state -> "pwm_en=1"
//   hwmon0/pwm1     -> "76" (out of 255)
//   fan_rpm_state   -> "Current RPM 2815"


    @Override // com.ayaneo.devices.ar03.AR03, com.ayaneo.devices.IAyaDeviceHardware
    public final int c1() {
        Integer numC0 = StringsKt.c0(b("cat /sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state"));
        return numC0 != null ? numC0.intValue() : super.c1();
    }


    @Override // com.ayaneo.devices.ar03.AR03, com.ayaneo.devices.IAyaDeviceHardware
    public final void n1(int i) {
        if (i > 0) {
            b("echo 1 > /sys/devices/platform/soc/soc:pwm-fan/fan_power_state");
        } else {
            b("echo 0 > /sys/devices/platform/soc/soc:pwm-fan/fan_power_state");
        }
        t1((i * 255) / 100);
