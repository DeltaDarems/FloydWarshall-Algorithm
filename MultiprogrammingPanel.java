import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI for the Multiprogramming module (Part 9).
 *
 * The user sets the total memory and adds programs. A program loads when it
 * fits, otherwise it waits. Terminate frees memory and moves waiting programs
 * in automatically. Shows memory usage, utilization %, the loaded list, the
 * waiting queue, a memory bar and an event log.
 */
public class MultiprogrammingPanel extends JPanel {

    private static final int DEFAULT_MEMORY = 1024;
    private static final String[][] SAMPLE = {
        {"P1", "300"}, {"P2", "500"}, {"P3", "200"}, {"P4", "400"}, {"P5", "100"}
    };
    private static final Color FREE_COLOR = new Color(0xDDE3EC);
    private static final Color[] PALETTE = {
        new Color(0x2F5DA8), new Color(0x2E8B57), new Color(0xE67E22),
        new Color(0x8E44AD), new Color(0x16A085), new Color(0xB9770E)
    };

    private final Multiprogramming system = new Multiprogramming(DEFAULT_MEMORY);

    private final JTextField totalField = UIStyles.createTextField(7);
    private final JTextField idField = UIStyles.createTextField(7);
    private final JTextField sizeField = UIStyles.createTextField(7);
    private final JCheckBox strictCheck;

    private final DefaultTableModel loadedModel;
    private final DefaultTableModel waitingModel;
    private final JTable loadedTable;
    private final JTable waitingTable;

    private final JLabel totalValue = statValue();
    private final JLabel usedValue = statValue();
    private final JLabel freeValue = statValue();
    private final JLabel utilValue = statValue();
    private final JLabel loadedValue = statValue();
    private final JLabel waitingValue = statValue();

    private final MemoryBar memoryBar;
    private final JTextArea logArea;

