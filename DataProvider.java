/**
 * A source of air-quality readings (real sensor or simulator).
 * read() is called from a background thread, never from the Swing event thread.
 */
public interface DataProvider extends AutoCloseable {

    /** Blocks until a reading is available (or throws). */
    AirQualityData read() throws Exception;

    /** Short name shown in the GUI. */
    String getName();

    /** Releases any hardware resources. */
    @Override
    default void close() {}
}
