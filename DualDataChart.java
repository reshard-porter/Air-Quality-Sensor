import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Live line chart of PM2.5 and PM10 with nice y-axis ticks, time labels,
 * optional PM2.5 AQI zones in the background and a hover read-out.
 */
public class DualDataChart extends JComponent {

    private final List<AirQualityData> points = new ArrayList<>();
    private int maxPoints = 300;
    private boolean showZones = true;
    private int hoverIndex = -1;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    // plot area, recomputed on every paint
    private int pLeft, pTop, pW, pH;

    public DualDataChart() {
        setPreferredSize(new Dimension(640, 320));
        setMinimumSize(new Dimension(320, 180));
        MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e)  { updateHover(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    // ------------------------------------------------------------------ data

    public void add(AirQualityData d) {
        points.add(d);
        while (points.size() > maxPoints) points.remove(0);
        repaint();
    }

    /** Kept for compatibility with the old API. */
    public void add(double pm25, double pm10) {
        add(new AirQualityData(pm25, pm10));
    }

    public void reset() {
        points.clear();
        hoverIndex = -1;
        repaint();
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int n) {
        maxPoints = Math.max(10, n);
        while (points.size() > maxPoints) points.remove(0);
        repaint();
    }

    public void setShowZones(boolean show) {
        showZones = show;
        repaint();
    }

    // ------------------------------------------------------------------ hover

    private void updateHover(int x, int y) {
        if (points.isEmpty() || x < pLeft - 4 || x > pLeft + pW + 4 || y < pTop || y > pTop + pH) {
            setHover(-1);
            return;
        }
        double step = pW / (double) (maxPoints - 1);
        int idx = (int) Math.round((x - pLeft) / step);
        setHover(Math.max(0, Math.min(points.size() - 1, idx)));
    }

    private void setHover(int idx) {
        if (idx != hoverIndex) {
            hoverIndex = idx;
            repaint();
        }
    }

    // ------------------------------------------------------------------ painting

    private double xFor(int i) {
        return pLeft + i * (pW / (double) (maxPoints - 1));
    }

    private double yFor(double v, double yMax) {
        return pTop + pH - (v / yMax) * pH;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.antialias(g);

        int w = getWidth(), h = getHeight();
        pLeft = 46;
        pTop = 30;
        int right = showZones ? 82 : 16;
        int bottom = 26;
        pW = w - pLeft - right;
        pH = h - pTop - bottom;
        if (pW < 60 || pH < 40) { g.dispose(); return; }

        // scale
        double dataMax = 0;
        for (AirQualityData d : points) dataMax = Math.max(dataMax, Math.max(d.pm25, d.pm10));
        double raw = Math.max(20, dataMax * 1.12);
        double step = niceStep(raw / 5);
        double yMax = Math.ceil(raw / step) * step;

        if (showZones) drawZones(g, yMax, right);
        drawGrid(g, yMax, step);
        drawLegend(g, w);
        drawTimeAxis(g);

        if (points.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(Theme.MUTED);
            String msg = "Waiting for data — press Start";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, pLeft + (pW - fm.stringWidth(msg)) / 2, pTop + pH / 2);
            g.dispose();
            return;
        }

        drawSeries(g, yMax, true);   // PM10 underneath
        drawSeries(g, yMax, false);  // PM2.5 on top

        // latest-value markers
        int last = points.size() - 1;
        drawMarker(g, xFor(last), yFor(points.get(last).pm10, yMax), Theme.PM10);
        drawMarker(g, xFor(last), yFor(points.get(last).pm25, yMax), Theme.PM25);

        if (hoverIndex >= 0 && hoverIndex < points.size()) drawHover(g, yMax);

        g.dispose();
    }

    private void drawZones(Graphics2D g, double yMax, int rightGutter) {
        double[] bounds = Aqi.pm25UpperBounds();
        Aqi.Category[] cats = Aqi.Category.values();
        g.setFont(Theme.font(Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();

        double lower = 0;
        for (int i = 0; i < cats.length && lower < yMax; i++) {
            double upper = (i == cats.length - 1) ? yMax : Math.min(bounds[i], yMax);
            int yTop = (int) Math.round(yFor(upper, yMax));
            int yBot = (int) Math.round(yFor(lower, yMax));

            g.setColor(Theme.alpha(cats[i].color, 26));
            g.fillRect(pLeft, yTop, pW, yBot - yTop);

            // boundary line + label in the right-hand gutter
            if (upper < yMax) {
                g.setColor(Theme.alpha(cats[i].color, 140));
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[] {4f, 4f}, 0f));
                g.drawLine(pLeft, yTop, pLeft + pW, yTop);
            }
            if (yBot - yTop >= 13) {
                g.setColor(cats[i].ink);
                int ty = (yTop + yBot) / 2 + fm.getAscent() / 2 - 1;
                g.drawString(cats[i].shortLabel, pLeft + pW + 8, ty);
            }
            lower = upper;
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawGrid(Graphics2D g, double yMax, double step) {
        g.setFont(Theme.font(Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        for (double v = 0; v <= yMax + 1e-9; v += step) {
            int y = (int) Math.round(yFor(v, yMax));
            g.setColor(v == 0 ? Theme.BORDER.darker() : Theme.alpha(Theme.MUTED, 40));
            g.drawLine(pLeft, y, pLeft + pW, y);
            String s = (step < 1) ? String.format("%.1f", v) : String.format("%.0f", v);
            g.setColor(Theme.MUTED);
            g.drawString(s, pLeft - 8 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
        }
    }

    private void drawLegend(Graphics2D g, int w) {
        g.setFont(Theme.font(Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        int y = pTop - 12;

        g.setColor(Theme.MUTED);
        g.drawString(Theme.UNIT, 4, y + 4);

        int x = pLeft;
        x = legendItem(g, x, y, Theme.PM25, "PM2.5", fm);
        legendItem(g, x + 14, y, Theme.PM10, "PM10", fm);

        if (showZones) {
            String note = "Shaded zones: PM2.5 AQI categories";
            g.setColor(Theme.MUTED);
            g.drawString(note, pLeft + pW - fm.stringWidth(note), y + 4);
        }
    }

    private int legendItem(Graphics2D g, int x, int y, Color c, String text, FontMetrics fm) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y - 2, 14, 4, 4, 4));
        g.setColor(Theme.TEXT);
        g.drawString(text, x + 20, y + 4);
        return x + 20 + fm.stringWidth(text);
    }

    private void drawTimeAxis(Graphics2D g) {
        if (points.isEmpty()) return;
        g.setFont(Theme.font(Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        int ticks = Math.max(2, Math.min(6, pW / 110));
        int lastLabelEnd = Integer.MIN_VALUE;
        for (int k = 0; k <= ticks; k++) {
            int idx = (int) Math.round(k * (maxPoints - 1) / (double) ticks);
            if (idx >= points.size()) break;
            int x = (int) Math.round(xFor(idx));
            String s = timeFmt.format(new Date(points.get(idx).timestamp));
            int sw = fm.stringWidth(s);
            int sx = Math.max(pLeft - 10, Math.min(x - sw / 2, pLeft + pW - sw + 10));
            if (sx <= lastLabelEnd + 8) continue;
            g.setColor(Theme.BORDER.darker());
            g.drawLine(x, pTop + pH, x, pTop + pH + 4);
            g.setColor(Theme.MUTED);
            g.drawString(s, sx, pTop + pH + 17);
            lastLabelEnd = sx + sw;
        }
    }

    private void drawSeries(Graphics2D g, double yMax, boolean pm10) {
        int n = points.size();
        Color color = pm10 ? Theme.PM10 : Theme.PM25;
        Path2D line = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double v = pm10 ? points.get(i).pm10 : points.get(i).pm25;
            double x = xFor(i), y = yFor(v, yMax);
            if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
        }

        if (!pm10 && n > 1) {   // soft area fill under PM2.5
            Path2D area = new Path2D.Double(line);
            area.lineTo(xFor(n - 1), pTop + pH);
            area.lineTo(xFor(0), pTop + pH);
            area.closePath();
            g.setPaint(new GradientPaint(0, pTop, Theme.alpha(color, 70), 0, pTop + pH, Theme.alpha(color, 0)));
            g.fill(area);
        }

        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawMarker(Graphics2D g, double x, double y, Color c) {
        double r = 4.5;
        Ellipse2D e = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        g.setColor(Color.WHITE);
        g.fill(e);
        g.setColor(c);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(e);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawHover(Graphics2D g, double yMax) {
        AirQualityData d = points.get(hoverIndex);
        int x = (int) Math.round(xFor(hoverIndex));

        g.setColor(Theme.alpha(Theme.TEXT, 90));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {3f, 3f}, 0f));
        g.drawLine(x, pTop, x, pTop + pH);
        g.setStroke(new BasicStroke(1f));

        drawMarker(g, x, yFor(d.pm10, yMax), Theme.PM10);
        drawMarker(g, x, yFor(d.pm25, yMax), Theme.PM25);

        String[] lines = {
                timeFmt.format(new Date(d.timestamp)),
                String.format("PM2.5   %.1f %s", d.pm25, Theme.UNIT),
                String.format("PM10    %.1f %s", d.pm10, Theme.UNIT)
        };
        Color[] dots = {null, Theme.PM25, Theme.PM10};

        g.setFont(Theme.font(Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        int lh = fm.getHeight() + 2;
        int tw = 0;
        for (String s : lines) tw = Math.max(tw, fm.stringWidth(s));
        int bw = tw + 34, bh = lines.length * lh + 14;
        int bx = x + 12;
        if (bx + bw > pLeft + pW) bx = x - 12 - bw;
        int by = pTop + 8;

        g.setColor(Theme.alpha(Theme.HEADER, 235));
        g.fillRoundRect(bx, by, bw, bh, 10, 10);
        for (int i = 0; i < lines.length; i++) {
            int ty = by + 8 + fm.getAscent() + i * lh;
            if (dots[i] != null) {
                g.setColor(dots[i]);
                g.fillOval(bx + 12, ty - fm.getAscent() / 2 - 3, 8, 8);
            }
            g.setFont(Theme.font(i == 0 ? Font.BOLD : Font.PLAIN, 12));
            g.setColor(i == 0 ? Color.WHITE : new Color(0xE2E8F0));
            g.drawString(lines[i], bx + (dots[i] == null ? 12 : 26), ty);
        }
    }

    /** 1, 2 or 5 times a power of ten, at least x. */
    private static double niceStep(double x) {
        if (x <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(x)));
        double f = x / exp;
        double nf = (f <= 1) ? 1 : (f <= 2) ? 2 : (f <= 5) ? 5 : 10;
        return nf * exp;
    }
}
