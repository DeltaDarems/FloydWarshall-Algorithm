import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for Module 2: Banker's Algorithm.
 * All calculations are done by {@link BankersAlgorithm}; this class only collects
 * input, validates it and displays the results.
 */
public class BankersAlgorithmPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_PROCESSES = 20;
    private static final int MAX_RESOURCES = 10;
    private static final int MAX_VALUE = 1000;

    private int processCount;
    private int resourceCount;

    // Setup widgets
    private final JTextField processCountField = UIStyles.createTextField(4);
    private final JTextField resourceCountField = UIStyles.createTextField(4);

    // Tables (models are replaced whenever the dimensions change)
    private final JTable allocationTable = new JTable();
    private final JTable maxTable = new JTable();
    private final JTable availableTable = new JTable();
    private final JTable needTable = new JTable();
    private final JTable stepsTable = new JTable();

    // Result widgets
    private final JLabel resultLabel = UIStyles.createLabel("Click \"Run Safety Algorithm\" to see the result.");
    private final JComboBox<String> requestProcessBox = new JComboBox<String>();
    private final JTextField requestField = UIStyles.createTextField(12);
    private final JLabel requestResultLabel = UIStyles.createMutedLabel(
            "Enter a request vector and click \"Check Request\".");

    public BankersAlgorithmPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(UIStyles.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        for (JTable t : new JTable[]{allocationTable, maxTable, availableTable, needTable, stepsTable}) {
            UIStyles.styleTable(t);
        }
        for (JTable t : new JTable[]{allocationTable, maxTable, availableTable}) {
            // Keep a value typed into a cell even if the user clicks a button right away
            t.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        }
        requestProcessBox.setFont(UIStyles.BODY_FONT);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(buildSetupCard());
        top.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));
        top.add(buildAvailableCard());

        add(top, BorderLayout.NORTH);
        add(buildMatrixRow(), BorderLayout.CENTER);
        add(buildBottomRow(), BorderLayout.SOUTH);

        processCountField.setText("5");
        resourceCountField.setText("3");
        generateTables(5, 3);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JPanel buildSetupCard() {
        JPanel card = UIStyles.createTitledPanel("Setup");
        card.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 4));

        JButton generate = UIStyles.createButton("Generate Tables");
        JButton sample = UIStyles.createSecondaryButton("Load Sample");
        JButton clear = UIStyles.createDangerButton("Clear Values");
        JButton run = UIStyles.createButton("Run Safety Algorithm");
        generate.addActionListener(e -> onGenerate());
        sample.addActionListener(e -> loadSample());
        clear.addActionListener(e -> clearValues());
        run.addActionListener(e -> runSafety());

        card.add(UIStyles.createLabel("Processes:"));
        card.add(processCountField);
        card.add(UIStyles.createLabel("Resource types:"));
        card.add(resourceCountField);
        card.add(generate);
        card.add(sample);
        card.add(clear);
        card.add(run);
        return card;
    }

    private JPanel buildAvailableCard() {
        JPanel card = UIStyles.createTitledPanel("Available Resources");
        card.setLayout(new BorderLayout());
        card.add(tableWithHeader(availableTable, 3), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMatrixRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.add(tableCard("Allocation (editable)", allocationTable));
        row.add(tableCard("Max (editable)", maxTable));
        row.add(tableCard("Need = Max - Allocation", needTable));
        return row;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 215));

        // --- Safety steps ---
        JPanel stepsCard = UIStyles.createTitledPanel("Safety Algorithm Steps");
        stepsCard.setLayout(new BorderLayout(0, 6));
        stepsCard.add(UIStyles.createScrollPane(stepsTable), BorderLayout.CENTER);
        stepsCard.add(resultLabel, BorderLayout.SOUTH);

        // --- Resource request ---
        JPanel requestCard = UIStyles.createTitledPanel("Resource Request");
        requestCard.setLayout(new GridBagLayout());
        addRequestRow(requestCard, 0, "Process:", requestProcessBox);
        addRequestRow(requestCard, 1, "Request (e.g. 1 0 2):", requestField);

        JButton check = UIStyles.createButton("Check Request");
        check.addActionListener(e -> checkRequest());
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 2;
        bc.gridwidth = 2;
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.insets = new Insets(8, 4, 6, 4);
        requestCard.add(check, bc);

        GridBagConstraints rc = new GridBagConstraints();
        rc.gridx = 0;
        rc.gridy = 3;
        rc.gridwidth = 2;
        rc.weightx = 1.0;
        rc.weighty = 1.0;
        rc.fill = GridBagConstraints.BOTH;
        rc.anchor = GridBagConstraints.NORTHWEST;
        rc.insets = new Insets(2, 4, 2, 4);
        requestCard.add(requestResultLabel, rc);

        row.add(stepsCard);
        row.add(requestCard);
        return row;
    }

    private void addRequestRow(JPanel panel, int row, String labelText, java.awt.Component field) {
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

    private JPanel tableCard(String title, JTable table) {
        JPanel card = UIStyles.createTitledPanel(title);
        card.setLayout(new BorderLayout());
        card.add(UIStyles.createScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    /** Scroll pane sized to show the header plus the given number of rows. */
    private javax.swing.JScrollPane tableWithHeader(JTable table, int rows) {
        javax.swing.JScrollPane pane = UIStyles.createScrollPane(table);
        pane.setPreferredSize(new Dimension(100, 28 + 26 * Math.max(1, rows - 2)));
        return pane;
    }

    // ------------------------------------------------------------------
    // Table setup
    // ------------------------------------------------------------------

    private void onGenerate() {
        try {
            int p = UIStyles.parseInt(processCountField.getText(), "Number of processes", 1);
            int r = UIStyles.parseInt(resourceCountField.getText(), "Number of resource types", 1);
            if (p > MAX_PROCESSES) {
                throw new IllegalArgumentException("Number of processes must be at most " + MAX_PROCESSES + ".");
            }
            if (r > MAX_RESOURCES) {
                throw new IllegalArgumentException("Number of resource types must be at most " + MAX_RESOURCES + ".");
            }
            generateTables(p, r);
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    /** Rebuilds every table for the given dimensions (input cells start at 0). */
    private void generateTables(int processes, int resources) {
        processCount = processes;
        resourceCount = resources;

        allocationTable.setModel(inputModel(processes, resources, true));
        maxTable.setModel(inputModel(processes, resources, true));
        availableTable.setModel(inputModel(1, resources, false));
        needTable.setModel(readOnlyModel(headers("Process", resources), 0));
        stepsTable.setModel(readOnlyModel(new String[]{"Step", "Process", "Need", "Work Before",
                "Allocation", "Work After"}, 0));
        int[] stepWidths = {45, 65, 85, 110, 90, 105};
        for (int c = 0; c < stepWidths.length; c++) {
            stepsTable.getColumnModel().getColumn(c).setPreferredWidth(stepWidths[c]);
        }

        for (int i = 0; i < processes; i++) {
            ((DefaultTableModel) needTable.getModel()).addRow(blankNeedRow(i, resources));
        }

        requestProcessBox.removeAllItems();
        for (int i = 0; i < processes; i++) {
            requestProcessBox.addItem(BankersAlgorithm.processName(i));
        }

        resultLabel.setText("Click \"Run Safety Algorithm\" to see the result.");
        resultLabel.setForeground(UIStyles.TEXT_DARK);
        requestResultLabel.setText("Enter a request vector and click \"Check Request\".");
        requestResultLabel.setForeground(UIStyles.TEXT_MUTED);
    }

    private Object[] blankNeedRow(int process, int resources) {
        Object[] row = new Object[resources + 1];
        row[0] = BankersAlgorithm.processName(process);
        for (int j = 1; j <= resources; j++) {
            row[j] = "-";
        }
        return row;
    }

    private String[] headers(String first, int resources) {
        String[] h = new String[resources + 1];
        h[0] = first;
        for (int j = 0; j < resources; j++) {
            h[j + 1] = BankersAlgorithm.resourceName(j);
        }
        return h;
    }

    /** Editable model: column 0 is a fixed label, all other cells hold numbers typed by the user. */
    private DefaultTableModel inputModel(int rows, int resources, boolean processRows) {
        DefaultTableModel model = new DefaultTableModel(headers(processRows ? "Process" : "", resources), 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        for (int i = 0; i < rows; i++) {
            Object[] row = new Object[resources + 1];
            row[0] = processRows ? BankersAlgorithm.processName(i) : "Available";
            for (int j = 1; j <= resources; j++) {
                row[j] = "0";
            }
            model.addRow(row);
        }
        return model;
    }

    private DefaultTableModel readOnlyModel(String[] columns, int rows) {
        return new DefaultTableModel(columns, rows) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void loadSample() {
        processCountField.setText("5");
        resourceCountField.setText("3");
        generateTables(5, 3);

        int[][] allocation = {{0, 1, 0}, {2, 0, 0}, {3, 0, 2}, {2, 1, 1}, {0, 0, 2}};
        int[][] max = {{7, 5, 3}, {3, 2, 2}, {9, 0, 2}, {2, 2, 2}, {4, 3, 3}};
        int[] available = {3, 3, 2};
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 3; j++) {
                allocationTable.getModel().setValueAt(String.valueOf(allocation[i][j]), i, j + 1);
                maxTable.getModel().setValueAt(String.valueOf(max[i][j]), i, j + 1);
            }
        }
        for (int j = 0; j < 3; j++) {
            availableTable.getModel().setValueAt(String.valueOf(available[j]), 0, j + 1);
        }
        requestField.setText("1 0 2");
        requestProcessBox.setSelectedIndex(1);
    }

    private void clearValues() {
        generateTables(processCount, resourceCount);
        requestField.setText("");
    }

    private void runSafety() {
        try {
            List<List<Integer>> allocation = readMatrix(allocationTable, "Allocation");
            List<List<Integer>> max = readMatrix(maxTable, "Max");
            List<Integer> available = readVector(availableTable, "Available");
            showSafety(BankersAlgorithm.checkSafety(allocation, max, available));
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    private void checkRequest() {
        try {
            List<List<Integer>> allocation = readMatrix(allocationTable, "Allocation");
            List<List<Integer>> max = readMatrix(maxTable, "Max");
            List<Integer> available = readVector(availableTable, "Available");

            int process = requestProcessBox.getSelectedIndex();
            if (process < 0) {
                throw new IllegalArgumentException("Select a process for the request.");
            }
            List<Integer> request = UIStyles.parseIntList(requestField.getText(), "Request", 0);
            for (int v : request) {
                if (v > MAX_VALUE) {
                    throw new IllegalArgumentException("Request values must be at most " + MAX_VALUE + ".");
                }
            }
            if (request.size() != resourceCount) {
                throw new IllegalArgumentException("Dimension mismatch: the request needs exactly "
                        + resourceCount + " values (one per resource type), but " + request.size()
                        + " were entered.");
            }

            BankersAlgorithm.RequestResult result =
                    BankersAlgorithm.requestResources(allocation, max, available, process, request);
            requestResultLabel.setText("<html><div style='width:300px'>" + result.getMessage() + "</div></html>");
            requestResultLabel.setForeground(result.isGranted() ? UIStyles.SUCCESS : UIStyles.DANGER);

            if (result.isGranted()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "The request can be granted safely.\nApply it to the Allocation and Available tables?",
                        "Request Granted", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    applyGrant(result);
                }
            }
        } catch (IllegalArgumentException ex) {
            UIStyles.showError(this, ex.getMessage());
        }
    }

    /** Writes the post-grant state back into the input tables and re-runs the safety check. */
    private void applyGrant(BankersAlgorithm.RequestResult result) {
        List<List<Integer>> newAllocation = result.getNewAllocation();
        for (int i = 0; i < newAllocation.size(); i++) {
            for (int j = 0; j < resourceCount; j++) {
                allocationTable.getModel().setValueAt(String.valueOf(newAllocation.get(i).get(j)), i, j + 1);
            }
        }
        for (int j = 0; j < resourceCount; j++) {
            availableTable.getModel().setValueAt(String.valueOf(result.getNewAvailable().get(j)), 0, j + 1);
        }
        showSafety(result.getSafety());
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private void showSafety(BankersAlgorithm.SafetyResult result) {
        DefaultTableModel need = (DefaultTableModel) needTable.getModel();
        need.setRowCount(0);
        for (int i = 0; i < result.getNeed().size(); i++) {
            Object[] row = new Object[resourceCount + 1];
            row[0] = BankersAlgorithm.processName(i);
            for (int j = 0; j < resourceCount; j++) {
                row[j + 1] = result.getNeed().get(i).get(j);
            }
            need.addRow(row);
        }

        DefaultTableModel steps = (DefaultTableModel) stepsTable.getModel();
        steps.setRowCount(0);
        for (BankersAlgorithm.Step s : result.getSteps()) {
            steps.addRow(new Object[]{s.getStepNumber(), s.getProcessName(), format(s.getNeed()),
                    format(s.getWorkBefore()), format(s.getAllocation()), format(s.getWorkAfter())});
        }

        if (result.isSafe()) {
            resultLabel.setText("SAFE state. Safe sequence: <" + result.sequenceText() + ">");
            resultLabel.setForeground(UIStyles.SUCCESS);
        } else {
            StringBuilder stuck = new StringBuilder();
            for (int index : result.getUnfinished()) {
                if (stuck.length() > 0) {
                    stuck.append(", ");
                }
                stuck.append(BankersAlgorithm.processName(index));
            }
            resultLabel.setText("UNSAFE state (possible deadlock). No safe sequence. Cannot finish: " + stuck);
            resultLabel.setForeground(UIStyles.DANGER);
        }
    }

    private static String format(List<Integer> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(values.get(i));
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // Reading and validating table input
    // ------------------------------------------------------------------

    private List<List<Integer>> readMatrix(JTable table, String name) {
        commitEdit(table);
        TableModel model = table.getModel();
        List<List<Integer>> matrix = new ArrayList<List<Integer>>();
        for (int r = 0; r < model.getRowCount(); r++) {
            List<Integer> row = new ArrayList<Integer>();
            for (int c = 1; c < model.getColumnCount(); c++) {
                row.add(readCell(model, r, c, name + " (" + model.getValueAt(r, 0) + ", "
                        + model.getColumnName(c) + ")"));
            }
            matrix.add(row);
        }
        return matrix;
    }

    private List<Integer> readVector(JTable table, String name) {
        commitEdit(table);
        TableModel model = table.getModel();
        List<Integer> row = new ArrayList<Integer>();
        for (int c = 1; c < model.getColumnCount(); c++) {
            row.add(readCell(model, 0, c, name + " " + model.getColumnName(c)));
        }
        return row;
    }

    private int readCell(TableModel model, int row, int column, String description) {
        Object raw = model.getValueAt(row, column);
        int value = UIStyles.parseInt(raw == null ? "" : raw.toString(), description, 0);
        if (value > MAX_VALUE) {
            throw new IllegalArgumentException(description + " must be at most " + MAX_VALUE + ".");
        }
        return value;
    }

    /** Stores a value that is still being typed in a cell. */
    private void commitEdit(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }
}