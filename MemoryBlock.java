
/**
 * Represents one contiguous block (partition) of main memory.
 * Used by Fragmentation (First/Best/Worst Fit) and Multiprogramming.
 */
public class MemoryBlock {

    private final int blockId;
    private final int startAddress;
    private final int size;

    private int allocatedSize;      // 0 when the block is free
    private String processId;       // null when the block is free

    public MemoryBlock(int blockId, int startAddress, int size) {
        if (startAddress < 0) {
            throw new IllegalArgumentException("Start address cannot be negative.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Block size must be greater than zero.");
        }
        this.blockId = blockId;
        this.startAddress = startAddress;
        this.size = size;
        this.allocatedSize = 0;
        this.processId = null;
    }

    /** Allocates a request to this block. Leftover space becomes internal fragmentation. */
    public void allocate(String processId, int requestSize) {
        if (!isFree()) {
            throw new IllegalStateException("Block " + blockId + " is already allocated.");
        }
        if (requestSize <= 0) {
            throw new IllegalArgumentException("Request size must be greater than zero.");
        }
        if (requestSize > size) {
            throw new IllegalArgumentException("Request (" + requestSize
                    + ") is larger than block " + blockId + " (" + size + ").");
        }
        this.processId = processId;
        this.allocatedSize = requestSize;
    }

    /** Releases the block. */
    public void free() {
        this.processId = null;
        this.allocatedSize = 0;
    }

    public boolean isFree() {
        return processId == null;
    }

    public boolean canFit(int requestSize) {
        return isFree() && requestSize <= size;
    }

    /** Wasted space inside an allocated block (block size minus request size). */
    public int getInternalFragmentation() {
        return isFree() ? 0 : size - allocatedSize;
    }

    /** Whole size when free, otherwise 0. */
    public int getFreeSize() {
        return isFree() ? size : 0;
    }

    public int getEndAddress() {
        return startAddress + size - 1;
    }

    /** Returns a fresh, unallocated copy with the same id, start and size. */
    public MemoryBlock copy() {
        return new MemoryBlock(blockId, startAddress, size);
    }

    public int getBlockId() {
        return blockId;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getSize() {
        return size;
    }

    public int getAllocatedSize() {
        return allocatedSize;
    }

    public String getProcessId() {
        return processId;
    }

    @Override
    public String toString() {
        return "Block " + blockId + " [" + startAddress + "-" + getEndAddress() + ", size=" + size
                + ", " + (isFree() ? "free" : "used by " + processId) + "]";
    }
}