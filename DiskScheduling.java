import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Disk head scheduling: FCFS, SSTF, SCAN, C-SCAN, LOOK and C-LOOK.
 *
 * The disk has {@code diskSize} cylinders numbered 0 .. diskSize-1. Every run
 * validates the input and records each head movement.
 *
 * Rules used:
 *  - SSTF: the nearest request is chosen; on a tie the one that comes first in the queue.
 *  - SCAN / C-SCAN always travel to the end of the disk in the chosen direction.
 *  - SCAN then reverses; C-SCAN jumps to the other end and sweeps the same way again.
 *    Both only turn back / jump when requests are still waiting.
 *  - LOOK / C-LOOK go only as far as the last request in the chosen direction.
 *  - The return jump of C-SCAN / C-LOOK is counted in the total only when
 *    {@code countReturnJump} is true.
 */
public class DiskScheduling {

    private DiskScheduling() {
        // Utility class - no instances
    }

    public enum Algorithm {
        FCFS("FCFS"),
        SSTF("SSTF"),
        SCAN("SCAN"),
        C_SCAN("C-SCAN"),
        LOOK("LOOK"),
        C_LOOK("C-LOOK");

        private final String label;

        Algorithm(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** FCFS and SSTF do not depend on the head direction. */
        public boolean usesDirection() {
            return this != FCFS && this != SSTF;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Direction {
        LEFT("Left (toward cylinder 0)"),
        RIGHT("Right (toward last cylinder)");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum MoveType {
        REQUEST, TO_END, JUMP
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    /** One head movement from one cylinder to another. */
    public static class Move {
        private final int from;
        private final int to;
        private final MoveType type;
        private final boolean counted;
        private boolean served;

        Move(int from, int to, MoveType type, boolean counted, boolean served) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.counted = counted;
            this.served = served;
        }

        void markServed() {
            this.served = true;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public MoveType getType() {
            return type;
        }

        public int getDistance() {
            return Math.abs(to - from);
        }

        /** False only for a return jump that is not counted in the total. */
        public boolean isCounted() {
            return counted;
        }

        public int getCountedDistance() {
            return counted ? getDistance() : 0;
        }

        /** True when a request is served at the cylinder the head arrives at. */
        public boolean isServed() {
            return served;
        }

        public String getDescription() {
            String base;
            switch (type) {
                case REQUEST:
                    return "Serve request";
                case TO_END:
                    base = "Go to disk end";
                    break;
                default:
                    base = "Return jump";
                    break;
            }
            return served ? base + " + serve request" : base;
        }
    }

    /** Full result of one algorithm run. */
    public static class Result {
        private final Algorithm algorithm;
        private final Direction direction;
        private final int head;
        private final int diskSize;
        private final List<Integer> requests;
        private final List<Integer> serviceOrder;
        private final List<Move> moves;
        private final boolean returnJumpCounted;
        private final int totalMovement;

        Result(Algorithm algorithm, Direction direction, int head, int diskSize,
                List<Integer> requests, List<Integer> serviceOrder, List<Move> moves,
                boolean returnJumpCounted) {
            this.algorithm = algorithm;
            this.direction = direction;
            this.head = head;
            this.diskSize = diskSize;
            this.requests = Collections.unmodifiableList(new ArrayList<Integer>(requests));
            this.serviceOrder = Collections.unmodifiableList(serviceOrder);
            this.moves = Collections.unmodifiableList(moves);
            this.returnJumpCounted = returnJumpCounted;
            int total = 0;
            for (Move m : moves) {
                total += m.getCountedDistance();
            }
            this.totalMovement = total;
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public Direction getDirection() {
            return direction;
        }

        public int getHead() {
            return head;
        }

        public int getDiskSize() {
            return diskSize;
        }

        public List<Integer> getRequests() {
            return requests;
        }

        /** Requests in the order they are served. */
        public List<Integer> getServiceOrder() {
            return serviceOrder;
        }

        public List<Move> getMoves() {
            return moves;
        }

        public boolean isReturnJumpCounted() {
            return returnJumpCounted;
        }

        public int getTotalMovement() {
            return totalMovement;
        }

        public double getAverageMovement() {
            return requests.isEmpty() ? 0 : (double) totalMovement / requests.size();
        }

        /** Every cylinder the head stops at, starting with the initial head position. */
        public List<Integer> getPath() {
            List<Integer> path = new ArrayList<Integer>();
            path.add(head);
            for (Move m : moves) {
                path.add(m.getTo());
            }
            return path;
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Runs one algorithm.
     *
     * @throws IllegalArgumentException when the head or a request is outside the disk
     *                                  or the input is empty
     */
    public static Result run(List<Integer> requests, int head, int diskSize,
            Direction direction, Algorithm algorithm, boolean countReturnJump) {
        validate(requests, head, diskSize);
        Direction dir = direction == null ? Direction.RIGHT : direction;

        Runner runner = new Runner(head, countReturnJump);
        switch (algorithm) {
            case FCFS:
                fcfs(requests, runner);
                break;
            case SSTF:
                sstf(requests, runner);
                break;
            default:
                sweep(requests, head, diskSize, dir, algorithm, runner);
                break;
        }
        return new Result(algorithm, dir, head, diskSize, requests,
                runner.order, runner.moves, countReturnJump);
    }

    /** Runs all six algorithms on the same input. */
    public static List<Result> compareAll(List<Integer> requests, int head, int diskSize,
            Direction direction, boolean countReturnJump) {
        List<Result> results = new ArrayList<Result>();
        for (Algorithm algorithm : Algorithm.values()) {
            results.add(run(requests, head, diskSize, direction, algorithm, countReturnJump));
        }
        return results;
    }

    private static void validate(List<Integer> requests, int head, int diskSize) {
        if (diskSize < 1) {
            throw new IllegalArgumentException("Disk size must be at least 1 cylinder.");
        }
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("The request queue cannot be empty.");
        }
        int last = diskSize - 1;
        String range = "(valid cylinders: 0 to " + last + ")";
        if (head < 0 || head > last) {
            throw new IllegalArgumentException("Head position " + head
                    + " is outside the disk " + range + ".");
        }
        StringBuilder bad = new StringBuilder();
        int badCount = 0;
        for (int request : requests) {
            if (request < 0 || request > last) {
                if (badCount > 0) {
                    bad.append(", ");
                }
                bad.append(request);
                badCount++;
            }
        }
        if (badCount > 0) {
            throw new IllegalArgumentException((badCount == 1 ? "Request " : "Requests ")
                    + bad + (badCount == 1 ? " is" : " are") + " outside the disk " + range + ".");
        }
    }

    // ------------------------------------------------------------------
    // Algorithms
    // ------------------------------------------------------------------

    private static void fcfs(List<Integer> requests, Runner runner) {
        for (int cylinder : requests) {
            runner.serve(cylinder);
        }
    }

    private static void sstf(List<Integer> requests, Runner runner) {
        List<Integer> remaining = new ArrayList<Integer>(requests);
        while (!remaining.isEmpty()) {
            int best = 0;
            for (int i = 1; i < remaining.size(); i++) {
                if (Math.abs(remaining.get(i) - runner.position)
                        < Math.abs(remaining.get(best) - runner.position)) {
                    best = i;
                }
            }
            int cylinder = remaining.remove(best);
            runner.serve(cylinder);
        }
    }

    /** SCAN, C-SCAN, LOOK and C-LOOK. */
    private static void sweep(List<Integer> requests, int head, int diskSize,
            Direction direction, Algorithm algorithm, Runner runner) {
        int last = diskSize - 1;
        boolean right = direction == Direction.RIGHT;

        List<Integer> sorted = new ArrayList<Integer>(requests);
        Collections.sort(sorted);

        // ahead: requests in the chosen direction (including the head cylinder), in service order
        // behind: requests on the other side, ascending
        List<Integer> ahead = new ArrayList<Integer>();
        List<Integer> behind = new ArrayList<Integer>();
        for (int cylinder : sorted) {
            boolean isAhead = right ? cylinder >= head : cylinder <= head;
            if (isAhead) {
                ahead.add(cylinder);
            } else {
                behind.add(cylinder);
            }
        }
        if (!right) {
            Collections.reverse(ahead);
        }

        // Order when the head turns around (SCAN, LOOK)
        List<Integer> back = new ArrayList<Integer>(behind);
        if (right) {
            Collections.reverse(back);
        }
        // Order when the head jumps and sweeps the same way again (C-SCAN, C-LOOK)
        List<Integer> wrap = new ArrayList<Integer>(behind);
        if (!right) {
            Collections.reverse(wrap);
        }

        int endAhead = right ? last : 0;
        int endBehind = right ? 0 : last;

        for (int cylinder : ahead) {
            runner.serve(cylinder);
        }

        switch (algorithm) {
            case SCAN:
                runner.toEnd(endAhead);
                for (int cylinder : back) {
                    runner.serve(cylinder);
                }
                break;
            case LOOK:
                for (int cylinder : back) {
                    runner.serve(cylinder);
                }
                break;
            case C_SCAN:
                runner.toEnd(endAhead);
                if (!wrap.isEmpty()) {
                    runner.jump(endBehind);
                    for (int cylinder : wrap) {
                        runner.serve(cylinder);
                    }
                }
                break;
            case C_LOOK:
                if (!wrap.isEmpty()) {
                    runner.jump(wrap.get(0));
                    for (int cylinder : wrap) {
                        runner.serve(cylinder);
                    }
                }
                break;
            default:
                break;
        }
    }

    /** Keeps track of the head position, the moves made and the service order. */
    private static class Runner {
        int position;
        final boolean countJump;
        final List<Move> moves = new ArrayList<Move>();
        final List<Integer> order = new ArrayList<Integer>();

        Runner(int head, boolean countJump) {
            this.position = head;
            this.countJump = countJump;
        }

        void serve(int cylinder) {
            order.add(cylinder);
            if (!moves.isEmpty()) {
                Move last = moves.get(moves.size() - 1);
                // The head already arrived here by a travel/jump move: it serves the request there.
                if (position == cylinder && last.getType() != MoveType.REQUEST && !last.isServed()) {
                    last.markServed();
                    return;
                }
            }
            add(cylinder, MoveType.REQUEST, true);
        }

        void toEnd(int cylinder) {
            if (cylinder != position) {
                add(cylinder, MoveType.TO_END, false);
            }
        }

        void jump(int cylinder) {
            if (cylinder != position) {
                add(cylinder, MoveType.JUMP, false);
            }
        }

        private void add(int cylinder, MoveType type, boolean served) {
            boolean counted = type != MoveType.JUMP || countJump;
            moves.add(new Move(position, cylinder, type, counted, served));
            position = cylinder;
        }
    }
}