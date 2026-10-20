# Air Quality Monitor

Live PM2.5 / PM10 dashboard for the Nova Fitness **SDS011** sensor (Java Swing + jSerialComm).
Works without hardware using the built-in simulator.

## Run

**BlueJ:** add `jSerialComm-2.11.4.jar` under *Tools → Preferences → Libraries* (or put it in the
project's `+libs` folder), compile, then right-click `Main` → `void main(String[] args)`.

**Command line** (Java 11+):

```
javac -cp jSerialComm-2.11.4.jar *.java
java  -cp jSerialComm-2.11.4.jar:. Main        # macOS / Linux
java  -cp jSerialComm-2.11.4.jar;. Main        # Windows
```

## Using the sensor

1. Plug in the SDS011 USB adapter, set **Source** to *SDS011 sensor*.
2. Pick the port (click **Rescan** if it isn't listed) — `COM3` etc. on Windows, `/dev/ttyUSB0` on Linux.
3. Press **Start** (F5). Linux users may need `sudo usermod -aG dialout $USER` and a re-login.

If the cable is pulled the app keeps retrying and reconnects on its own.

## Files

| File | Role |
|---|---|
| `Main` | Entry point; sets the look and feel and starts the GUI on the Swing thread |
| `MainWindow` | Dashboard window, controls, statistics, CSV export |
| `AcquisitionWorker` | Reads the data source on a background thread |
| `DataProvider`, `SensorProvider`, `SimulatedProvider` | Data sources (real sensor / simulator) |
| `SensorReader` | SDS011 serial protocol: frame sync, checksum, timeouts |
| `Aqi` | US EPA AQI (2024 breakpoints) for PM2.5 and PM10 |
| `AirQualityData`, `RunningStats` | Reading model and min/max/mean |
| `DualDataChart` | Live chart with AQI zones and hover read-out |
| `MetricCard`, `AqiGauge`, `StatusPill`, `FlatButton`, `RoundedPanel`, `Theme` | UI components and styling |

The AQI shown is an indication based on the latest reading; the official AQI uses 24-hour averages.
