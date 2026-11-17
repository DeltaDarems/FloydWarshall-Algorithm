import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * UI for the Fragmentation module (Part 7).
 *
 * Blocks and processes are edited in two tables. The user can run one
 * algorithm or Compare All. Results show an allocation table, a block table,
 * a fragmentation summary and a bar-style memory map.
 */
public class FragmentationPanel extends JPanel {

    private static final Color INTERNAL_COLOR = new Color(0xE67E22);
    private static final Color FREE_COLOR = new Color(0xDDE3EC);

    private static final String[] SAMPLE_BLOCKS = {"100", "500", "200", "300", "600"};
    private static final String[] SAMPLE_PROCESSES = {"212", "417", "112", "426"};

    private final DefaultTableModel blockModel;
    private final DefaultTableModel processModel;
    private final JTable blockTable;
    private final JTable processTable;
    private final JComboBox<Fragmentation.Algorithm> algorithmBox;
    private final JPanel resultsPanel;

    public FragmentationPanel() {
        super(new BorderLayout(0, 10));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        blockModel = createEditableModel(new String[] {"Block", "Size"});
        processModel = createEditableModel(new String[] {"Process", "Size"});
        blockTable = new JTable(blockModel);
        processTable = new JTable(processModel);
        UIStyles.styleTable(blockTable);
        UIStyles.styleTable(processTable);
        loadSample();

        algorithmBox = new JComboBox<Fragmentation.Algorithm>(Fragmentation.Algorithm.values());
        algorithmBox.setFont(UIStyles.BODY_FONT);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(UIStyles.BACKGROUND);
        showHint();

        add(buildInputArea(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(resultsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIStyles.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // Input area
    // ------------------------------------------------------------------

    private DefaultTableModel createEditableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0 || getColumnName(0).equals("Process");
            }
        };
    }

    private JPanel buildInputArea() {
        JPanel area = new JPanel(new BorderLayout(0, 8));
        area.setOpaque(false);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 0));
        tables.setOpaque(false);
        tables.add(buildListPanel("Memory Blocks", blockTable, true));
        tables.add(buildListPanel("Process Requests", processTable, false));
        tables.setPreferredSize(new Dimension(100, 200));
        area.add(tables, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(UIStyles.createLabel("Algorithm:"));
        controls.add(algorithmBox);

        JButton run = UIStyles.createButton("Run");
        run.addActionListener(e -> runSelected());
        JButton compare = UIStyles.createButton("Compare All");
        compare.addActionListener(e -> compareAll());
        JButton sample = UIStyles.createSecondaryButton("Reset Sample");
        sample.addActionListener(e -> {
            loadSample();
            showHint();
        });
        JButton clear = UIStyles.createSecondaryButton("Clear Results");
        clear.addActionListener(e -> showHint());

        controls.add(run);
        controls.add(compare);
        controls.add(sample);
        controls.add(clear);
        area.add(controls, BorderLayout.SOUTH);
        return area;
    }

