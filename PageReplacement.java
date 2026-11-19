import java.util.ArrayList;
import java.util.List;

/**
 * Page replacement algorithms: FIFO, LRU, Optimal, Second Chance and LFU.
 *
 * This class contains no GUI code. Each algorithm returns a {@link Result} with one
 * {@link Step} per page reference, so a GUI can show every frame change.
 *
 * Rules shared by all algorithms:
 *  - A reference is a HIT when the page is already in a frame, otherwise a FAULT.
 *  - On a fault the lowest-numbered empty frame is used first.
 *  - When every frame is full the algorithm picks a victim frame:
 *      FIFO    - the page that has been in memory the longest,
 *      LRU     - the page that was used least recently,
 *      Optimal       - the page whose next use is farthest in the future (a page that is
 *                      never used again counts as infinitely far; ties go to the page
 *                      that was loaded earliest),
 *      Second Chance - the clock algorithm, using a reference bit and circular hand,
 *      LFU           - the page with the fewest references while resident (ties go to
 *                      the page that was loaded earliest).
 */
public class PageReplacement {

    /** Marks an empty frame in {@link Step#getFrames()} and "nothing replaced". */
    public static final int EMPTY = -1;

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** State after processing one page reference. */
    public static class Step {
        private final int stepNumber;
        private final int page;
        private final List<Integer> frames;
        private final boolean hit;
        private final int changedFrame;
        private final int replacedPage;

        Step(int stepNumber, int page, List<Integer> frames, boolean hit,
             int changedFrame, int replacedPage) {
            this.stepNumber = stepNumber;
            this.page = page;
            this.frames = frames;
            this.hit = hit;
            this.changedFrame = changedFrame;
            this.replacedPage = replacedPage;
        }

        /** 1-based position in the reference string. */
        public int getStepNumber() {
            return stepNumber;
        }

        /** The page that was referenced. */
        public int getPage() {
            return page;
        }

        /** Contents of every frame after this reference ({@link #EMPTY} for an empty frame). */
        public List<Integer> getFrames() {
            return frames;
        }

        public boolean isHit() {
            return hit;
        }

        public boolean isFault() {
            return !hit;
        }

        /** Index of the frame that was loaded on a fault, or -1 on a hit. */
        public int getChangedFrame() {
            return changedFrame;
        }

        /** Page that was removed on a fault, or {@link #EMPTY} if the frame was empty / on a hit. */
        public int getReplacedPage() {
            return replacedPage;
        }
    }

    /** Complete outcome of one algorithm run. */
    public static class Result {
        private final String algorithmName;
        private final int frameCount;
        private final List<Integer> references;
        private final List<Step> steps;
        private final int hits;
        private final int faults;

        Result(String algorithmName, int frameCount, List<Integer> references,
               List<Step> steps, int hits, int faults) {
            this.algorithmName = algorithmName;
            this.frameCount = frameCount;
            this.references = references;
            this.steps = steps;
            this.hits = hits;
            this.faults = faults;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        public int getFrameCount() {
            return frameCount;
        }

        public List<Integer> getReferences() {
            return references;
        }

        public List<Step> getSteps() {
            return steps;
        }

        public int getHits() {
            return hits;
        }

        public int getFaults() {
            return faults;
        }

        public int getTotalReferences() {
            return references.size();
        }

        /** Hits as a percentage of all references. */
        public double getHitRatio() {
            return hits * 100.0 / references.size();
        }

        /** Page faults as a percentage of all references. */
        public double getFaultRatio() {
            return faults * 100.0 / references.size();
        }
    }

    /** Chooses which full frame to overwrite. */
    private interface VictimChooser {
        int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                   int[] frequencies, int[] referenceBits, List<Integer> references);
    }

    // ------------------------------------------------------------------
    // Public algorithms
    // ------------------------------------------------------------------

