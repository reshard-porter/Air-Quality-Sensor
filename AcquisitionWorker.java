import javax.swing.SwingUtilities;

/**
 * Polls a DataProvider on a background thread at a fixed interval and delivers
 * results to a Listener on the Swing event thread. This keeps the GUI responsive
 * even when the serial port is slow or unplugged.
 */
public class AcquisitionWorker {

    public interface Listener {
        void onData(AirQualityData data);
        void onError(Exception error);
    }

    private final DataProvider provider;
    private final long intervalMs;
    private final Listener listener;

    private volatile boolean running = false;
    private Thread thread;

    public AcquisitionWorker(DataProvider provider, long intervalMs, Listener listener) {
        this.provider = provider;
        this.intervalMs = intervalMs;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "air-quality-acquisition");
        thread.setDaemon(true);
        thread.start();
    }

    /** Asks the thread to finish. Results that arrive after this call are discarded. */
    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            long started = System.currentTimeMillis();
            boolean failed = false;

            try {
                final AirQualityData data = provider.read();
                if (running && data != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (running) listener.onData(data);
                    });
                }
            } catch (Throwable t) {
                if (!running) break;
                failed = true;
                final Exception e = (t instanceof Exception) ? (Exception) t : new Exception(t.toString(), t);
                SwingUtilities.invokeLater(() -> {
                    if (running) listener.onError(e);
                });
            }

            long wait = intervalMs - (System.currentTimeMillis() - started);
            if (failed) wait = Math.max(wait, 1000);   // don't hammer a broken port
            if (wait < 10) wait = 10;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }
}
