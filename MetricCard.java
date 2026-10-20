import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Big-number card for one pollutant, with an accent strip coloured by its AQI category. */
public class MetricCard extends RoundedPanel {

    private final JLabel value = Theme.label("--", Font.BOLD, 40, Theme.TEXT);
    private final JLabel sub   = Theme.label("No data yet", Font.BOLD, 12, Theme.MUTED);
    private Color accent = Theme.BORDER;

    public MetricCard(String title, String subtitle, Color seriesColor) {
        super(new BorderLayout(0, 2));
        setBorder(new EmptyBorder(14, 22, 14, 16));

        JLabel titleLabel = Theme.label(title, Font.BOLD, 14, Theme.TEXT);
        titleLabel.setIcon(Theme.dot(seriesColor, 10));
        titleLabel.setIconTextGap(8);
        JLabel subtitleLabel = Theme.label(subtitle, Font.PLAIN, 11, Theme.MUTED);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleLabel, BorderLayout.WEST);
        top.add(subtitleLabel, BorderLayout.EAST);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        center.setOpaque(false);
        ((FlowLayout) center.getLayout()).setAlignOnBaseline(true);
        center.add(value);
        center.add(Box.createHorizontalStrut(6));
        center.add(Theme.label(Theme.UNIT, Font.PLAIN, 14, Theme.MUTED));

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(sub, BorderLayout.SOUTH);
    }

    public void update(double v, int aqi, Aqi.Category c) {
        value.setText(String.format("%.1f", v));
        if (c != null) {
            sub.setText("AQI " + aqi + "  ·  " + c.label);
            sub.setForeground(c.ink);
            accent = c.color;
        }
        repaint();
    }

    public void clear() {
        value.setText("--");
        sub.setText("No data yet");
        sub.setForeground(Theme.MUTED);
        accent = Theme.BORDER;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        g2.clip(cardShape());
        g2.setColor(accent);
        g2.fillRect(0, 0, 6, getHeight());
        g2.dispose();
    }
}
