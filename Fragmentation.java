import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contiguous memory allocation with First Fit, Best Fit and Worst Fit.
 *
 * Every run works on fresh copies of the given MemoryBlocks, so the original
 * list is never modified. One request occupies one whole block (no splitting),
 * so the unused part of the block is internal fragmentation.
 *
 * External fragmentation: a request could not be placed in any single free
 * block even though the total free memory is large enough for it. In that
 * case the external fragmentation is the total free memory left at the end.
 */
public class Fragmentation {

    private Fragmentation() {
        // Utility class - no instances
    }

    public enum Algorithm {
        FIRST_FIT("First Fit"),
        BEST_FIT("Best Fit"),
        WORST_FIT("Worst Fit");

        private final String label;

        Algorithm(String label) {
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

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** A process asking for memory. */
    public static class Request {
        private final String processId;
        private final int size;

        public Request(String processId, int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Process size must be greater than zero.");
            }
            this.processId = processId;
            this.size = size;
        }

        public String getProcessId() {
            return processId;
        }

        public int getSize() {
            return size;
        }
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    /** Outcome for one process. */
    public static class Allocation {
        private final Request request;
        private final int blockId;          // -1 when not allocated
        private final int blockSize;        // 0 when not allocated
        private final boolean externalCause; // not allocated although total free memory was enough
        private final int freeAtFailure;    // total free memory when the request failed

        private Allocation(Request request, int blockId, int blockSize,
                boolean externalCause, int freeAtFailure) {
            this.request = request;
            this.blockId = blockId;
            this.blockSize = blockSize;
            this.externalCause = externalCause;
            this.freeAtFailure = freeAtFailure;
        }

        static Allocation placed(Request request, MemoryBlock block) {
            return new Allocation(request, block.getBlockId(), block.getSize(), false, 0);
        }

        static Allocation failed(Request request, int totalFree) {
            return new Allocation(request, -1, 0, totalFree >= request.getSize(), totalFree);
        }

        public Request getRequest() {
            return request;
        }

        public String getProcessId() {
            return request.getProcessId();
        }

        public int getSize() {
            return request.getSize();
        }

        public boolean isAllocated() {
            return blockId != -1;
        }

        public int getBlockId() {
            return blockId;
        }

        public int getBlockSize() {
            return blockSize;
        }

        /** Block size minus process size, or 0 when not allocated. */
        public int getInternalFragmentation() {
            return isAllocated() ? blockSize - request.getSize() : 0;
        }

        /** True when the process failed only because free memory is split into small holes. */
        public boolean isExternalCause() {
            return externalCause;
        }

        public int getFreeAtFailure() {
            return freeAtFailure;
        }
    }

    /** Full result of one algorithm run. */
    public static class Result {
        private final Algorithm algorithm;
        private final List<Allocation> allocations;
        private final List<MemoryBlock> blocks; // final state of the working copies
        private final int totalInternal;
        private final int totalFree;
        private final int externalFragmentation;
        private final int allocatedCount;

        Result(Algorithm algorithm, List<Allocation> allocations, List<MemoryBlock> blocks) {
            this.algorithm = algorithm;
            this.allocations = Collections.unmodifiableList(allocations);
            this.blocks = Collections.unmodifiableList(blocks);

            int internal = 0;
            int free = 0;
            for (MemoryBlock b : blocks) {
                internal += b.getInternalFragmentation();
                free += b.getFreeSize();
            }
            int allocated = 0;
            boolean external = false;
            for (Allocation a : allocations) {
                if (a.isAllocated()) {
                    allocated++;
                } else if (a.isExternalCause()) {
                    external = true;
                }
            }
            this.totalInternal = internal;
            this.totalFree = free;
            this.externalFragmentation = external ? free : 0;
            this.allocatedCount = allocated;
        }

        public Algorithm getAlgorithm() {
            return algorithm;
        }

        public List<Allocation> getAllocations() {
            return allocations;
        }

        public List<MemoryBlock> getBlocks() {
            return blocks;
        }

        public int getTotalInternalFragmentation() {
            return totalInternal;
        }

        /** Total size of blocks that are still free. */
        public int getTotalFree() {
            return totalFree;
        }

        public int getExternalFragmentation() {
            return externalFragmentation;
        }

        public boolean hasExternalFragmentation() {
            return externalFragmentation > 0;
        }

        public int getAllocatedCount() {
            return allocatedCount;
        }

        public int getNotAllocatedCount() {
            return allocations.size() - allocatedCount;
        }
    }

    // ------------------------------------------------------------------
    // Algorithms
    // ------------------------------------------------------------------

    /** Runs one algorithm on copies of the blocks. The given list is not changed. */
    public static Result run(List<MemoryBlock> blocks, List<Request> requests, Algorithm algorithm) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one memory block.");
        }
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one process.");
        }

        List<MemoryBlock> work = new ArrayList<MemoryBlock>();
        for (MemoryBlock block : blocks) {
            work.add(block.copy());
        }

        List<Allocation> allocations = new ArrayList<Allocation>();
        for (Request request : requests) {
            MemoryBlock chosen = select(work, request.getSize(), algorithm);
            if (chosen != null) {
                chosen.allocate(request.getProcessId(), request.getSize());
                allocations.add(Allocation.placed(request, chosen));
            } else {
                allocations.add(Allocation.failed(request, totalFree(work)));
            }
        }
        return new Result(algorithm, allocations, work);
    }

    /** Runs First, Best and Worst Fit on the same input. */
    public static List<Result> compareAll(List<MemoryBlock> blocks, List<Request> requests) {
        List<Result> results = new ArrayList<Result>();
        for (Algorithm algorithm : Algorithm.values()) {
            results.add(run(blocks, requests, algorithm));
        }
        return results;
    }

    private static MemoryBlock select(List<MemoryBlock> blocks, int size, Algorithm algorithm) {
        MemoryBlock chosen = null;
        for (MemoryBlock block : blocks) {
            if (!block.canFit(size)) {
                continue;
            }
            switch (algorithm) {
                case FIRST_FIT:
                    return block;
                case BEST_FIT:
                    if (chosen == null || block.getSize() < chosen.getSize()) {
                        chosen = block;
                    }
                    break;
                case WORST_FIT:
                    if (chosen == null || block.getSize() > chosen.getSize()) {
                        chosen = block;
                    }
                    break;
                default:
                    break;
            }
        }
        return chosen;
    }

    private static int totalFree(List<MemoryBlock> blocks) {
        int total = 0;
        for (MemoryBlock block : blocks) {
            total += block.getFreeSize();
        }
        return total;
    }
}