    public MultiprogrammingPanel() {
        super(new BorderLayout(0, 10));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        strictCheck = new JCheckBox("Strict FIFO (a waiting program blocks the ones behind it)", false);
        strictCheck.setFont(UIStyles.BODY_FONT);
        strictCheck.setForeground(UIStyles.TEXT_DARK);
        strictCheck.setOpaque(false);
        strictCheck.addActionListener(e -> system.setStrictFifo(strictCheck.isSelected()));

        loadedModel = readOnlyModel(new String[] {"Program", "Memory", "% of Total"});
        waitingModel = readOnlyModel(new String[] {"Position", "Program", "Memory", "Still Needed"});
        loadedTable = new JTable(loadedModel);
        waitingTable = new JTable(waitingModel);
        UIStyles.styleTable(loadedTable);
        UIStyles.styleTable(waitingTable);
        // Only one table can have a selected row at a time
        loadedTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && loadedTable.getSelectedRow() >= 0) {
                waitingTable.clearSelection();
            }
        });
        waitingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && waitingTable.getSelectedRow() >= 0) {
                loadedTable.clearSelection();
            }
        });

        memoryBar = new MemoryBar(system);
        logArea = UIStyles.createOutputArea(6, 40);

        totalField.setText(String.valueOf(DEFAULT_MEMORY));
        sizeField.setText("100");

        add(buildInputPanel(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(buildContent());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIStyles.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        refresh();
        idField.setText(suggestId());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildInputPanel() {
        JPanel panel = UIStyles.createTitledPanel("Memory and Programs");
        panel.setLayout(new GridLayout(0, 1, 0, 6));

        JPanel row1 = row();
        row1.add(UIStyles.createLabel("Total memory:"));
        row1.add(totalField);
        JButton setMemory = UIStyles.createSecondaryButton("Set Memory");
        setMemory.addActionListener(e -> setMemory());
        row1.add(setMemory);
        row1.add(strictCheck);

        JPanel row2 = row();
        row2.add(UIStyles.createLabel("Program ID:"));
        row2.add(idField);
        row2.add(UIStyles.createLabel("Memory size:"));
        row2.add(sizeField);
        JButton add = UIStyles.createButton("Add Program");
        add.addActionListener(e -> addProgram());
        row2.add(add);

        JPanel row3 = row();
        JButton terminate = UIStyles.createDangerButton("Terminate Selected");
        terminate.addActionListener(e -> terminateSelected());
        JButton sample = UIStyles.createSecondaryButton("Load Sample");
        sample.addActionListener(e -> loadSample());
        JButton reset = UIStyles.createSecondaryButton("Reset");
        reset.addActionListener(e -> resetAll());
        row3.add(terminate);
        row3.add(sample);
        row3.add(reset);

        JPanel row4 = row();
        row4.add(UIStyles.createMutedLabel(
                "A program loads if it fits, otherwise it waits. Select a row in either table and press"
                        + " Terminate Selected to free memory."));

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(row4);
        return panel;
    }

    private JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIStyles.BACKGROUND);

        // Statistic cards
        JPanel stats = new JPanel(new GridLayout(1, 6, 10, 0));
        stats.setOpaque(false);
        stats.add(statCard("Total Memory", totalValue));
        stats.add(statCard("Used", usedValue));
        stats.add(statCard("Free", freeValue));
        stats.add(statCard("Utilization", utilValue));
        stats.add(statCard("Loaded", loadedValue));
        stats.add(statCard("Waiting", waitingValue));
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, stats.getPreferredSize().height));
        content.add(stats);
        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // Memory bar
        JPanel barContent = new JPanel(new BorderLayout(0, 4));
        barContent.setOpaque(false);
        barContent.add(buildLegend(), BorderLayout.NORTH);
        barContent.add(memoryBar, BorderLayout.CENTER);
        content.add(section("Memory Bar", barContent));
        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // Queues side by side
        JPanel queues = new JPanel(new GridLayout(1, 2, 12, 0));
        queues.setOpaque(false);
        queues.add(tablePanel("Loaded Programs", loadedTable));
        queues.add(tablePanel("Waiting Queue (front first)", waitingTable));
        queues.setAlignmentX(Component.LEFT_ALIGNMENT);
        queues.setMaximumSize(new Dimension(Integer.MAX_VALUE, queues.getPreferredSize().height));
        content.add(queues);
        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // Event log
        JScrollPane logPane = UIStyles.createScrollPane(logArea);
        logPane.setPreferredSize(new Dimension(400, 130));
        content.add(section("Event Log", logPane));

        content.add(Box.createVerticalGlue());
        return content;
    }

    private JPanel tablePanel(String title, JTable table) {
        JPanel panel = UIStyles.createTitledPanel(title);
        panel.setLayout(new BorderLayout());
        JScrollPane pane = UIStyles.createScrollPane(table);
        pane.setPreferredSize(new Dimension(300, 170));
        panel.add(pane, BorderLayout.CENTER);
        return panel;
    }

    /** Titled white box that never grows taller than its content. */
    private JPanel section(String title, Component content) {
        JPanel panel = UIStyles.createTitledPanel(title);
        panel.setLayout(new BorderLayout());
        panel.add(content, BorderLayout.CENTER);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel statCard(String caption, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(UIStyles.PANEL_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIStyles.BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        card.add(UIStyles.createMutedLabel(caption), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("0");
        label.setFont(UIStyles.HEADING_FONT);
        label.setForeground(UIStyles.PRIMARY_DARK);
        return label;
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendItem(UIStyles.PRIMARY, "Loaded program (one colour each)"));
        legend.add(legendItem(FREE_COLOR, "Free memory"));
        return legend;
    }

    private JPanel legendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setBorder(BorderFactory.createLineBorder(UIStyles.BORDER));
        swatch.setPreferredSize(new Dimension(14, 14));
        item.add(swatch);
        item.add(UIStyles.createMutedLabel(text));
        return item;
    }

    private DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void setMemory() {
        try {
            int total = UIStyles.parseInt(totalField.getText(), "Total memory", 1);
            if (system.hasPrograms()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "Changing the total memory clears all programs. Continue?",
                        "Set Memory", JOptionPane.YES_NO_OPTION);
                if (answer != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            system.reset(total);
            memoryBar.resetColors();
            refresh();
            idField.setText(suggestId());
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void addProgram() {
        try {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Program ID cannot be empty.");
            }
            int size = UIStyles.parseInt(sizeField.getText(), "Memory size", 1);
            // Only ID and memory matter here; the other Process fields get neutral values.
            system.load(new Process(id, 0, 1, 0, size));
            refresh();
            idField.setText(suggestId());
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void terminateSelected() {
        String id = null;
        int row = loadedTable.getSelectedRow();
        if (row >= 0) {
            id = String.valueOf(loadedModel.getValueAt(row, 0));
        } else {
            row = waitingTable.getSelectedRow();
            if (row >= 0) {
                id = String.valueOf(waitingModel.getValueAt(row, 1));
            }
        }
        if (id == null) {
            UIStyles.showWarning(this, "Select a program in the Loaded or Waiting table first.");
            return;
        }
        try {
            system.terminate(id);
            refresh();
            idField.setText(suggestId());
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void loadSample() {
        system.reset(DEFAULT_MEMORY);
        memoryBar.resetColors();
        totalField.setText(String.valueOf(DEFAULT_MEMORY));
        for (String[] program : SAMPLE) {
            system.load(new Process(program[0], 0, 1, 0, Integer.parseInt(program[1])));
        }
        refresh();
        idField.setText(suggestId());
    }

    private void resetAll() {
        try {
            int total = UIStyles.parseInt(totalField.getText(), "Total memory", 1);
            system.reset(total);
            memoryBar.resetColors();
            refresh();
            idField.setText(suggestId());
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    /** Smallest "P1", "P2", ... that is not currently loaded or waiting. */
    private String suggestId() {
        int n = 1;
        while (system.contains("P" + n)) {
            n++;
        }
        return "P" + n;
    }

    // ------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------

    private void refresh() {
        int total = system.getTotalMemory();
        int free = system.getFreeMemory();

        loadedModel.setRowCount(0);
        for (Process p : system.getLoaded()) {
            loadedModel.addRow(new Object[] {p.getId(), p.getMemorySize(),
                    String.format("%.1f%%", 100.0 * p.getMemorySize() / total)});
        }

        waitingModel.setRowCount(0);
        List<Process> waiting = system.getWaiting();
        for (int i = 0; i < waiting.size(); i++) {
            Process p = waiting.get(i);
            waitingModel.addRow(new Object[] {i + 1, p.getId(), p.getMemorySize(),
                    Math.max(0, p.getMemorySize() - free)});
        }

        totalValue.setText(String.valueOf(total));
        usedValue.setText(String.valueOf(system.getUsedMemory()));
        freeValue.setText(String.valueOf(free));
        utilValue.setText(String.format("%.1f%%", system.getUtilization()));
        loadedValue.setText(String.valueOf(system.getLoaded().size()));
        waitingValue.setText(String.valueOf(waiting.size()));

        StringBuilder sb = new StringBuilder();
        List<String> log = system.getLog();
        for (int i = 0; i < log.size(); i++) {
            sb.append(i + 1).append(". ").append(log.get(i)).append('\n');
        }
        logArea.setText(sb.toString());
        logArea.setCaretPosition(logArea.getDocument().getLength());

        memoryBar.repaint();
    }

    // ------------------------------------------------------------------
    // Memory bar
    // ------------------------------------------------------------------

    /** Loaded programs side by side (widths proportional to size) followed by the free space. */
    private static class MemoryBar extends JPanel {
        private static final int PAD = 4;
        private static final int BAR_TOP = 6;
        private static final int BAR_HEIGHT = 46;

        private final Multiprogramming system;
        private final Map<String, Integer> colorIndex = new HashMap<String, Integer>();

        MemoryBar(Multiprogramming system) {
            this.system = system;
            setBackground(UIStyles.PANEL_BG);
            setPreferredSize(new Dimension(600, 84));
        }

        void resetColors() {
            colorIndex.clear();
        }

        /** Each program keeps the same colour while it exists. */
        private Color colorFor(String id) {
            Integer index = colorIndex.get(id);
            if (index == null) {
                index = colorIndex.size();
                colorIndex.put(id, index);
            }
            return PALETTE[index % PALETTE.length];
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int total = system.getTotalMemory();
            int available = getWidth() - 2 * PAD;

            // Free background for the whole bar, then the loaded programs on top
            g.setColor(FREE_COLOR);
            g.fillRect(PAD, BAR_TOP, available, BAR_HEIGHT);

            int used = 0;
            for (Process p : system.getLoaded()) {
                int x1 = PAD + (int) Math.round((double) used * available / total);
                used += p.getMemorySize();
                int x2 = PAD + (int) Math.round((double) used * available / total);
                int w = x2 - x1;

                g.setColor(colorFor(p.getId()));
                g.fillRect(x1, BAR_TOP, w, BAR_HEIGHT);
                g.setColor(Color.WHITE);
                g.drawLine(x1, BAR_TOP, x1, BAR_TOP + BAR_HEIGHT);

                g.setFont(UIStyles.BUTTON_FONT);
                FontMetrics fm = g.getFontMetrics();
                if (fm.stringWidth(p.getId()) < w - 6) {
                    g.setColor(Color.WHITE);
                    g.drawString(p.getId(), x1 + 5, BAR_TOP + (BAR_HEIGHT + fm.getAscent()) / 2 - 2);
                }

                g.setFont(UIStyles.SMALL_FONT);
                fm = g.getFontMetrics();
                String caption = p.getId() + " (" + p.getMemorySize() + ")";
                if (fm.stringWidth(caption) <= w) {
                    g.setColor(UIStyles.TEXT_MUTED);
                    g.drawString(caption, x1 + 2, BAR_TOP + BAR_HEIGHT + 16);
                }
            }

            // Free space label
            int free = system.getFreeMemory();
            if (free > 0) {
                int x1 = PAD + (int) Math.round((double) used * available / total);
                int w = PAD + available - x1;
                g.setFont(UIStyles.BUTTON_FONT);
                FontMetrics fm = g.getFontMetrics();
                String label = "free";
                if (fm.stringWidth(label) < w - 6) {
                    g.setColor(UIStyles.TEXT_DARK);
                    g.drawString(label, x1 + (w - fm.stringWidth(label)) / 2,
                            BAR_TOP + (BAR_HEIGHT + fm.getAscent()) / 2 - 2);
                }
                g.setFont(UIStyles.SMALL_FONT);
                fm = g.getFontMetrics();
                String caption = "free (" + free + ")";
                if (fm.stringWidth(caption) <= w) {
                    g.setColor(UIStyles.TEXT_MUTED);
                    g.drawString(caption, x1 + 2, BAR_TOP + BAR_HEIGHT + 16);
                }
            }

            g.setColor(UIStyles.TEXT_DARK);
            g.drawRect(PAD, BAR_TOP, available, BAR_HEIGHT);
            g.dispose();
        }
    }
}