/**
 * One reading from the sensor (or simulator).
 * Immutable, so it can safely be handed from the background reader thread to the GUI thread.
 */
public final class AirQualityData {
    /** PM2.5 concentration in micrograms per cubic metre. */
    public final double pm25;
    /** PM10 concentration in micrograms per cubic metre. */
    public final double pm10;
    /** Time of the reading, milliseconds since the epoch. */
    public final long timestamp;

    public AirQualityData(double pm25, double pm10) {
        this(pm25, pm10, System.currentTimeMillis());
    }

    public AirQualityData(double pm25, double pm10, long timestamp) {
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AirQualityData[pm25=" + pm25 + ", pm10=" + pm10 + ", t=" + timestamp + "]";
    }
}
