
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI for Module 1: CPU Scheduling.
 * All calculations are done by {@link CPUScheduling}; this class only collects
 * input, validates it and displays the results.
 */
public class CPUSchedulingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Upper limit for typed values; keeps the simulation and Gantt chart responsive. */
    private static final int MAX_VALUE = 1000;

    private static final String ALGO_FCFS = "FCFS (First Come First Serve)";
    private static final String ALGO_SJF = "SJF (Non-preemptive)";
    private static final String ALGO_SRTF = "SRTF (Preemptive)";
    private static final String ALGO_PRIORITY = "Priority (Non-preemptive)";
    private static final String ALGO_RR = "Round Robin";

    private final List<Process> processes = new ArrayList<Process>();
    private int nextId = 1;

    // Input widgets
    private final JTextField arrivalField = UIStyles.createTextField(8);
    private final JTextField burstField = UIStyles.createTextField(8);
    private final JTextField priorityField = UIStyles.createTextField(8);
    private final JTextField quantumField = UIStyles.createTextField(8);
    private final JComboBox<String> algorithmBox =
            new JComboBox<String>(
                    new String[]{ALGO_FCFS, ALGO_SJF, ALGO_SRTF, ALGO_PRIORITY, ALGO_RR});

    // Tables and result widgets
    private final DefaultTableModel processModel;
    private final JTable processTable;
    private final DefaultTableModel resultModel;
    private final GanttChartPanel ganttPanel = new GanttChartPanel();
    private final JLabel resultHeading = UIStyles.createHeadingLabel("Results");
    private final JLabel summaryLabel = UIStyles.createLabel("Run a simulation to see the averages.");

    public CPUSchedulingPanel() {
        setLayout(new BorderLayout(16, 0));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        processModel = new DefaultTableModel(
                new String[]{"Process", "Arrival", "Burst", "Priority"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        processTable = new JTable(processModel);
        UIStyles.styleTable(processTable);

        resultModel = new DefaultTableModel(
                new String[]{"Process", "Arrival", "Burst", "Priority", "Start",
                        "Completion", "Turnaround", "Waiting"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        add(buildLeftColumn(), BorderLayout.WEST);
        add(buildRightColumn(), BorderLayout.CENTER);

        quantumField.setText("2");
        priorityField.setText("1");
        updateQuantumState();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(390, 100));

        // --- Add process ---
        JPanel inputPanel = UIStyles.createTitledPanel("Add Process");
        inputPanel.setLayout(new GridBagLayout());
        addRow(inputPanel, 0, "Arrival time:", arrivalField);
        addRow(inputPanel, 1, "Burst time:", burstField);
        addRow(inputPanel, 2, "Priority (lower = higher):", priorityField);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.setOpaque(false);
        JButton addButton = UIStyles.createButton("Add Process");
        JButton removeButton = UIStyles.createSecondaryButton("Remove Selected");
        JButton clearButton = UIStyles.createDangerButton("Clear All");
        JButton sampleButton = UIStyles.createSecondaryButton("Load Sample");
        addButton.addActionListener(e -> addProcess());
        removeButton.addActionListener(e -> removeSelected());
        clearButton.addActionListener(e -> clearAll());
        sampleButton.addActionListener(e -> loadSample());
        buttons.add(addButton);
        buttons.add(removeButton);
        buttons.add(clearButton);
        buttons.add(sampleButton);

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 3;
        bc.gridwidth = 2;
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.insets = new Insets(10, 4, 4, 4);
        inputPanel.add(buttons, bc);

        // --- Process list ---
        JPanel listPanel = UIStyles.createTitledPanel("Process List");
        listPanel.setLayout(new BorderLayout());
        listPanel.add(UIStyles.createScrollPane(processTable), BorderLayout.CENTER);

        // --- Algorithm ---
        JPanel algoPanel = UIStyles.createTitledPanel("Algorithm");
        algoPanel.setLayout(new GridBagLayout());
        algorithmBox.setFont(UIStyles.BODY_FONT);
        algorithmBox.addActionListener(e -> updateQuantumState());
        addRow(algoPanel, 0, "Algorithm:", algorithmBox);
        addRow(algoPanel, 1, "Time quantum (RR):", quantumField);

        JButton runButton = UIStyles.createButton("Run Simulation");
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridx = 0;
        rc.gridy = 2;
        rc.gridwidth = 2;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.insets = new Insets(10, 4, 4, 4);
        runButton.addActionListener(e -> runSimulation());
        algoPanel.add(runButton, rc);

        left.add(inputPanel);
        left.add(javax.swing.Box.createRigidArea(new Dimension(0, 12)));
        left.add(listPanel);
        left.add(javax.swing.Box.createRigidArea(new Dimension(0, 12)));
        left.add(algoPanel);

        inputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        algoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return left;
    }

    private JPanel buildRightColumn() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);

        // --- Gantt chart ---
        JPanel ganttCard = UIStyles.createTitledPanel("Gantt Chart");
        ganttCard.setLayout(new BorderLayout());
        JScrollPane ganttScroll = UIStyles.createScrollPane(ganttPanel);
        ganttScroll.setPreferredSize(new Dimension(100, 125));
        ganttCard.add(ganttScroll, BorderLayout.CENTER);
        right.add(ganttCard, BorderLayout.NORTH);

        // --- Results table ---
        JPanel resultsCard = UIStyles.createTitledPanel("Results");
        resultsCard.setLayout(new BorderLayout(0, 8));

        JTable resultTable = new JTable(resultModel);
        UIStyles.styleTable(resultTable);

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setOpaque(false);
        JLabel formula = UIStyles.createMutedLabel(
                "Turnaround = Completion - Arrival      Waiting = Turnaround - Burst");
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formula.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(summaryLabel);
        summary.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
        summary.add(formula);

        resultsCard.add(resultHeading, BorderLayout.NORTH);
        resultsCard.add(UIStyles.createScrollPane(resultTable), BorderLayout.CENTER);
        resultsCard.add(summary, BorderLayout.SOUTH);
        right.add(resultsCard, BorderLayout.CENTER);

        return right;
    }

    private void addRow(JPanel panel, int row, String labelText, Component field) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 4, 4, 8);
        panel.add(UIStyles.createLabel(labelText), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridy = row;
        fc.weightx = 1.0;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(4, 0, 4, 4);
        panel.add(field, fc);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void updateQuantumState() {
        boolean roundRobin = ALGO_RR.equals(algorithmBox.getSelectedItem());
        quantumField.setEnabled(roundRobin);
    }

    private void addProcess() {
        try {
            int arrival = readBounded(arrivalField, "Arrival time", 0);
            int burst = readBounded(burstField, "Burst time", 1);
            int priority = readBounded(priorityField, "Priority", 0);

            Process p = new Process("P" + nextId, arrival, burst, priority);
            processes.add(p);
            processModel.addRow(new Object[]{p.getId(), p.getArrivalTime(),
                    p.getBurstTime(), p.getPriority()});
            nextId++;

            arrivalField.setText("");
            burstField.setText("");
            arrivalField.requestFocusInWindow();
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void removeSelected() {
        int row = processTable.getSelectedRow();
        if (row < 0) {
            UIStyles.showWarning(this, "Select a process in the list first.");
            return;
        }
        processes.remove(row);
        processModel.removeRow(row);
    }

    private void clearAll() {
        processes.clear();
        processModel.setRowCount(0);
        nextId = 1;
        clearResults();
    }

    private void loadSample() {
        clearAll();
        int[][] sample = {{0, 5, 2}, {1, 3, 1}, {2, 8, 4}, {3, 6, 3}};
        for (int[] row : sample) {
            Process p = new Process("P" + nextId, row[0], row[1], row[2]);
            processes.add(p);
            processModel.addRow(new Object[]{p.getId(), p.getArrivalTime(),
                    p.getBurstTime(), p.getPriority()});
            nextId++;
        }
    }

    private void runSimulation() {
        if (processes.isEmpty()) {
            UIStyles.showError(this, "Add at least one process before running the simulation.");
            return;
        }

        try {
            String selected = (String) algorithmBox.getSelectedItem();
            CPUScheduling.Result result;

            if (ALGO_FCFS.equals(selected)) {
                result = CPUScheduling.fcfs(processes);
            } else if (ALGO_SJF.equals(selected)) {
                result = CPUScheduling.sjf(processes);
            } else if (ALGO_SRTF.equals(selected)) {
                result = CPUScheduling.srtf(processes);
            } else if (ALGO_PRIORITY.equals(selected)) {
                result = CPUScheduling.priority(processes);
            } else {
                int quantum = readBounded(quantumField, "Time quantum", 1);
                result = CPUScheduling.roundRobin(processes, quantum);
            }
            showResult(result);
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private void showResult(CPUScheduling.Result result) {
        ganttPanel.setEntries(result.getGantt());
        resultHeading.setText("Results - " + result.getAlgorithmName());

        resultModel.setRowCount(0);
        for (Process p : result.getProcesses()) {
            resultModel.addRow(new Object[]{p.getId(), p.getArrivalTime(), p.getBurstTime(),
                    p.getPriority(), p.getStartTime(), p.getCompletionTime(),
                    p.getTurnaroundTime(), p.getWaitingTime()});
        }

        summaryLabel.setText(String.format(
                "Average Waiting Time: %.2f     Average Turnaround Time: %.2f     "
                        + "Total Time: %d     CPU Utilization: %.1f%%",
                result.getAverageWaitingTime(), result.getAverageTurnaroundTime(),
                result.getTotalTime(), result.getCpuUtilization()));
    }

    private void clearResults() {
        ganttPanel.setEntries(new ArrayList<CPUScheduling.GanttEntry>());
        resultModel.setRowCount(0);
        resultHeading.setText("Results");
        summaryLabel.setText("Run a simulation to see the averages.");
    }

    /** Reads a whole number from a field and checks it is between min and MAX_VALUE. */
    private int readBounded(JTextField field, String name, int min) {
        int value = UIStyles.parseInt(field.getText(), name, min);
        if (value > MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be at most " + MAX_VALUE + ".");
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Gantt chart component
    // ------------------------------------------------------------------

    /** Draws the Gantt chart: one coloured block per entry with time marks below. */
    private static class GanttChartPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private static final Color[] PALETTE = {
                new Color(0x2F5DA8), new Color(0x2E8B57), new Color(0xC0392B),
                new Color(0x8E44AD), new Color(0xD68910), new Color(0x138D90),
                new Color(0x7D6608), new Color(0x5D6D7E)
        };
        private static final Color IDLE_COLOR = new Color(0xD5DAE1);

        private static final int LEFT_MARGIN = 20;
        private static final int BLOCK_TOP = 18;
        private static final int BLOCK_HEIGHT = 44;

        private List<CPUScheduling.GanttEntry> entries = new ArrayList<CPUScheduling.GanttEntry>();
        private final Map<String, Color> colors = new HashMap<String, Color>();
        private int scale = 30;

        GanttChartPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(100, 95));
        }

        void setEntries(List<CPUScheduling.GanttEntry> newEntries) {
            entries = new ArrayList<CPUScheduling.GanttEntry>(newEntries);
            colors.clear();

            int paletteIndex = 0;
            for (CPUScheduling.GanttEntry entry : entries) {
                if (!entry.isIdle() && !colors.containsKey(entry.getProcessId())) {
                    colors.put(entry.getProcessId(), PALETTE[paletteIndex % PALETTE.length]);
                    paletteIndex++;
                }
            }

            int total = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).getEnd();
            scale = total == 0 ? 30 : Math.max(12, Math.min(30, 1200 / total));
            setPreferredSize(new Dimension(LEFT_MARGIN * 2 + total * scale, 95));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(UIStyles.BODY_FONT);
            FontMetrics fm = g2.getFontMetrics();

            if (entries.isEmpty()) {
                g2.setColor(UIStyles.TEXT_MUTED);
                g2.drawString("Run a simulation to see the Gantt chart here.",
                        LEFT_MARGIN, BLOCK_TOP + BLOCK_HEIGHT / 2);
                g2.dispose();
                return;
            }

            int timeLabelY = BLOCK_TOP + BLOCK_HEIGHT + 20;
            int lastLabelRight = Integer.MIN_VALUE;

            for (CPUScheduling.GanttEntry entry : entries) {
                int x = LEFT_MARGIN + entry.getStart() * scale;
                int width = (entry.getEnd() - entry.getStart()) * scale;

                g2.setColor(entry.isIdle() ? IDLE_COLOR : colors.get(entry.getProcessId()));
                g2.fillRect(x, BLOCK_TOP, width, BLOCK_HEIGHT);
                g2.setColor(UIStyles.TEXT_DARK);
                g2.drawRect(x, BLOCK_TOP, width, BLOCK_HEIGHT);

                String label = entry.getProcessId();
                int labelWidth = fm.stringWidth(label);
                if (labelWidth + 4 <= width) {
                    g2.setColor(entry.isIdle() ? UIStyles.TEXT_DARK : Color.WHITE);
                    g2.drawString(label, x + (width - labelWidth) / 2,
                            BLOCK_TOP + (BLOCK_HEIGHT + fm.getAscent()) / 2 - 2);
                }

                lastLabelRight = drawTime(g2, fm, entry.getStart(), x, timeLabelY, lastLabelRight);
            }

            CPUScheduling.GanttEntry last = entries.get(entries.size() - 1);
            drawTime(g2, fm, last.getEnd(), LEFT_MARGIN + last.getEnd() * scale,
                    timeLabelY, lastLabelRight);
            g2.dispose();
        }

        /** Draws a time mark centred at x unless it would overlap the previous one. */
        private int drawTime(Graphics2D g2, FontMetrics fm, int time, int x, int y, int lastRight) {
            String text = String.valueOf(time);
            int textWidth = fm.stringWidth(text);
            int textX = x - textWidth / 2;
            if (textX > lastRight + 4) {
                g2.setColor(UIStyles.TEXT_DARK);
                g2.drawString(text, textX, y);
                return textX + textWidth;
            }
            return lastRight;
        }
    }
}