    private JPanel buildListPanel(String title, final JTable table, final boolean isBlocks) {
        JPanel panel = UIStyles.createTitledPanel(title);
        panel.setLayout(new BorderLayout(0, 6));
        panel.add(UIStyles.createScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        JButton add = UIStyles.createButton("Add");
        add.addActionListener(e -> addRow(table, isBlocks));
        JButton remove = UIStyles.createDangerButton("Remove");
        remove.addActionListener(e -> removeRow(table, isBlocks));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(UIStyles.createMutedLabel("Double-click a cell to edit."));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void loadSample() {
        stopEditing(blockTable);
        stopEditing(processTable);
        blockModel.setRowCount(0);
        processModel.setRowCount(0);
        for (String size : SAMPLE_BLOCKS) {
            blockModel.addRow(new Object[] {"Block " + (blockModel.getRowCount() + 1), size});
        }
        for (String size : SAMPLE_PROCESSES) {
            processModel.addRow(new Object[] {"P" + (processModel.getRowCount() + 1), size});
        }
    }

    private void addRow(JTable table, boolean isBlocks) {
        stopEditing(table);
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        int next = model.getRowCount() + 1;
        model.addRow(new Object[] {isBlocks ? "Block " + next : "P" + next, "100"});
    }

    private void removeRow(JTable table, boolean isBlocks) {
        stopEditing(table);
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        if (model.getRowCount() <= 1) {
            UIStyles.showWarning(this, "At least one row must remain.");
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            row = model.getRowCount() - 1;
        }
        model.removeRow(row);
        if (isBlocks) {
            for (int i = 0; i < model.getRowCount(); i++) {
                model.setValueAt("Block " + (i + 1), i, 0);
            }
        }
    }

    private void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    // ------------------------------------------------------------------
    // Reading input
    // ------------------------------------------------------------------

    /** Builds MemoryBlocks laid out one after another starting at address 0. */
    private List<MemoryBlock> readBlocks() {
        stopEditing(blockTable);
        List<MemoryBlock> blocks = new ArrayList<MemoryBlock>();
        int address = 0;
        for (int i = 0; i < blockModel.getRowCount(); i++) {
            int size = UIStyles.parseInt(String.valueOf(blockModel.getValueAt(i, 1)),
                    "Size of Block " + (i + 1), 1);
            blocks.add(new MemoryBlock(i + 1, address, size));
            address += size;
        }
        return blocks;
    }

    private List<Fragmentation.Request> readRequests() {
        stopEditing(processTable);
        List<Fragmentation.Request> requests = new ArrayList<Fragmentation.Request>();
        List<String> seen = new ArrayList<String>();
        for (int i = 0; i < processModel.getRowCount(); i++) {
            String id = String.valueOf(processModel.getValueAt(i, 0)).trim();
            if (id.isEmpty() || id.equals("null")) {
                id = "P" + (i + 1);
            }
            if (seen.contains(id)) {
                throw new IllegalArgumentException("Process name \"" + id + "\" is used more than once.");
            }
            seen.add(id);
            int size = UIStyles.parseInt(String.valueOf(processModel.getValueAt(i, 1)),
                    "Size of " + id, 1);
            requests.add(new Fragmentation.Request(id, size));
        }
        return requests;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void runSelected() {
        try {
            List<MemoryBlock> blocks = readBlocks();
            List<Fragmentation.Request> requests = readRequests();
            Fragmentation.Algorithm algorithm = (Fragmentation.Algorithm) algorithmBox.getSelectedItem();
            showSingle(Fragmentation.run(blocks, requests, algorithm));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void compareAll() {
        try {
            List<MemoryBlock> blocks = readBlocks();
            List<Fragmentation.Request> requests = readRequests();
            showComparison(Fragmentation.compareAll(blocks, requests));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Result views
    // ------------------------------------------------------------------

    private void resetResults() {
        resultsPanel.removeAll();
    }

    private void finishResults() {
        resultsPanel.add(Box.createVerticalGlue());
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void addSection(JPanel section) {
        resultsPanel.add(section);
        resultsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    private void showHint() {
        resetResults();
        JLabel hint = UIStyles.createMutedLabel(
                "Edit the blocks and processes above, then press Run or Compare All.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsPanel.add(hint);
        finishResults();
    }

    private void showSingle(Fragmentation.Result result) {
        resetResults();

        // Allocation table
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Fragmentation.Allocation a : result.getAllocations()) {
            if (a.isAllocated()) {
                rows.add(new Object[] {a.getProcessId(), a.getSize(), "Block " + a.getBlockId(),
                        a.getBlockSize(), a.getInternalFragmentation(), "Allocated"});
            } else {
                String why = a.isExternalCause()
                        ? "Not allocated (external fragmentation)"
                        : "Not allocated (not enough memory)";
                rows.add(new Object[] {a.getProcessId(), a.getSize(), "-", "-", "-", why});
            }
        }
        addSection(section("Allocation Table - " + result.getAlgorithm().getLabel(),
                readOnlyTable(new String[] {"Process", "Size", "Block", "Block Size",
                        "Internal Frag.", "Status"}, rows)));

        // Block table
        List<Object[]> blockRows = new ArrayList<Object[]>();
        for (MemoryBlock b : result.getBlocks()) {
            blockRows.add(new Object[] {"Block " + b.getBlockId(),
                    b.getStartAddress() + " - " + b.getEndAddress(), b.getSize(),
                    b.isFree() ? "-" : b.getProcessId(),
                    b.isFree() ? "-" : b.getAllocatedSize(),
                    b.getInternalFragmentation(), b.getFreeSize()});
        }
        addSection(section("Block Status",
                readOnlyTable(new String[] {"Block", "Address Range", "Size", "Process",
                        "Used", "Internal Frag.", "Free"}, blockRows)));

        // Summary
        addSection(section("Fragmentation Summary", buildSummary(result)));

        // Memory map
        addSection(mapSection("Memory Map - " + result.getAlgorithm().getLabel(), result));

        finishResults();
    }

    private void showComparison(List<Fragmentation.Result> results) {
        resetResults();

        // Totals per algorithm
        List<Object[]> totals = new ArrayList<Object[]>();
        for (Fragmentation.Result r : results) {
            totals.add(new Object[] {r.getAlgorithm().getLabel(), r.getAllocatedCount(),
                    r.getNotAllocatedCount(), r.getTotalInternalFragmentation(),
                    r.getTotalFree(), r.getExternalFragmentation()});
        }
        addSection(section("Comparison",
                readOnlyTable(new String[] {"Algorithm", "Allocated", "Not Allocated",
                        "Internal Frag.", "Free Memory", "External Frag."}, totals)));

        // Which block each algorithm chose for each process
        List<Object[]> matrix = new ArrayList<Object[]>();
        int count = results.get(0).getAllocations().size();
        for (int i = 0; i < count; i++) {
            Fragmentation.Allocation first = results.get(0).getAllocations().get(i);
            Object[] row = new Object[2 + results.size()];
            row[0] = first.getProcessId();
            row[1] = first.getSize();
            for (int j = 0; j < results.size(); j++) {
                Fragmentation.Allocation a = results.get(j).getAllocations().get(i);
                row[2 + j] = a.isAllocated()
                        ? "Block " + a.getBlockId() + " (frag " + a.getInternalFragmentation() + ")"
                        : "Not allocated";
            }
            matrix.add(row);
        }
        String[] matrixColumns = new String[2 + results.size()];
        matrixColumns[0] = "Process";
        matrixColumns[1] = "Size";
        for (int j = 0; j < results.size(); j++) {
            matrixColumns[2 + j] = results.get(j).getAlgorithm().getLabel();
        }
        addSection(section("Chosen Block per Process", readOnlyTable(matrixColumns, matrix)));

        // Conclusion
        addSection(section("Result", buildConclusion(results)));

        // One memory map per algorithm
        for (Fragmentation.Result r : results) {
            addSection(mapSection("Memory Map - " + r.getAlgorithm().getLabel(), r));
        }

        finishResults();
    }

    private Component buildSummary(Fragmentation.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Algorithm                    : ").append(r.getAlgorithm().getLabel()).append('\n');
        sb.append("Processes allocated          : ").append(r.getAllocatedCount())
                .append(" of ").append(r.getAllocations().size()).append('\n');
        sb.append("Total internal fragmentation : ").append(r.getTotalInternalFragmentation()).append('\n');
        sb.append("Free memory (unused blocks)  : ").append(r.getTotalFree()).append('\n');
        sb.append("External fragmentation       : ").append(r.getExternalFragmentation());

        for (Fragmentation.Allocation a : r.getAllocations()) {
            if (!a.isAllocated() && a.isExternalCause()) {
                sb.append("\n  - ").append(a.getProcessId()).append(" (").append(a.getSize())
                        .append(") fits in the total free memory (").append(a.getFreeAtFailure())
                        .append(") but not in any single block.");
            }
        }
        for (Fragmentation.Allocation a : r.getAllocations()) {
            if (!a.isAllocated() && !a.isExternalCause()) {
                sb.append("\n  - ").append(a.getProcessId()).append(" (").append(a.getSize())
                        .append(") is larger than the total free memory (").append(a.getFreeAtFailure())
                        .append(").");
            }
        }
        return textArea(sb.toString());
    }

    private Component buildConclusion(List<Fragmentation.Result> results) {
        Fragmentation.Result best = results.get(0);
        for (Fragmentation.Result r : results) {
            if (r.getAllocatedCount() > best.getAllocatedCount()
                    || (r.getAllocatedCount() == best.getAllocatedCount()
                            && r.getTotalInternalFragmentation() < best.getTotalInternalFragmentation())) {
                best = r;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Best for this input: ").append(best.getAlgorithm().getLabel())
                .append(" (").append(best.getAllocatedCount()).append(" of ")
                .append(best.getAllocations().size()).append(" allocated, internal fragmentation ")
                .append(best.getTotalInternalFragmentation()).append(").");
        sb.append("\nRule used: most processes allocated first, then the least internal fragmentation.");
        return textArea(sb.toString());
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    private JTextArea textArea(String text) {
        JTextArea area = UIStyles.createOutputArea(1, 40);
        area.setText(text);
        area.setRows(text.split("\n").length);
        return area;
    }

    private JScrollPane readOnlyTable(String[] columns, List<Object[]> rows) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Object[] row : rows) {
            model.addRow(row);
        }
        JTable table = new JTable(model);
        UIStyles.styleTable(table);
        JScrollPane pane = UIStyles.createScrollPane(table);
        pane.setPreferredSize(new Dimension(400, 30 + rows.size() * 26 + 4));
        return pane;
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

    private JPanel mapSection(String title, Fragmentation.Result result) {
        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setOpaque(false);
        content.add(buildLegend(), BorderLayout.NORTH);
        content.add(new MemoryMap(result.getBlocks()), BorderLayout.CENTER);
        return section(title, content);
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendItem(UIStyles.PRIMARY, "Used by process"));
        legend.add(legendItem(INTERNAL_COLOR, "Internal fragmentation"));
        legend.add(legendItem(FREE_COLOR, "Free block"));
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

    // ------------------------------------------------------------------
    // Memory map
    // ------------------------------------------------------------------

    /** Draws all blocks side by side, widths proportional to block size. */
    private static class MemoryMap extends JPanel {
        private static final int PAD = 4;
        private static final int BAR_TOP = 6;
        private static final int BAR_HEIGHT = 46;

        private final List<MemoryBlock> blocks;

        MemoryMap(List<MemoryBlock> blocks) {
            this.blocks = blocks;
            setBackground(UIStyles.PANEL_BG);
            setPreferredSize(new Dimension(600, 84));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int total = 0;
            for (MemoryBlock b : blocks) {
                total += b.getSize();
            }
            if (total == 0) {
                g.dispose();
                return;
            }

            int available = getWidth() - 2 * PAD;
            int x = PAD;
            for (int i = 0; i < blocks.size(); i++) {
                MemoryBlock b = blocks.get(i);
                int w = (i == blocks.size() - 1)
                        ? PAD + available - x
                        : (int) Math.round((double) b.getSize() / total * available);
                w = Math.max(w, 2);

                g.setColor(FREE_COLOR);
                g.fillRect(x, BAR_TOP, w, BAR_HEIGHT);

                int usedWidth = 0;
                if (!b.isFree()) {
                    usedWidth = (int) Math.round((double) b.getAllocatedSize() / b.getSize() * w);
                    g.setColor(UIStyles.PRIMARY);
                    g.fillRect(x, BAR_TOP, usedWidth, BAR_HEIGHT);
                    g.setColor(INTERNAL_COLOR);
                    g.fillRect(x + usedWidth, BAR_TOP, w - usedWidth, BAR_HEIGHT);
                }
                g.setColor(UIStyles.TEXT_DARK);
                g.drawRect(x, BAR_TOP, w, BAR_HEIGHT);

                // Text inside the bar
                g.setFont(UIStyles.BUTTON_FONT);
                FontMetrics fm = g.getFontMetrics();
                int textY = BAR_TOP + (BAR_HEIGHT + fm.getAscent()) / 2 - 2;
                if (b.isFree()) {
                    String label = "free";
                    if (fm.stringWidth(label) < w - 6) {
                        g.drawString(label, x + (w - fm.stringWidth(label)) / 2, textY);
                    }
                } else {
                    String label = b.getProcessId();
                    if (fm.stringWidth(label) < usedWidth - 6) {
                        g.setColor(Color.WHITE);
                        g.drawString(label, x + 5, textY);
                    }
                }

                // Block label under the bar
                g.setFont(UIStyles.SMALL_FONT);
                fm = g.getFontMetrics();
                String caption = "B" + b.getBlockId() + " (" + b.getSize() + ")";
                if (fm.stringWidth(caption) <= w) {
                    g.setColor(UIStyles.TEXT_MUTED);
                    g.drawString(caption, x + 2, BAR_TOP + BAR_HEIGHT + 16);
                }
                x += w;
            }
            g.dispose();
        }
    }
}