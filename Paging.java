import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Paging: page table, page-to-frame mapping and logical-to-physical address
 * translation with invalid address handling.
 *
 * This class contains no GUI code. Any number of pages and frames is supported
 * because the page table is a List.
 *
 * Translation formulas:
 *   page number      = logical address / page size
 *   offset           = logical address mod page size
 *   physical address = frame number * page size + offset
 */
public class Paging {

    /** Frame number meaning "this page is not mapped" / "this frame is free". */
    public static final int NONE = -1;

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** One row of the page table: page number mapped to a frame number. */
    public static class PageEntry {
        private final int page;
        private final int frame;

        public PageEntry(int page, int frame) {
            if (page < 0) {
                throw new IllegalArgumentException("Page number cannot be negative.");
            }
            if (frame < 0) {
                throw new IllegalArgumentException("Frame number cannot be negative.");
            }
            this.page = page;
            this.frame = frame;
        }

        public int getPage() {
            return page;
        }

        public int getFrame() {
            return frame;
        }

        @Override
        public String toString() {
            return "Page " + page + " -> Frame " + frame;
        }
    }

    /** Result of translating one logical address. */
    public static class Translation {
        private final long logicalAddress;
        private final long page;
        private final long offset;
        private final int frame;
        private final long physicalAddress;
        private final boolean valid;
        private final String message;

        Translation(long logicalAddress, long page, long offset, int frame,
                    long physicalAddress, boolean valid, String message) {
            this.logicalAddress = logicalAddress;
            this.page = page;
            this.offset = offset;
            this.frame = frame;
            this.physicalAddress = physicalAddress;
            this.valid = valid;
            this.message = message;
        }

        public long getLogicalAddress() {
            return logicalAddress;
        }

        public long getPage() {
            return page;
        }

        public long getOffset() {
            return offset;
        }

        /** Frame holding the page, or {@link #NONE} when the address is invalid. */
        public int getFrame() {
            return frame;
        }

        /** Physical address, or -1 when the address is invalid. */
        public long getPhysicalAddress() {
            return physicalAddress;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    // ------------------------------------------------------------------
    // Public algorithms
    // ------------------------------------------------------------------

    /** Checks page size and number of frames. */
    public static void validateConfig(int pageSize, int frameCount) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero.");
        }
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Number of frames must be greater than zero.");
        }
    }

    /**
     * Checks a whole page table: every frame must exist (0 to frameCount - 1),
     * and no page or frame may appear twice.
     */
    public static void validateTable(int frameCount, List<PageEntry> table) {
        if (table == null) {
            throw new IllegalArgumentException("The page table cannot be null.");
        }
        Set<Integer> pages = new HashSet<Integer>();
        Set<Integer> frames = new HashSet<Integer>();
        for (PageEntry e : table) {
            if (e.getFrame() >= frameCount) {
                throw new IllegalArgumentException("Frame " + e.getFrame() + " does not exist. "
                        + "Valid frames are 0 to " + (frameCount - 1) + ".");
            }
            if (!pages.add(e.getPage())) {
                throw new IllegalArgumentException("Page " + e.getPage()
                        + " is already in the page table.");
            }
            if (!frames.add(e.getFrame())) {
                throw new IllegalArgumentException("Frame " + e.getFrame()
                        + " is already used by another page.");
            }
        }
    }

    /**
     * For every frame, the page stored in it, or {@link #NONE} when the frame is free.
     * The returned list has exactly frameCount entries.
     */
    public static List<Integer> frameMap(int frameCount, List<PageEntry> table) {
        validateConfig(1, frameCount);
        validateTable(frameCount, table);
        List<Integer> map = new ArrayList<Integer>();
        for (int i = 0; i < frameCount; i++) {
            map.add(NONE);
        }
        for (PageEntry e : table) {
            map.set(e.getFrame(), e.getPage());
        }
        return map;
    }

    /**
     * Translates one logical address. A negative address or a page that is not in the
     * page table does not throw: it returns an invalid Translation with an explanation.
     */
    public static Translation translate(int pageSize, int frameCount,
                                        List<PageEntry> table, long logicalAddress) {
        validateConfig(pageSize, frameCount);
        validateTable(frameCount, table);

        if (logicalAddress < 0) {
            return new Translation(logicalAddress, NONE, NONE, NONE, -1, false,
                    "Invalid: a logical address cannot be negative.");
        }

        long page = logicalAddress / pageSize;
        long offset = logicalAddress % pageSize;

        for (PageEntry e : table) {
            if (e.getPage() == page) {
                long physical = (long) e.getFrame() * pageSize + offset;
                return new Translation(logicalAddress, page, offset, e.getFrame(), physical, true,
                        "Valid: " + e.getFrame() + " x " + pageSize + " + " + offset + " = " + physical);
            }
        }
        return new Translation(logicalAddress, page, offset, NONE, -1, false,
                "Invalid: page " + page + " is not in the page table.");
    }

    /** Translates several logical addresses in order. */
    public static List<Translation> translateAll(int pageSize, int frameCount,
                                                 List<PageEntry> table, List<Integer> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalArgumentException("There are no logical addresses to translate.");
        }
        List<Translation> results = new ArrayList<Translation>();
        for (int address : addresses) {
            results.add(translate(pageSize, frameCount, table, address));
        }
        return results;
    }
}