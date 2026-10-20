import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/** Semi-circular AQI gauge: one coloured segment per category and a marker at the current value. */
public class AqiGauge extends JComponent {

    private int aqi = -1;
    private Aqi.Category category;

    public AqiGauge() {
        setPreferredSize(new Dimension(230, 128));
        setMinimumSize(new Dimension(160, 110));
    }

    public void setAqi(int aqi, Aqi.Category category) {
        this.aqi = aqi;
        this.category = category;
        repaint();
    }

    public void clear() {
        setAqi(-1, null);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);

        int w = getWidth(), h = getHeight();
        float thick = 14f;
        double r = Math.min(w / 2.0 - thick - 8, h - thick - 40);
        if (r < 30) { g.dispose(); return; }
        double cx = w / 2.0;
        double cy = thick / 2.0 + 6 + r;

        // coloured segments
        Aqi.Category[] cats = Aqi.Category.values();
        g.setStroke(new BasicStroke(thick, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        for (int i = 0; i < cats.length; i++) {
            Color c = cats[i].color;
            if (aqi < 0) c = Theme.alpha(c, 110);
            else if (cats[i] != category) c = Theme.alpha(c, 60);
            g.setColor(c);
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 180 - i * 30 - 1, -28, Arc2D.OPEN));
        }

        // marker
        if (aqi >= 0) {
            double ang = Math.toRadians(180 - Aqi.gaugePosition(aqi) * 30);
            double mx = cx + r * Math.cos(ang);
            double my = cy - r * Math.sin(ang);
            double mr = thick / 2.0 + 3;
            Ellipse2D dot = new Ellipse2D.Double(mx - mr, my - mr, 2 * mr, 2 * mr);
            g.setColor(Color.WHITE);
            g.fill(dot);
            g.setStroke(new BasicStroke(3f));
            g.setColor(Theme.TEXT);
            g.draw(dot);
        }

        // scale ends
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.setColor(Theme.MUTED);
        FontMetrics small = g.getFontMetrics();
        g.drawString("0", (int) (cx - r - small.stringWidth("0") / 2.0), (int) (cy + 15));
        g.drawString("500", (int) (cx + r - small.stringWidth("500") / 2.0), (int) (cy + 15));

        // value
        String num = aqi < 0 ? "--" : String.valueOf(aqi);
        g.setFont(Theme.font(Font.BOLD, 34));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        g.drawString(num, (int) (cx - fm.stringWidth(num) / 2.0), (int) (cy - 4));

        g.setFont(Theme.font(Font.PLAIN, 11));
        fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString("AQI", (int) (cx - fm.stringWidth("AQI") / 2.0), (int) (cy - 40));

        // category name
        String label = category == null ? "No data yet" : category.label;
        g.setFont(Theme.font(Font.BOLD, 13));
        fm = g.getFontMetrics();
        g.setColor(category == null ? Theme.MUTED : category.ink);
        g.drawString(label, (int) (cx - fm.stringWidth(label) / 2.0), (int) (cy + 24));

        g.dispose();
    }
}
