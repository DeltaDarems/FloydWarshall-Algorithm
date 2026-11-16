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

    static final long INF = Long.MAX_VALUE / 4;
    static final long MAX_ABS_WEIGHT = 1_000_000_000L;
    static final int DISPLAY_LIMIT = 30;     // matrices wider than this are not drawn
    static final int GRAPH_DRAW_LIMIT = 60;  // canvas stops drawing beyond this
    static final int GUI_MAX_VERTICES = 1000;

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

    // ---- state ----
    private Graph graph = new Graph(0);
    private Result result = null;
    private ArrayList<Integer> highlight = new ArrayList<>();
    private int stepIndex = 0;
    private boolean[][] changed = new boolean[0][0];
    private boolean sliderAdjusting = false;
    private Timer playTimer;

    // ---- widgets ----
    private final JTextField nField = new JTextField("4", 5);
    private final JTextField fromField = new JTextField(4);
    private final JTextField toField = new JTextField(4);
    private final JTextField weightField = new JTextField(6);
    private final JLabel graphInfo = new JLabel();
    private final EdgeTableModel edgeModel = new EdgeTableModel();
    private final JTable edgeTable = new JTable(edgeModel);
    private final GraphPanel graphPanel = new GraphPanel();
    private final JLabel status = new JLabel(" ");

    private final DefaultTableModel matrixModel = new DefaultTableModel() {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable matrixTable = new JTable(matrixModel);
    private final DefaultTableModel nextModel = new DefaultTableModel() {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable nextTable = new JTable(nextModel);
    private final JLabel stepLabel = new JLabel("Not computed yet - press Run.");
    private final JSlider stepSlider = new JSlider(0, 0, 0);
    private final JTextArea passLog = new JTextArea(4, 40);
    private final JButton playButton = new JButton("Play");
    private final JTextField qFrom = new JTextField(4);
    private final JTextField qTo = new JTextField(4);
    private final JLabel pathResult = new JLabel(" ");
    private final JTextArea allPaths = new JTextArea();
    private final JTextArea logArea = new JTextArea();

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        for (String a : args) {
            if (a.equals("--selftest")) {
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

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu tests = new JMenu("Test suite");
        for (TestCase tc : buildSuite()) {
            JMenuItem item = new JMenuItem("Load: " + tc.name);
            final String purpose = tc.purpose;
            final String name = tc.name;
            item.addActionListener(ev -> {
                // rebuild so each load gets a fresh Graph object
                for (TestCase fresh : buildSuite()) {
                    if (fresh.name.equals(name)) { loadGraph(fresh.graph, name + " - " + purpose); break; }
                }
            });
            tests.add(item);
        }
        tests.addSeparator();
        JMenuItem all = new JMenuItem("Run ALL tests (PASS/FAIL summary)...");
        all.addActionListener(ev -> showTextDialog("Test suite results", runAllTestsReport()));
        tests.add(all);
        JMenuItem inv = new JMenuItem("Invalid-input validation demo...");
        inv.addActionListener(ev -> {
            StringBuilder sb = new StringBuilder();
            boolean ok = runInvalidInputDemo(sb);
            showTextDialog("Invalid input demo", sb + "\nRESULT: " + (ok ? "PASS" : "FAIL"));
        });
        tests.add(inv);

        JMenu help = new JMenu("Help");
        JMenuItem restr = new JMenuItem("Restrictions & edge cases...");
        restr.addActionListener(ev -> showTextDialog("Restrictions", RESTRICTIONS));
        help.add(restr);
        JMenuItem how = new JMenuItem("How the algorithm works...");
        how.addActionListener(ev -> showTextDialog("Floyd-Warshall",
                "For every possible intermediate vertex k (in order 0..n-1), check for every pair (i, j)\n"
                + "whether going i -> k -> j is cheaper than the best known i -> j.\n\n"
                + "After pass k, dist[i][j] is optimal among paths that only use intermediate vertices {0..k}\n"
                + "(the dynamic-programming invariant). Use the step controls under 'Distance matrix' to watch\n"
                + "each pass; changed cells are highlighted.\n\n"
                + "A next-hop matrix is kept so any path can be rebuilt using O(n^2) memory.\n"
                + "Time O(V^3), space O(V^2). A negative diagonal entry means a negative cycle."));
        help.add(how);
        bar.add(tests);
        bar.add(help);
        return bar;
    }

    private static JPanel row(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        for (Component c : cs) p.add(c);
        return p;
    }

    private JPanel buildControlPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
        left.setPreferredSize(new Dimension(320, 100));

        JPanel gp = titled("Graph");
        JButton newBtn = new JButton("New graph");
        newBtn.addActionListener(e -> createNewGraph());
        JButton addV = new JButton("Add vertex");
        addV.addActionListener(e -> addVertex());
        gp.add(row(new JLabel("Vertices:"), nField, newBtn));
        gp.add(row(addV, graphInfo));
        left.add(gp);

        JPanel ep = titled("Edges (directed)");
        JButton addE = new JButton("Add / update");
        addE.addActionListener(e -> addEdge());
        JButton remE = new JButton("Remove (from,to)");
        remE.addActionListener(e -> removeEdgeFromFields());
        ep.add(row(new JLabel("from"), fromField, new JLabel("to"), toField, new JLabel("w"), weightField));
        ep.add(row(addE, remE));
        edgeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        edgeTable.getSelectionModel().addListSelectionListener(e -> {
            int r = edgeTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < graph.edgeCount()) {
                Edge ed = graph.edges().get(r);
                fromField.setText(Integer.toString(ed.from));
                toField.setText(Integer.toString(ed.to));
                weightField.setText(Long.toString(ed.weight));
            }
        });
        JScrollPane sp = new JScrollPane(edgeTable);
        sp.setPreferredSize(new Dimension(280, 200));
        ep.add(sp);
        JLabel hint = new JLabel("<html><small>Click a row to edit it. Weight may be negative.</small></html>");
        ep.add(row(hint));
        left.add(ep);

        JPanel rp = titled("Compute");
        JButton run = new JButton("Run Floyd-Warshall");
        run.setFont(run.getFont().deriveFont(Font.BOLD, 14f));
        run.addActionListener(e -> runAlgorithm());
        rp.add(row(run));
        left.add(rp);
        left.add(Box.createVerticalGlue());
        return left;
    }

    private JPanel titled(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();

        // --- distance matrix tab with step controls ---
        JPanel mp = new JPanel(new BorderLayout(4, 4));
        styleMatrixTable(matrixTable, true);
        mp.add(new JScrollPane(matrixTable), BorderLayout.CENTER);
        JPanel ctl = new JPanel(new BorderLayout(4, 4));
        JButton first = new JButton("|<");
        JButton prev = new JButton("<");
        JButton nextB = new JButton(">");
        JButton last = new JButton(">|");
        first.addActionListener(e -> { stopPlay(); showStep(0); });
        prev.addActionListener(e -> { stopPlay(); showStep(stepIndex - 1); });
        nextB.addActionListener(e -> { stopPlay(); showStep(stepIndex + 1); });
        last.addActionListener(e -> {
            stopPlay();
            if (result != null) showStep(result.snapshots.size() - 1);
        });
        playButton.addActionListener(e -> togglePlay());
        stepSlider.addChangeListener(e -> { if (!sliderAdjusting) showStep(stepSlider.getValue()); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        buttons.add(first); buttons.add(prev); buttons.add(nextB); buttons.add(last); buttons.add(playButton);
        buttons.add(stepSlider);
        buttons.add(stepLabel);
        ctl.add(buttons, BorderLayout.NORTH);
        passLog.setEditable(false);
        passLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ctl.add(new JScrollPane(passLog), BorderLayout.CENTER);
        ctl.setPreferredSize(new Dimension(100, 140));
        mp.add(ctl, BorderLayout.SOUTH);
        tabs.addTab("Distance matrix (step-by-step)", mp);

        // --- next hop tab ---
        styleMatrixTable(nextTable, false);
        tabs.addTab("Next-hop matrix", new JScrollPane(nextTable));

        // --- paths tab ---
        JPanel pp = new JPanel(new BorderLayout(4, 4));
        JButton q = new JButton("Show path");
        q.addActionListener(e -> queryPath());
        JButton clear = new JButton("Clear highlight");
        clear.addActionListener(e -> { highlight = new ArrayList<>(); graphPanel.repaint(); });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        top.add(new JLabel("From:")); top.add(qFrom);
        top.add(new JLabel("To:")); top.add(qTo);
        top.add(q); top.add(clear); top.add(pathResult);
        pp.add(top, BorderLayout.NORTH);
        allPaths.setEditable(false);
        allPaths.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pp.add(new JScrollPane(allPaths), BorderLayout.CENTER);
        tabs.addTab("Paths", pp);

        // --- log tab ---
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Log", new JScrollPane(logArea));
        return tabs;
    }

    private void styleMatrixTable(JTable t, boolean distance) {
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        t.setRowHeight(22);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setCellSelectionEnabled(false);
        t.getTableHeader().setReorderingAllowed(false);
        t.setDefaultRenderer(Object.class, new MatrixRenderer(distance));
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================
    private void createNewGraph() {
        try {
            int n = Integer.parseInt(nField.getText().trim());
            if (n < 0 || n > GUI_MAX_VERTICES) {
                error("Vertex count must be between 0 and " + GUI_MAX_VERTICES + ".");
                return;
            }
            loadGraph(new Graph(n), "Created graph with " + n + " vertices and no edges.");
        } catch (NumberFormatException ex) {
            error("'" + nField.getText().trim() + "' is not a valid whole number.");
        }
    }

    private void addVertex() {
        if (graph.vertexCount() >= GUI_MAX_VERTICES) {
            error("The GUI is limited to " + GUI_MAX_VERTICES + " vertices.");
            return;
        }
        graph.addVertex();
        invalidateResult();
        ok("Added vertex " + (graph.vertexCount() - 1) + ".");
    }

    private void addEdge() {
        if (graph.vertexCount() == 0) {
            error("The graph has no vertices. Create one or add a vertex first.");
            return;
        }
        try {
            int from = Integer.parseInt(fromField.getText().trim());
            int to = Integer.parseInt(toField.getText().trim());
            long w = Long.parseLong(weightField.getText().trim());
            boolean overwritten = graph.addOrUpdateEdge(from, to, w);
            invalidateResult();
            ok((overwritten ? "Updated" : "Added") + " edge " + from + " -> " + to + " (weight " + w + ")"
                    + (w < 0 ? "  [negative weight]" : ""));
        } catch (NumberFormatException ex) {
            error("from, to and weight must all be whole numbers.");
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    private void removeEdgeFromFields() {
        try {
            int from = Integer.parseInt(fromField.getText().trim());
            int to = Integer.parseInt(toField.getText().trim());
            if (graph.removeEdge(from, to)) {
                invalidateResult();
                ok("Removed edge " + from + " -> " + to + ".");
            } else {
                error("Edge " + from + " -> " + to + " does not exist; nothing changed.");
            }
        } catch (NumberFormatException ex) {
            error("Enter whole numbers in the from and to boxes.");
        }
    }

    private void runAlgorithm() {
        stopPlay();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            result = FloydWarshall.run(graph, true);
        } catch (OutOfMemoryError oom) {
            result = null;
            error("Not enough memory for n x n matrices with n = " + graph.vertexCount() + ".");
            return;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        highlight = new ArrayList<>();
        showResult();
    }

    private void queryPath() {
        if (result == null) { error("No up-to-date result. Press Run first."); return; }
        if (graph.vertexCount() == 0) { error("The graph is empty; there are no paths."); return; }
        if (result.negativeCycle) { error("A negative cycle was found, so shortest paths are undefined."); return; }
        try {
            int from = Integer.parseInt(qFrom.getText().trim());
            int to = Integer.parseInt(qTo.getText().trim());
            int max = graph.vertexCount() - 1;
            if (from < 0 || from > max || to < 0 || to > max) {
                error("Vertices must be in 0.." + max + ".");
                return;
            }
            pathResult.setText(describePath(result, graph, from, to));
            highlight = pathVertices(result, from, to);
            graphPanel.repaint();
            status.setText(" ");
        } catch (NumberFormatException ex) {
            error("Enter whole numbers for source and target.");
        }
    }

    private void loadGraph(Graph g, String message) {
        graph = g;
        nField.setText(Integer.toString(g.vertexCount()));
        graphPanel.resetLayout();
        invalidateResult();
        ok(message);
    }

    // =====================================================================
    //  VIEW UPDATES
    // =====================================================================
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
        stepLabel.setForeground(UIManager.getColor("Label.foreground"));
        stepLabel.setText("Not computed yet - press Run.");
        passLog.setText("");
        allPaths.setText("");
        pathResult.setText(" ");
        logArea.setText("");
        graphInfo.setText(graph.vertexCount() + " vertices, " + graph.edgeCount() + " edges");
        edgeModel.fireTableDataChanged();
        graphPanel.repaint();
    }

    private void showResult() {
        Result r = result;
        graphInfo.setText(graph.vertexCount() + " vertices, " + graph.edgeCount() + " edges");
        logArea.setText(r.log.toString());
        int n = graph.vertexCount();

        if (n == 0) {
            stepLabel.setText("Empty graph - nothing to compute.");
            status.setForeground(new Color(0x1B5E20));
            status.setText("Edge case: 0 vertices, nothing to compute.");
            graphPanel.repaint();
            return;
        }
        if (r.snapshots.isEmpty()) {
            stepLabel.setText("Matrices wider than " + DISPLAY_LIMIT + " are not shown; use the Paths tab.");
            matrixModel.setDataVector(new Object[0][0], new Object[0]);
            nextModel.setDataVector(new Object[0][0], new Object[0]);
        } else {
            sliderAdjusting = true;
            stepSlider.setEnabled(true);
            stepSlider.setMaximum(r.snapshots.size() - 1);
            sliderAdjusting = false;
            showStep(r.snapshots.size() - 1);
        }

        if (r.negativeCycle) {
            status.setForeground(new Color(0xB71C1C));
            status.setText("NEGATIVE CYCLE detected through vertex " + r.cycleVertex
                    + (r.detectedAtPass >= 0 ? " after pass k = " + r.detectedAtPass : " (negative self-loop)")
                    + ". Shortest paths are undefined.");
            allPaths.setText("A negative cycle was found; shortest paths are undefined.");
        } else {
            status.setForeground(new Color(0x1B5E20));
            status.setText("Done. No negative cycle. " + r.updates + " shorter routes found.");
            fillNextTable(r);
            if (n <= 40) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        if (i != j) sb.append(describePath(r, graph, i, j)).append('\n');
                allPaths.setText(sb.toString());
                allPaths.setCaretPosition(0);
            } else {
                allPaths.setText("Graph has more than 40 vertices; use the query boxes above.");
            }
        }
        graphPanel.repaint();
    }

    private void showStep(int idx) {
        if (result == null || result.snapshots.isEmpty()) return;
        idx = Math.max(0, Math.min(idx, result.snapshots.size() - 1));
        stepIndex = idx;
        long[][] cur = result.snapshots.get(idx);
        long[][] prev = idx > 0 ? result.snapshots.get(idx - 1) : null;
        int n = cur.length;
        changed = new boolean[n][n];
        if (prev != null) {
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) changed[i][j] = cur[i][j] != prev[i][j];
        }
        String[] cols = new String[n + 1];
        cols[0] = "from \\ to";
        for (int j = 0; j < n; j++) cols[j + 1] = Integer.toString(j);
        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = Integer.toString(i);
            for (int j = 0; j < n; j++) data[i][j + 1] = cur[i][j] == INF ? "\u221E" : Long.toString(cur[i][j]);
        }
        matrixModel.setDataVector(data, cols);
        sizeColumns(matrixTable, 56);

        sliderAdjusting = true;
        stepSlider.setValue(idx);
        sliderAdjusting = false;

        boolean lastNeg = result.negativeCycle && idx == result.snapshots.size() - 1;
        String label = (idx == 0) ? "D(-1): initial (direct edges)" : "after pass k = " + (idx - 1)
                + "  (" + idx + "/" + (result.snapshots.size() - 1) + ")";
        if (lastNeg) label += "  - NEGATIVE CYCLE";
        stepLabel.setText(label);
        stepLabel.setForeground(lastNeg ? new Color(0xB71C1C) : UIManager.getColor("Label.foreground"));

        StringBuilder sb = new StringBuilder();
        for (String s : result.passLogs.get(idx)) sb.append(s).append('\n');
        passLog.setText(sb.toString());
        passLog.setCaretPosition(0);
    }

    private void fillNextTable(Result r) {
        int n = r.next.length;
        if (n > DISPLAY_LIMIT) { nextModel.setDataVector(new Object[0][0], new Object[0]); return; }
        String[] cols = new String[n + 1];
        cols[0] = "from \\ to";
        for (int j = 0; j < n; j++) cols[j + 1] = Integer.toString(j);
        Object[][] data = new Object[n][n + 1];
        for (int i = 0; i < n; i++) {
            data[i][0] = Integer.toString(i);
            for (int j = 0; j < n; j++) data[i][j + 1] = r.next[i][j] < 0 ? "\u00B7" : Integer.toString(r.next[i][j]);
        }
        nextModel.setDataVector(data, cols);
        sizeColumns(nextTable, 56);
    }

    private void sizeColumns(JTable t, int w) {
        for (int c = 0; c < t.getColumnCount(); c++) {
            t.getColumnModel().getColumn(c).setPreferredWidth(c == 0 ? 80 : w);
        }
    }

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

    private void error(String msg) {
        status.setForeground(new Color(0xB71C1C));
        status.setText("[Invalid] " + msg);
        Toolkit.getDefaultToolkit().beep();
    }

    private void ok(String msg) {
        status.setForeground(UIManager.getColor("Label.foreground"));
        status.setText(msg);
    }

    private void showTextDialog(String title, String text) {
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(720, 420));
        JOptionPane.showMessageDialog(this, sp, title, JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================================
    //  TABLE PIECES
    // =====================================================================
    private final class EdgeTableModel extends AbstractTableModel {
        private final String[] names = {"From", "To", "Weight"};
        @Override public int getRowCount() { return graph.edgeCount(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int c) { return names[c]; }
        @Override public Object getValueAt(int r, int c) {
            Edge e = graph.edges().get(r);
            return c == 0 ? e.from : c == 1 ? e.to : e.weight;
        }
    }

    private final class MatrixRenderer extends DefaultTableCellRenderer {
        private final boolean distance;
        MatrixRenderer(boolean distance) { this.distance = distance; setHorizontalAlignment(CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            String s = v == null ? "" : v.toString();
            setFont(t.getFont());
            setForeground(Color.BLACK);
            setBackground(Color.WHITE);
            if (col == 0) {
                setBackground(new Color(0xE3E8EF));
                setFont(t.getFont().deriveFont(Font.BOLD));
            } else if (distance && row < changed.length && col - 1 < changed.length && changed[row][col - 1]) {
                setBackground(new Color(0xFFE08A));
                setFont(t.getFont().deriveFont(Font.BOLD));
            } else if (row == col - 1) {
                setBackground(new Color(0xF2F2F2));
            }
            if (col > 0) {
                if (s.equals("\u221E") || s.equals("\u00B7")) setForeground(Color.GRAY);
                else if (distance && s.startsWith("-")) setForeground(new Color(0xC0392B));
                if (distance && row == col - 1 && s.startsWith("-")) setBackground(new Color(0xFFB3B3));
            }
            return this;
        }
    }

    // =====================================================================
    //  GRAPH CANVAS
    // =====================================================================
    private final class GraphPanel extends JPanel {
        private static final int R = 18;
        private double[][] pos = new double[0][2];
        private int dragging = -1;

        GraphPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("Graph  (drag vertices to rearrange)"));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { dragging = hit(e.getX(), e.getY()); }
                @Override public void mouseReleased(MouseEvent e) { dragging = -1; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragging < 0 || dragging >= pos.length) return;
                    int w = Math.max(1, getWidth() - 80), h = Math.max(1, getHeight() - 80);
                    pos[dragging][0] = Math.min(1, Math.max(0, (e.getX() - 40) / (double) w));
                    pos[dragging][1] = Math.min(1, Math.max(0, (e.getY() - 40) / (double) h));
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void resetLayout() { pos = new double[0][2]; }

        private void ensureLayout(int n) {
            if (pos.length == n) return;
            pos = new double[n][2];
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / Math.max(1, n) - Math.PI / 2;
                pos[i][0] = n == 1 ? 0.5 : 0.5 + 0.5 * Math.cos(a);
                pos[i][1] = n == 1 ? 0.5 : 0.5 + 0.5 * Math.sin(a);
            }
        }

        private Point2D.Double px(int i) {
            int w = Math.max(1, getWidth() - 80), h = Math.max(1, getHeight() - 80);
            return new Point2D.Double(40 + pos[i][0] * w, 40 + pos[i][1] * h);
        }

        private int hit(int x, int y) {
            for (int i = pos.length - 1; i >= 0; i--) {
                Point2D.Double p = px(i);
                if (p.distance(x, y) <= R) return i;
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
            if (n == 0) { centered(g, "Empty graph - create vertices on the left."); return; }
            if (n > GRAPH_DRAW_LIMIT) {
                centered(g, "Graph has " + n + " vertices (drawing limit " + GRAPH_DRAW_LIMIT + "). Computation still works.");
                return;
            }
            ensureLayout(n);

            HashSet<Long> pathEdges = new HashSet<>();
            for (int i = 0; i + 1 < highlight.size(); i++) pathEdges.add((long) highlight.get(i) * 100000L + highlight.get(i + 1));

            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            for (Edge e : graph.edges()) {
                boolean hl = pathEdges.contains((long) e.from * 100000L + e.to);
                drawEdge(g, e, hl);
            }
            HashSet<Integer> onPath = new HashSet<>(highlight);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            for (int i = 0; i < n; i++) {
                Point2D.Double p = px(i);
                Color fill = new Color(0xCFE3FF);
                if (result != null && result.negativeCycle && i == result.cycleVertex) fill = new Color(0xFFB3B3);
                else if (onPath.contains(i)) fill = new Color(0xFFD59A);
                g.setColor(fill);
                g.fill(new Ellipse2D.Double(p.x - R, p.y - R, 2 * R, 2 * R));
                g.setColor(new Color(0x34495E));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(p.x - R, p.y - R, 2 * R, 2 * R));
                String s = Integer.toString(i);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(s, (float) (p.x - fm.stringWidth(s) / 2.0), (float) (p.y + fm.getAscent() / 2.0 - 2));
            }
        }

        private void centered(Graphics2D g, String msg) {
            g.setColor(Color.GRAY);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
        }

        private void drawEdge(Graphics2D g, Edge e, boolean hl) {
            Color c = hl ? new Color(0xE67E22) : e.weight < 0 ? new Color(0xC0392B) : new Color(0x7F8C8D);
            g.setColor(c);
            g.setStroke(new BasicStroke(hl ? 3.2f : 1.6f));
            Point2D.Double a = px(e.from);
            if (e.from == e.to) {
                double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                double ux = a.x - cx, uy = a.y - cy;
                double len = Math.hypot(ux, uy);
                if (len < 1) { ux = 0; uy = -1; } else { ux /= len; uy /= len; }
                double lx = a.x + ux * (R + 8), ly = a.y + uy * (R + 8);
                g.draw(new Ellipse2D.Double(lx - 12, ly - 12, 24, 24));
                label(g, Long.toString(e.weight), lx + ux * 22, ly + uy * 22, c);
                return;
            }
            Point2D.Double b = px(e.to);
            double dx = b.x - a.x, dy = b.y - a.y;
            double len = Math.hypot(dx, dy);
            if (len < 1) return;
            double mx = (a.x + b.x) / 2 - dy / len * 44, my = (a.y + b.y) / 2 + dx / len * 44;
            double sx = mx - a.x, sy = my - a.y, sl = Math.hypot(sx, sy);
            double ex = mx - b.x, ey = my - b.y, el = Math.hypot(ex, ey);
            double x1 = a.x + sx / sl * R, y1 = a.y + sy / sl * R;
            double x2 = b.x + ex / el * (R + 2), y2 = b.y + ey / el * (R + 2);
            g.draw(new QuadCurve2D.Double(x1, y1, mx, my, x2, y2));
            double ang = Math.atan2(y2 - my, x2 - mx);
            Path2D.Double head = new Path2D.Double();
            head.moveTo(x2, y2);
            head.lineTo(x2 - 11 * Math.cos(ang - 0.4), y2 - 11 * Math.sin(ang - 0.4));
            head.lineTo(x2 - 11 * Math.cos(ang + 0.4), y2 - 11 * Math.sin(ang + 0.4));
            head.closePath();
            g.fill(head);
            label(g, Long.toString(e.weight), 0.25 * x1 + 0.5 * mx + 0.25 * x2, 0.25 * y1 + 0.5 * my + 0.25 * y2, c);
        }

        private void label(Graphics2D g, String s, double x, double y, Color c) {
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(s) + 8, h = fm.getHeight();
            g.setColor(new Color(255, 255, 255, 230));
            g.fillRoundRect((int) (x - w / 2.0), (int) (y - h / 2.0), w, h, 8, 8);
            g.setColor(c);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect((int) (x - w / 2.0), (int) (y - h / 2.0), w, h, 8, 8);
            g.drawString(s, (float) (x - fm.stringWidth(s) / 2.0), (float) (y + fm.getAscent() / 2.0 - 1));
        }
    }

    // =====================================================================
    //  FLOYD-WARSHALL CORE (no printing; results are returned as data)
    // =====================================================================
    static final class FloydWarshall {

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

            boolean rec = record && n <= DISPLAY_LIMIT;
            if (rec) snapshot(r, dist, "Initial matrix D(-1): direct edges only (diagonal 0, \u221E = no edge).");

            int bad = firstNegativeDiagonal(dist);
            if (bad >= 0) {
                markNegative(r, bad, dist[bad][bad], -1);
                return r;
            }

            for (int k = 0; k < n; k++) {
                ArrayList<String> lines = new ArrayList<>();
                if (rec) lines.add("Pass k = " + k + ": can routing through vertex " + k + " shorten any path?");
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
                            if (rec) {
                                lines.add("  dist[" + i + "][" + j + "]: " + cell(dist[i][j]) + " -> " + viaK
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
                if (rec) {
                    if (passUpdates == 0) lines.add("  (no improvements in this pass)");
                    r.snapshots.add(copy(dist));
                    r.passLogs.add(lines);
                }
                // NEGATIVE-CYCLE CHECK after every pass (also keeps values small: no overflow).
                int neg = firstNegativeDiagonal(dist);
                if (neg >= 0) {
                    markNegative(r, neg, dist[neg][neg], k);
                    if (rec) r.passLogs.get(r.passLogs.size() - 1)
                            .add("!!! NEGATIVE CYCLE: dist[" + neg + "][" + neg + "] = " + dist[neg][neg] + " < 0");
                    return r;
                }
            }
            r.log.append("\nFinished. No negative cycle detected.\n");
            r.log.append("Statistics: candidate sums evaluated = ").append(r.evaluated).append(" of n^3 = ")
                    .append(String.format("%.0f", Math.pow(n, 3))).append(" possible; shorter routes found = ")
                    .append(r.updates).append('\n');
            r.log.append("Complexity: time O(V^3), space O(V^2).\n");
            return r;
        }

        private static void snapshot(Result r, long[][] dist, String line) {
            r.snapshots.add(copy(dist));
            ArrayList<String> l = new ArrayList<>();
            l.add(line);
            r.passLogs.add(l);
        }

        private static long[][] copy(long[][] m) {
            long[][] c = new long[m.length][];
            for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
            return c;
        }

        private static int firstNegativeDiagonal(long[][] dist) {
            for (int i = 0; i < dist.length; i++) if (dist[i][i] < 0) return i;
            return -1;
        }

        private static void markNegative(Result r, int vertex, long value, int pass) {
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

    private static String cell(long v) { return v == INF ? "INF" : Long.toString(v); }

    // =====================================================================
    //  PATH RECONSTRUCTION
    // =====================================================================
    static ArrayList<Integer> pathVertices(Result r, int from, int to) {
        ArrayList<Integer> path = new ArrayList<>();
        if (r.dist[from][to] == INF) return path;
        int current = from;
        path.add(current);
        int guard = 0;
        while (current != to && guard++ <= r.dist.length) {
            current = r.next[current][to];
            path.add(current);
        }
        return path;
    }

    static String describePath(Result r, Graph g, int from, int to) {
        long d = r.dist[from][to];
        if (d == INF) return "Path " + from + " -> " + to + ": UNREACHABLE (no path exists)";
        if (from == to) return "Path " + from + " -> " + to + ": cost 0, path: " + from;
        StringBuilder sb = new StringBuilder();
        int current = from;
        sb.append(current);
        int guard = 0;
        while (current != to && guard++ <= r.dist.length) {
            int step = r.next[current][to];
            long w = g.getWeight(current, step);
            sb.append(" -(").append(w).append(")-> ").append(step);
            current = step;
        }
        return "Path " + from + " -> " + to + ": cost " + d + ", path: " + sb;
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

        long getWeight(int from, int to) {
            for (Edge e : edges) if (e.from == from && e.to == to) return e.weight;
            return INF;
        }

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

        private void checkVertex(int v, String role) {
            if (v < 0 || v >= vertexCount) {
                throw new IllegalArgumentException(role + " vertex " + v + " is out of range (valid: 0.."
                        + (vertexCount - 1) + ").");
            }
        }
    }

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

    private static long[] e(int from, int to, long weight) { return new long[]{from, to, weight}; }

    private static Graph buildGraph(int n, long[]... edgeSpecs) {
        Graph g = new Graph(n);
        for (long[] spec : edgeSpecs) g.addOrUpdateEdge((int) spec[0], (int) spec[1], spec[2]);
        return g;
    }

    static ArrayList<TestCase> buildSuite() {
        ArrayList<TestCase> s = new ArrayList<>();
        s.add(new TestCase("NORMAL - classic 4-vertex graph with negative edges",
                "Negative weights without a cycle; several paths improve via intermediates.",
                buildGraph(4, e(0, 2, -2), e(1, 0, 4), e(1, 2, 3), e(2, 3, 2), e(3, 1, -1)),
                new long[][]{{0, -1, -2, 0}, {4, 0, 2, 4}, {5, 1, 0, 2}, {3, -1, 1, 0}}, false));
        s.add(new TestCase("NORMAL - symmetric (undirected-style) triangle",
                "Both directions added; 0->2 should drop from 7 to 5 by going through vertex 1.",
                buildGraph(3, e(0, 1, 4), e(1, 0, 4), e(1, 2, 1), e(2, 1, 1), e(0, 2, 7), e(2, 0, 7)),
                new long[][]{{0, 4, 5}, {4, 0, 1}, {5, 1, 0}}, false));
        s.add(new TestCase("BOUNDARY - single vertex, no edges",
                "Smallest non-empty graph; the answer is [[0]].",
                buildGraph(1), new long[][]{{0}}, false));
        s.add(new TestCase("BOUNDARY - empty graph (0 vertices)",
                "Empty structure: the algorithm must exit cleanly without touching any matrix.",
                buildGraph(0), new long[0][0], false));
        s.add(new TestCase("BOUNDARY - 3 vertices, no edges",
                "Every off-diagonal distance must remain INF.",
                buildGraph(3), new long[][]{{0, INF, INF}, {INF, 0, INF}, {INF, INF, 0}}, false));
        s.add(new TestCase("BOUNDARY - disconnected components",
                "{0,1} and {2,3} cannot reach each other; INF must survive every pass.",
                buildGraph(4, e(0, 1, 5), e(1, 0, 2), e(2, 3, 1)),
                new long[][]{{0, 5, INF, INF}, {2, 0, INF, INF}, {INF, INF, 0, 1}, {INF, INF, INF, 0}}, false));
        s.add(new TestCase("BOUNDARY - duplicate edge overwrites",
                "Edge 0->1 is entered twice (8 then 3); the second weight replaces the first.",
                buildGraph(3, e(0, 1, 8), e(0, 1, 3), e(1, 2, 1)),
                new long[][]{{0, 3, 4}, {INF, 0, 1}, {INF, INF, 0}}, false));
        s.add(new TestCase("BOUNDARY - positive self-loop ignored",
                "A self-loop of +5 can never shorten anything; distance to self stays 0.",
                buildGraph(2, e(0, 0, 5), e(0, 1, 2)),
                new long[][]{{0, 2}, {INF, 0}}, false));
        s.add(new TestCase("INVALID DOMAIN - negative cycle (0->1->2->0, total -1)",
                "Must be DETECTED and reported, not silently wrong.",
                buildGraph(3, e(0, 1, 1), e(1, 2, -3), e(2, 0, 1)), null, true));
        s.add(new TestCase("INVALID DOMAIN - negative self-loop",
                "A one-vertex negative cycle is caught before pass 0 even starts.",
                buildGraph(2, e(1, 1, -1), e(0, 1, 3)), null, true));
        return s;
    }

    static String runAllTestsReport() {
        StringBuilder sb = new StringBuilder();
        ArrayList<TestCase> suite = buildSuite();
        int passed = 0;
        for (TestCase tc : suite) {
            Result r = FloydWarshall.run(tc.graph, false);
            boolean pass = tc.expectNegativeCycle ? r.negativeCycle
                    : (!r.negativeCycle && sameMatrix(r.dist, tc.expected));
            if (pass) passed++;
            sb.append(pass ? "PASS  " : "FAIL  ").append(tc.name).append('\n');
            sb.append("      ").append(tc.purpose).append('\n');
        }
        sb.append('\n').append("INVALID INPUT validation demo:\n");
        boolean ok = runInvalidInputDemo(sb);
        sb.append(ok ? "PASS  " : "FAIL  ").append("Invalid input rejected\n");
        if (ok) passed++;
        int total = suite.size() + 1;
        sb.append("\n=============== SUITE SUMMARY: ").append(passed).append(" / ").append(total)
                .append(" PASSED ===============\n");
        return sb.toString();
    }

    /** Shows that bad data is rejected with a clear message instead of crashing. */
    static boolean runInvalidInputDemo(StringBuilder out) {
        out.append("Each of the following must be REJECTED with a clear message:\n");
        final Graph g = new Graph(3);
        boolean ok = true;
        ok &= expectRejected(out, "Create graph with -5 vertices", () -> new Graph(-5));
        ok &= expectRejected(out, "Edge 0 -> 7 in a 3-vertex graph (target out of range)", () -> g.addOrUpdateEdge(0, 7, 1));
        ok &= expectRejected(out, "Edge -1 -> 1 (negative source vertex)", () -> g.addOrUpdateEdge(-1, 1, 1));
        ok &= expectRejected(out, "Edge 0 -> 1 with weight 5,000,000,000 (overflow risk)",
                () -> g.addOrUpdateEdge(0, 1, 5_000_000_000L));
        boolean removedGhost = g.removeEdge(0, 1);
        out.append("  - Remove non-existent edge 0 -> 1: ")
                .append(removedGhost ? "NOT handled" : "handled, nothing changed").append('\n');
        return ok && !removedGhost;
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