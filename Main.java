import javax.swing.*;
import java.awt.Color;

public class Main {

    public static void main(String[] args) {
        // smoother text on Linux
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Swing must be created on the event dispatch thread
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            new MainWindow();
        });
    }

    /** Nimbus gives a clean, consistent look on Windows, macOS and Linux. */
    private static void installLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.put("control", Theme.BG);
                    UIManager.put("nimbusBase", new Color(0x334155));
                    UIManager.put("nimbusFocus", new Color(0x93C5FD));
                    UIManager.put("nimbusSelectionBackground", Theme.PRIMARY);
                    UIManager.put("nimbusSelection", Theme.PRIMARY);
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
    }
}
