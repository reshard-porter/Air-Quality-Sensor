import javax.swing.*;
import java.awt.*;

/** Colours, fonts and small drawing helpers shared by the GUI. */
public final class Theme {

    public static final Color BG         = new Color(0xF1F4F8);
    public static final Color CARD       = Color.WHITE;
    public static final Color BORDER     = new Color(0xE2E7EE);
    public static final Color TEXT       = new Color(0x1F2937);
    public static final Color MUTED      = new Color(0x6B7280);
    public static final Color GRID       = new Color(0xEDF0F4);
    public static final Color HEADER     = new Color(0x0F172A);
    public static final Color HEADER_2   = new Color(0x1E293B);
    public static final Color HEADER_SUB = new Color(0x94A3B8);

    public static final Color PRIMARY    = new Color(0x2563EB);
    public static final Color DANGER     = new Color(0xDC2626);
    public static final Color OK         = new Color(0x22C55E);
    public static final Color WARN       = new Color(0xF59E0B);
    public static final Color IDLE       = new Color(0x94A3B8);

    public static final Color PM25       = new Color(0x2563EB);
    public static final Color PM10       = new Color(0x0D9488);

    /** "µg/m³" written with escapes so it survives any source-file encoding. */
    public static final String UNIT = "µg/m³";

    private Theme() {}

    public static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    public static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** A small filled circle, handy as a legend marker on labels. */
    public static Icon dot(final Color c, final int size) {
        return new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                antialias(g2);
                g2.setColor(c);
                g2.fillOval(x, y, size, size);
                g2.dispose();
            }
            public int getIconWidth()  { return size; }
            public int getIconHeight() { return size; }
        };
    }
}
