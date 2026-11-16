import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;

/** Quick launcher: opens only the CPU Scheduling panel so you can test Part 2. */
public class CPUSchedulingDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("CPU Scheduling - Test");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setContentPane(new CPUSchedulingPanel());
                frame.setMinimumSize(new Dimension(1100, 650));
                frame.setSize(1200, 700);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}