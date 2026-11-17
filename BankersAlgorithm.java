import java.util.ArrayList;
import java.util.List;

/**
 * Banker's Algorithm: need matrix, safety algorithm (safe sequence detection)
 * and the resource-request algorithm.
 *
 * This class contains no GUI code. Matrices are List&lt;List&lt;Integer&gt;&gt; (one inner
 * list per process) so any number of processes and resources is supported.
 * Inputs are never modified; every method works on copies.
 */
public class BankersAlgorithm {

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** One iteration of the safety algorithm: a process that could finish. */
    public static class Step {
        private final int stepNumber;
        private final String processName;
        private final List<Integer> need;
        private final List<Integer> workBefore;
        private final List<Integer> allocation;
        private final List<Integer> workAfter;

        Step(int stepNumber, String processName, List<Integer> need, List<Integer> workBefore,
             List<Integer> allocation, List<Integer> workAfter) {
            this.stepNumber = stepNumber;
            this.processName = processName;
            this.need = need;
            this.workBefore = workBefore;
            this.allocation = allocation;
            this.workAfter = workAfter;
        }

        public int getStepNumber() {
            return stepNumber;
        }

        public String getProcessName() {
            return processName;
        }

        public List<Integer> getNeed() {
            return need;
        }

        /** Work vector before the process runs (must be &gt;= need). */
        public List<Integer> getWorkBefore() {
            return workBefore;
        }

        public List<Integer> getAllocation() {
            return allocation;
        }

        /** Work vector after the process finishes and releases its allocation. */
        public List<Integer> getWorkAfter() {
            return workAfter;
        }
    }

    /** Outcome of the safety algorithm. */
    public static class SafetyResult {
        private final boolean safe;
        private final List<List<Integer>> need;
        private final List<Integer> safeSequence;
        private final List<Step> steps;
        private final List<Integer> unfinished;
        private final List<Integer> finalWork;

        SafetyResult(boolean safe, List<List<Integer>> need, List<Integer> safeSequence,
                     List<Step> steps, List<Integer> unfinished, List<Integer> finalWork) {
            this.safe = safe;
            this.need = need;
            this.safeSequence = safeSequence;
            this.steps = steps;
            this.unfinished = unfinished;
            this.finalWork = finalWork;
        }

        public boolean isSafe() {
            return safe;
        }

        /** Need = Max - Allocation, one row per process. */
        public List<List<Integer>> getNeed() {
            return need;
        }

        /** Process indexes in the order they can finish (complete only when safe). */
        public List<Integer> getSafeSequence() {
            return safeSequence;
        }

        public List<Step> getSteps() {
            return steps;
        }

        /** Process indexes that could not finish (empty when safe). */
        public List<Integer> getUnfinished() {
            return unfinished;
        }

        /** Work vector at the end of the simulation. */
        public List<Integer> getFinalWork() {
            return finalWork;
        }

