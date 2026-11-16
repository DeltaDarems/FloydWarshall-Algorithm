import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * ============================================================================
 *  FLOYD-WARSHALL ALL-PAIRS SHORTEST PATHS  -  Data Structures & Algorithms
 * ============================================================================
 *  Implemented from scratch: no java.util graph classes, no Arrays.sort, no
 *  Collections helpers. The only library structure used is ArrayList, purely as
 *  a growable container for the edge list (auxiliary storage, not the algorithm).
 *
 *  ALGORITHM IN ONE SENTENCE
 *    For every possible "intermediate vertex" k (in order 0..n-1), check for every
 *    pair (i, j) whether going i -> k -> j is cheaper than the best known i -> j.
 *    After pass k, dist[i][j] is optimal among paths that only use intermediate
 *    vertices {0..k}  (this is the dynamic-programming invariant).
 *
 *  RESTRICTIONS / EDGE-CASE RULES (also printed by the program)
 *    1. Directed, weighted graph; vertices are numbered 0..n-1.
 *    2. Edge weights are whole numbers with |w| <= 1,000,000,000 (prevents
 *       overflow of 64-bit longs when summing path costs).
 *    3. Negative edge weights ARE allowed. Negative CYCLES are detected
 *       (dist[v][v] < 0) and reported; shortest paths are undefined then.
 *    4. No edge = INF = unreachable. Disconnected graphs are fine.
 *    5. Re-entering an existing edge (u,v) overwrites its weight (no parallel edges).
 *    6. Self-loops: non-negative ones are harmless; a negative self-loop is a
 *       negative cycle.
 *    7. Empty graph (0 vertices): nothing to compute. One vertex: trivial.
 *    8. No fixed capacity: vertices and edges are bounded only by memory (the
 *       algorithm itself needs two n x n matrices).
 *
 *  Compile:  javac FloydWarshallProject.java
 *  Run:      java FloydWarshallProject
 * ============================================================================
 */
public class FloydWarshallProject {

    // Sentinel for "no path". Long.MAX_VALUE / 4 leaves head-room so INF + INF can never
    // overflow; we also skip any addition involving INF explicitly.
    static final long INF = Long.MAX_VALUE / 4;
    static final long MAX_ABS_WEIGHT = 1_000_000_000L;
    // Display-only threshold: matrices wider than this are not printed (computation is unaffected).
    static final int DISPLAY_LIMIT = 20;

    private static final Input INPUT = new Input();
    private static Graph graph = new Graph(0);
    private static Result lastResult = null;   // null => no valid result for the current graph
    private static boolean stepMode = true;    // true => print matrix after every pass k

    // =====================================================================
    //  MAIN + MENU
    // =====================================================================
    public static void main(String[] args) {
        printBanner();
        try {
            menuLoop();
        } catch (InputEnded e) {
            System.out.println("\n[Input stream closed] Exiting.");
        }
    }

    private static void menuLoop() {
        while (true) {
            printMenu();
            int choice = INPUT.readInt("Enter choice: ", 0, 11);
            try {
                switch (choice) {
                    case 1:  createNewGraph(); break;
                    case 2:  addVertex(); break;
                    case 3:  enterEdges(); break;
                    case 4:  removeEdge(); break;
                    case 5:  System.out.println(); displayGraph(graph); break;
                    case 6:  runAlgorithmOnCurrentGraph(); break;
                    case 7:  queryPath(); break;
                    case 8:  showAllPaths(); break;
                    case 9:
                        stepMode = !stepMode;
                        System.out.println("  Step-by-step mode is now " + (stepMode ? "ON (matrix shown after every pass k)"
                                : "OFF (only initial and final matrices)"));
                        break;
                    case 10: testSuiteMenu(); break;
                    case 11: printConstraints(); break;
                    case 0:  System.out.println("Goodbye!"); return;
                    default: break; // unreachable: readInt already enforces 0..11
                }
            } catch (OutOfMemoryError oom) {
                // An n x n matrix for a very large n can exceed memory; fail gracefully.
                lastResult = null;
                System.out.println("  [Error] Not enough memory for n x n matrices with n = "
                        + graph.vertexCount() + ". Try a smaller graph.");
            }
        }
    }

    private static void printBanner() {
        System.out.println("==============================================================");
        System.out.println("   FLOYD-WARSHALL  ALL-PAIRS SHORTEST PATH  (Java, from scratch)");
        System.out.println("==============================================================");
        printConstraints();
    }

