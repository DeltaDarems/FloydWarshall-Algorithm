import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Segmentation: segment table, base/limit and logical-to-physical address
 * translation with bounds checking.
 *
 * This class contains no GUI code. A logical address is a pair (segment, offset).
 *   - if the segment does not exist          -> invalid (trap)
 *   - if offset is not in 0 .. limit - 1     -> invalid (trap: offset beyond the limit)
 *   - otherwise physical address = base + offset
 */
public class Segmentation {

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** A logical address: segment number plus offset inside that segment. */
    public static class Address {
        private final int segment;
        private final int offset;

        public Address(int segment, int offset) {
            this.segment = segment;
            this.offset = offset;
        }

        public int getSegment() {
            return segment;
        }

        public int getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return segment + ":" + offset;
        }
    }

    /** Result of translating one logical address. */
    public static class Translation {
        private final Address address;
        private final boolean segmentFound;
        private final int base;
        private final int limit;
        private final boolean valid;
        private final long physicalAddress;
        private final String message;

        Translation(Address address, boolean segmentFound, int base, int limit,
                    boolean valid, long physicalAddress, String message) {
            this.address = address;
            this.segmentFound = segmentFound;
            this.base = base;
            this.limit = limit;
            this.valid = valid;
            this.physicalAddress = physicalAddress;
            this.message = message;
        }

        public Address getAddress() {
            return address;
        }

        /** False when the segment number is not in the segment table. */
        public boolean isSegmentFound() {
            return segmentFound;
        }

        /** Base of the segment (only meaningful when the segment was found). */
        public int getBase() {
            return base;
        }

        /** Limit of the segment (only meaningful when the segment was found). */
        public int getLimit() {
            return limit;
        }

        public boolean isValid() {
            return valid;
        }

        /** Physical address, or -1 when the address is invalid. */
        public long getPhysicalAddress() {
            return physicalAddress;
        }

        public String getMessage() {
            return message;
        }
    }

    // ------------------------------------------------------------------
    // Public algorithms
    // ------------------------------------------------------------------

    /** Checks that no segment number appears twice. */
    public static void validateTable(List<Segment> table) {
        if (table == null) {
            throw new IllegalArgumentException("The segment table cannot be null.");
        }
        Set<Integer> numbers = new HashSet<Integer>();
        for (Segment s : table) {
            if (!numbers.add(s.getSegmentNumber())) {
                throw new IllegalArgumentException("Segment " + s.getSegmentNumber()
                        + " is already in the segment table.");
            }
        }
    }

    /**
     * Describes every pair of segments whose physical ranges overlap. Overlap is not an
     * error (translation still works) but it usually means a mistake in the table.
     */
    public static List<String> findOverlaps(List<Segment> table) {
        List<String> overlaps = new ArrayList<String>();
        for (int i = 0; i < table.size(); i++) {
            for (int j = i + 1; j < table.size(); j++) {
                Segment a = table.get(i);
                Segment b = table.get(j);
                if (a.getBase() <= b.getEndAddress() && b.getBase() <= a.getEndAddress()) {
                    overlaps.add("Segment " + a.getSegmentNumber() + " [" + a.getBase() + "-"
                            + a.getEndAddress() + "] overlaps segment " + b.getSegmentNumber()
                            + " [" + b.getBase() + "-" + b.getEndAddress() + "]");
                }
            }
        }
        return overlaps;
    }

    /** Finds a segment by number, or returns null. */
    public static Segment findSegment(List<Segment> table, int segmentNumber) {
        for (Segment s : table) {
            if (s.getSegmentNumber() == segmentNumber) {
                return s;
            }
        }
        return null;
    }

    /**
     * Translates one logical address. A missing segment, a negative offset or an offset
     * beyond the limit does not throw: it returns an invalid Translation with an explanation.
     */
    public static Translation translate(List<Segment> table, Address address) {
        validateTable(table);

        Segment segment = findSegment(table, address.getSegment());
        if (segment == null) {
            return new Translation(address, false, -1, -1, false, -1,
                    "Invalid: segment " + address.getSegment() + " is not in the segment table (trap).");
        }
        if (address.getOffset() < 0) {
            return new Translation(address, true, segment.getBase(), segment.getLimit(), false, -1,
                    "Invalid: the offset cannot be negative (trap).");
        }
        if (!segment.isValidOffset(address.getOffset())) {
            return new Translation(address, true, segment.getBase(), segment.getLimit(), false, -1,
                    "Invalid: offset " + address.getOffset() + " >= limit " + segment.getLimit()
                            + " (trap).");
        }
        long physical = (long) segment.getBase() + address.getOffset();
        return new Translation(address, true, segment.getBase(), segment.getLimit(), true, physical,
                "Valid: " + segment.getBase() + " + " + address.getOffset() + " = " + physical);
    }

    /** Translates several logical addresses in order. */
    public static List<Translation> translateAll(List<Segment> table, List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("There are no logical addresses to translate.");
        }
        List<Translation> results = new ArrayList<Translation>();
        for (Address a : addresses) {
            results.add(translate(table, a));
        }
        return results;
    }
}