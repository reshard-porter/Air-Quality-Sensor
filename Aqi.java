import java.awt.Color;

/**
 * US EPA Air Quality Index for PM2.5 and PM10 (breakpoints as revised by the EPA in 2024).
 *
 * Note: the official AQI is defined on 24-hour averages. This app applies the same
 * breakpoints to live readings, which is a useful indication but not an official AQI.
 */
public final class Aqi {

    public enum Category {
        GOOD("Good", "Good", 0, 50,
                new Color(0x22C55E), new Color(0x15803D),
                "Air quality is satisfactory and poses little or no risk. Enjoy your usual outdoor activities."),
        MODERATE("Moderate", "Moderate", 51, 100,
                new Color(0xEAB308), new Color(0xA16207),
                "Air quality is acceptable. Unusually sensitive people should consider reducing prolonged or heavy exertion outdoors."),
        SENSITIVE("Unhealthy for Sensitive Groups", "USG", 101, 150,
                new Color(0xF97316), new Color(0xC2410C),
                "Children, older adults and people with heart or lung conditions (including asthma) should reduce prolonged or heavy outdoor exertion."),
        UNHEALTHY("Unhealthy", "Unhealthy", 151, 200,
                new Color(0xEF4444), new Color(0xB91C1C),
                "Everyone may begin to feel health effects. Sensitive groups should avoid prolonged outdoor exertion; everyone else should limit it."),
        VERY_UNHEALTHY("Very Unhealthy", "V. Unhealthy", 201, 300,
                new Color(0x9333EA), new Color(0x7E22CE),
                "Health alert: everyone should avoid prolonged or heavy exertion outdoors. Sensitive groups should stay indoors."),
        HAZARDOUS("Hazardous", "Hazardous", 301, 500,
                new Color(0x881337), new Color(0x881337),
                "Emergency conditions. Everyone should avoid all outdoor physical activity and keep windows closed.");

        public final String label;
        public final String shortLabel;
        public final int lo, hi;
        /** Strong colour for fills and badges. */
        public final Color color;
        /** Darker variant that stays readable as text on white. */
        public final Color ink;
        public final String advice;

        Category(String label, String shortLabel, int lo, int hi, Color color, Color ink, String advice) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.lo = lo;
            this.hi = hi;
            this.color = color;
            this.ink = ink;
            this.advice = advice;
        }

        /** Text colour to use on top of {@link #color}. */
        public Color textOn() {
            return this == MODERATE ? new Color(0x422006) : Color.WHITE;
        }
    }

    // Concentration breakpoints (µg/m³), one row per category above.
    private static final double[] PM25_LO = {0.0, 9.1, 35.5, 55.5, 125.5, 225.5};
    private static final double[] PM25_HI = {9.0, 35.4, 55.4, 125.4, 225.4, 325.4};
    private static final double[] PM10_LO = {0, 55, 155, 255, 355, 425};
    private static final double[] PM10_HI = {54, 154, 254, 354, 424, 604};

    private Aqi() {}

    /** AQI sub-index for a PM2.5 concentration, or -1 if the value is not a number. */
    public static int pm25(double ugm3) {
        if (Double.isNaN(ugm3)) return -1;
        double c = Math.floor(ugm3 * 10 + 1e-9) / 10.0;   // EPA: truncate to 0.1
        return calc(c, PM25_LO, PM25_HI);
    }

    /** AQI sub-index for a PM10 concentration, or -1 if the value is not a number. */
    public static int pm10(double ugm3) {
        if (Double.isNaN(ugm3)) return -1;
        double c = Math.floor(ugm3 + 1e-9);                // EPA: truncate to integer
        return calc(c, PM10_LO, PM10_HI);
    }

    private static int calc(double c, double[] lo, double[] hi) {
        if (c < 0) c = 0;
        Category[] cats = Category.values();
        for (int i = 0; i < hi.length; i++) {
            if (c <= hi[i]) {
                Category k = cats[i];
                double aqi = (k.hi - k.lo) / (hi[i] - lo[i]) * (c - lo[i]) + k.lo;
                return (int) Math.round(Math.max(k.lo, aqi));
            }
        }
        return 500; // beyond the scale
    }

    /** Category for an AQI value, or null for a negative (missing) value. */
    public static Category category(int aqi) {
        if (aqi < 0) return null;
        for (Category c : Category.values()) {
            if (aqi <= c.hi) return c;
        }
        return Category.HAZARDOUS;
    }

    /** Position 0..6 along a gauge drawn with one equal-width segment per category. */
    public static double gaugePosition(int aqi) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (aqi <= cats[i].hi) {
                double lo = (i == 0) ? 0 : cats[i - 1].hi;
                double f = (aqi - lo) / (cats[i].hi - lo);
                return i + Math.max(0, Math.min(1, f));
            }
        }
        return cats.length;
    }

    /** Upper PM2.5 concentration of each category (used to shade the chart). */
    public static double[] pm25UpperBounds() {
        return PM25_HI.clone();
    }

    /** Human-readable PM2.5 range for a category, e.g. "9.1 – 35.4". */
    public static String pm25Range(Category c) {
        int i = c.ordinal();
        return (i == PM25_HI.length - 1)
                ? PM25_LO[i] + "+"
                : PM25_LO[i] + " – " + PM25_HI[i];
    }

    /** Human-readable PM10 range for a category, e.g. "55 – 154". */
    public static String pm10Range(Category c) {
        int i = c.ordinal();
        return (i == PM10_HI.length - 1)
                ? (int) PM10_LO[i] + "+"
                : (int) PM10_LO[i] + " – " + (int) PM10_HI[i];
    }
}
