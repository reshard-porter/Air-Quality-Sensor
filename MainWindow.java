import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main dashboard window.
 *
 * Threading: all sensor I/O happens on a background thread (AcquisitionWorker);
 * every method in this class runs on the Swing event thread.
 */
public class MainWindow extends JFrame implements AcquisitionWorker.Listener {

    private static final String SOURCE_SIM    = "Simulator";
    private static final String SOURCE_SENSOR = "SDS011 sensor";
    private static final String[] INTERVAL_NAMES = {"1 s", "2 s", "5 s", "10 s", "30 s"};
    private static final int[]    INTERVAL_MS    = {1000, 2000, 5000, 10000, 30000};
    private static final int CHART_POINTS = 300;
    private static final int MAX_LOG_ROWS = 200_000;   // ~2.3 days at 1 sample/s
    private static final long TREND_WINDOW_MS = 3 * 60_000;
    private static final double TREND_THRESHOLD = 0.3; // µg/m³ per minute

    // ===== header =====
    private final StatusPill connectionPill = new StatusPill();
    private final JLabel sourceInfo = Theme.label("", Font.PLAIN, 12, Theme.HEADER_SUB);

    // ===== toolbar =====
    private final FlatButton startStopBtn = FlatButton.primary("Start");
    private final FlatButton resetBtn     = FlatButton.secondary("Reset");
    private final FlatButton exportBtn    = FlatButton.secondary("Export CSV…");
    private final FlatButton refreshBtn   = FlatButton.secondary("Rescan");
    private final JComboBox<String> sourceBox   = new JComboBox<>(new String[] {SOURCE_SIM, SOURCE_SENSOR});
    private final JComboBox<String> portBox     = new JComboBox<>();
    private final JComboBox<String> intervalBox = new JComboBox<>(INTERVAL_NAMES);
    private final Map<String, String> portDescriptions = new HashMap<>();
    private boolean updatingPorts = false;

    // ===== dashboard =====
    private final MetricCard pm25Card = new MetricCard("PM2.5", "fine particles", Theme.PM25);
    private final MetricCard pm10Card = new MetricCard("PM10", "coarse particles", Theme.PM10);
    private final AqiGauge gauge = new AqiGauge();
    private final JLabel gaugeCaption = Theme.label(" ", Font.PLAIN, 11, Theme.MUTED);
    private final DualDataChart chart = new DualDataChart();
    private final JCheckBox zonesBox = new JCheckBox("AQI zones", true);

    // ===== statistics =====
    private final JLabel[][] statCells = new JLabel[5][2]; // rows: current, 1-min, session, min, max
    private final JLabel trendValue    = valueLabel();
    private final JLabel samplesValue  = valueLabel();
    private final JLabel durationValue = valueLabel();
    private final JLabel lastValue     = valueLabel();

    // ===== health =====
    private final JLabel healthBadge = new JLabel("No data yet");
    private final JLabel healthText = new JLabel();

    // ===== status bar =====
    private final JLabel statusMsg = Theme.label("Ready.", Font.PLAIN, 12, Theme.MUTED);
    private final JLabel logInfo   = Theme.label("", Font.PLAIN, 12, Theme.MUTED);

    private JMenuItem startStopItem;

    // ===== runtime state =====
    private DataProvider provider;
    private AcquisitionWorker worker;
    private boolean running = false;
    private final RunningStats pm25Stats = new RunningStats();
    private final RunningStats pm10Stats = new RunningStats();
    private final ArrayDeque<AirQualityData> lastMinute = new ArrayDeque<>();
    private final ArrayDeque<AirQualityData> sessionLog = new ArrayDeque<>();
    private long sessionStart = 0;
    private long lastDataTime = 0;
    private int consecutiveErrors = 0;
    private final javax.swing.Timer clock;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm:ss");

