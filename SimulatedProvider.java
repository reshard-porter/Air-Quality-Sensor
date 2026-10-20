import java.util.Random;

/**
 * Generates realistic-looking readings so the GUI can be demonstrated without hardware:
 * a slowly drifting background level, sensor noise, and occasional pollution events
 * (cooking, traffic, smoke) that spike and then decay.
 */
public class SimulatedProvider implements DataProvider {

    private final Random r = new Random();
    private double background = 7;   // slowly drifting baseline PM2.5
    private double event = 0;        // extra PM2.5 from a pollution event
    private double pm25 = 7;
    private double coarseRatio = 1.8;

    @Override
    public synchronized AirQualityData read() {
        background = clamp(background + r.nextGaussian() * 0.15, 3, 22);

        if (event < 1 && r.nextDouble() < 0.012) {
            event = 15 + r.nextDouble() * 70;          // start an event
        }
        event *= 0.965;                                 // events fade out

        double target = background + event;
        pm25 = Math.max(0, pm25 + (target - pm25) * 0.18 + r.nextGaussian() * 0.5);

        coarseRatio = clamp(coarseRatio + r.nextGaussian() * 0.02, 1.3, 2.4);
        double pm10 = Math.max(pm25, pm25 * coarseRatio + 1.5 + r.nextGaussian() * 0.8);

        // the real sensor reports in steps of 0.1 µg/m³
        return new AirQualityData(Math.round(pm25 * 10) / 10.0, Math.round(pm10 * 10) / 10.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public String getName() {
        return "Simulator";
    }
}
