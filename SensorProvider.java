import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Reads from a real SDS011 on a serial port. Opens the port lazily and, if the
 * connection breaks (cable pulled, timeout), closes it so the next read reconnects.
 */
public class SensorProvider implements DataProvider {

    private final String portName;
    private volatile SensorReader reader;
    private volatile boolean closed = false;

    public SensorProvider(String portName) {
        this.portName = portName;
    }

    public String getPortName() {
        return portName;
    }

    @Override
    public synchronized AirQualityData read() throws Exception {
        if (closed) throw new IOException("Sensor connection closed");

        if (reader == null) {
            try {
                reader = new SensorReader(portName);
            } catch (LinkageError e) {
                // jSerialComm jar missing from the classpath or native library failed to load
                throw new SensorReader.OpenException(
                        "Serial library (jSerialComm) could not be loaded: " + e.getMessage());
            }
            if (closed) {          // close() was called while we were opening
                reader.close();
                reader = null;
                throw new IOException("Sensor connection closed");
            }
        }

        try {
            return reader.readData();
        } catch (IOException e) {
            if (!(e instanceof InterruptedIOException)) {
                // drop the connection; it will be reopened on the next read
                SensorReader r = reader;
                reader = null;
                if (r != null) r.close();
            }
            throw e;
        }
    }

    @Override
    public String getName() {
        return "SDS011 on " + portName;
    }

    /** Not synchronized on purpose: closing the port unblocks a read that is in progress. */
    @Override
    public void close() {
        closed = true;
        SensorReader r = reader;
        reader = null;
        if (r != null) r.close();
    }
}