    public MainWindow() {
        super("Air Quality Monitor");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
        setJMenuBar(buildMenuBar());

        hookActions();
        refreshPorts();
        intervalBox.setSelectedIndex(0);
        chart.setMaxPoints(CHART_POINTS);

        provider = new SimulatedProvider();
        updateControls();
        clearReadouts();
        setConnection("Stopped", Theme.IDLE);

        clock = new javax.swing.Timer(1000, e -> onClockTick());
        clock.start();

        setSize(1200, 800);
        setMinimumSize(new Dimension(1020, 700));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, Theme.HEADER, getWidth(), 0, Theme.HEADER_2));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Theme.label("Air Quality Monitor", Font.BOLD, 20, Color.WHITE));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Theme.label("Live particulate matter dashboard · SDS011", Font.PLAIN, 12, Theme.HEADER_SUB));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(sourceInfo);
        right.add(connectionPill);
        JPanel rightWrap = new JPanel(new GridBagLayout());   // vertically centres the pill
        rightWrap.setOpaque(false);
        rightWrap.add(right);

        header.add(titles, BorderLayout.WEST);
        header.add(rightWrap, BorderLayout.EAST);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(14, 14));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 14, 10, 14));

        body.add(buildToolbar(), BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(0, 14));
        main.setOpaque(false);
        main.add(buildCards(), BorderLayout.NORTH);
        main.add(buildChartCard(), BorderLayout.CENTER);

        body.add(main, BorderLayout.CENTER);
        body.add(buildSidebar(), BorderLayout.EAST);
        return body;
    }

    private JComponent buildToolbar() {
        RoundedPanel bar = new RoundedPanel(new BorderLayout(), 14);
        bar.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        startStopBtn.setPreferredSize(new Dimension(96, startStopBtn.getPreferredSize().height));
        left.add(startStopBtn);
        left.add(resetBtn);
        left.add(exportBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(fieldLabel("Source"));
        right.add(sourceBox);
        right.add(Box.createHorizontalStrut(6));
        right.add(fieldLabel("Port"));
        portBox.setEditable(true);
        portBox.setPrototypeDisplayValue("/dev/ttyUSB0  ");
        portBox.setToolTipText("Serial port of the SDS011 USB adapter. Type a name or pick one from the list.");
        right.add(portBox);
        right.add(refreshBtn);
        right.add(Box.createHorizontalStrut(6));
        right.add(fieldLabel("Every"));
        intervalBox.setToolTipText("How often a reading is taken");
        right.add(intervalBox);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildCards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 14, 0));
        row.setOpaque(false);
        row.add(pm25Card);
        row.add(pm10Card);

        RoundedPanel gaugeCard = new RoundedPanel(new BorderLayout(0, 0));
        gaugeCard.setBorder(new EmptyBorder(12, 16, 10, 16));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.label("Air Quality Index", Font.BOLD, 14, Theme.TEXT), BorderLayout.WEST);
        JLabel info = Theme.label("US EPA · instant", Font.PLAIN, 11, Theme.MUTED);
        info.setToolTipText("<html>Uses the US EPA AQI breakpoints (2024 revision) applied to the latest reading.<br>"
                + "The official AQI is based on 24-hour averages, so treat this as an indication.</html>");
        top.add(info, BorderLayout.EAST);
        gaugeCard.add(top, BorderLayout.NORTH);
        gaugeCard.add(gauge, BorderLayout.CENTER);
        gaugeCaption.setHorizontalAlignment(SwingConstants.CENTER);
        gaugeCard.add(gaugeCaption, BorderLayout.SOUTH);
        row.add(gaugeCard);

        for (Component c : row.getComponents()) {
            ((JComponent) c).setPreferredSize(new Dimension(260, 168));
        }
        return row;
    }

    private JComponent buildChartCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(12, 16, 10, 16));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titles.setOpaque(false);
        titles.add(Theme.label("Live readings", Font.BOLD, 14, Theme.TEXT));
        titles.add(Theme.label("last " + CHART_POINTS + " samples · hover for details",
                Font.PLAIN, 11, Theme.MUTED));
        top.add(titles, BorderLayout.WEST);

        zonesBox.setOpaque(false);
        zonesBox.setFont(Theme.font(Font.PLAIN, 12));
        zonesBox.setForeground(Theme.TEXT);
        zonesBox.setFocusPainted(false);
        zonesBox.setToolTipText("Shade the chart background with the PM2.5 AQI categories");
        top.add(zonesBox, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(320, 10));

        side.add(buildStatsCard());
        side.add(Box.createVerticalStrut(14));
        side.add(buildHealthCard());
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JComponent buildStatsCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(12, 16, 14, 16));
        card.add(Theme.label("Statistics", Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 0);
        c.anchor = GridBagConstraints.WEST;

        // header row
        c.gridy = 0;
        c.gridx = 0; c.weightx = 1; grid.add(new JLabel(""), c);
        c.weightx = 0; c.anchor = GridBagConstraints.EAST;
        c.gridx = 1; grid.add(columnHeader("PM2.5", Theme.PM25), c);
        c.gridx = 2; grid.add(columnHeader("PM10", Theme.PM10), c);

        String[] rows = {"Current", "1-min average", "Session average", "Minimum", "Maximum"};
        for (int r = 0; r < rows.length; r++) {
            c.gridy = r + 1;
            c.gridx = 0; c.anchor = GridBagConstraints.WEST;
            grid.add(Theme.label(rows[r], Font.PLAIN, 13, Theme.MUTED), c);
            c.anchor = GridBagConstraints.EAST;
            for (int k = 0; k < 2; k++) {
                statCells[r][k] = valueLabel();
                statCells[r][k].setHorizontalAlignment(SwingConstants.RIGHT);
                statCells[r][k].setPreferredSize(new Dimension(70, 18));
                c.gridx = k + 1;
                grid.add(statCells[r][k], c);
            }
        }

        // separator
        c.gridy = rows.length + 1;
        c.gridx = 0; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 8, 0);
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        grid.add(sep, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.insets = new Insets(3, 0, 3, 0);

        String[] keys = {"Trend (PM2.5)", "Samples", "Running time", "Last reading"};
        JLabel[] vals = {trendValue, samplesValue, durationValue, lastValue};
        for (int i = 0; i < keys.length; i++) {
            c.gridy = rows.length + 2 + i;
            c.gridx = 0; c.anchor = GridBagConstraints.WEST;
            grid.add(Theme.label(keys[i], Font.PLAIN, 13, Theme.MUTED), c);
            c.gridx = 1; c.gridwidth = 2; c.anchor = GridBagConstraints.EAST;
            grid.add(vals[i], c);
            c.gridwidth = 1;
        }

        card.add(grid, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private JComponent buildHealthCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(12, 16, 14, 16));
        card.add(Theme.label("Health guidance", Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);

        healthBadge.setOpaque(true);
        healthBadge.setFont(Theme.font(Font.BOLD, 13));
        healthBadge.setBorder(new EmptyBorder(5, 10, 5, 10));
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.add(healthBadge);

        healthText.setFont(Theme.font(Font.PLAIN, 13));
        healthText.setForeground(Theme.TEXT);
        healthText.setVerticalAlignment(SwingConstants.TOP);

        JLabel note = new JLabel("<html>Indicative only: based on the latest reading, while the official "
                + "AQI uses 24-hour averages. See Help → AQI scale.</html>");
        note.setFont(Theme.font(Font.PLAIN, 11));
        note.setForeground(Theme.MUTED);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(badgeRow, BorderLayout.NORTH);
        center.add(healthText, BorderLayout.CENTER);
        center.add(note, BorderLayout.SOUTH);

        card.add(center, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        return card;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG);
        bar.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(5, 16, 6, 16)));
        bar.add(statusMsg, BorderLayout.CENTER);
        bar.add(logInfo, BorderLayout.EAST);
        return bar;
    }

    private JMenuBar buildMenuBar() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem export = new JMenuItem("Export CSV…");
        export.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask));
        export.addActionListener(e -> exportCSV());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> shutdown());
        file.add(export);
        file.addSeparator();
        file.add(exit);

        JMenu acq = new JMenu("Acquisition");
        startStopItem = new JMenuItem("Start");
        startStopItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        startStopItem.addActionListener(e -> toggleRunning());
        JMenuItem reset = new JMenuItem("Reset session");
        reset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask));
        reset.addActionListener(e -> confirmReset());
        JMenuItem rescan = new JMenuItem("Rescan serial ports");
        rescan.addActionListener(e -> refreshPorts());
        acq.add(startStopItem);
        acq.add(reset);
        acq.addSeparator();
        acq.add(rescan);

        JMenu help = new JMenu("Help");
        JMenuItem scale = new JMenuItem("AQI scale…");
        scale.addActionListener(e -> showAqiScale());
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> showAbout());
        help.add(scale);
        help.add(about);

        mb.add(file);
        mb.add(acq);
        mb.add(help);
        return mb;
    }

    private static JLabel fieldLabel(String text) {
        return Theme.label(text, Font.PLAIN, 12, Theme.MUTED);
    }

    private static JLabel valueLabel() {
        return Theme.label("--", Font.BOLD, 13, Theme.TEXT);
    }

    private static JLabel columnHeader(String text, Color c) {
        JLabel l = Theme.label(text, Font.BOLD, 12, c);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    private void hookActions() {
        startStopBtn.addActionListener(e -> toggleRunning());
        resetBtn.addActionListener(e -> confirmReset());
        exportBtn.addActionListener(e -> exportCSV());
        refreshBtn.addActionListener(e -> refreshPorts());
        sourceBox.addActionListener(e -> switchSource());
        portBox.addActionListener(e -> onPortChanged());
        intervalBox.addActionListener(e -> onIntervalChanged());
        zonesBox.addActionListener(e -> chart.setShowZones(zonesBox.isSelected()));

        portBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                String desc = value == null ? null : portDescriptions.get(value.toString());
                if (index >= 0 && desc != null && !desc.isEmpty()) {
                    setText("<html>" + escape(value.toString())
                            + " <font color='#6B7280'>— " + escape(desc) + "</font></html>");
                }
                return this;
            }
        });
    }

    private void toggleRunning() {
        if (running) stop(); else start();
    }

    private void start() {
        if (running) return;
        if (provider == null) provider = createProvider();
        if (provider == null) return;

        running = true;
        consecutiveErrors = 0;
        if (sessionStart == 0) sessionStart = System.currentTimeMillis();
        startWorker();
        setConnection(provider instanceof SensorProvider ? "Connecting…" : "Live",
                provider instanceof SensorProvider ? Theme.WARN : Theme.OK);
        setStatus("Reading from " + provider.getName() + " every " + intervalBox.getSelectedItem() + ".");
        updateControls();
    }

    private void stop() {
        if (!running) return;
        running = false;
        stopWorker();
        if (provider instanceof SensorProvider) {   // release the serial port while stopped
            provider.close();
            provider = null;
        }
        setConnection("Stopped", Theme.IDLE);
        setStatus("Stopped. " + sessionLog.size() + " readings in this session.");
        updateControls();
    }

    private void startWorker() {
        stopWorker();
        worker = new AcquisitionWorker(provider, currentIntervalMs(), this);
        worker.start();
    }

    private void stopWorker() {
        if (worker != null) {
            worker.stop();
            worker = null;
        }
    }

    private void closeProvider() {
        if (provider != null) {
            provider.close();
            provider = null;
        }
    }

    private DataProvider createProvider() {
        if (SOURCE_SIM.equals(sourceBox.getSelectedItem())) {
            return new SimulatedProvider();
        }
        String port = getPortText();
        if (port.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Choose or type the serial port of your SDS011 first\n(e.g. COM3 on Windows, /dev/ttyUSB0 on Linux).",
                    "No port selected", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return new SensorProvider(port);
    }

    private void switchSource() {
        boolean wasRunning = running;
        stopWorker();
        closeProvider();
        provider = createProvider();
        updateControls();

        if (wasRunning) {
            if (provider == null) {
                running = false;
                setConnection("Stopped", Theme.IDLE);
                updateControls();
            } else {
                running = false;   // let start() do the bookkeeping
                start();
            }
        } else if (provider != null) {
            setStatus("Source set to " + provider.getName() + ". Press Start to begin.");
        }
    }

    private void onPortChanged() {
        if (updatingPorts || !SOURCE_SENSOR.equals(sourceBox.getSelectedItem())) return;
        String port = getPortText();
        if (provider instanceof SensorProvider && ((SensorProvider) provider).getPortName().equals(port)) return;
        if (provider == null && !running) {
            updateControls();
            return;
        }
        switchSource();
    }

    private void onIntervalChanged() {
        if (running) {
            startWorker();
            setStatus("Sampling every " + intervalBox.getSelectedItem() + ".");
        }
    }

    private int currentIntervalMs() {
        int i = intervalBox.getSelectedIndex();
        return INTERVAL_MS[i < 0 ? 0 : i];
    }

    private void confirmReset() {
        if (!sessionLog.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Clear all " + sessionLog.size() + " readings from this session?\n"
                            + "Export them first if you want to keep them.",
                    "Reset session", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;
        }
        reset();
    }

    private void reset() {
        sessionLog.clear();
        lastMinute.clear();
        pm25Stats.reset();
        pm10Stats.reset();
        chart.reset();
        sessionStart = running ? System.currentTimeMillis() : 0;
        lastDataTime = 0;
        clearReadouts();
        setStatus("Session cleared.");
    }

    private void refreshPorts() {
        String current = getPortText();
        List<String[]> ports;
        try {
            ports = SensorReader.listPorts();
        } catch (Throwable t) {          // jSerialComm missing from the classpath
            ports = new ArrayList<>();
            setStatus("Serial library not available: " + t);
        }

        updatingPorts = true;
        try {
            portBox.removeAllItems();
            portDescriptions.clear();
            String preferred = null;
            for (String[] p : ports) {
                portBox.addItem(p[0]);
                portDescriptions.put(p[0], p[1]);
                String d = (p[1] == null ? "" : p[1]).toUpperCase(Locale.ROOT);
                if (preferred == null && (d.contains("CH340") || d.contains("CH341") || d.contains("USB")
                        || p[0].contains("ttyUSB") || p[0].contains("usbserial"))) {
                    preferred = p[0];
                }
            }

            String pick;
            if (!current.isEmpty()) pick = current;
            else if (preferred != null) pick = preferred;
            else if (!ports.isEmpty()) pick = ports.get(0)[0];
            else pick = System.getProperty("os.name", "").toLowerCase().contains("win") ? "COM3" : "/dev/ttyUSB0";
            portBox.setSelectedItem(pick);
        } finally {
            updatingPorts = false;
        }

        if (ports.isEmpty()) {
            setStatus("No serial ports found. Plug in the sensor and click Rescan, or type the port name.");
        } else {
            setStatus("Found " + ports.size() + " serial port" + (ports.size() == 1 ? "" : "s") + ".");
        }
    }

    private String getPortText() {
        Object o = portBox.isEditable() ? portBox.getEditor().getItem() : portBox.getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    private void updateControls() {
        boolean sensor = SOURCE_SENSOR.equals(sourceBox.getSelectedItem());
        portBox.setEnabled(sensor);
        refreshBtn.setEnabled(sensor);

        if (running) {
            startStopBtn.setText("Stop");
            startStopBtn.setStyle(Theme.DANGER, Color.WHITE, null);
        } else {
            startStopBtn.setText("Start");
            startStopBtn.setStyle(Theme.PRIMARY, Color.WHITE, null);
        }
        if (startStopItem != null) startStopItem.setText(running ? "Stop" : "Start");

        String name = provider != null ? provider.getName()
                : sensor ? "SDS011 on " + getPortText() : SOURCE_SIM;
        sourceInfo.setText("Source: " + name);
    }

    // =====================================================================
    // Data handling (called on the event thread by AcquisitionWorker)
    // =====================================================================

    @Override
    public void onData(AirQualityData d) {
        if (!running) return;
        if (consecutiveErrors > 0) setStatus("Connection restored.");
        consecutiveErrors = 0;
        lastDataTime = d.timestamp;

        sessionLog.addLast(d);
        if (sessionLog.size() > MAX_LOG_ROWS) sessionLog.removeFirst();

        lastMinute.addLast(d);
        while (!lastMinute.isEmpty() && d.timestamp - lastMinute.peekFirst().timestamp > 60_000) {
            lastMinute.removeFirst();
        }

        pm25Stats.add(d.pm25);
        pm10Stats.add(d.pm10);
        chart.add(d);

        updateReadouts(d);
        setConnection("Live", Theme.OK);
    }

    @Override
    public void onError(Exception e) {
        if (!running) return;
        consecutiveErrors++;

        if (e instanceof SensorReader.OpenException) {
            stop();
            setConnection("Error", Theme.DANGER);
            setStatus(e.getMessage());
            showSensorHelp(e.getMessage());
            return;
        }

        setConnection("No data", Theme.DANGER);
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        setStatus("Problem reading sensor (attempt " + consecutiveErrors + "): " + msg + " — retrying…");
    }

    private void updateReadouts(AirQualityData d) {
        int aqi25 = Aqi.pm25(d.pm25);
        int aqi10 = Aqi.pm10(d.pm10);
        Aqi.Category c25 = Aqi.category(aqi25);
        Aqi.Category c10 = Aqi.category(aqi10);
        int aqi = Math.max(aqi25, aqi10);
        Aqi.Category cat = Aqi.category(aqi);

        pm25Card.update(d.pm25, aqi25, c25);
        pm10Card.update(d.pm10, aqi10, c10);
        gauge.setAqi(aqi, cat);
        gaugeCaption.setText("Driven by " + (aqi25 >= aqi10 ? "PM2.5" : "PM10")
                + "  ·  PM2.5 " + aqi25 + " / PM10 " + aqi10);

        // statistics table
        setPair(0, d.pm25, d.pm10);
        setPair(1, average(lastMinute, true), average(lastMinute, false));
        setPair(2, pm25Stats.getMean(), pm10Stats.getMean());
        setPair(3, pm25Stats.getMin(), pm10Stats.getMin());
        setPair(4, pm25Stats.getMax(), pm10Stats.getMax());

        double slope = pm25TrendPerMinute();
        if (Double.isNaN(slope)) {
            trendValue.setText("Collecting…");
            trendValue.setForeground(Theme.MUTED);
        } else if (slope > TREND_THRESHOLD) {
            trendValue.setText(String.format("↑ Rising  (+%.1f/min)", slope));
            trendValue.setForeground(Aqi.Category.SENSITIVE.ink);
        } else if (slope < -TREND_THRESHOLD) {
            trendValue.setText(String.format("↓ Falling  (%.1f/min)", slope));
            trendValue.setForeground(Aqi.Category.GOOD.ink);
        } else {
            trendValue.setText("→ Stable");
            trendValue.setForeground(Theme.TEXT);
        }
        samplesValue.setText(String.format("%,d", pm25Stats.getCount()));

        // health guidance
        healthBadge.setText(cat.label);
        healthBadge.setBackground(cat.color);
        healthBadge.setForeground(cat.textOn());
        setHealthText(cat.advice);

        logInfo.setText(String.format("%,d rows logged", sessionLog.size()));
        onClockTick();
    }

    private void setHealthText(String text) {
        healthText.setText("<html><body style='width:220px'>" + escape(text) + "</body></html>");
    }

    private void setPair(int row, double a, double b) {
        statCells[row][0].setText(fmt(a));
        statCells[row][1].setText(fmt(b));
    }

    private void clearReadouts() {
        pm25Card.clear();
        pm10Card.clear();
        gauge.clear();
        gaugeCaption.setText(" ");
        for (JLabel[] row : statCells) for (JLabel l : row) l.setText("--");
        trendValue.setText("--");
        trendValue.setForeground(Theme.TEXT);
        samplesValue.setText("0");
        durationValue.setText("--");
        lastValue.setText("--");
        healthBadge.setText("No data yet");
        healthBadge.setBackground(Theme.BG);
        healthBadge.setForeground(Theme.MUTED);
        setHealthText("Start a session to see guidance for the current air quality.");
        logInfo.setText("0 rows logged");
    }

    /** Runs once a second: running time, "x s ago", and a stale-data warning. */
    private void onClockTick() {
        long now = System.currentTimeMillis();
        if (sessionStart > 0) {
            long end = running ? now : (lastDataTime > 0 ? lastDataTime : sessionStart);
            durationValue.setText(formatDuration(end - sessionStart));
        }
        if (lastDataTime > 0) {
            long ago = Math.max(0, (now - lastDataTime) / 1000);
            lastValue.setText(clockFmt.format(new Date(lastDataTime)) + (ago >= 2 ? "  (" + ago + " s ago)" : ""));
        }
        if (running && consecutiveErrors == 0 && lastDataTime > 0
                && now - lastDataTime > Math.max(3L * currentIntervalMs(), 6000)) {
            setConnection("Waiting…", Theme.WARN);
        }
    }

    // =====================================================================
    // Analysis helpers
    // =====================================================================

    private static double average(ArrayDeque<AirQualityData> list, boolean pm25) {
        if (list.isEmpty()) return Double.NaN;
        double sum = 0;
        for (AirQualityData d : list) sum += pm25 ? d.pm25 : d.pm10;
        return sum / list.size();
    }

    /** Least-squares slope of PM2.5 over the last few minutes, in µg/m³ per minute. */
    private double pm25TrendPerMinute() {
        if (sessionLog.size() < 5) return Double.NaN;
        long tLast = sessionLog.peekLast().timestamp;

        List<double[]> pts = new ArrayList<>();
        Iterator<AirQualityData> it = sessionLog.descendingIterator();
        while (it.hasNext() && pts.size() < 600) {
            AirQualityData d = it.next();
            if (tLast - d.timestamp > TREND_WINDOW_MS) break;
            pts.add(new double[] {(d.timestamp - tLast) / 60000.0, d.pm25});
        }
        if (pts.size() < 5) return Double.NaN;

        double mx = 0, my = 0;
        for (double[] p : pts) { mx += p[0]; my += p[1]; }
        mx /= pts.size();
        my /= pts.size();
        double sxx = 0, sxy = 0;
        for (double[] p : pts) {
            sxx += (p[0] - mx) * (p[0] - mx);
            sxy += (p[0] - mx) * (p[1] - my);
        }
        return sxx == 0 ? Double.NaN : sxy / sxx;
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "--" : String.format("%.1f", v);
    }

    private static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    // =====================================================================
    // Export
    // =====================================================================

    private void exportCSV() {
        if (sessionLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is no data to export yet.\nPress Start to record some readings.",
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String name = "air_quality_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export readings");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File(name));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists()) {
            int ok = JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
                    "Export CSV", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)))) {
            out.println("time,epoch_ms,pm25_ugm3,pm10_ugm3,aqi_pm25,aqi_pm10,aqi,category");
            for (AirQualityData d : sessionLog) {
                int a25 = Aqi.pm25(d.pm25), a10 = Aqi.pm10(d.pm10), a = Math.max(a25, a10);
                out.println(String.format(Locale.ROOT, "%s,%d,%.1f,%.1f,%d,%d,%d,%s",
                        iso.format(new Date(d.timestamp)), d.timestamp, d.pm25, d.pm10,
                        a25, a10, a, Aqi.category(a).label));
            }
            if (out.checkError()) throw new IOException("write error");
            setStatus("Exported " + sessionLog.size() + " readings to " + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "Exported " + sessionLog.size() + " readings to:\n" + file.getAbsolutePath(),
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export CSV", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================================
    // Dialogs & misc
    // =====================================================================

    private void showSensorHelp(String message) {
        JOptionPane.showMessageDialog(this,
                "<html><body style='width:380px'>"
                        + "<b>" + escape(message) + "</b><br><br>"
                        + "Things to check:<ul>"
                        + "<li>The SDS011's USB adapter is plugged in (click <i>Rescan</i> to list ports).</li>"
                        + "<li>The right port is selected: COM3, COM4… on Windows; /dev/ttyUSB0 on Linux; "
                        + "/dev/tty.usbserial-… on macOS.</li>"
                        + "<li>No other program (e.g. the Arduino Serial Monitor) is using the port.</li>"
                        + "<li>Linux only: your user is in the <tt>dialout</tt> group "
                        + "(<tt>sudo usermod -aG dialout $USER</tt>, then log out and back in).</li>"
                        + "<li>The jSerialComm jar is on the classpath (BlueJ: Tools → Preferences → Libraries).</li>"
                        + "</ul>You can switch Source to <i>Simulator</i> to keep using the app.</body></html>",
                "Sensor not available", JOptionPane.ERROR_MESSAGE);
    }

    private void showAqiScale() {
        StringBuilder sb = new StringBuilder("<html><table cellpadding='4'>"
                + "<tr><th align='left'>AQI</th><th align='left'>Category</th>"
                + "<th align='left'>PM2.5 (" + Theme.UNIT + ")</th><th align='left'>PM10 (" + Theme.UNIT + ")</th></tr>");
        for (Aqi.Category c : Aqi.Category.values()) {
            String hex = String.format("#%06X", c.color.getRGB() & 0xFFFFFF);
            sb.append("<tr><td>").append(c.lo).append("–").append(c.hi).append("</td>")
              .append("<td><font color='").append(hex).append("'>■</font> ").append(c.label).append("</td>")
              .append("<td>").append(Aqi.pm25Range(c)).append("</td>")
              .append("<td>").append(Aqi.pm10Range(c)).append("</td></tr>");
        }
        sb.append("</table><br><small>US EPA breakpoints (2024 revision). The official AQI uses 24-hour averages;<br>"
                + "this app applies the scale to live readings as an indication.</small></html>");
        JOptionPane.showMessageDialog(this, sb.toString(), "AQI scale", JOptionPane.PLAIN_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><b>Air Quality Monitor</b><br>Live PM2.5 / PM10 dashboard for the Nova SDS011 sensor.<br><br>"
                        + "Serial communication: jSerialComm<br>"
                        + "Shortcuts: F5 start/stop · Ctrl+E export · Ctrl+R reset</html>",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setConnection(String text, Color color) {
        connectionPill.setStatus(text, color);
    }

    private void setStatus(String text) {
        statusMsg.setText(text);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void shutdown() {
        running = false;
        stopWorker();
        closeProvider();
        clock.stop();
        dispose();
        System.exit(0);
    }
}