    private static void printConstraints() {
        System.out.println("ALGORITHM RESTRICTIONS & EDGE-CASE RULES");
        System.out.println("  1. Directed weighted graph; vertices are numbered 0..n-1.");
        System.out.println("  2. Weights: whole numbers, |w| <= " + MAX_ABS_WEIGHT + " (prevents long overflow).");
        System.out.println("  3. Negative weights OK. Negative CYCLES are detected and reported.");
        System.out.println("  4. No edge = INF (unreachable). Disconnected graphs are fine.");
        System.out.println("  5. Re-entering an existing edge (u,v) overwrites its weight.");
        System.out.println("  6. Positive self-loops are harmless; a negative self-loop is a negative cycle.");
        System.out.println("  7. 0 vertices = nothing to compute; 1 vertex = trivial [[0]].");
        System.out.println("  8. No fixed capacity: limited only by memory (needs two n x n matrices).");
        System.out.println("  9. Matrices wider than " + DISPLAY_LIMIT + " are not printed (display only).");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("------------------------- MENU -------------------------");
        System.out.println("  Current graph: " + graph.vertexCount() + " vertices, " + graph.edgeCount() + " edges"
                + (lastResult == null ? "  [not yet computed]" : "  [computed]"));
        System.out.println("  1. Create new graph            6. Run Floyd-Warshall");
        System.out.println("  2. Add a vertex                7. Query shortest path (u -> v)");
        System.out.println("  3. Add/update edges            8. Show all shortest paths");
        System.out.println("  4. Remove an edge              9. Toggle step-by-step mode (now "
                + (stepMode ? "ON" : "OFF") + ")");
        System.out.println("  5. Display graph              10. Load built-in test suite");
        System.out.println("  11. Show restrictions          0. Exit");
        System.out.println("--------------------------------------------------------");
    }

    // =====================================================================
    //  MENU ACTIONS (all user input is validated by the Input helper)
    // =====================================================================
    private static void createNewGraph() {
        int n = INPUT.readInt("  Number of vertices (0 = start empty): ", 0, Integer.MAX_VALUE);
        graph = new Graph(n);
        lastResult = null;
        System.out.println("  Created graph with " + n + " vertices and no edges.");
    }

    private static void addVertex() {
        graph.addVertex();
        lastResult = null;
        System.out.println("  Added vertex " + (graph.vertexCount() - 1) + ". Graph now has "
                + graph.vertexCount() + " vertices.");
    }

    /** Reads edges as "from to weight" lines; a blank line ends entry. */
    private static void enterEdges() {
        if (graph.vertexCount() == 0) {
            System.out.println("  [Info] The graph has no vertices. Use option 1 or 2 first.");
            return;
        }
        System.out.println("  Enter edges as:  from to weight   (e.g. 0 2 -2).  Blank line = done.");
        System.out.println("  Valid vertices: 0.." + (graph.vertexCount() - 1) + ", |weight| <= " + MAX_ABS_WEIGHT);
        while (true) {
            String line = INPUT.readLine("  edge> ");
            if (line.isEmpty()) return;                  // blank line is the normal terminator here
            String[] parts = line.split("\\s+");
            if (parts.length != 3) {
                System.out.println("  [Invalid] Expected exactly 3 numbers: from to weight.");
                continue;
            }
            try {
                int from = Integer.parseInt(parts[0]);
                int to = Integer.parseInt(parts[1]);
                long weight = Long.parseLong(parts[2]);
                boolean overwritten = graph.addOrUpdateEdge(from, to, weight);
                lastResult = null;                       // old results no longer match the graph
                System.out.println("  " + (overwritten ? "Updated" : "Added") + " edge " + from + " -> " + to
                        + " (weight " + weight + ")" + (weight < 0 ? "   [negative weight]" : ""));
            } catch (NumberFormatException e) {
                System.out.println("  [Invalid] '" + line + "' contains a non-integer value.");
            } catch (IllegalArgumentException e) {
                System.out.println("  [Invalid] " + e.getMessage());
            }
        }
    }

