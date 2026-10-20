import javax.swing.*;
import java.awt.*;

/** Connection indicator for the dark header: a coloured dot plus a short status word. */
public class StatusPill extends JComponent {

    private String text = "Stopped";
    private Color color = Theme.IDLE;

    public StatusPill() {
        setFont(Theme.font(Font.BOLD, 12));
    }

    public void setStatus(String text, Color color) {
        if (text.equals(this.text) && color.equals(this.color)) return;
        this.text = text;
        this.color = color;
        revalidate();
        repaint();
    }

    public String getStatusText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 38, 28);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        int w = getWidth(), h = getHeight();

        g2.setColor(Theme.alpha(color, 45));
        g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
        g2.setColor(Theme.alpha(color, 140));
        g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

        int d = 8;
        g2.setColor(color);
        g2.fillOval(13, (h - d) / 2, d, d);

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        g2.drawString(text, 28, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
