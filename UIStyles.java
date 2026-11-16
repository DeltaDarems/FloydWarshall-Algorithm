
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Central place for colours, fonts, widget factories, dialogs and
 * input-parsing helpers so every panel looks and behaves consistently.
 */
public final class UIStyles {

    // ---------- Colours ----------
    public static final Color BACKGROUND     = new Color(0xF4F6FA);
    public static final Color PANEL_BG       = Color.WHITE;
    public static final Color PRIMARY        = new Color(0x2F5DA8);
    public static final Color PRIMARY_DARK   = new Color(0x1F3F73);
    public static final Color SECONDARY      = new Color(0x6B7A90);
    public static final Color SECONDARY_DARK = new Color(0x4F5C6E);
    public static final Color DANGER         = new Color(0xC0392B);
    public static final Color DANGER_DARK    = new Color(0x962D22);
    public static final Color SUCCESS        = new Color(0x2E8B57);
    public static final Color TEXT_DARK      = new Color(0x222B38);
    public static final Color TEXT_MUTED     = new Color(0x6B7585);
    public static final Color BORDER         = new Color(0xD0D6E0);
    public static final Color STRIPE         = new Color(0xEEF2F9);
    public static final Color SELECTION      = new Color(0xBFD3F2);

    // ---------- Fonts ----------
    public static final Font TITLE_FONT   = new Font("SansSerif", Font.BOLD, 28);
    public static final Font HEADING_FONT = new Font("SansSerif", Font.BOLD, 16);
    public static final Font BODY_FONT    = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font SMALL_FONT   = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font BUTTON_FONT  = new Font("SansSerif", Font.BOLD, 13);
    public static final Font MONO_FONT    = new Font("Monospaced", Font.PLAIN, 13);

    private UIStyles() {
        // Utility class - no instances
    }

    // ---------- Buttons ----------

    public static JButton createButton(String text) {
        return buildButton(text, PRIMARY, PRIMARY_DARK);
    }

    public static JButton createSecondaryButton(String text) {
        return buildButton(text, SECONDARY, SECONDARY_DARK);
    }

    public static JButton createDangerButton(String text) {
        return buildButton(text, DANGER, DANGER_DARK);
    }

    private static JButton buildButton(String text, final Color normal, final Color hover) {
        final JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setForeground(Color.WHITE);
        button.setBackground(normal);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(normal);
            }
        });
        return button;
    }

    // ---------- Labels ----------

    public static JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE_FONT);
        label.setForeground(Color.WHITE);
        return label;
    }

    public static JLabel createHeadingLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(HEADING_FONT);
        label.setForeground(PRIMARY_DARK);
        return label;
    }

    public static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BODY_FONT);
        label.setForeground(TEXT_DARK);
        return label;
    }

    public static JLabel createMutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    // ---------- Input widgets ----------

    public static JTextField createTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(BODY_FONT);
        field.setForeground(TEXT_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return field;
    }

    /** Read-only monospaced text area for step-by-step results. */
    public static JTextArea createOutputArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(MONO_FONT);
        area.setForeground(TEXT_DARK);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }

    // ---------- Containers ----------

    /** White panel with a titled border, used to group related controls. */
    public static JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(BORDER),
                        title,
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        HEADING_FONT,
                        PRIMARY_DARK),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        return panel;
    }

    public static JScrollPane createScrollPane(Component view) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(PANEL_BG);
        return scrollPane;
    }

    // ---------- Tables ----------

    /** Applies the shared look (header colours, striped rows, centred cells) to a table. */
    public static void styleTable(JTable table) {
        table.setFont(BODY_FONT);
        table.setForeground(TEXT_DARK);
        table.setRowHeight(26);
        table.setGridColor(BORDER);
        table.setShowGrid(true);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT_DARK);
        table.setFillsViewportHeight(true);

        StripedRenderer renderer = new StripedRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Number.class, renderer);

        JTableHeader header = table.getTableHeader();
        header.setFont(BUTTON_FONT);
        header.setBackground(PRIMARY);
        header.setForeground(Color.WHITE);
        header.setReorderingAllowed(false);
    }

    private static class StripedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : STRIPE);
                c.setForeground(TEXT_DARK);
            }
            return c;
        }
    }

    // ---------- Dialogs ----------

    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Input Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void showWarning(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Input parsing / validation ----------

    /**
     * Parses a whole number from user text.
     *
     * @param text      raw text from a text field
     * @param fieldName name shown in the error message
     * @param min       smallest accepted value
     * @throws IllegalArgumentException with a user-friendly message when invalid
     */
    public static int parseInt(String text, String fieldName, int min) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a whole number (got \"" + text.trim() + "\").");
        }
        if (value < min) {
            throw new IllegalArgumentException(fieldName + " must be at least " + min + ".");
        }
        return value;
    }

    /**
     * Parses a list of whole numbers separated by commas and/or spaces, e.g. "7, 0 1 2".
     *
     * @throws IllegalArgumentException with a user-friendly message when invalid
     */
    public static List<Integer> parseIntList(String text, String fieldName, int min) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        List<Integer> values = new ArrayList<Integer>();
        for (String token : text.trim().split("[,\\s]+")) {
            if (!token.isEmpty()) {
                values.add(parseInt(token, fieldName, min));
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
        return values;
    }
}