    private static void removeEdge() {
        if (graph.edgeCount() == 0) {
            System.out.println("  [Info] There are no edges to remove.");
            return;
        }
        int max = graph.vertexCount() - 1;
        int from = INPUT.readInt("  Remove edge - from vertex (0.." + max + "): ", 0, max);
        int to = INPUT.readInt("  Remove edge - to vertex   (0.." + max + "): ", 0, max);
        if (graph.removeEdge(from, to)) {
            lastResult = null;
            System.out.println("  Removed edge " + from + " -> " + to + ".");
        } else {
            System.out.println("  [Info] Edge " + from + " -> " + to + " does not exist; nothing changed.");
        }
    }

    private static void runAlgorithmOnCurrentGraph() {
        System.out.println();
        System.out.println("=============== RUNNING FLOYD-WARSHALL ===============");
        lastResult = FloydWarshall.run(graph, stepMode);
    }

    private static void queryPath() {
        if (!requireUsableResult()) return;
        int max = graph.vertexCount() - 1;
        int from = INPUT.readInt("  Source vertex (0.." + max + "): ", 0, max);
        int to = INPUT.readInt("  Target vertex (0.." + max + "): ", 0, max);
        System.out.println("  " + describePath(lastResult, graph, from, to));
    }

    private static void showAllPaths() {
        if (!requireUsableResult()) return;
        int n = graph.vertexCount();
        System.out.println();
        System.out.println("  ALL SHORTEST PATHS");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) System.out.println("  " + describePath(lastResult, graph, i, j));
            }
        }
    }

    /** Path queries need a fresh, valid (no negative cycle) result. */
    private static boolean requireUsableResult() {
        if (lastResult == null) {
            System.out.println("  [Info] No up-to-date result. Run option 6 first (graph changed or never computed).");
            return false;
        }
        if (graph.vertexCount() == 0) {
            System.out.println("  [Info] The graph is empty; there are no paths.");
            return false;
        }
        if (lastResult.negativeCycle) {
            System.out.println("  [Info] The last run found a negative cycle, so shortest paths are undefined.");
            return false;
        }
        return true;
    }

    // =====================================================================
    //  FLOYD-WARSHALL CORE
    // =====================================================================
    static final class FloydWarshall {

        static Result run(Graph g, boolean verbose) {
            int n = g.vertexCount();
            System.out.println("  Vertices: " + n + "   Edges: " + g.edgeCount());

            // EDGE CASE: empty graph. There is no pair to evaluate.
            if (n == 0) {
                System.out.println("  [Edge case] Graph has 0 vertices - nothing to compute.");
                return new Result(new long[0][0], new int[0][0], false, -1, -1, 0, 0);
            }

            long[][] dist = g.toDistanceMatrix();
            // next[i][j] = the vertex that comes right after i on a shortest i -> j path.
            // Storing only the "next hop" lets us rebuild any path later using O(n^2) memory
            // instead of storing every path explicitly.
            int[][] next = new int[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    next[i][j] = (dist[i][j] == INF) ? -1 : j;
                }
            }

            System.out.println();
            System.out.println("  INITIAL distance matrix D(-1)  (direct edges only, diagonal = 0, INF = no edge)");
            printDistanceMatrix(dist);

            // A negative self-loop makes dist[v][v] < 0 before any pass: negative cycle of length 1.
            int bad = firstNegativeDiagonal(dist);
            if (bad >= 0) {
                reportNegativeCycle(bad, dist[bad][bad], -1);
                return new Result(dist, next, true, bad, -1, 0, 0);
            }

            long evaluated = 0;  // candidate sums actually computed (n^3 minus INF skips)
            long updates = 0;    // times a shorter route was found

            // k is the OUTERMOST loop on purpose: the DP invariant "paths using only
            // intermediates {0..k}" requires all pairs to be finished for k before k+1.
            for (int k = 0; k < n; k++) {
                long passUpdates = 0;
                if (verbose) {
                    System.out.println();
                    System.out.println("  --- Pass k = " + k + " : can routing through vertex " + k
                            + " shorten any path? ---");
                }
                for (int i = 0; i < n; i++) {
                    long distIK = dist[i][k];
                    if (distIK == INF) continue;          // i cannot reach k, so k cannot help i
                    for (int j = 0; j < n; j++) {
                        long distKJ = dist[k][j];
                        if (distKJ == INF) continue;      // k cannot reach j; also avoids INF + x overflow
                        evaluated++;
                        long viaK = dist[i][k] + distKJ;  // re-read dist[i][k]: it may change when i == k
                        if (viaK < dist[i][j]) {
                            if (verbose) {
                                System.out.println("    dist[" + i + "][" + j + "]: " + cell(dist[i][j]) + " -> " + viaK
                                        + "   (" + i + "->" + k + " costs " + dist[i][k] + ", "
                                        + k + "->" + j + " costs " + distKJ + ")");
                            }
                            dist[i][j] = viaK;
                            next[i][j] = next[i][k];      // first hop toward k is the first hop toward j
                            passUpdates++;
                        }
                    }
                }
                updates += passUpdates;
                if (verbose) {
                    if (passUpdates == 0) System.out.println("    (no improvements in this pass)");
                    System.out.println("  Distance matrix after pass k = " + k + ":");
                    printDistanceMatrix(dist);
                }

                // NEGATIVE-CYCLE CHECK after every pass: stopping at the first negative diagonal
                // also keeps values small, so a negative cycle can never overflow a long.
                int neg = firstNegativeDiagonal(dist);
                if (neg >= 0) {
                    reportNegativeCycle(neg, dist[neg][neg], k);
                    return new Result(dist, next, true, neg, k, evaluated, updates);
                }
            }

            System.out.println();
            System.out.println("  FINAL shortest-distance matrix (row = source, column = target):");
            printDistanceMatrix(dist);
            System.out.println("  NEXT-HOP matrix (first vertex after the source on the best path, '-' = no path):");
            printNextMatrix(next);
            System.out.println("  Statistics: candidate sums evaluated = " + evaluated + " of n^3 = "
                    + String.format("%.0f", Math.pow(n, 3)) + " possible; shorter routes found = " + updates);
            System.out.println("  Complexity: time O(V^3), space O(V^2).  No negative cycle detected.");
            return new Result(dist, next, false, -1, -1, evaluated, updates);
        }

        private static int firstNegativeDiagonal(long[][] dist) {
            for (int i = 0; i < dist.length; i++) {
                if (dist[i][i] < 0) return i;
            }
            return -1;
        }

        private static void reportNegativeCycle(int vertex, long value, int pass) {
            System.out.println();
            System.out.println("  !!! NEGATIVE CYCLE DETECTED !!!");
            System.out.println("  dist[" + vertex + "][" + vertex + "] = " + value + " < 0"
                    + (pass >= 0 ? " after pass k = " + pass : " (negative self-loop)") + ".");
            System.out.println("  Vertex " + vertex + " lies on a cycle with negative total weight, so repeating the");
            System.out.println("  cycle makes paths arbitrarily cheap: shortest paths are UNDEFINED.");
            System.out.println("  Algorithm stopped early (this also prevents numeric overflow).");
        }
    }

    // =====================================================================
    //  PATH RECONSTRUCTION
    // =====================================================================
    private static String describePath(Result r, Graph g, int from, int to) {
        long d = r.dist[from][to];
        if (d == INF) return "Path " + from + " -> " + to + ": UNREACHABLE (no path exists)";
        if (from == to) return "Path " + from + " -> " + to + ": cost 0, path: " + from;

        StringBuilder sb = new StringBuilder();
        int current = from;
        sb.append(current);
        int guard = 0;
        // Follow next-hop pointers until we arrive; the guard protects against corrupted state.
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
                    e.weight = weight;           // duplicates overwrite: keeps the graph simple
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
            for (Edge e : edges) {
                if (e.from == from && e.to == to) return e.weight;
            }
            return INF;
        }

        /** Builds D(-1): 0 on the diagonal, direct edge weights, INF elsewhere. */
        long[][] toDistanceMatrix() {
            long[][] d = new long[vertexCount][vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                for (int j = 0; j < vertexCount; j++) {
                    d[i][j] = (i == j) ? 0 : INF;
                }
            }
            for (Edge e : edges) {
                // min() keeps a negative self-loop (a cycle) but ignores a positive one (it can't help).
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
        final long[][] dist;
        final int[][] next;
        final boolean negativeCycle;
        final int cycleVertex;
        final int detectedAtPass;
        final long evaluated;
        final long updates;

        Result(long[][] dist, int[][] next, boolean negativeCycle, int cycleVertex, int detectedAtPass,
               long evaluated, long updates) {
            this.dist = dist;
            this.next = next;
            this.negativeCycle = negativeCycle;
            this.cycleVertex = cycleVertex;
            this.detectedAtPass = detectedAtPass;
            this.evaluated = evaluated;
            this.updates = updates;
        }
    }

    // =====================================================================
    //  DISPLAY HELPERS
    // =====================================================================
    private static void displayGraph(Graph g) {
        int n = g.vertexCount();
        System.out.println("  Graph: " + n + " vertices" + (n > 0 ? " (0.." + (n - 1) + ")" : " (empty)")
                + ", " + g.edgeCount() + " edges");
        if (g.edgeCount() == 0) {
            System.out.println("  Edge list: (no edges)");
        } else {
            System.out.println("  Edge list:");
            for (Edge e : g.edges()) {
                System.out.println("    " + e.from + " -> " + e.to + "  (weight " + e.weight + ")");
            }
        }
        if (n > 0) {
            System.out.println("  Adjacency (direct-edge) matrix:");
            printDistanceMatrix(g.toDistanceMatrix());
        }
    }

    private static String cell(long v) {
        return v == INF ? "INF" : Long.toString(v);
    }

    private static void printDistanceMatrix(long[][] m) {
        int n = m.length;
        String[][] cells = new String[0][0];
        if (n <= DISPLAY_LIMIT) {
            cells = new String[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) cells[i][j] = cell(m[i][j]);
            }
        }
        printGrid(cells, n);
    }

    private static void printNextMatrix(int[][] m) {
        int n = m.length;
        String[][] cells = new String[0][0];
        if (n <= DISPLAY_LIMIT) {
            cells = new String[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) cells[i][j] = (m[i][j] < 0) ? "-" : Integer.toString(m[i][j]);
            }
        }
        printGrid(cells, n);
    }

    /** Prints a right-aligned grid with row/column labels; width adapts to the widest cell. */
    private static void printGrid(String[][] cells, int n) {
        if (n == 0) {
            System.out.println("      (empty)");
            return;
        }
        if (n > DISPLAY_LIMIT) {
            System.out.println("      (matrix with " + n + " rows not printed; use path queries instead)");
            return;
        }
        int labelWidth = String.valueOf(n - 1).length();
        int width = labelWidth;
        for (String[] row : cells) {
            for (String c : row) width = Math.max(width, c.length());
        }
        width += 2;  // gap between columns

        StringBuilder sb = new StringBuilder();
        sb.append("      ").append(" ".repeat(labelWidth + 2));
        for (int j = 0; j < n; j++) sb.append(padLeft(Integer.toString(j), width));
        sb.append(System.lineSeparator());
        sb.append("      ").append("-".repeat(labelWidth + 1)).append("+").append("-".repeat(width * n))
                .append(System.lineSeparator());
        for (int i = 0; i < n; i++) {
            sb.append("      ").append(padLeft(Integer.toString(i), labelWidth)).append(" |");
            for (int j = 0; j < n; j++) sb.append(padLeft(cells[i][j], width));
            sb.append(System.lineSeparator());
        }
        System.out.print(sb);
    }

    private static String padLeft(String s, int width) {
        return " ".repeat(Math.max(0, width - s.length())) + s;
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

    private static long[] e(int from, int to, long weight) {
        return new long[]{from, to, weight};
    }

    private static Graph buildGraph(int n, long[]... edgeSpecs) {
        Graph g = new Graph(n);
        for (long[] spec : edgeSpecs) g.addOrUpdateEdge((int) spec[0], (int) spec[1], spec[2]);
        return g;
    }

    private static ArrayList<TestCase> buildSuite() {
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

    private static void testSuiteMenu() {
        ArrayList<TestCase> suite = buildSuite();
        int invalidDemoIndex = suite.size() + 1;
        int runAllIndex = suite.size() + 2;
        while (true) {
            System.out.println();
            System.out.println("==================== BUILT-IN TEST SUITE ====================");
            for (int i = 0; i < suite.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + suite.get(i).name);
            }
            System.out.println("  " + invalidDemoIndex + ". INVALID INPUT - rejected vertices / weights / sizes (validation demo)");
            System.out.println("  " + runAllIndex + ". Run ALL tests (compact output) with PASS/FAIL summary");
            System.out.println("  0. Back to main menu");
            int choice = INPUT.readInt("Select test: ", 0, runAllIndex);
            if (choice == 0) return;
            if (choice == runAllIndex) {
                runAllTests(suite);
            } else if (choice == invalidDemoIndex) {
                System.out.println(runInvalidInputDemo() ? "  RESULT: PASS" : "  RESULT: FAIL");
            } else {
                runTestCase(suite.get(choice - 1), stepMode);
            }
        }
    }

    private static boolean runTestCase(TestCase tc, boolean verbose) {
        System.out.println();
        System.out.println("##### " + tc.name);
        System.out.println("  Purpose: " + tc.purpose);
        displayGraph(tc.graph);
        System.out.println();
        Result r = FloydWarshall.run(tc.graph, verbose);

        boolean pass;
        if (tc.expectNegativeCycle) {
            pass = r.negativeCycle;
        } else {
            pass = !r.negativeCycle && sameMatrix(r.dist, tc.expected);
            if (!pass && tc.expected != null) {
                System.out.println("  Expected matrix was:");
                printDistanceMatrix(tc.expected);
            }
        }
        System.out.println("  RESULT: " + (pass ? "PASS" : "FAIL")
                + (tc.expectNegativeCycle ? "  (expected: negative cycle reported)" : "  (matches expected matrix)"));
        return pass;
    }

    private static void runAllTests(ArrayList<TestCase> suite) {
        int passed = 0;
        int total = suite.size() + 1;
        for (TestCase tc : suite) {
            if (runTestCase(tc, false)) passed++;
        }
        System.out.println();
        System.out.println("##### INVALID INPUT validation demo");
        if (runInvalidInputDemo()) {
            passed++;
            System.out.println("  RESULT: PASS");
        } else {
            System.out.println("  RESULT: FAIL");
        }
        System.out.println();
        System.out.println("=============== SUITE SUMMARY: " + passed + " / " + total + " PASSED ===============");
    }

    /** Shows that bad data is rejected with a clear message instead of crashing. */
    private static boolean runInvalidInputDemo() {
        System.out.println("  Each of the following must be REJECTED with a clear message:");
        final Graph g = new Graph(3);
        boolean ok = true;
        ok &= expectRejected("Create graph with -5 vertices", () -> new Graph(-5));
        ok &= expectRejected("Edge 0 -> 7 in a 3-vertex graph (target out of range)", () -> g.addOrUpdateEdge(0, 7, 1));
        ok &= expectRejected("Edge -1 -> 1 (negative source vertex)", () -> g.addOrUpdateEdge(-1, 1, 1));
        ok &= expectRejected("Edge 0 -> 1 with weight 5,000,000,000 (overflow risk)", () -> g.addOrUpdateEdge(0, 1, 5_000_000_000L));
        boolean removedGhost = g.removeEdge(0, 1);
        System.out.println("    - Remove non-existent edge 0 -> 1: " + (removedGhost ? "NOT handled" : "handled, nothing changed"));
        return ok && !removedGhost;
    }

    private static boolean expectRejected(String label, Runnable action) {
        try {
            action.run();
            System.out.println("    - " + label + ": ACCEPTED (should have been rejected!)");
            return false;
        } catch (IllegalArgumentException ex) {
            System.out.println("    - " + label + ": rejected -> " + ex.getMessage());
            return true;
        }
    }

    private static boolean sameMatrix(long[][] a, long[][] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != b[i].length) return false;
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j]) return false;
            }
        }
        return true;
    }

    // =====================================================================
    //  INPUT VALIDATION
    // =====================================================================
    /** Thrown when stdin closes (Ctrl+D / piped input ends) so the program can exit cleanly. */
    static final class InputEnded extends RuntimeException {
        InputEnded() { super("input ended"); }
    }

    /** Reads console input and re-prompts until the user enters a valid whole number in range. */
    static final class Input {
        private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String readLine(String prompt) {
            System.out.print(prompt);
            System.out.flush();
            try {
                String line = reader.readLine();
                if (line == null) throw new InputEnded();
                return line.trim();
            } catch (IOException ex) {
                throw new InputEnded();
            }
        }

        long readLong(String prompt, long min, long max) {
            while (true) {
                String text = readLine(prompt);
                if (text.isEmpty()) {
                    System.out.println("  [Invalid] Input cannot be empty.");
                    continue;
                }
                try {
                    long value = Long.parseLong(text);
                    if (value < min || value > max) {
                        System.out.println("  [Invalid] " + value + " is out of range (allowed: " + min + " to " + max + ").");
                        continue;
                    }
                    return value;
                } catch (NumberFormatException ex) {
                    System.out.println("  [Invalid] '" + text + "' is not a valid whole number.");
                }
            }
        }

        int readInt(String prompt, int min, int max) {
            return (int) readLong(prompt, min, max);
        }
    }
}