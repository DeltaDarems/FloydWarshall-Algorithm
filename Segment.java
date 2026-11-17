/**
 * One entry of a segment table: segment number, base address and limit (length).
 * Used by the Segmentation module for logical-to-physical translation.
 */
public class Segment {

    private final int segmentNumber;
    private final int base;
    private final int limit;

    public Segment(int segmentNumber, int base, int limit) {
        if (segmentNumber < 0) {
            throw new IllegalArgumentException("Segment number cannot be negative.");
        }
        if (base < 0) {
            throw new IllegalArgumentException("Base address cannot be negative.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Segment limit must be greater than zero.");
        }
        this.segmentNumber = segmentNumber;
        this.base = base;
        this.limit = limit;
    }

    /** True when the offset lies inside this segment (0 <= offset < limit). */
    public boolean isValidOffset(int offset) {
        return offset >= 0 && offset < limit;
    }

    /**
     * Converts an offset to a physical address (base + offset).
     *
     * @throws IllegalArgumentException when the offset is outside the segment limit
     */
    public int translate(int offset) {
        if (!isValidOffset(offset)) {
            throw new IllegalArgumentException("Offset " + offset + " is outside the limit of segment "
                    + segmentNumber + " (valid range 0 to " + (limit - 1) + ").");
        }
        return base + offset;
    }

    /** Last physical address used by this segment. */
    public int getEndAddress() {
        return base + limit - 1;
    }

    public int getSegmentNumber() {
        return segmentNumber;
    }

    public int getBase() {
        return base;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public String toString() {
        return "Segment " + segmentNumber + " [base=" + base + ", limit=" + limit + "]";
    }
}