    /** First In First Out: replace the page that has been in memory the longest. */
    public static Result fifo(List<Integer> references, int frameCount) {
        return simulate("FIFO", references, frameCount, new VictimChooser() {
            @Override
            public int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                              int[] frequencies, int[] referenceBits, List<Integer> refs) {
                int victim = 0;
                for (int i = 1; i < slots.length; i++) {
                    if (loadTime[i] < loadTime[victim]) {
                        victim = i;
                    }
                }
                return victim;
            }
        });
    }

    /** Least Recently Used: replace the page that has not been used for the longest time. */
    public static Result lru(List<Integer> references, int frameCount) {
        return simulate("LRU", references, frameCount, new VictimChooser() {
            @Override
            public int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                              int[] frequencies, int[] referenceBits, List<Integer> refs) {
                int victim = 0;
                for (int i = 1; i < slots.length; i++) {
                    if (lastUse[i] < lastUse[victim]) {
                        victim = i;
                    }
                }
                return victim;
            }
        });
    }

    /** Optimal: replace the page that will not be needed for the longest time. */
    public static Result optimal(List<Integer> references, int frameCount) {
        return simulate("Optimal", references, frameCount, new VictimChooser() {
            @Override
            public int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                              int[] frequencies, int[] referenceBits, List<Integer> refs) {
                int victim = -1;
                int victimNext = -1;
                for (int i = 0; i < slots.length; i++) {
                    int next = nextUse(refs, position, slots[i]);
                    boolean better = victim < 0 || next > victimNext
                            || (next == victimNext && loadTime[i] < loadTime[victim]);
                    if (better) {
                        victim = i;
                        victimNext = next;
                    }
                }
                return victim;
            }
        });
    }

    /** Second Chance (Clock): skip recently referenced pages once before replacing them. */
    public static Result secondChance(List<Integer> references, int frameCount) {
        final int[] hand = {0};
        return simulate("Second Chance (Clock)", references, frameCount, new VictimChooser() {
            @Override
            public int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                              int[] frequencies, int[] referenceBits, List<Integer> refs) {
                while (referenceBits[hand[0]] != 0) {
                    referenceBits[hand[0]] = 0;
                    hand[0] = (hand[0] + 1) % slots.length;
                }
                int victim = hand[0];
                hand[0] = (hand[0] + 1) % slots.length;
                return victim;
            }
        });
    }

    /** Least Frequently Used: replace the page with the fewest references while resident. */
    public static Result lfu(List<Integer> references, int frameCount) {
        return simulate("LFU", references, frameCount, new VictimChooser() {
            @Override
            public int choose(int position, int[] slots, int[] loadTime, int[] lastUse,
                              int[] frequencies, int[] referenceBits, List<Integer> refs) {
                int victim = 0;
                for (int i = 1; i < slots.length; i++) {
                    if (frequencies[i] < frequencies[victim]
                            || (frequencies[i] == frequencies[victim]
                            && loadTime[i] < loadTime[victim])) {
                        victim = i;
                    }
                }
                return victim;
            }
        });
    }

    /** Runs all supported algorithms on the same input. */
    public static List<Result> compareAll(List<Integer> references, int frameCount) {
        List<Result> results = new ArrayList<Result>();
        results.add(fifo(references, frameCount));
        results.add(lru(references, frameCount));
        results.add(optimal(references, frameCount));
        results.add(secondChance(references, frameCount));
        results.add(lfu(references, frameCount));
        return results;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Shared simulation loop; only the victim choice differs between algorithms. */
    private static Result simulate(String name, List<Integer> references, int frameCount,
                                   VictimChooser chooser) {
        validate(references, frameCount);

        int[] slots = new int[frameCount];
        int[] loadTime = new int[frameCount];
        int[] lastUse = new int[frameCount];
        int[] frequencies = new int[frameCount];
        int[] referenceBits = new int[frameCount];
        for (int i = 0; i < frameCount; i++) {
            slots[i] = EMPTY;
        }

        List<Step> steps = new ArrayList<Step>();
        int hits = 0;
        int faults = 0;

        for (int pos = 0; pos < references.size(); pos++) {
            int page = references.get(pos);

            int found = -1;
            for (int i = 0; i < frameCount; i++) {
                if (slots[i] == page) {
                    found = i;
                    break;
                }
            }

            boolean hit = found >= 0;
            int changed = -1;
            int replaced = EMPTY;

            if (hit) {
                hits++;
                lastUse[found] = pos;
                frequencies[found]++;
                referenceBits[found] = 1;
            } else {
                faults++;
                int target = -1;
                for (int i = 0; i < frameCount; i++) {
                    if (slots[i] == EMPTY) {
                        target = i;
                        break;
                    }
                }
                if (target < 0) {
                    target = chooser.choose(pos, slots, loadTime, lastUse, frequencies,
                            referenceBits, references);
                    replaced = slots[target];
                }
                slots[target] = page;
                loadTime[target] = pos;
                lastUse[target] = pos;
                frequencies[target] = 1;
                referenceBits[target] = 1;
                changed = target;
            }

            List<Integer> snapshot = new ArrayList<Integer>();
            for (int value : slots) {
                snapshot.add(value);
            }
            steps.add(new Step(pos + 1, page, snapshot, hit, changed, replaced));
        }

        return new Result(name, frameCount, new ArrayList<Integer>(references), steps, hits, faults);
    }

    /** Index of the next use of a page after position, or Integer.MAX_VALUE if never used again. */
    private static int nextUse(List<Integer> references, int position, int page) {
        for (int i = position + 1; i < references.size(); i++) {
            if (references.get(i) == page) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static void validate(List<Integer> references, int frameCount) {
        if (references == null || references.isEmpty()) {
            throw new IllegalArgumentException("The reference string cannot be empty.");
        }
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Number of frames must be greater than zero.");
        }
        for (int page : references) {
            if (page < 0) {
                throw new IllegalArgumentException("Page numbers cannot be negative (found " + page + ").");
            }
        }
    }
}