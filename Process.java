
/**
 * Represents a process / job in the simulator.
 * Shared by CPU Scheduling, Multiprogramming and other modules.
 */
public class Process {

    private final String id;
    private final int arrivalTime;
    private final int burstTime;
    private final int priority;
    private final int memorySize;

    // Values filled in by the scheduling algorithms
    private int remainingTime;
    private int startTime;
    private int completionTime;
    private int waitingTime;
    private int turnaroundTime;

    /** Creates a process with no memory requirement. */
    public Process(String id, int arrivalTime, int burstTime, int priority) {
        this(id, arrivalTime, burstTime, priority, 0);
    }

    /** Creates a process with a memory requirement (used by Multiprogramming). */
    public Process(String id, int arrivalTime, int burstTime, int priority, int memorySize) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Process ID cannot be empty.");
        }
        if (arrivalTime < 0) {
            throw new IllegalArgumentException("Arrival time cannot be negative.");
        }
        if (burstTime <= 0) {
            throw new IllegalArgumentException("Burst time must be greater than zero.");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Priority cannot be negative.");
        }
        if (memorySize < 0) {
            throw new IllegalArgumentException("Memory size cannot be negative.");
        }
        this.id = id.trim();
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.memorySize = memorySize;
        reset();
    }

    /** Clears all computed results so the process can be simulated again. */
    public void reset() {
        this.remainingTime = burstTime;
        this.startTime = -1;
        this.completionTime = 0;
        this.waitingTime = 0;
        this.turnaroundTime = 0;
    }

    /** Returns a fresh copy (input values only, results cleared). */
    public Process copy() {
        return new Process(id, arrivalTime, burstTime, priority, memorySize);
    }

    public String getId() {
        return id;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getBurstTime() {
        return burstTime;
    }

    public int getPriority() {
        return priority;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(int completionTime) {
        this.completionTime = completionTime;
    }

    public int getWaitingTime() {
        return waitingTime;
    }

    public void setWaitingTime(int waitingTime) {
        this.waitingTime = waitingTime;
    }

    public int getTurnaroundTime() {
        return turnaroundTime;
    }

    public void setTurnaroundTime(int turnaroundTime) {
        this.turnaroundTime = turnaroundTime;
    }

    @Override
    public String toString() {
        return id + " [arrival=" + arrivalTime + ", burst=" + burstTime
                + ", priority=" + priority + ", memory=" + memorySize + "]";
    }
}