import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/** A flat, rounded button that looks the same on every platform / look-and-feel. */
public class FlatButton extends JButton {

    private Color bg;
    private Color fg;
    private Color border;

    public FlatButton(String text, Color bg, Color fg, Color border) {
        super(text);
        setUI(new BasicButtonUI());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Theme.font(Font.BOLD, 13));
        setBorder(new EmptyBorder(8, 16, 8, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setStyle(bg, fg, border);
    }

    public static FlatButton primary(String text) {
        return new FlatButton(text, Theme.PRIMARY, Color.WHITE, null);
    }

    public static FlatButton secondary(String text) {
        return new FlatButton(text, Color.WHITE, Theme.TEXT, Theme.BORDER.darker());
    }

    public void setStyle(Color bg, Color fg, Color border) {
        this.bg = bg;
        this.fg = fg;
        this.border = border;
        setForeground(fg);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        int w = getWidth(), h = getHeight();

        Color fill = bg;
        ButtonModel m = getModel();
        if (!isEnabled()) {
            fill = blend(bg, Theme.BG, 0.55f);
        } else if (m.isPressed()) {
            fill = blend(bg, Color.BLACK, 0.18f);
        } else if (m.isRollover()) {
            fill = blend(bg, bg.equals(Color.WHITE) ? Theme.BG : Color.BLACK, bg.equals(Color.WHITE) ? 0.9f : 0.09f);
        }

        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        if (border != null) {
            g2.setColor(isEnabled() ? border : Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        }

        if (isFocusOwner()) {
            g2.setColor(Theme.alpha(Theme.PRIMARY, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(2, 2, w - 5, h - 5, 8, 8);
        }

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String t = getText();
        int tx = (w - fm.stringWidth(t)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(isEnabled() ? fg : blend(fg, Theme.BG, 0.55f));
        g2.drawString(t, tx, ty);
        g2.dispose();
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }
}
