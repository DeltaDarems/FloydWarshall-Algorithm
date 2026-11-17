import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Multiprogramming with a fixed total memory.
 *
 * A program is loaded when it fits in the free memory; otherwise it goes to
 * the waiting queue. Terminating a program frees its memory and the waiting
 * programs are moved in automatically.
 *
 * Two admission policies for the waiting queue:
 *  - normal (default): every waiting program that fits is loaded, front to back,
 *    even if one before it is still too big (a bigger program does not block the rest).
 *  - strict FIFO: loading stops at the first waiting program that does not fit, and a
 *    new program that arrives while others wait must queue behind them.
 *
 * Memory is treated as one pool of {@code totalMemory} units (no addresses or
 * partitions): used = sum of loaded programs, free = total - used.
 */
public class Multiprogramming {

    /** What happened to a program that was submitted. */
    public enum Status {
        LOADED, WAITING
    }

    private int totalMemory;
    private boolean strictFifo;
    private final List<Process> loaded = new ArrayList<Process>();
    private final List<Process> waiting = new ArrayList<Process>();
    private final List<String> log = new ArrayList<String>();

    public Multiprogramming(int totalMemory) {
        validateTotal(totalMemory);
        this.totalMemory = totalMemory;
        log.add("Simulation started. Total memory: " + totalMemory + ".");
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    /**
     * Submits a program. It is loaded if it fits, otherwise it joins the waiting queue.
     *
     * @throws IllegalArgumentException when the program needs no memory, needs more than the
     *                                  total memory, or its ID is already in use
     */
    public Status load(Process process) {
        validateNew(process);
        int size = process.getMemorySize();
        boolean fits = size <= getFreeMemory();
        boolean blockedByQueue = strictFifo && !waiting.isEmpty();

        if (fits && !blockedByQueue) {
            loaded.add(process);
            log.add(process.getId() + " loaded (" + size + "). Free memory: " + getFreeMemory() + ".");
            return Status.LOADED;
        }

        waiting.add(process);
        if (fits) {
            log.add(process.getId() + " needs " + size + " and would fit, but strict FIFO keeps it behind"
                    + " the waiting queue (position " + waiting.size() + ").");
        } else {
            log.add(process.getId() + " needs " + size + " but only " + getFreeMemory()
                    + " is free. Added to the waiting queue (position " + waiting.size() + ").");
        }
        return Status.WAITING;
    }

    /**
     * Terminates a loaded program, frees its memory and moves waiting programs in.
     * If the ID belongs to a waiting program it is simply removed from the queue.
     *
     * @return the programs that were moved from the waiting queue into memory
     * @throws IllegalArgumentException when no program with that ID exists
     */
    public List<Process> terminate(String processId) {
        Process running = find(loaded, processId);
        if (running != null) {
            loaded.remove(running);
            log.add(running.getId() + " terminated. Freed " + running.getMemorySize()
                    + ". Free memory: " + getFreeMemory() + ".");
            return admitWaiting();
        }
        Process queued = find(waiting, processId);
        if (queued != null) {
            waiting.remove(queued);
            log.add(queued.getId() + " removed from the waiting queue.");
            // In strict FIFO the removed program may have been the one blocking the queue.
            return admitWaiting();
        }
        throw new IllegalArgumentException("No program named \"" + processId + "\" is loaded or waiting.");
    }

    /** Clears all programs and the log, and sets a new total memory. */
    public void reset(int newTotalMemory) {
        validateTotal(newTotalMemory);
        this.totalMemory = newTotalMemory;
        loaded.clear();
        waiting.clear();
        log.clear();
        log.add("Simulation reset. Total memory: " + newTotalMemory + ".");
    }

    private List<Process> admitWaiting() {
        List<Process> moved = new ArrayList<Process>();
        Iterator<Process> it = waiting.iterator();
        while (it.hasNext()) {
            Process p = it.next();
            if (p.getMemorySize() <= getFreeMemory()) {
                it.remove();
                loaded.add(p);
                moved.add(p);
                log.add(p.getId() + " moved from the waiting queue to memory (" + p.getMemorySize()
                        + "). Free memory: " + getFreeMemory() + ".");
            } else if (strictFifo) {
                break;
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private static void validateTotal(int total) {
        if (total <= 0) {
            throw new IllegalArgumentException("Total memory must be greater than zero.");
        }
    }

    private void validateNew(Process process) {
        if (process == null) {
            throw new IllegalArgumentException("Program cannot be null.");
        }
        if (process.getMemorySize() <= 0) {
            throw new IllegalArgumentException("Program " + process.getId()
                    + " must need at least 1 unit of memory.");
        }
        if (process.getMemorySize() > totalMemory) {
            throw new IllegalArgumentException("Program " + process.getId() + " needs "
                    + process.getMemorySize() + " but the total memory is only " + totalMemory
                    + ", so it can never be loaded.");
        }
        if (find(loaded, process.getId()) != null || find(waiting, process.getId()) != null) {
            throw new IllegalArgumentException("A program named \"" + process.getId()
                    + "\" is already loaded or waiting.");
        }
    }

    private static Process find(List<Process> list, String id) {
        if (id == null) {
            return null;
        }
        for (Process p : list) {
            if (p.getId().equals(id.trim())) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public int getTotalMemory() {
        return totalMemory;
    }

    public int getUsedMemory() {
        int used = 0;
        for (Process p : loaded) {
            used += p.getMemorySize();
        }
        return used;
    }

    public int getFreeMemory() {
        return totalMemory - getUsedMemory();
    }

    /** Used memory as a percentage of total memory (0 to 100). */
    public double getUtilization() {
        return 100.0 * getUsedMemory() / totalMemory;
    }

    /** Programs in memory, in the order they were loaded. */
    public List<Process> getLoaded() {
        return Collections.unmodifiableList(loaded);
    }

    /** Programs waiting for memory, front of the queue first. */
    public List<Process> getWaiting() {
        return Collections.unmodifiableList(waiting);
    }

    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    public boolean hasPrograms() {
        return !loaded.isEmpty() || !waiting.isEmpty();
    }

    public boolean contains(String processId) {
        return find(loaded, processId) != null || find(waiting, processId) != null;
    }

    public boolean isStrictFifo() {
        return strictFifo;
    }

    public void setStrictFifo(boolean strictFifo) {
        this.strictFifo = strictFifo;
    }
}