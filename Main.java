

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of the Operating System Simulator.
 */
public class Main {

    public static void main(String[] args) {
        try {
            // Cross-platform (Metal) look and feel respects our custom colours on every OS
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ex) {
            // The default look and feel is perfectly acceptable if this fails
        }

        // Always create Swing components on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainMenu menu = new MainMenu();
            menu.setVisible(true);
        });
    }
}