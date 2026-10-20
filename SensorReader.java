import com.fazecast.jSerialComm.SerialPort;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads measurement frames from a Nova Fitness SDS011 particulate sensor.
 *
 * Frame format (10 bytes, sent once per second in the sensor's default "active" mode):
 * <pre>
 *  AA C0 PM25_lo PM25_hi PM10_lo PM10_hi ID1 ID2 CHECKSUM AB
 *  checksum = (sum of bytes 2..7) & 0xFF,  values are tenths of a µg/m³
 * </pre>
 */
public class SensorReader implements AutoCloseable {

    /** Thrown when the serial port itself can't be opened (wrong name, in use, no permission...). */
    public static class OpenException extends IOException {
        public OpenException(String message) { super(message); }
    }

    private static final int FRAME_LEN = 10;
    private static final int HEAD = 0xAA;
    private static final int CMD  = 0xC0;
    private static final int TAIL = 0xAB;

    private final SerialPort port;   // null when reading from a plain stream (tests)
    private final InputStream in;
    private final String portName;
    private long readTimeoutMs = 3000;
    private int badFrames = 0;

    public SensorReader(String portName) throws OpenException {
        this.portName = portName;
        SerialPort p;
        try {
            p = SerialPort.getCommPort(portName);
        } catch (RuntimeException e) {
            throw new OpenException("Unknown serial port \"" + portName + "\"");
        }
        p.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        // Short blocking reads so we can notice timeouts / stop requests quickly.
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 500, 0);
        if (!p.openPort()) {
            throw new OpenException("Could not open serial port \"" + portName
                    + "\" (not connected, in use by another program, or no permission)");
        }
        this.port = p;
        this.in = p.getInputStream();
    }

    /** Reads frames from any stream. Package-private: used for testing without hardware. */
    SensorReader(InputStream in) {
        this.port = null;
        this.in = in;
        this.portName = "(stream)";
    }

    public void setReadTimeoutMs(long ms) { this.readTimeoutMs = ms; }

    /** Number of frames rejected because of a bad checksum or tail byte. */
    public int getBadFrameCount() { return badFrames; }

    public String getPortName() { return portName; }

    /**
     * Returns the next valid reading. Blocks for at most the read timeout (default 3 s)
     * and throws an IOException if no valid frame arrives in that time.
     */
    public synchronized AirQualityData readData() throws IOException {
        discardStaleBytes();

        long deadline = System.currentTimeMillis() + readTimeoutMs;
        int[] f = new int[FRAME_LEN];
        int pos = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Reading interrupted");
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("No data from sensor on " + portName
                        + " (timed out). Check the port and that the sensor is powered.");
            }

            int b;
            try {
                b = in.read();
            } catch (InterruptedIOException serialTimeout) {
                continue; // jSerialComm read timeout: just loop and check the deadline
            }

            if (b < 0) {
                if (port == null) throw new EOFException("End of sensor stream");
                if (!port.isOpen()) throw new IOException("Serial port " + portName + " was closed");
                sleepQuietly(20);
                continue;
            }

            // --- small state machine that re-synchronises on the AA C0 header ---
            if (pos == 0) {
                if (b == HEAD) f[pos++] = b;
                continue;
            }
            if (pos == 1) {
                if (b == CMD) f[pos++] = b;
                else if (b != HEAD) pos = 0;   // AA AA C0 ... keeps the second AA as header
                continue;
            }

            f[pos++] = b;
            if (pos < FRAME_LEN) continue;
            pos = 0;

            if (f[9] != TAIL || checksum(f) != f[8]) {
                badFrames++;
                continue;
            }

            int pm25raw = (f[3] << 8) | f[2];
            int pm10raw = (f[5] << 8) | f[4];
            return new AirQualityData(pm25raw / 10.0, pm10raw / 10.0);
        }
    }

    private static int checksum(int[] f) {
        int sum = 0;
        for (int i = 2; i <= 7; i++) sum += f[i];
        return sum & 0xFF;
    }

    /**
     * The sensor sends a frame every second. When we sample less often, old frames pile up
     * in the serial buffer; drop them so the reading we return is (nearly) fresh.
     */
    private void discardStaleBytes() {
        if (port == null) return;
        try {
            int avail = in.available();
            int keep = 2 * FRAME_LEN;
            if (avail > keep) in.skip(avail - keep);
        } catch (IOException ignored) {
            // not fatal; we'll just read slightly older data
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        try { in.close(); } catch (Exception ignored) {}
        if (port != null) port.closePort();
    }

    /**
     * Lists serial ports as {path, description} pairs. Never throws: returns an empty list
     * if the native serial library can't be loaded on this machine.
     */
    public static List<String[]> listPorts() {
        List<String[]> result = new ArrayList<>();
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            for (SerialPort p : SerialPort.getCommPorts()) {
                String path = windows ? p.getSystemPortName() : p.getSystemPortPath();
                result.add(new String[] {path, p.getDescriptivePortName()});
            }
        } catch (Throwable ignored) {
            // jSerialComm missing or native library not available
        }
        return result;
    }
}
