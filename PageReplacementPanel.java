import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

/**
 * GUI for Module 4: Page Replacement.
 * All calculations are done by {@link PageReplacement}; this class only collects
 * input, validates it and displays the results.
 */
public class PageReplacementPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_REFERENCES = 100;
    private static final int MAX_PAGE_NUMBER = 999;
    private static final int MAX_FRAMES = 10;

    private static final String ALGO_FIFO = "FIFO";
    private static final String ALGO_LRU = "LRU";
    private static final String ALGO_OPTIMAL = "Optimal";
    private static final String ALGO_SECOND_CHANCE = "Second Chance (Clock)";
    private static final String ALGO_LFU = "LFU";

    private static final Color CHANGED_COLOR = new Color(0xFFE29A);

    // Input widgets
    private final JTextField referenceField = UIStyles.createTextField(32);
    private final JTextField frameField = UIStyles.createTextField(5);
    private final JComboBox<String> algorithmBox =
            new JComboBox<String>(new String[]{
                    ALGO_FIFO, ALGO_LRU, ALGO_OPTIMAL, ALGO_SECOND_CHANCE, ALGO_LFU});

    // Result widgets
    private final DefaultTableModel stepModel = new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable stepTable = new JTable(stepModel);
    private final DefaultTableModel compareModel = new DefaultTableModel(
            new String[]{"Algorithm", "Hits", "Faults", "Hit Ratio", "Fault Ratio"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel stepHeading = UIStyles.createHeadingLabel("Frame Changes");
    private final JLabel summaryLabel = UIStyles.createLabel("Run a simulation to see hits and faults.");
    private final JLabel hintLabel = UIStyles.createMutedLabel(
            "Green = hit     Red = page fault     Yellow cell = frame that was loaded");

    /** Result currently shown in the step table (used by the cell renderer). */
    private PageReplacement.Result shown;

    public PageReplacementPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        UIStyles.styleTable(stepTable);
        stepTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        stepTable.setDefaultRenderer(Object.class, new StepRenderer());
        stepTable.setRowSelectionAllowed(false);
        stepModel.setColumnIdentifiers(new String[]{"Step"});

        algorithmBox.setFont(UIStyles.BODY_FONT);
        frameField.setText("3");

        add(buildInputCard(), BorderLayout.NORTH);
        add(buildStepCard(), BorderLayout.CENTER);
        add(buildBottomRow(), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildInputCard() {
        JPanel card = UIStyles.createTitledPanel("Input");
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        fields.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        buttons.setOpaque(false);
        fields.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton run = UIStyles.createButton("Run Simulation");
        JButton compare = UIStyles.createButton("Compare All");
        JButton sample = UIStyles.createSecondaryButton("Load Sample");
        JButton clear = UIStyles.createDangerButton("Clear");
        run.addActionListener(e -> runSelected());
        compare.addActionListener(e -> compareAll());
        sample.addActionListener(e -> loadSample());
        clear.addActionListener(e -> clearAll());

        fields.add(UIStyles.createLabel("Reference string:"));
        fields.add(referenceField);
        fields.add(UIStyles.createLabel("Frames:"));
        fields.add(frameField);
        fields.add(UIStyles.createLabel("Algorithm:"));
        fields.add(algorithmBox);
        buttons.add(run);
        buttons.add(compare);
        buttons.add(sample);
        buttons.add(clear);
        card.add(fields);
        card.add(buttons);
        return card;
    }

    private JPanel buildStepCard() {
        JPanel card = UIStyles.createTitledPanel("Simulation");
        card.setLayout(new BorderLayout(0, 6));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(stepHeading, BorderLayout.WEST);
        top.add(hintLabel, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(UIStyles.createScrollPane(stepTable), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 150));

        JPanel summaryCard = UIStyles.createTitledPanel("Summary");
        summaryCard.setLayout(new BoxLayout(summaryCard, BoxLayout.Y_AXIS));
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel formula = UIStyles.createMutedLabel(
                "Hit ratio = hits / references     Fault ratio = faults / references");
        formula.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryCard.add(summaryLabel);
        summaryCard.add(Box.createRigidArea(new Dimension(0, 6)));
        summaryCard.add(formula);

        JPanel compareCard = UIStyles.createTitledPanel("Comparison (Compare All)");
        compareCard.setLayout(new BorderLayout());
        JTable compareTable = new JTable(compareModel);
        UIStyles.styleTable(compareTable);
        compareCard.add(UIStyles.createScrollPane(compareTable), BorderLayout.CENTER);

        row.add(summaryCard);
        row.add(compareCard);
        return row;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void loadSample() {
        referenceField.setText("7 0 1 2 0 3 0 4 2 3 0 3 2 1 2 0 1 7 0 1");
        frameField.setText("3");
    }

    private void clearAll() {
        referenceField.setText("");
        frameField.setText("3");
        shown = null;
        stepModel.setColumnIdentifiers(new String[]{"Step"});
        stepModel.setRowCount(0);
        compareModel.setRowCount(0);
        stepHeading.setText("Frame Changes");
        summaryLabel.setText("Run a simulation to see hits and faults.");
    }

    private void runSelected() {
        try {
            List<Integer> references = readReferences();
            int frames = readFrames();
            showResult(runAlgorithm((String) algorithmBox.getSelectedItem(), references, frames));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void compareAll() {
        try {
            List<Integer> references = readReferences();
            int frames = readFrames();
            List<PageReplacement.Result> results = PageReplacement.compareAll(references, frames);

            int fewestFaults = Integer.MAX_VALUE;
            for (PageReplacement.Result r : results) {
                fewestFaults = Math.min(fewestFaults, r.getFaults());
            }
            compareModel.setRowCount(0);
            PageReplacement.Result selected = results.get(0);
            for (PageReplacement.Result r : results) {
                compareModel.addRow(new Object[]{
                        r.getAlgorithmName() + (r.getFaults() == fewestFaults ? " (best)" : ""),
                        r.getHits(), r.getFaults(),
                        String.format("%.2f%%", r.getHitRatio()),
                        String.format("%.2f%%", r.getFaultRatio())});
                if (r.getAlgorithmName().equals(algorithmBox.getSelectedItem())) {
                    selected = r;
                }
            }
            showResult(selected);
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private PageReplacement.Result runAlgorithm(String name, List<Integer> references, int frames) {
        if (ALGO_FIFO.equals(name)) {
            return PageReplacement.fifo(references, frames);
        } else if (ALGO_LRU.equals(name)) {
            return PageReplacement.lru(references, frames);
        } else if (ALGO_SECOND_CHANCE.equals(name)) {
            return PageReplacement.secondChance(references, frames);
        } else if (ALGO_LFU.equals(name)) {
            return PageReplacement.lfu(references, frames);
        }
        return PageReplacement.optimal(references, frames);
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    /** Fills the step table: one column per reference, rows = reference, frames, result. */
    private void showResult(PageReplacement.Result result) {
        shown = result;
        int steps = result.getSteps().size();
        int frames = result.getFrameCount();

        String[] columns = new String[steps + 1];
        columns[0] = "Step";
        for (int i = 0; i < steps; i++) {
            columns[i + 1] = String.valueOf(i + 1);
        }
        stepModel.setColumnIdentifiers(columns);
        stepModel.setRowCount(0);

        Object[] referenceRow = new Object[steps + 1];
        referenceRow[0] = "Reference";
        for (int i = 0; i < steps; i++) {
            referenceRow[i + 1] = result.getSteps().get(i).getPage();
        }
        stepModel.addRow(referenceRow);

        for (int f = 0; f < frames; f++) {
            Object[] row = new Object[steps + 1];
            row[0] = "Frame " + (f + 1);
            for (int i = 0; i < steps; i++) {
                int value = result.getSteps().get(i).getFrames().get(f);
                row[i + 1] = value == PageReplacement.EMPTY ? "-" : String.valueOf(value);
            }
            stepModel.addRow(row);
        }

        Object[] resultRow = new Object[steps + 1];
        resultRow[0] = "Result";
        for (int i = 0; i < steps; i++) {
            resultRow[i + 1] = result.getSteps().get(i).isHit() ? "Hit" : "Fault";
        }
        stepModel.addRow(resultRow);

        stepTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        for (int c = 1; c <= steps; c++) {
            stepTable.getColumnModel().getColumn(c).setPreferredWidth(46);
        }

        stepHeading.setText("Frame Changes - " + result.getAlgorithmName()
                + " (" + frames + " frames)");
        summaryLabel.setText(String.format(
                "<html>%s:  References: %d     Hits: %d     Faults: %d<br>"
                        + "Hit ratio: %.2f%%     Fault ratio: %.2f%%</html>",
                result.getAlgorithmName(), result.getTotalReferences(), result.getHits(),
                result.getFaults(), result.getHitRatio(), result.getFaultRatio()));
        stepTable.repaint();
    }

    // ------------------------------------------------------------------
    // Reading and validating input
    // ------------------------------------------------------------------

    private List<Integer> readReferences() {
        List<Integer> references = UIStyles.parseIntList(referenceField.getText(), "Reference string", 0);
        if (references.size() > MAX_REFERENCES) {
            throw new IllegalArgumentException("The reference string can have at most "
                    + MAX_REFERENCES + " pages (found " + references.size() + ").");
        }
        for (int page : references) {
            if (page > MAX_PAGE_NUMBER) {
                throw new IllegalArgumentException("Page numbers must be at most " + MAX_PAGE_NUMBER
                        + " (found " + page + ").");
            }
        }
        return references;
    }

    private int readFrames() {
        int frames = UIStyles.parseInt(frameField.getText(), "Number of frames", 1);
        if (frames > MAX_FRAMES) {
            throw new IllegalArgumentException("Number of frames must be at most " + MAX_FRAMES + ".");
        }
        return frames;
    }

    // ------------------------------------------------------------------
    // Cell renderer
    // ------------------------------------------------------------------

    /** Colours the step grid: green hits, red faults and a yellow cell where a page was loaded. */
    private class StepRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UIStyles.BODY_FONT);
            c.setBackground(Color.WHITE);
            c.setForeground(UIStyles.TEXT_DARK);

            if (column == 0) {
                c.setBackground(UIStyles.STRIPE);
                setFont(UIStyles.BUTTON_FONT);
            } else if (shown != null && column - 1 < shown.getSteps().size()) {
                PageReplacement.Step step = shown.getSteps().get(column - 1);
                int lastRow = table.getRowCount() - 1;
                if (row == 0) {
                    c.setBackground(UIStyles.STRIPE);
                    setFont(UIStyles.BUTTON_FONT);
                } else if (row == lastRow) {
                    c.setBackground(step.isHit() ? UIStyles.SUCCESS : UIStyles.DANGER);
                    c.setForeground(Color.WHITE);
                    setFont(UIStyles.BUTTON_FONT);
                } else if (step.isFault() && step.getChangedFrame() == row - 1) {
                    c.setBackground(CHANGED_COLOR);
                    setFont(UIStyles.BUTTON_FONT);
                }
            }
            return c;
        }
    }
}