        /** Safe sequence as text, e.g. "P1 -> P3 -> P4 -> P0 -> P2". */
        public String sequenceText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < safeSequence.size(); i++) {
                if (i > 0) {
                    sb.append(" -> ");
                }
                sb.append(processName(safeSequence.get(i)));
            }
            return sb.toString();
        }
    }

    /** What happened to a resource request. */
    public enum RequestStatus {
        /** Request is larger than the process's remaining need. */
        EXCEEDS_NEED,
        /** Not enough resources are available right now, so the process must wait. */
        MUST_WAIT,
        /** Granting would leave the system in an unsafe state. */
        UNSAFE,
        /** Request can be granted and the system stays safe. */
        GRANTED
    }

    /** Outcome of the resource-request algorithm. */
    public static class RequestResult {
        private final RequestStatus status;
        private final String message;
        private final SafetyResult safety;
        private final List<List<Integer>> newAllocation;
        private final List<Integer> newAvailable;

        RequestResult(RequestStatus status, String message, SafetyResult safety,
                      List<List<Integer>> newAllocation, List<Integer> newAvailable) {
            this.status = status;
            this.message = message;
            this.safety = safety;
            this.newAllocation = newAllocation;
            this.newAvailable = newAvailable;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public boolean isGranted() {
            return status == RequestStatus.GRANTED;
        }

        public String getMessage() {
            return message;
        }

        /** Safety check of the pretend-allocated state (null if it was never reached). */
        public SafetyResult getSafety() {
            return safety;
        }

        /** Allocation matrix after the grant (only meaningful when granted). */
        public List<List<Integer>> getNewAllocation() {
            return newAllocation;
        }

        /** Available vector after the grant (only meaningful when granted). */
        public List<Integer> getNewAvailable() {
            return newAvailable;
        }
    }

    // ------------------------------------------------------------------
    // Public algorithms
    // ------------------------------------------------------------------

    /** Display name of a process: P0, P1, ... */
    public static String processName(int index) {
        return "P" + index;
    }

    /** Display name of a resource type: R0, R1, ... */
    public static String resourceName(int index) {
        return "R" + index;
    }

    /** Need = Max - Allocation. Validates the input first. */
    public static List<List<Integer>> computeNeed(List<List<Integer>> allocation,
                                                  List<List<Integer>> max) {
        validateMatrices(allocation, max);
        List<List<Integer>> need = new ArrayList<List<Integer>>();
        for (int i = 0; i < allocation.size(); i++) {
            List<Integer> row = new ArrayList<Integer>();
            for (int j = 0; j < allocation.get(i).size(); j++) {
                row.add(max.get(i).get(j) - allocation.get(i).get(j));
            }
            need.add(row);
        }
        return need;
    }

    /**
     * Safety algorithm. Scans the processes in order (P0, P1, ...); any unfinished
     * process whose need fits in Work finishes and adds its allocation back to Work.
     * Scans repeat until a full pass finishes nothing. The state is safe when every
     * process was able to finish.
     */
    public static SafetyResult checkSafety(List<List<Integer>> allocation,
                                           List<List<Integer>> max,
                                           List<Integer> available) {
        validateMatrices(allocation, max);
        validateAvailable(allocation, available);

        List<List<Integer>> need = computeNeed(allocation, max);
        int n = allocation.size();
        List<Integer> work = new ArrayList<Integer>(available);
        boolean[] finished = new boolean[n];
        List<Integer> sequence = new ArrayList<Integer>();
        List<Step> steps = new ArrayList<Step>();

        boolean progress = true;
        while (progress && sequence.size() < n) {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (!finished[i] && fits(need.get(i), work)) {
                    List<Integer> before = new ArrayList<Integer>(work);
                    for (int j = 0; j < work.size(); j++) {
                        work.set(j, work.get(j) + allocation.get(i).get(j));
                    }
                    finished[i] = true;
                    sequence.add(i);
                    steps.add(new Step(sequence.size(), processName(i),
                            new ArrayList<Integer>(need.get(i)), before,
                            new ArrayList<Integer>(allocation.get(i)),
                            new ArrayList<Integer>(work)));
                    progress = true;
                    // No break: the scan keeps going to the next process (textbook order),
                    // and a new pass starts from P0 only after reaching the last process.
                }
            }
        }

        List<Integer> unfinished = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            if (!finished[i]) {
                unfinished.add(i);
            }
        }
        return new SafetyResult(unfinished.isEmpty(), need, sequence, steps, unfinished, work);
    }

    /**
     * Resource-request algorithm for one process:
     * 1) request must not exceed the process's need,
     * 2) request must not exceed Available (otherwise the process waits),
     * 3) pretend to grant it and run the safety algorithm on the new state.
     */
    public static RequestResult requestResources(List<List<Integer>> allocation,
                                                 List<List<Integer>> max,
                                                 List<Integer> available,
                                                 int process, List<Integer> request) {
        validateMatrices(allocation, max);
        validateAvailable(allocation, available);
        int n = allocation.size();
        int m = available.size();
        if (process < 0 || process >= n) {
            throw new IllegalArgumentException("Process index " + process
                    + " does not exist (valid range 0 to " + (n - 1) + ").");
        }
        if (request == null || request.size() != m) {
            throw new IllegalArgumentException("The request must have exactly " + m
                    + " values (one per resource type).");
        }
        for (int j = 0; j < m; j++) {
            if (request.get(j) < 0) {
                throw new IllegalArgumentException("Request values cannot be negative.");
            }
        }

        String name = processName(process);
        List<Integer> need = computeNeed(allocation, max).get(process);

        for (int j = 0; j < m; j++) {
            if (request.get(j) > need.get(j)) {
                return new RequestResult(RequestStatus.EXCEEDS_NEED,
                        "Denied: " + name + " asked for " + request.get(j) + " of "
                                + resourceName(j) + " but its remaining need is only "
                                + need.get(j) + " (it exceeded its maximum claim).",
                        null, null, null);
            }
        }
        for (int j = 0; j < m; j++) {
            if (request.get(j) > available.get(j)) {
                return new RequestResult(RequestStatus.MUST_WAIT,
                        "Wait: " + name + " asked for " + request.get(j) + " of "
                                + resourceName(j) + " but only " + available.get(j)
                                + " is available right now.",
                        null, null, null);
            }
        }

        // Pretend to allocate
        List<List<Integer>> newAllocation = copyMatrix(allocation);
        List<Integer> newAvailable = new ArrayList<Integer>(available);
        for (int j = 0; j < m; j++) {
            newAllocation.get(process).set(j, newAllocation.get(process).get(j) + request.get(j));
            newAvailable.set(j, newAvailable.get(j) - request.get(j));
        }

        SafetyResult safety = checkSafety(newAllocation, max, newAvailable);
        if (safety.isSafe()) {
            return new RequestResult(RequestStatus.GRANTED,
                    "Granted: the system stays safe. Safe sequence: " + safety.sequenceText(),
                    safety, newAllocation, newAvailable);
        }
        return new RequestResult(RequestStatus.UNSAFE,
                "Denied: granting this request would leave the system in an UNSAFE state, "
                        + "so " + name + " must wait.",
                safety, null, null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** True when every value of need is &lt;= the matching value of work. */
    private static boolean fits(List<Integer> need, List<Integer> work) {
        for (int j = 0; j < need.size(); j++) {
            if (need.get(j) > work.get(j)) {
                return false;
            }
        }
        return true;
    }

    private static List<List<Integer>> copyMatrix(List<List<Integer>> source) {
        List<List<Integer>> copy = new ArrayList<List<Integer>>();
        for (List<Integer> row : source) {
            copy.add(new ArrayList<Integer>(row));
        }
        return copy;
    }

    private static void validateMatrices(List<List<Integer>> allocation, List<List<Integer>> max) {
        if (allocation == null || max == null || allocation.isEmpty() || max.isEmpty()) {
            throw new IllegalArgumentException("Allocation and Max matrices cannot be empty.");
        }
        if (allocation.size() != max.size()) {
            throw new IllegalArgumentException("Dimension mismatch: Allocation has "
                    + allocation.size() + " processes but Max has " + max.size() + ".");
        }
        int resources = allocation.get(0).size();
        if (resources == 0) {
            throw new IllegalArgumentException("There must be at least one resource type.");
        }
        for (int i = 0; i < allocation.size(); i++) {
            if (allocation.get(i).size() != resources || max.get(i).size() != resources) {
                throw new IllegalArgumentException("Dimension mismatch: every row of Allocation "
                        + "and Max must have " + resources + " values (check " + processName(i) + ").");
            }
            for (int j = 0; j < resources; j++) {
                int a = allocation.get(i).get(j);
                int mx = max.get(i).get(j);
                if (a < 0 || mx < 0) {
                    throw new IllegalArgumentException("Negative value found for " + processName(i)
                            + ", " + resourceName(j) + ". Values cannot be negative.");
                }
                if (a > mx) {
                    throw new IllegalArgumentException(processName(i) + " has Allocation (" + a
                            + ") greater than Max (" + mx + ") for " + resourceName(j)
                            + ". Allocation cannot exceed Max.");
                }
            }
        }
    }

    private static void validateAvailable(List<List<Integer>> allocation, List<Integer> available) {
        if (available == null || available.isEmpty()) {
            throw new IllegalArgumentException("Available resources cannot be empty.");
        }
        int resources = allocation.get(0).size();
        if (available.size() != resources) {
            throw new IllegalArgumentException("Dimension mismatch: Available has "
                    + available.size() + " values but the matrices have " + resources
                    + " resource types.");
        }
        for (int j = 0; j < available.size(); j++) {
            if (available.get(j) < 0) {
                throw new IllegalArgumentException("Available " + resourceName(j)
                        + " cannot be negative.");
            }
        }
    }
}