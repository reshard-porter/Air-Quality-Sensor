import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A white "card" with rounded corners and a hairline border. */
public class RoundedPanel extends JPanel {

    private final int arc;

    public RoundedPanel(LayoutManager layout) {
        this(layout, 16);
    }

    public RoundedPanel(LayoutManager layout, int arc) {
        super(layout);
        this.arc = arc;
        setOpaque(false);
    }

    protected Shape cardShape() {
        return new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, arc, arc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.antialias(g2);
        Shape s = cardShape();
        g2.setColor(Theme.CARD);
        g2.fill(s);
        g2.setColor(Theme.BORDER);
        g2.draw(s);
        g2.dispose();
    }
}
