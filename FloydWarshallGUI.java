import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * ============================================================================
 *  FLOYD-WARSHALL ALL-PAIRS SHORTEST PATHS  -  Swing GUI edition
 * ============================================================================
 *  Same from-scratch algorithm as the console version (no java.util graph
 *  classes, no sort helpers; ArrayList is used only as the edge-list container).
 *  The console menu is replaced by a window:
 *    - left:   build the graph (vertices, edges, edge table)
 *    - centre: graph canvas (drag vertices; path highlighting)
 *    - bottom: step-through distance matrices, next-hop matrix, path queries, log
 *    - menus:  built-in test suite, restrictions
 *
 *  RESTRICTIONS / EDGE-CASE RULES
 *    1. Directed, weighted graph; vertices numbered 0..n-1.
 *    2. Weights are whole numbers, |w| <= 1,000,000,000 (no long overflow).
 *    3. Negative weights allowed; negative CYCLES are detected and reported.
 *    4. No edge = INF (unreachable). Disconnected graphs are fine.
 *    5. Re-entering an edge (u,v) overwrites its weight (no parallel edges).
 *    6. Positive self-loops are harmless; a negative self-loop is a negative cycle.
 *    7. 0 vertices = nothing to compute; 1 vertex = trivial [[0]].
 *    8. GUI caps the graph at 1000 vertices (time O(V^3), memory 2 n x n matrices).
 *    9. Matrices wider than 30 are not drawn, and the canvas stops at 60 vertices.
 *
 *  Compile:  javac FloydWarshallGUI.java
 *  Run:      java FloydWarshallGUI            (window)
 *            java FloydWarshallGUI --selftest (headless test suite)
 * ============================================================================
 */
public class FloydWarshallGUI extends JFrame {

    // =====================================================================
    //  CONSTANTS
    // =====================================================================

    // ---- algorithm limits ----
    static final long INF = Long.MAX_VALUE / 4;
    static final long MAX_ABS_WEIGHT = 1_000_000_000L;
    static final int DISPLAY_LIMIT = 30;     // matrices wider than this are not drawn
    static final int GRAPH_DRAW_LIMIT = 60;  // canvas stops drawing beyond this
    static final int GUI_MAX_VERTICES = 1000;

    // ---- display symbols ----
    private static final String INFINITY_SYMBOL = "\u221E";  // shown for "no path"
    private static final String NO_HOP_SYMBOL = "\u00B7";    // shown for "no next hop"

    // ---- colours ----
    private static final Color COLOR_ERROR = new Color(0xB71C1C);
    private static final Color COLOR_SUCCESS = new Color(0x1B5E20);
    private static final Color COLOR_HEADER_CELL = new Color(0xE3E8EF);
    private static final Color COLOR_CHANGED_CELL = new Color(0xFFE08A);
    private static final Color COLOR_DIAGONAL_CELL = new Color(0xF2F2F2);
    private static final Color COLOR_NEGATIVE_TEXT = new Color(0xC0392B);
    private static final Color COLOR_NEGATIVE_DIAGONAL = new Color(0xFFB3B3);
    private static final Color COLOR_VERTEX = new Color(0xCFE3FF);
    private static final Color COLOR_VERTEX_CYCLE = new Color(0xFFB3B3);
    private static final Color COLOR_VERTEX_ON_PATH = new Color(0xFFD59A);
    private static final Color COLOR_VERTEX_OUTLINE = new Color(0x34495E);
    private static final Color COLOR_EDGE = new Color(0x7F8C8D);
    private static final Color COLOR_EDGE_NEGATIVE = new Color(0xC0392B);
    private static final Color COLOR_EDGE_ON_PATH = new Color(0xE67E22);

    static final String RESTRICTIONS =
            "ALGORITHM RESTRICTIONS & EDGE-CASE RULES\n\n"
            + "1. Directed weighted graph; vertices are numbered 0..n-1.\n"
            + "2. Weights: whole numbers, |w| <= " + MAX_ABS_WEIGHT + " (prevents long overflow).\n"
            + "3. Negative weights OK. Negative CYCLES are detected and reported.\n"
            + "4. No edge = INF (unreachable). Disconnected graphs are fine.\n"
            + "5. Re-entering an existing edge (u,v) overwrites its weight.\n"
            + "6. Positive self-loops are harmless; a negative self-loop is a negative cycle.\n"
            + "7. 0 vertices = nothing to compute; 1 vertex = trivial [[0]].\n"
            + "8. GUI limit: " + GUI_MAX_VERTICES + " vertices (time O(V^3), space O(V^2)).\n"
            + "9. Matrices wider than " + DISPLAY_LIMIT + " are not drawn; the canvas stops at "
            + GRAPH_DRAW_LIMIT + " vertices (display only, computation is unaffected).\n";

    // =====================================================================
    //  STATE
    // =====================================================================
    private Graph graph = new Graph(0);
    private Result result = null;                           // null = not computed / out of date
    private ArrayList<Integer> highlight = new ArrayList<>(); // vertices of the path being highlighted
    private int stepIndex = 0;                              // snapshot currently shown
    private boolean[][] changed = new boolean[0][0];        // cells changed in the current step
    private boolean sliderAdjusting = false;                // true while code (not the user) moves the slider
    private Timer playTimer;

    // =====================================================================
    //  WIDGETS
    // =====================================================================

    // ---- graph / edge input ----
    private final JTextField nField = new JTextField("4", 5);
    private final JTextField fromField = new JTextField(4);
    private final JTextField toField = new JTextField(4);
    private final JTextField weightField = new JTextField(6);
    private final JLabel graphInfo = new JLabel();
    private final EdgeTableModel edgeModel = new EdgeTableModel();
    private final JTable edgeTable = new JTable(edgeModel);

    // ---- canvas and status bar ----
    private final GraphPanel graphPanel = new GraphPanel();
    private final JLabel status = new JLabel(" ");

    // ---- distance matrix tab ----
    private final DefaultTableModel matrixModel = new ReadOnlyTableModel();
    private final JTable matrixTable = new JTable(matrixModel);
    private final JLabel stepLabel = new JLabel("Not computed yet - press Run.");
    private final JSlider stepSlider = new JSlider(0, 0, 0);
    private final JTextArea passLog = new JTextArea(4, 40);
    private final JButton playButton = new JButton("Play");

    // ---- next-hop tab ----
    private final DefaultTableModel nextModel = new ReadOnlyTableModel();
    private final JTable nextTable = new JTable(nextModel);

    // ---- paths tab ----
    private final JTextField qFrom = new JTextField(4);
    private final JTextField qTo = new JTextField(4);
    private final JLabel pathResult = new JLabel(" ");
    private final JTextArea allPaths = new JTextArea();

