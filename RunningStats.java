/**
 * Keeps min / max / mean of a stream of values without storing them.
 */
public class RunningStats {
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    private double sum = 0;
    private long count = 0;

    public void add(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return; // ignore bad values
        if (v < min) min = v;
        if (v > max) max = v;
        sum += v;
        count++;
    }

    public double getMin()  { return count == 0 ? Double.NaN : min; }
    public double getMax()  { return count == 0 ? Double.NaN : max; }
    public double getMean() { return count == 0 ? Double.NaN : sum / count; }
    public long getCount()  { return count; }

    public void reset() {
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        sum = 0;
        count = 0;
    }
}
