import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
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
 * UI for the Disk Scheduling module (Part 8).
 *
 * Input: request queue, starting head, disk size (cylinders) and direction.
 * Run shows the service order, every head movement, the total distance and a
 * line graph of the head movement. Compare All runs all six algorithms.
 */
public class DiskSchedulingPanel extends JPanel {

    private static final String SAMPLE_QUEUE = "98, 183, 37, 122, 14, 124, 65, 67";
    private static final String SAMPLE_HEAD = "53";
    private static final String SAMPLE_SIZE = "200";

    private final JTextField queueField = UIStyles.createTextField(36);
    private final JTextField headField = UIStyles.createTextField(6);
    private final JTextField sizeField = UIStyles.createTextField(6);
    private final JComboBox<DiskScheduling.Direction> directionBox;
    private final JComboBox<DiskScheduling.Algorithm> algorithmBox;
    private final JCheckBox jumpCheck;
    private final JPanel resultsPanel;

    public DiskSchedulingPanel() {
        super(new BorderLayout(0, 10));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        directionBox = new JComboBox<DiskScheduling.Direction>(DiskScheduling.Direction.values());
        directionBox.setFont(UIStyles.BODY_FONT);
        algorithmBox = new JComboBox<DiskScheduling.Algorithm>(DiskScheduling.Algorithm.values());
        algorithmBox.setFont(UIStyles.BODY_FONT);
        jumpCheck = new JCheckBox("Count return jump (C-SCAN, C-LOOK)", true);
        jumpCheck.setFont(UIStyles.BODY_FONT);
        jumpCheck.setForeground(UIStyles.TEXT_DARK);
        jumpCheck.setOpaque(false);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(UIStyles.BACKGROUND);

        loadSample();
        showHint();

        add(buildInputPanel(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(resultsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UIStyles.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private JPanel buildInputPanel() {
        JPanel panel = UIStyles.createTitledPanel("Disk Request Input");
        panel.setLayout(new GridLayout(0, 1, 0, 6));

        JPanel row1 = row();
        row1.add(UIStyles.createLabel("Request queue:"));
        row1.add(queueField);
        row1.add(UIStyles.createMutedLabel("(separate with commas or spaces)"));

        JPanel row2 = row();
        row2.add(UIStyles.createLabel("Head start:"));
        row2.add(headField);
        row2.add(UIStyles.createLabel("Disk size (cylinders):"));
        row2.add(sizeField);
        row2.add(UIStyles.createLabel("Direction:"));
        row2.add(directionBox);

        JPanel row3 = row();
        row3.add(UIStyles.createLabel("Algorithm:"));
        row3.add(algorithmBox);
        row3.add(jumpCheck);

        JPanel row4 = row();
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
        row4.add(run);
        row4.add(compare);
        row4.add(sample);
        row4.add(clear);

        JPanel row5 = row();
        row5.add(UIStyles.createMutedLabel(
                "Cylinders go from 0 to size-1. Direction applies to SCAN, C-SCAN, LOOK and C-LOOK."));

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(row4);
        panel.add(row5);
        return panel;
    }

    private JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private void loadSample() {
        queueField.setText(SAMPLE_QUEUE);
        headField.setText(SAMPLE_HEAD);
        sizeField.setText(SAMPLE_SIZE);
        directionBox.setSelectedItem(DiskScheduling.Direction.RIGHT);
        algorithmBox.setSelectedItem(DiskScheduling.Algorithm.FCFS);
        jumpCheck.setSelected(true);
    }

    /** Values typed by the user, already parsed. */
    private static class Input {
        List<Integer> requests;
        int head;
        int diskSize;
        DiskScheduling.Direction direction;
        boolean countJump;
    }

    private Input readInput() {
        Input in = new Input();
        // Negative numbers are accepted here so DiskScheduling can report them as "outside the disk".
        in.requests = UIStyles.parseIntList(queueField.getText(), "Request queue", Integer.MIN_VALUE);
        in.head = UIStyles.parseInt(headField.getText(), "Head start", Integer.MIN_VALUE);
        in.diskSize = UIStyles.parseInt(sizeField.getText(), "Disk size", 1);
        in.direction = (DiskScheduling.Direction) directionBox.getSelectedItem();
        in.countJump = jumpCheck.isSelected();
        return in;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void runSelected() {
        try {
            Input in = readInput();
            DiskScheduling.Algorithm algorithm = (DiskScheduling.Algorithm) algorithmBox.getSelectedItem();
            showSingle(DiskScheduling.run(in.requests, in.head, in.diskSize,
                    in.direction, algorithm, in.countJump));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void compareAll() {
        try {
            Input in = readInput();
            showComparison(DiskScheduling.compareAll(in.requests, in.head, in.diskSize,
                    in.direction, in.countJump));
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
                "Enter the request queue, head and disk size, then press Run or Compare All.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsPanel.add(hint);
        finishResults();
    }

    private void showSingle(DiskScheduling.Result r) {
        resetResults();
        String name = r.getAlgorithm().getLabel();
        addSection(section("Summary - " + name, textArea(buildSummary(r))));
        addSection(section("Head Movement", movementTable(r)));
        addSection(graphSection("Head Movement Graph - " + name, r));
        finishResults();
    }

    private void showComparison(List<DiskScheduling.Result> results) {
        resetResults();

        int bestIndex = 0;
        for (int i = 1; i < results.size(); i++) {
            if (results.get(i).getTotalMovement() < results.get(bestIndex).getTotalMovement()) {
                bestIndex = i;
            }
        }

        addSection(section("Total Head Movement", new TotalBars(results, bestIndex)));

        List<Object[]> rows = new ArrayList<Object[]>();
        for (DiskScheduling.Result r : results) {
            rows.add(new Object[] {r.getAlgorithm().getLabel(), r.getTotalMovement(),
                    String.format("%.2f", r.getAverageMovement()), r.getMoves().size()});
        }
        addSection(section("Comparison", readOnlyTable(
                new String[] {"Algorithm", "Total Movement", "Average per Request", "Steps"}, rows)));

        // Service order of every algorithm
        StringBuilder orders = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            DiskScheduling.Result r = results.get(i);
            if (i > 0) {
                orders.append('\n');
            }
            orders.append(String.format("%-7s: ", r.getAlgorithm().getLabel()));
            orders.append(joinOrder(r.getServiceOrder(), " -> "));
        }
        JTextArea orderArea = UIStyles.createOutputArea(results.size(), 40);
        orderArea.setText(orders.toString());
        JScrollPane orderPane = UIStyles.createScrollPane(orderArea);
        orderPane.setPreferredSize(new Dimension(400, results.size() * 18 + 34));
        addSection(section("Service Order (requests only)", orderPane));

        DiskScheduling.Result first = results.get(0);
        DiskScheduling.Result best = results.get(bestIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("Lowest total head movement: ").append(best.getAlgorithm().getLabel())
                .append(" (").append(best.getTotalMovement()).append(" cylinders).");
        sb.append("\nHead start ").append(first.getHead()).append(", disk cylinders 0 to ")
                .append(first.getDiskSize() - 1).append(".");
        sb.append("\nDirection used by SCAN, C-SCAN, LOOK and C-LOOK: ")
                .append(first.getDirection().getLabel()).append(".");
        sb.append("\nReturn jump of C-SCAN / C-LOOK: ")
                .append(first.isReturnJumpCounted() ? "counted in the total." : "not counted in the total.");
        addSection(section("Result", textArea(sb.toString())));

        for (DiskScheduling.Result r : results) {
            addSection(graphSection("Head Movement Graph - " + r.getAlgorithm().getLabel(), r));
        }
        finishResults();
    }

    private String buildSummary(DiskScheduling.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Algorithm            : ").append(r.getAlgorithm().getLabel()).append('\n');
        sb.append("Direction            : ")
                .append(r.getAlgorithm().usesDirection() ? r.getDirection().getLabel() : "not used")
                .append('\n');
        sb.append("Head start           : ").append(r.getHead()).append('\n');
        sb.append("Disk                 : cylinders 0 to ").append(r.getDiskSize() - 1)
                .append(" (").append(r.getDiskSize()).append(" cylinders)\n");
        sb.append("Requests             : ").append(r.getRequests().size()).append('\n');
        sb.append("Total head movement  : ").append(r.getTotalMovement()).append(" cylinders\n");
        sb.append("Average per request  : ").append(String.format("%.2f", r.getAverageMovement())).append('\n');
        sb.append("Service order        :\n  ").append(joinOrder(r.getServiceOrder(), " -> "));
        return sb.toString();
    }

    /** Joins cylinder numbers, breaking the line after every 8 numbers. */
    private String joinOrder(List<Integer> order, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) {
                sb.append(i % 8 == 0 ? separator.trim() + "\n  " : separator);
            }
            sb.append(order.get(i));
        }
        return sb.toString();
    }

    private JScrollPane movementTable(DiskScheduling.Result r) {
        List<Object[]> rows = new ArrayList<Object[]>();
        int step = 1;
        for (DiskScheduling.Move m : r.getMoves()) {
            String distance = m.isCounted()
                    ? String.valueOf(m.getDistance())
                    : m.getDistance() + " (not counted)";
            rows.add(new Object[] {step++, m.getFrom(), m.getTo(), distance, m.getDescription()});
        }
        rows.add(new Object[] {"Total", "", "", r.getTotalMovement(), ""});
        return readOnlyTable(new String[] {"Step", "From", "To", "Distance", "Action"}, rows);
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

    private JPanel graphSection(String title, DiskScheduling.Result result) {
        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setOpaque(false);
        content.add(buildLegend(), BorderLayout.NORTH);
        content.add(new MovementGraph(result), BorderLayout.CENTER);
        return section(title, content);
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendItem(UIStyles.SUCCESS, "Head start"));
        legend.add(legendItem(UIStyles.PRIMARY, "Request served"));
        legend.add(legendItem(UIStyles.SECONDARY, "Disk end (no request)"));
        legend.add(legendItem(UIStyles.DANGER, "Return jump (dashed line)"));
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
    // Head movement graph
    // ------------------------------------------------------------------

    /**
     * Line graph: the horizontal axis is the cylinder number and every step
     * of the head goes one row down, so the path reads from top to bottom.
     */
    private static class MovementGraph extends JPanel {
        private static final int LEFT = 30;
        private static final int RIGHT = 30;
        private static final int TOP = 40;
        private static final int BOTTOM = 14;
        private static final int ROW = 26;

        private final DiskScheduling.Result result;
        private final List<Integer> path;

        MovementGraph(DiskScheduling.Result result) {
            this.result = result;
            this.path = result.getPath();
            setBackground(UIStyles.PANEL_BG);
            setPreferredSize(new Dimension(700, TOP + BOTTOM + (path.size() - 1) * ROW));
        }

        private double xOf(int cylinder) {
            double plotWidth = getWidth() - LEFT - RIGHT;
            int last = result.getDiskSize() - 1;
            return LEFT + (last <= 0 ? 0 : cylinder * plotWidth / last);
        }

        private int yOf(int index) {
            return TOP + index * ROW;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawAxis(g);

            List<DiskScheduling.Move> moves = result.getMoves();

            // Lines
            for (int i = 0; i < moves.size(); i++) {
                DiskScheduling.Move m = moves.get(i);
                int x1 = (int) Math.round(xOf(path.get(i)));
                int x2 = (int) Math.round(xOf(path.get(i + 1)));
                if (m.getType() == DiskScheduling.MoveType.JUMP) {
                    g.setColor(UIStyles.DANGER);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10f, new float[] {6f, 5f}, 0f));
                } else {
                    g.setColor(UIStyles.PRIMARY);
                    g.setStroke(new BasicStroke(2f));
                }
                g.drawLine(x1, yOf(i), x2, yOf(i + 1));
            }
            g.setStroke(new BasicStroke(1f));

            // Points and labels
            g.setFont(UIStyles.SMALL_FONT);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < path.size(); i++) {
                int x = (int) Math.round(xOf(path.get(i)));
                int y = yOf(i);
                Color color;
                if (i == 0) {
                    color = UIStyles.SUCCESS;
                } else {
                    DiskScheduling.Move m = moves.get(i - 1);
                    color = m.isServed() ? UIStyles.PRIMARY : UIStyles.SECONDARY;
                }
                g.setColor(color);
                g.fillOval(x - 5, y - 5, 10, 10);
                g.setColor(Color.WHITE);
                g.drawOval(x - 5, y - 5, 10, 10);

                String label = String.valueOf(path.get(i));
                int labelWidth = fm.stringWidth(label);
                int labelX = x + 9;
                if (labelX + labelWidth > getWidth() - 2) {
                    labelX = x - 9 - labelWidth;
                }
                g.setColor(UIStyles.TEXT_DARK);
                g.drawString(label, labelX, y + 4);
            }
            g.dispose();
        }

        private void drawAxis(Graphics2D g) {
            int last = result.getDiskSize() - 1;
            int divisions = Math.max(1, Math.min(10, last));
            g.setFont(UIStyles.SMALL_FONT);
            FontMetrics fm = g.getFontMetrics();
            int bottomY = getHeight() - BOTTOM + 6;
            for (int k = 0; k <= divisions; k++) {
                int value = (int) Math.round(k * last / (double) divisions);
                int x = (int) Math.round(xOf(value));
                g.setColor(UIStyles.BORDER);
                g.drawLine(x, TOP - 12, x, bottomY);
                String label = String.valueOf(value);
                int labelWidth = fm.stringWidth(label);
                int labelX = Math.max(2, Math.min(x - labelWidth / 2, getWidth() - labelWidth - 2));
                g.setColor(UIStyles.TEXT_MUTED);
                g.drawString(label, labelX, TOP - 18);
            }
        }
    }

    // ------------------------------------------------------------------
    // Total movement bars (Compare All)
    // ------------------------------------------------------------------

    private static class TotalBars extends JPanel {
        private static final int ROW = 26;

        private final List<DiskScheduling.Result> results;
        private final int bestIndex;

        TotalBars(List<DiskScheduling.Result> results, int bestIndex) {
            this.results = results;
            this.bestIndex = bestIndex;
            setBackground(UIStyles.PANEL_BG);
            setPreferredSize(new Dimension(600, 12 + results.size() * ROW));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int max = 1;
            for (DiskScheduling.Result r : results) {
                max = Math.max(max, r.getTotalMovement());
            }
            int labelWidth = 80;
            int valueWidth = 60;
            int barX = 4 + labelWidth;
            int barMax = Math.max(10, getWidth() - barX - valueWidth - 8);

            g.setFont(UIStyles.BUTTON_FONT);
            for (int i = 0; i < results.size(); i++) {
                DiskScheduling.Result r = results.get(i);
                int y = 6 + i * ROW;
                g.setColor(UIStyles.TEXT_DARK);
                g.drawString(r.getAlgorithm().getLabel(), 4, y + 15);

                int w = (int) Math.round((double) r.getTotalMovement() / max * barMax);
                g.setColor(i == bestIndex ? UIStyles.SUCCESS : UIStyles.PRIMARY);
                g.fillRect(barX, y + 2, w, 18);

                g.setColor(UIStyles.TEXT_DARK);
                g.drawString(String.valueOf(r.getTotalMovement()), barX + w + 6, y + 15);
            }
            g.dispose();
        }
    }
}