    // ---- log tab ----
    private final JTextArea logArea = new JTextArea();

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equals("--selftest")) {
                String report = runAllTestsReport();
                System.out.println(report);
                System.exit(report.contains("FAIL") ? 1 : 0);
            }
        }
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            new FloydWarshallGUI().setVisible(true);
        });
    }

    // =====================================================================
    //  WINDOW CONSTRUCTION
    // =====================================================================
    FloydWarshallGUI() {
        super("Floyd-Warshall All-Pairs Shortest Paths");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        setJMenuBar(buildMenuBar());
        add(buildControlPanel(), BorderLayout.WEST);

        JTabbedPane tabs = buildTabs();
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, graphPanel, tabs);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);

        // Auto-play: advance one pass every 700 ms until the last snapshot.
        playTimer = new Timer(700, e -> {
            if (result == null || stepIndex >= result.snapshots.size() - 1) { stopPlay(); return; }
            showStep(stepIndex + 1);
        });

        loadGraph(buildSuite().get(0).graph, "Loaded the classic 4-vertex example. Press Run Floyd-Warshall.");
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        split.setDividerLocation(360);
    }

    // ---------------------------------------------------------------------
    //  Menu bar
    // ---------------------------------------------------------------------
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(buildTestMenu());
        bar.add(buildHelpMenu());
        return bar;
    }

    private JMenu buildTestMenu() {
        JMenu menu = new JMenu("Test suite");

        for (TestCase testCase : buildSuite()) {
            JMenuItem item = new JMenuItem("Load: " + testCase.name);
            final String name = testCase.name;
            final String purpose = testCase.purpose;
            item.addActionListener(ev -> {
                // Rebuild the suite so each load gets a fresh Graph object.
                for (TestCase fresh : buildSuite()) {
                    if (fresh.name.equals(name)) { loadGraph(fresh.graph, name + " - " + purpose); break; }
                }
            });
            menu.add(item);
        }

        menu.addSeparator();

        JMenuItem runAll = new JMenuItem("Run ALL tests (PASS/FAIL summary)...");
        runAll.addActionListener(ev -> showTextDialog("Test suite results", runAllTestsReport()));
        menu.add(runAll);

        JMenuItem invalidDemo = new JMenuItem("Invalid-input validation demo...");
        invalidDemo.addActionListener(ev -> {
            StringBuilder sb = new StringBuilder();
            boolean ok = runInvalidInputDemo(sb);
            showTextDialog("Invalid input demo", sb + "\nRESULT: " + (ok ? "PASS" : "FAIL"));
        });
        menu.add(invalidDemo);
        return menu;
    }

    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");

        JMenuItem restrictions = new JMenuItem("Restrictions & edge cases...");
        restrictions.addActionListener(ev -> showTextDialog("Restrictions", RESTRICTIONS));
        menu.add(restrictions);

        JMenuItem howItWorks = new JMenuItem("How the algorithm works...");
        howItWorks.addActionListener(ev -> showTextDialog("Floyd-Warshall",
                "For every possible intermediate vertex k (in order 0..n-1), check for every pair (i, j)\n"
                + "whether going i -> k -> j is cheaper than the best known i -> j.\n\n"
                + "After pass k, dist[i][j] is optimal among paths that only use intermediate vertices {0..k}\n"
                + "(the dynamic-programming invariant). Use the step controls under 'Distance matrix' to watch\n"
                + "each pass; changed cells are highlighted.\n\n"
                + "A next-hop matrix is kept so any path can be rebuilt using O(n^2) memory.\n"
                + "Time O(V^3), space O(V^2). A negative diagonal entry means a negative cycle."));
        menu.add(howItWorks);
        return menu;
    }

    // ---------------------------------------------------------------------
    //  Left control panel (graph, edges, run)
    // ---------------------------------------------------------------------
    private JPanel buildControlPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
        left.setPreferredSize(new Dimension(320, 100));

        left.add(buildGraphSection());
        left.add(buildEdgeSection());
        left.add(buildComputeSection());
        left.add(Box.createVerticalGlue());
        return left;
    }

    private JPanel buildGraphSection() {
        JPanel panel = titledPanel("Graph");
        JButton newGraphButton = new JButton("New graph");
        newGraphButton.addActionListener(e -> createNewGraph());
        JButton addVertexButton = new JButton("Add vertex");
        addVertexButton.addActionListener(e -> addVertex());

        panel.add(flowRow(new JLabel("Vertices:"), nField, newGraphButton));
        panel.add(flowRow(addVertexButton, graphInfo));
        return panel;
    }

    private JPanel buildEdgeSection() {
        JPanel panel = titledPanel("Edges (directed)");
        JButton addEdgeButton = new JButton("Add / update");
        addEdgeButton.addActionListener(e -> addEdge());
        JButton removeEdgeButton = new JButton("Remove (from,to)");
        removeEdgeButton.addActionListener(e -> removeEdgeFromFields());

        panel.add(flowRow(new JLabel("from"), fromField, new JLabel("to"), toField, new JLabel("w"), weightField));
        panel.add(flowRow(addEdgeButton, removeEdgeButton));

        // Selecting a table row copies that edge into the input boxes for editing.
        edgeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        edgeTable.getSelectionModel().addListSelectionListener(e -> {
            int row = edgeTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0 && row < graph.edgeCount()) {
                Edge selected = graph.edges().get(row);
                fromField.setText(Integer.toString(selected.from));
                toField.setText(Integer.toString(selected.to));
                weightField.setText(Long.toString(selected.weight));
            }
        });
        JScrollPane tableScroll = new JScrollPane(edgeTable);
        tableScroll.setPreferredSize(new Dimension(280, 200));
        panel.add(tableScroll);

        JLabel hint = new JLabel("<html><small>Click a row to edit it. Weight may be negative.</small></html>");
        panel.add(flowRow(hint));
        return panel;
    }

    private JPanel buildComputeSection() {
        JPanel panel = titledPanel("Compute");
        JButton runButton = new JButton("Run Floyd-Warshall");
        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD, 14f));
        runButton.addActionListener(e -> runAlgorithm());
        panel.add(flowRow(runButton));
        return panel;
    }

    private JPanel titledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /** A left-aligned row of components. */
    private static JPanel flowRow(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        for (Component c : components) panel.add(c);
        return panel;
    }

    // ---------------------------------------------------------------------
    //  Bottom tabs
    // ---------------------------------------------------------------------
    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Distance matrix (step-by-step)", buildDistanceMatrixTab());

        styleMatrixTable(nextTable, false);
        tabs.addTab("Next-hop matrix", new JScrollPane(nextTable));

        tabs.addTab("Paths", buildPathsTab());

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Log", new JScrollPane(logArea));
        return tabs;
    }

    private JPanel buildDistanceMatrixTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        styleMatrixTable(matrixTable, true);
        panel.add(new JScrollPane(matrixTable), BorderLayout.CENTER);

        JButton firstButton = new JButton("|<");
        JButton previousButton = new JButton("<");
        JButton nextButton = new JButton(">");
        JButton lastButton = new JButton(">|");
        firstButton.addActionListener(e -> { stopPlay(); showStep(0); });
        previousButton.addActionListener(e -> { stopPlay(); showStep(stepIndex - 1); });
        nextButton.addActionListener(e -> { stopPlay(); showStep(stepIndex + 1); });
        lastButton.addActionListener(e -> {
            stopPlay();
            if (result != null) showStep(result.snapshots.size() - 1);
        });
        playButton.addActionListener(e -> togglePlay());
        stepSlider.addChangeListener(e -> { if (!sliderAdjusting) showStep(stepSlider.getValue()); });

        JPanel stepButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        stepButtons.add(firstButton);
        stepButtons.add(previousButton);
        stepButtons.add(nextButton);
        stepButtons.add(lastButton);
        stepButtons.add(playButton);
        stepButtons.add(stepSlider);
        stepButtons.add(stepLabel);

        passLog.setEditable(false);
        passLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel stepControls = new JPanel(new BorderLayout(4, 4));
        stepControls.add(stepButtons, BorderLayout.NORTH);
        stepControls.add(new JScrollPane(passLog), BorderLayout.CENTER);
        stepControls.setPreferredSize(new Dimension(100, 140));
        panel.add(stepControls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPathsTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JButton showPathButton = new JButton("Show path");
        showPathButton.addActionListener(e -> queryPath());
        JButton clearButton = new JButton("Clear highlight");
        clearButton.addActionListener(e -> { highlight = new ArrayList<>(); graphPanel.repaint(); });

        JPanel queryBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        queryBar.add(new JLabel("From:"));
        queryBar.add(qFrom);
        queryBar.add(new JLabel("To:"));
        queryBar.add(qTo);
        queryBar.add(showPathButton);
        queryBar.add(clearButton);
        queryBar.add(pathResult);
        panel.add(queryBar, BorderLayout.NORTH);

        allPaths.setEditable(false);
        allPaths.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(allPaths), BorderLayout.CENTER);
        return panel;
    }

    private void styleMatrixTable(JTable table, boolean isDistanceTable) {
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new MatrixRenderer(isDistanceTable));
    }

    // =====================================================================
    //  USER ACTIONS
    // =====================================================================
    private void createNewGraph() {
        try {
            int n = Integer.parseInt(nField.getText().trim());
            if (n < 0 || n > GUI_MAX_VERTICES) {
                showError("Vertex count must be between 0 and " + GUI_MAX_VERTICES + ".");
                return;
            }
            loadGraph(new Graph(n), "Created graph with " + n + " vertices and no edges.");
        } catch (NumberFormatException ex) {
            showError("'" + nField.getText().trim() + "' is not a valid whole number.");
        }
    }

    private void addVertex() {
        if (graph.vertexCount() >= GUI_MAX_VERTICES) {
            showError("The GUI is limited to " + GUI_MAX_VERTICES + " vertices.");
            return;
        }
        graph.addVertex();
        invalidateResult();
        showInfo("Added vertex " + (graph.vertexCount() - 1) + ".");
    }

    private void addEdge() {
        if (graph.vertexCount() == 0) {
            showError("The graph has no vertices. Create one or add a vertex first.");
            return;
        }
        try {
            int from = Integer.parseInt(fromField.getText().trim());
            int to = Integer.parseInt(toField.getText().trim());
            long weight = Long.parseLong(weightField.getText().trim());
            boolean overwritten = graph.addOrUpdateEdge(from, to, weight);
            invalidateResult();
            showInfo((overwritten ? "Updated" : "Added") + " edge " + from + " -> " + to
                    + " (weight " + weight + ")" + (weight < 0 ? "  [negative weight]" : ""));
        } catch (NumberFormatException ex) {
            showError("from, to and weight must all be whole numbers.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void removeEdgeFromFields() {
        try {
            int from = Integer.parseInt(fromField.getText().trim());
            int to = Integer.parseInt(toField.getText().trim());
            if (graph.removeEdge(from, to)) {
                invalidateResult();
                showInfo("Removed edge " + from + " -> " + to + ".");
            } else {
                showError("Edge " + from + " -> " + to + " does not exist; nothing changed.");
            }
        } catch (NumberFormatException ex) {
            showError("Enter whole numbers in the from and to boxes.");
        }
    }

    private void runAlgorithm() {
        stopPlay();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            result = FloydWarshall.run(graph, true);
        } catch (OutOfMemoryError oom) {
            result = null;
            showError("Not enough memory for n x n matrices with n = " + graph.vertexCount() + ".");
            return;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        highlight = new ArrayList<>();
        showResult();
    }

    private void queryPath() {
        if (result == null) { showError("No up-to-date result. Press Run first."); return; }
        if (graph.vertexCount() == 0) { showError("The graph is empty; there are no paths."); return; }
        if (result.negativeCycle) { showError("A negative cycle was found, so shortest paths are undefined."); return; }
        try {
            int from = Integer.parseInt(qFrom.getText().trim());
            int to = Integer.parseInt(qTo.getText().trim());
            int maxVertex = graph.vertexCount() - 1;
            if (from < 0 || from > maxVertex || to < 0 || to > maxVertex) {
                showError("Vertices must be in 0.." + maxVertex + ".");
                return;
            }
            pathResult.setText(describePath(result, graph, from, to));
            highlight = pathVertices(result, from, to);
            graphPanel.repaint();
            status.setText(" ");
        } catch (NumberFormatException ex) {
            showError("Enter whole numbers for source and target.");
        }
    }

    private void loadGraph(Graph newGraph, String message) {
        graph = newGraph;
        nField.setText(Integer.toString(newGraph.vertexCount()));
        graphPanel.resetLayout();
        invalidateResult();
        showInfo(message);
    }

    // ---- auto-play ----
    private void togglePlay() {
        if (playTimer.isRunning()) { stopPlay(); return; }
        if (result == null || result.snapshots.isEmpty()) return;
        if (stepIndex >= result.snapshots.size() - 1) showStep(0);
        playButton.setText("Pause");
        playTimer.start();
    }

    private void stopPlay() {
        if (playTimer != null) playTimer.stop();
        playButton.setText("Play");
    }

    // =====================================================================
    //  VIEW UPDATES
    // =====================================================================

    /** Clears everything derived from a previous run (called whenever the graph changes). */
    private void invalidateResult() {
        stopPlay();
        result = null;
        highlight = new ArrayList<>();
        matrixModel.setDataVector(new Object[0][0], new Object[0]);
        nextModel.setDataVector(new Object[0][0], new Object[0]);
        changed = new boolean[0][0];

        sliderAdjusting = true;
        stepSlider.setMaximum(0);
        stepSlider.setValue(0);
        stepSlider.setEnabled(false);
        sliderAdjusting = false;

        stepLabel.setForeground(defaultLabelColor());
        stepLabel.setText("Not computed yet - press Run.");
        passLog.setText("");
        allPaths.setText("");
        pathResult.setText(" ");
        logArea.setText("");
        refreshGraphInfo();
        edgeModel.fireTableDataChanged();
        graphPanel.repaint();
    }

    /** Fills every output widget after a run. */
    private void showResult() {
        Result r = result;
        refreshGraphInfo();
        logArea.setText(r.log.toString());
        int n = graph.vertexCount();

        // EDGE CASE: nothing to show for an empty graph.
        if (n == 0) {
            stepLabel.setText("Empty graph - nothing to compute.");
            status.setForeground(COLOR_SUCCESS);
            status.setText("Edge case: 0 vertices, nothing to compute.");
            graphPanel.repaint();
            return;
        }

        showFinalSnapshot(r);

        if (r.negativeCycle) {
            status.setForeground(COLOR_ERROR);
            status.setText("NEGATIVE CYCLE detected through vertex " + r.cycleVertex
                    + (r.detectedAtPass >= 0 ? " after pass k = " + r.detectedAtPass : " (negative self-loop)")
                    + ". Shortest paths are undefined.");
            allPaths.setText("A negative cycle was found; shortest paths are undefined.");
        } else {
            status.setForeground(COLOR_SUCCESS);
            status.setText("Done. No negative cycle. " + r.updates + " shorter routes found.");
            fillNextTable(r);
            fillAllPathsList(r, n);
        }
        graphPanel.repaint();
    }

    /** Jumps the step viewer to the last snapshot, or explains why no matrix is drawn. */
    private void showFinalSnapshot(Result r) {
        if (r.snapshots.isEmpty()) {
            stepLabel.setText("Matrices wider than " + DISPLAY_LIMIT + " are not shown; use the Paths tab.");
            matrixModel.setDataVector(new Object[0][0], new Object[0]);
            nextModel.setDataVector(new Object[0][0], new Object[0]);
            return;
        }
        sliderAdjusting = true;
        stepSlider.setEnabled(true);
        stepSlider.setMaximum(r.snapshots.size() - 1);
        sliderAdjusting = false;
        showStep(r.snapshots.size() - 1);
    }

    private void fillAllPathsList(Result r, int n) {
        if (n > 40) {
            allPaths.setText("Graph has more than 40 vertices; use the query boxes above.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (i != j) sb.append(describePath(r, graph, i, j)).append('\n');
        allPaths.setText(sb.toString());
        allPaths.setCaretPosition(0);
    }

    /** Displays snapshot {@code index}: D(-1) is the initial matrix, index k+1 is the state after pass k. */
    private void showStep(int index) {
        if (result == null || result.snapshots.isEmpty()) return;
        index = Math.max(0, Math.min(index, result.snapshots.size() - 1));
        stepIndex = index;

        long[][] current = result.snapshots.get(index);
        long[][] previous = index > 0 ? result.snapshots.get(index - 1) : null;
        int n = current.length;

        // Remember which cells differ from the previous snapshot so the renderer can highlight them.
        changed = new boolean[n][n];
        if (previous != null) {
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) changed[i][j] = current[i][j] != previous[i][j];
        }

        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = Integer.toString(i);
            for (int j = 0; j < n; j++) {
                data[i][j + 1] = current[i][j] == INF ? INFINITY_SYMBOL : Long.toString(current[i][j]);
            }
        }
        matrixModel.setDataVector(data, matrixColumnNames(n));
        sizeColumns(matrixTable, 56);

        sliderAdjusting = true;
        stepSlider.setValue(index);
        sliderAdjusting = false;

        boolean isNegativeCycleStep = result.negativeCycle && index == result.snapshots.size() - 1;
        String label = (index == 0)
                ? "D(-1): initial (direct edges)"
                : "after pass k = " + (index - 1) + "  (" + index + "/" + (result.snapshots.size() - 1) + ")";
        if (isNegativeCycleStep) label += "  - NEGATIVE CYCLE";
        stepLabel.setText(label);
        stepLabel.setForeground(isNegativeCycleStep ? COLOR_ERROR : defaultLabelColor());

        StringBuilder sb = new StringBuilder();
        for (String line : result.passLogs.get(index)) sb.append(line).append('\n');
        passLog.setText(sb.toString());
        passLog.setCaretPosition(0);
    }

    private void fillNextTable(Result r) {
        int n = r.next.length;
        if (n > DISPLAY_LIMIT) { nextModel.setDataVector(new Object[0][0], new Object[0]); return; }

        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = Integer.toString(i);
            for (int j = 0; j < n; j++) {
                data[i][j + 1] = r.next[i][j] < 0 ? NO_HOP_SYMBOL : Integer.toString(r.next[i][j]);
            }
        }
        nextModel.setDataVector(data, matrixColumnNames(n));
        sizeColumns(nextTable, 56);
    }

    /** Header row for an n x n matrix table: "from \ to", 0, 1, ..., n-1. */
    private static String[] matrixColumnNames(int n) {
        String[] columns = new String[n + 1];
        columns[0] = "from \\ to";
        for (int j = 0; j < n; j++) columns[j + 1] = Integer.toString(j);
        return columns;
    }

    private void sizeColumns(JTable table, int width) {
        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(c == 0 ? 80 : width);
        }
    }

    private void refreshGraphInfo() {
        graphInfo.setText(graph.vertexCount() + " vertices, " + graph.edgeCount() + " edges");
    }

    // ---- status bar and dialogs ----
    private void showError(String message) {
        status.setForeground(COLOR_ERROR);
        status.setText("[Invalid] " + message);
        Toolkit.getDefaultToolkit().beep();
    }

    private void showInfo(String message) {
        status.setForeground(defaultLabelColor());
        status.setText(message);
    }

    private static Color defaultLabelColor() {
        return UIManager.getColor("Label.foreground");
    }

    private void showTextDialog(String title, String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(720, 420));
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================================
    //  TABLE PIECES
    // =====================================================================

    /** Table model whose cells cannot be edited. */
    private static final class ReadOnlyTableModel extends DefaultTableModel {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    }

    /** Read-only view of the graph's edge list (From, To, Weight). */
    private final class EdgeTableModel extends AbstractTableModel {
        private final String[] columnNames = {"From", "To", "Weight"};

        @Override public int getRowCount() { return graph.edgeCount(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) { return columnNames[column]; }

        @Override public Object getValueAt(int row, int column) {
            Edge edge = graph.edges().get(row);
            return column == 0 ? edge.from : column == 1 ? edge.to : edge.weight;
        }
    }

    /** Colours matrix cells: header column, changed cells, diagonal, negative values, INF / no-hop. */
    private final class MatrixRenderer extends DefaultTableCellRenderer {
        private final boolean isDistanceTable;

        MatrixRenderer(boolean isDistanceTable) {
            this.isDistanceTable = isDistanceTable;
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int col) {
            super.getTableCellRendererComponent(table, value, false, false, row, col);
            String text = value == null ? "" : value.toString();
            setFont(table.getFont());
            setForeground(Color.BLACK);
            setBackground(Color.WHITE);

            int matrixCol = col - 1;   // column 0 is the row-header column
            boolean isChangedCell = isDistanceTable && row < changed.length
                    && matrixCol < changed.length && changed[row][matrixCol];

            // Background / font
            if (col == 0) {
                setBackground(COLOR_HEADER_CELL);
                setFont(table.getFont().deriveFont(Font.BOLD));
            } else if (isChangedCell) {
                setBackground(COLOR_CHANGED_CELL);
                setFont(table.getFont().deriveFont(Font.BOLD));
            } else if (row == matrixCol) {
                setBackground(COLOR_DIAGONAL_CELL);
            }

            // Text colour (and negative-diagonal override)
            if (col > 0) {
                if (text.equals(INFINITY_SYMBOL) || text.equals(NO_HOP_SYMBOL)) setForeground(Color.GRAY);
                else if (isDistanceTable && text.startsWith("-")) setForeground(COLOR_NEGATIVE_TEXT);
                if (isDistanceTable && row == matrixCol && text.startsWith("-")) setBackground(COLOR_NEGATIVE_DIAGONAL);
            }
            return this;
        }
    }

    // =====================================================================
    //  GRAPH CANVAS
    // =====================================================================
    private final class GraphPanel extends JPanel {
        private static final int R = 18;          // vertex radius in pixels
        private static final int MARGIN = 40;     // space kept free around the drawing area

        /** Vertex positions as fractions (0..1) of the drawing area. */
        private double[][] pos = new double[0][2];
        private int dragging = -1;

        GraphPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("Graph  (drag vertices to rearrange)"));
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragging = vertexAt(e.getX(), e.getY()); }
                @Override public void mouseReleased(MouseEvent e) { dragging = -1; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragging < 0 || dragging >= pos.length) return;
                    int w = Math.max(1, getWidth() - 2 * MARGIN), h = Math.max(1, getHeight() - 2 * MARGIN);
                    pos[dragging][0] = Math.min(1, Math.max(0, (e.getX() - MARGIN) / (double) w));
                    pos[dragging][1] = Math.min(1, Math.max(0, (e.getY() - MARGIN) / (double) h));
                    repaint();
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        void resetLayout() { pos = new double[0][2]; }

        /** Places vertices evenly on a circle (only when the vertex count has changed). */
        private void ensureLayout(int n) {
            if (pos.length == n) return;
            pos = new double[n][2];
            for (int i = 0; i < n; i++) {
                double angle = 2 * Math.PI * i / Math.max(1, n) - Math.PI / 2;
                pos[i][0] = n == 1 ? 0.5 : 0.5 + 0.5 * Math.cos(angle);
                pos[i][1] = n == 1 ? 0.5 : 0.5 + 0.5 * Math.sin(angle);
            }
        }

        /** Converts a vertex's fractional position to pixel coordinates. */
        private Point2D.Double toPixel(int vertex) {
            int w = Math.max(1, getWidth() - 2 * MARGIN), h = Math.max(1, getHeight() - 2 * MARGIN);
            return new Point2D.Double(MARGIN + pos[vertex][0] * w, MARGIN + pos[vertex][1] * h);
        }

        /** @return the vertex under the pixel (x, y), or -1 if none. Later vertices are drawn on top. */
        private int vertexAt(int x, int y) {
            for (int i = pos.length - 1; i >= 0; i--) {
                if (toPixel(i).distance(x, y) <= R) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int n = graph.vertexCount();
            if (n == 0) { drawCenteredMessage(g, "Empty graph - create vertices on the left."); return; }
            if (n > GRAPH_DRAW_LIMIT) {
                drawCenteredMessage(g, "Graph has " + n + " vertices (drawing limit " + GRAPH_DRAW_LIMIT
                        + "). Computation still works.");
                return;
            }
            ensureLayout(n);

            // Edges first, so vertices are painted on top of them.
            HashSet<Long> pathEdges = new HashSet<>();
            for (int i = 0; i + 1 < highlight.size(); i++) {
                pathEdges.add(edgeKey(highlight.get(i), highlight.get(i + 1)));
            }
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            for (Edge edge : graph.edges()) {
                drawEdge(g, edge, pathEdges.contains(edgeKey(edge.from, edge.to)));
            }

            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            HashSet<Integer> onPath = new HashSet<>(highlight);
            for (int i = 0; i < n; i++) drawVertex(g, i, onPath.contains(i));
        }

        /** Unique key for a directed (from, to) pair, used to look up highlighted edges. */
        private long edgeKey(int from, int to) {
            return (long) from * 100000L + to;
        }

        private void drawCenteredMessage(Graphics2D g, String message) {
            g.setColor(Color.GRAY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(message, (getWidth() - fm.stringWidth(message)) / 2, getHeight() / 2);
        }

        private void drawVertex(Graphics2D g, int vertex, boolean onPath) {
            Point2D.Double p = toPixel(vertex);
            Color fill = COLOR_VERTEX;
            if (result != null && result.negativeCycle && vertex == result.cycleVertex) fill = COLOR_VERTEX_CYCLE;
            else if (onPath) fill = COLOR_VERTEX_ON_PATH;

            g.setColor(fill);
            g.fill(new Ellipse2D.Double(p.x - R, p.y - R, 2 * R, 2 * R));
            g.setColor(COLOR_VERTEX_OUTLINE);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(p.x - R, p.y - R, 2 * R, 2 * R));

            String text = Integer.toString(vertex);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (float) (p.x - fm.stringWidth(text) / 2.0), (float) (p.y + fm.getAscent() / 2.0 - 2));
        }

        private void drawEdge(Graphics2D g, Edge edge, boolean onPath) {
            Color color = onPath ? COLOR_EDGE_ON_PATH : edge.weight < 0 ? COLOR_EDGE_NEGATIVE : COLOR_EDGE;
            g.setColor(color);
            g.setStroke(new BasicStroke(onPath ? 3.2f : 1.6f));

            if (edge.from == edge.to) drawSelfLoop(g, edge, color);
            else drawCurvedEdge(g, edge, color);
        }

        /** Draws a small circle pointing away from the canvas centre, with the weight beside it. */
        private void drawSelfLoop(Graphics2D g, Edge edge, Color color) {
            Point2D.Double a = toPixel(edge.from);
            double centreX = getWidth() / 2.0, centreY = getHeight() / 2.0;
            double ux = a.x - centreX, uy = a.y - centreY;
            double length = Math.hypot(ux, uy);
            if (length < 1) { ux = 0; uy = -1; } else { ux /= length; uy /= length; }

            double loopX = a.x + ux * (R + 8), loopY = a.y + uy * (R + 8);
            g.draw(new Ellipse2D.Double(loopX - 12, loopY - 12, 24, 24));
            drawWeightLabel(g, Long.toString(edge.weight), loopX + ux * 22, loopY + uy * 22, color);
        }

        /** Draws a quadratic curve from one vertex to another, with an arrowhead and weight label. */
        private void drawCurvedEdge(Graphics2D g, Edge edge, Color color) {
            Point2D.Double a = toPixel(edge.from);
            Point2D.Double b = toPixel(edge.to);
            double dx = b.x - a.x, dy = b.y - a.y;
            double length = Math.hypot(dx, dy);
            if (length < 1) return;

            // Control point: midpoint pushed 44 px sideways so opposite edges do not overlap.
            double controlX = (a.x + b.x) / 2 - dy / length * 44;
            double controlY = (a.y + b.y) / 2 + dx / length * 44;

            // Start / end points sit on the vertex circles, along the line to the control point.
            double startDx = controlX - a.x, startDy = controlY - a.y, startLen = Math.hypot(startDx, startDy);
            double endDx = controlX - b.x, endDy = controlY - b.y, endLen = Math.hypot(endDx, endDy);
            double x1 = a.x + startDx / startLen * R, y1 = a.y + startDy / startLen * R;
            double x2 = b.x + endDx / endLen * (R + 2), y2 = b.y + endDy / endLen * (R + 2);
            g.draw(new QuadCurve2D.Double(x1, y1, controlX, controlY, x2, y2));

            // Arrowhead at the target end.
            double angle = Math.atan2(y2 - controlY, x2 - controlX);
            Path2D.Double head = new Path2D.Double();
            head.moveTo(x2, y2);
            head.lineTo(x2 - 11 * Math.cos(angle - 0.4), y2 - 11 * Math.sin(angle - 0.4));
            head.lineTo(x2 - 11 * Math.cos(angle + 0.4), y2 - 11 * Math.sin(angle + 0.4));
            head.closePath();
            g.fill(head);

            // Weight label at the curve's midpoint (t = 0.5 of the quadratic Bezier).
            drawWeightLabel(g, Long.toString(edge.weight),
                    0.25 * x1 + 0.5 * controlX + 0.25 * x2,
                    0.25 * y1 + 0.5 * controlY + 0.25 * y2, color);
        }

        private void drawWeightLabel(Graphics2D g, String text, double x, double y, Color color) {
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 8, h = fm.getHeight();
            g.setColor(new Color(255, 255, 255, 230));
            g.fillRoundRect((int) (x - w / 2.0), (int) (y - h / 2.0), w, h, 8, 8);
            g.setColor(color);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect((int) (x - w / 2.0), (int) (y - h / 2.0), w, h, 8, 8);
            g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) (y + fm.getAscent() / 2.0 - 1));
        }
    }

    // =====================================================================
    //  FLOYD-WARSHALL CORE (no printing; results are returned as data)
    // =====================================================================
    static final class FloydWarshall {

        /**
         * Runs the algorithm on {@code g}.
         * @param record when true (and n <= DISPLAY_LIMIT) a snapshot and log are kept for every pass
         */
        static Result run(Graph g, boolean record) {
            int n = g.vertexCount();
            Result r = new Result();
            r.log.append("Vertices: ").append(n).append("   Edges: ").append(g.edgeCount()).append('\n');

            // EDGE CASE: empty graph. There is no pair to evaluate.
            if (n == 0) {
                r.log.append("[Edge case] Graph has 0 vertices - nothing to compute.\n");
                return r;
            }

            long[][] dist = g.toDistanceMatrix();
            // next[i][j] = vertex right after i on a shortest i -> j path (O(n^2) path storage).
            int[][] next = new int[n][n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) next[i][j] = (dist[i][j] == INF) ? -1 : j;
            r.dist = dist;
            r.next = next;

            boolean recording = record && n <= DISPLAY_LIMIT;
            if (recording) {
                recordInitialSnapshot(r, dist, "Initial matrix D(-1): direct edges only (diagonal 0, \u221E = no edge).");
            }

            // A negative self-loop already shows up as a negative diagonal entry.
            int negativeVertex = firstNegativeDiagonal(dist);
            if (negativeVertex >= 0) {
                markNegativeCycle(r, negativeVertex, dist[negativeVertex][negativeVertex], -1);
                return r;
            }

            // Main triple loop: k = intermediate vertex, (i, j) = every pair.
            for (int k = 0; k < n; k++) {
                ArrayList<String> passLines = new ArrayList<>();
                if (recording) passLines.add("Pass k = " + k + ": can routing through vertex " + k + " shorten any path?");
                long passUpdates = 0;

                for (int i = 0; i < n; i++) {
                    long distIK = dist[i][k];
                    if (distIK == INF) continue;          // i cannot reach k, so k cannot help i
                    for (int j = 0; j < n; j++) {
                        long distKJ = dist[k][j];
                        if (distKJ == INF) continue;      // k cannot reach j; also avoids INF + x overflow
                        r.evaluated++;
                        long viaK = dist[i][k] + distKJ;  // re-read dist[i][k]: it may change when i == k
                        if (viaK < dist[i][j]) {
                            if (recording) {
                                passLines.add("  dist[" + i + "][" + j + "]: " + formatDistance(dist[i][j]) + " -> " + viaK
                                        + "   (" + i + "->" + k + " costs " + dist[i][k] + ", "
                                        + k + "->" + j + " costs " + distKJ + ")");
                            }
                            dist[i][j] = viaK;
                            next[i][j] = next[i][k];
                            passUpdates++;
                        }
                    }
                }
                r.updates += passUpdates;

                if (recording) {
                    if (passUpdates == 0) passLines.add("  (no improvements in this pass)");
                    r.snapshots.add(copy(dist));
                    r.passLogs.add(passLines);
                }

                // NEGATIVE-CYCLE CHECK after every pass (also keeps values small: no overflow).
                int negative = firstNegativeDiagonal(dist);
                if (negative >= 0) {
                    markNegativeCycle(r, negative, dist[negative][negative], k);
                    if (recording) r.passLogs.get(r.passLogs.size() - 1)
                            .add("!!! NEGATIVE CYCLE: dist[" + negative + "][" + negative + "] = " + dist[negative][negative] + " < 0");
                    return r;
                }
            }

            appendSuccessSummary(r, n);
            return r;
        }

        private static void appendSuccessSummary(Result r, int n) {
            r.log.append("\nFinished. No negative cycle detected.\n");
            r.log.append("Statistics: candidate sums evaluated = ").append(r.evaluated).append(" of n^3 = ")
                    .append(String.format("%.0f", Math.pow(n, 3))).append(" possible; shorter routes found = ")
                    .append(r.updates).append('\n');
            r.log.append("Complexity: time O(V^3), space O(V^2).\n");
        }

        private static void recordInitialSnapshot(Result r, long[][] dist, String line) {
            r.snapshots.add(copy(dist));
            ArrayList<String> lines = new ArrayList<>();
            lines.add(line);
            r.passLogs.add(lines);
        }

        private static long[][] copy(long[][] matrix) {
            long[][] copy = new long[matrix.length][];
            for (int i = 0; i < matrix.length; i++) copy[i] = matrix[i].clone();
            return copy;
        }

        /** @return the first vertex whose distance to itself is negative, or -1 if none. */
        private static int firstNegativeDiagonal(long[][] dist) {
            for (int i = 0; i < dist.length; i++) if (dist[i][i] < 0) return i;
            return -1;
        }

        private static void markNegativeCycle(Result r, int vertex, long value, int pass) {
            r.negativeCycle = true;
            r.cycleVertex = vertex;
            r.detectedAtPass = pass;
            r.log.append("\n!!! NEGATIVE CYCLE DETECTED !!!\n");
            r.log.append("dist[").append(vertex).append("][").append(vertex).append("] = ").append(value)
                    .append(" < 0").append(pass >= 0 ? " after pass k = " + pass : " (negative self-loop)").append(".\n");
            r.log.append("Vertex ").append(vertex).append(" lies on a cycle with negative total weight, so repeating\n");
            r.log.append("the cycle makes paths arbitrarily cheap: shortest paths are UNDEFINED.\n");
            r.log.append("Algorithm stopped early (this also prevents numeric overflow).\n");
        }
    }

    private static String formatDistance(long value) {
        return value == INF ? "INF" : Long.toString(value);
    }

    // =====================================================================
    //  PATH RECONSTRUCTION
    // =====================================================================

    /** @return the vertices of the shortest from -> to path, or an empty list if unreachable. */
    static ArrayList<Integer> pathVertices(Result r, int from, int to) {
        ArrayList<Integer> path = new ArrayList<>();
        if (r.dist[from][to] == INF) return path;
        int current = from;
        path.add(current);
        int guard = 0;   // safety net against an endless loop
        while (current != to && guard++ <= r.dist.length) {
            current = r.next[current][to];
            path.add(current);
        }
        return path;
    }

    /** @return a one-line description such as "Path 1 -> 2: cost 3, path: 1 -(4)-> 0 -(-2)-> 2". */
    static String describePath(Result r, Graph g, int from, int to) {
        long distance = r.dist[from][to];
        if (distance == INF) return "Path " + from + " -> " + to + ": UNREACHABLE (no path exists)";
        if (from == to) return "Path " + from + " -> " + to + ": cost 0, path: " + from;

        StringBuilder route = new StringBuilder();
        int current = from;
        route.append(current);
        int guard = 0;   // safety net against an endless loop
        while (current != to && guard++ <= r.dist.length) {
            int step = r.next[current][to];
            long weight = g.getWeight(current, step);
            route.append(" -(").append(weight).append(")-> ").append(step);
            current = step;
        }
        return "Path " + from + " -> " + to + ": cost " + distance + ", path: " + route;
    }

    // =====================================================================
    //  DATA MODEL
    // =====================================================================
    static final class Edge {
        final int from;
        final int to;
        long weight;

        Edge(int from, int to, long weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    /** Directed weighted graph stored as a vertex count plus a growable edge list. */
    static final class Graph {
        private int vertexCount;
        private final ArrayList<Edge> edges = new ArrayList<>();

        Graph(int vertexCount) {
            if (vertexCount < 0) {
                throw new IllegalArgumentException("Vertex count cannot be negative (got " + vertexCount + ").");
            }
            this.vertexCount = vertexCount;
        }

        int vertexCount() { return vertexCount; }
        int edgeCount() { return edges.size(); }
        ArrayList<Edge> edges() { return edges; }

        void addVertex() {
            if (vertexCount == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Cannot add more vertices: integer limit reached.");
            }
            vertexCount++;
        }

        /** @return true if an existing edge was overwritten, false if a new edge was created. */
        boolean addOrUpdateEdge(int from, int to, long weight) {
            checkVertex(from, "Source");
            checkVertex(to, "Target");
            if (weight > MAX_ABS_WEIGHT || weight < -MAX_ABS_WEIGHT) {
                throw new IllegalArgumentException("Weight " + weight + " is outside the allowed range +/-"
                        + MAX_ABS_WEIGHT + ".");
            }
            for (Edge e : edges) {
                if (e.from == from && e.to == to) {
                    e.weight = weight;
                    return true;
                }
            }
            edges.add(new Edge(from, to, weight));
            return false;
        }

        /** @return true if the edge existed and was removed. */
        boolean removeEdge(int from, int to) {
            for (int i = 0; i < edges.size(); i++) {
                Edge e = edges.get(i);
                if (e.from == from && e.to == to) {
                    edges.remove(i);
                    return true;
                }
            }
            return false;
        }

        /** @return the weight of edge (from, to), or INF if there is no such edge. */
        long getWeight(int from, int to) {
            for (Edge e : edges) if (e.from == from && e.to == to) return e.weight;
            return INF;
        }

        /** Initial distance matrix: 0 on the diagonal, INF where there is no edge. */
        long[][] toDistanceMatrix() {
            long[][] d = new long[vertexCount][vertexCount];
            for (int i = 0; i < vertexCount; i++)
                for (int j = 0; j < vertexCount; j++) d[i][j] = (i == j) ? 0 : INF;
            for (Edge e : edges) {
                // min() keeps a negative self-loop (a cycle) but ignores a positive one.
                if (e.weight < d[e.from][e.to]) d[e.from][e.to] = e.weight;
            }
            return d;
        }

        private void checkVertex(int vertex, String role) {
            if (vertex < 0 || vertex >= vertexCount) {
                throw new IllegalArgumentException(role + " vertex " + vertex + " is out of range (valid: 0.."
                        + (vertexCount - 1) + ").");
            }
        }
    }

    /** Everything produced by one run of the algorithm. */
    static final class Result {
        long[][] dist = new long[0][0];
        int[][] next = new int[0][0];
        boolean negativeCycle = false;
        int cycleVertex = -1;
        int detectedAtPass = -1;
        long evaluated = 0;
        long updates = 0;
        final ArrayList<long[][]> snapshots = new ArrayList<>();          // [0] = D(-1), [k+1] = after pass k
        final ArrayList<ArrayList<String>> passLogs = new ArrayList<>();  // matches snapshots
        final StringBuilder log = new StringBuilder();
    }

    // =====================================================================
    //  BUILT-IN TEST SUITE
    // =====================================================================
    static final class TestCase {
        final String name;
        final String purpose;
        final Graph graph;
        final long[][] expected;          // null when a negative cycle is expected
        final boolean expectNegativeCycle;

        TestCase(String name, String purpose, Graph graph, long[][] expected, boolean expectNegativeCycle) {
            this.name = name;
            this.purpose = purpose;
            this.graph = graph;
            this.expected = expected;
            this.expectNegativeCycle = expectNegativeCycle;
        }
    }

    /** Shorthand for an edge description used by {@link #buildGraph}. */
    private static long[] edge(int from, int to, long weight) { return new long[]{from, to, weight}; }

    private static Graph buildGraph(int n, long[]... edgeSpecs) {
        Graph g = new Graph(n);
        for (long[] spec : edgeSpecs) g.addOrUpdateEdge((int) spec[0], (int) spec[1], spec[2]);
        return g;
    }

    static ArrayList<TestCase> buildSuite() {
        ArrayList<TestCase> suite = new ArrayList<>();

        // ---- normal cases ----
        suite.add(new TestCase("NORMAL - classic 4-vertex graph with negative edges",
                "Negative weights without a cycle; several paths improve via intermediates.",
                buildGraph(4, edge(0, 2, -2), edge(1, 0, 4), edge(1, 2, 3), edge(2, 3, 2), edge(3, 1, -1)),
                new long[][]{{0, -1, -2, 0}, {4, 0, 2, 4}, {5, 1, 0, 2}, {3, -1, 1, 0}}, false));
        suite.add(new TestCase("NORMAL - symmetric (undirected-style) triangle",
                "Both directions added; 0->2 should drop from 7 to 5 by going through vertex 1.",
                buildGraph(3, edge(0, 1, 4), edge(1, 0, 4), edge(1, 2, 1), edge(2, 1, 1), edge(0, 2, 7), edge(2, 0, 7)),
                new long[][]{{0, 4, 5}, {4, 0, 1}, {5, 1, 0}}, false));

        // ---- boundary cases ----
        suite.add(new TestCase("BOUNDARY - single vertex, no edges",
                "Smallest non-empty graph; the answer is [[0]].",
                buildGraph(1), new long[][]{{0}}, false));
        suite.add(new TestCase("BOUNDARY - empty graph (0 vertices)",
                "Empty structure: the algorithm must exit cleanly without touching any matrix.",
                buildGraph(0), new long[0][0], false));
        suite.add(new TestCase("BOUNDARY - 3 vertices, no edges",
                "Every off-diagonal distance must remain INF.",
                buildGraph(3), new long[][]{{0, INF, INF}, {INF, 0, INF}, {INF, INF, 0}}, false));
        suite.add(new TestCase("BOUNDARY - disconnected components",
                "{0,1} and {2,3} cannot reach each other; INF must survive every pass.",
                buildGraph(4, edge(0, 1, 5), edge(1, 0, 2), edge(2, 3, 1)),
                new long[][]{{0, 5, INF, INF}, {2, 0, INF, INF}, {INF, INF, 0, 1}, {INF, INF, INF, 0}}, false));
        suite.add(new TestCase("BOUNDARY - duplicate edge overwrites",
                "Edge 0->1 is entered twice (8 then 3); the second weight replaces the first.",
                buildGraph(3, edge(0, 1, 8), edge(0, 1, 3), edge(1, 2, 1)),
                new long[][]{{0, 3, 4}, {INF, 0, 1}, {INF, INF, 0}}, false));
        suite.add(new TestCase("BOUNDARY - positive self-loop ignored",
                "A self-loop of +5 can never shorten anything; distance to self stays 0.",
                buildGraph(2, edge(0, 0, 5), edge(0, 1, 2)),
                new long[][]{{0, 2}, {INF, 0}}, false));

        // ---- invalid-domain cases (negative cycles) ----
        suite.add(new TestCase("INVALID DOMAIN - negative cycle (0->1->2->0, total -1)",
                "Must be DETECTED and reported, not silently wrong.",
                buildGraph(3, edge(0, 1, 1), edge(1, 2, -3), edge(2, 0, 1)), null, true));
        suite.add(new TestCase("INVALID DOMAIN - negative self-loop",
                "A one-vertex negative cycle is caught before pass 0 even starts.",
                buildGraph(2, edge(1, 1, -1), edge(0, 1, 3)), null, true));
        return suite;
    }

    static String runAllTestsReport() {
        StringBuilder sb = new StringBuilder();
        ArrayList<TestCase> suite = buildSuite();
        int passed = 0;

        for (TestCase testCase : suite) {
            Result r = FloydWarshall.run(testCase.graph, false);
            boolean pass = testCase.expectNegativeCycle
                    ? r.negativeCycle
                    : (!r.negativeCycle && sameMatrix(r.dist, testCase.expected));
            if (pass) passed++;
            sb.append(pass ? "PASS  " : "FAIL  ").append(testCase.name).append('\n');
            sb.append("      ").append(testCase.purpose).append('\n');
        }

        sb.append('\n').append("INVALID INPUT validation demo:\n");
        boolean invalidInputOk = runInvalidInputDemo(sb);
        sb.append(invalidInputOk ? "PASS  " : "FAIL  ").append("Invalid input rejected\n");
        if (invalidInputOk) passed++;

        int total = suite.size() + 1;
        sb.append("\n=============== SUITE SUMMARY: ").append(passed).append(" / ").append(total)
                .append(" PASSED ===============\n");
        return sb.toString();
    }

    /** Shows that bad data is rejected with a clear message instead of crashing. */
    static boolean runInvalidInputDemo(StringBuilder out) {
        out.append("Each of the following must be REJECTED with a clear message:\n");
        final Graph g = new Graph(3);
        boolean allRejected = true;
        allRejected &= expectRejected(out, "Create graph with -5 vertices", () -> new Graph(-5));
        allRejected &= expectRejected(out, "Edge 0 -> 7 in a 3-vertex graph (target out of range)", () -> g.addOrUpdateEdge(0, 7, 1));
        allRejected &= expectRejected(out, "Edge -1 -> 1 (negative source vertex)", () -> g.addOrUpdateEdge(-1, 1, 1));
        allRejected &= expectRejected(out, "Edge 0 -> 1 with weight 5,000,000,000 (overflow risk)",
                () -> g.addOrUpdateEdge(0, 1, 5_000_000_000L));

        boolean removedGhost = g.removeEdge(0, 1);
        out.append("  - Remove non-existent edge 0 -> 1: ")
                .append(removedGhost ? "NOT handled" : "handled, nothing changed").append('\n');
        return allRejected && !removedGhost;
    }

    private static boolean expectRejected(StringBuilder out, String label, Runnable action) {
        try {
            action.run();
            out.append("  - ").append(label).append(": ACCEPTED (should have been rejected!)\n");
            return false;
        } catch (IllegalArgumentException ex) {
            out.append("  - ").append(label).append(": rejected -> ").append(ex.getMessage()).append('\n');
            return true;
        }
    }

    private static boolean sameMatrix(long[][] a, long[][] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != b[i].length) return false;
            for (int j = 0; j < a[i].length; j++) if (a[i][j] != b[i][j]) return false;
        }
        return true;
    }
}