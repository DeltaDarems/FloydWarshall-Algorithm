

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * CPU scheduling algorithms: FCFS, SJF (non-preemptive), Priority
 * (non-preemptive, lower number = higher priority) and Round Robin.
 *
 * This class contains no GUI code. Every method works on copies of the
 * given processes, so the caller's list is never modified.
 */
public class CPUScheduling {

    /** Label used in the Gantt chart when the CPU has nothing to run. */
    public static final String IDLE = "Idle";

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    /** One block of the Gantt chart: a process (or idle time) from start to end. */
    public static class GanttEntry {
        private final String processId;
        private final int start;
        private final int end;

        public GanttEntry(String processId, int start, int end) {
            this.processId = processId;
            this.start = start;
            this.end = end;
        }

        public String getProcessId() {
            return processId;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public boolean isIdle() {
            return IDLE.equals(processId);
        }

        @Override
        public String toString() {
            return processId + " [" + start + " - " + end + "]";
        }
    }

    /** Everything a GUI needs to show after a simulation. */
    public static class Result {
        private final String algorithmName;
        private final List<Process> processes;
        private final List<GanttEntry> gantt;
        private final double averageWaitingTime;
        private final double averageTurnaroundTime;
        private final int totalTime;
        private final double cpuUtilization;

        Result(String algorithmName, List<Process> processes, List<GanttEntry> gantt) {
            this.algorithmName = algorithmName;
            this.processes = processes;
            this.gantt = gantt;

            int totalWaiting = 0;
            int totalTurnaround = 0;
            int busyTime = 0;
            for (Process p : processes) {
                totalWaiting += p.getWaitingTime();
                totalTurnaround += p.getTurnaroundTime();
                busyTime += p.getBurstTime();
            }
            this.averageWaitingTime = (double) totalWaiting / processes.size();
            this.averageTurnaroundTime = (double) totalTurnaround / processes.size();
            this.totalTime = gantt.isEmpty() ? 0 : gantt.get(gantt.size() - 1).getEnd();
            this.cpuUtilization = totalTime == 0 ? 0 : (busyTime * 100.0) / totalTime;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        /** Processes (copies) with results filled in, in the same order as the input. */
        public List<Process> getProcesses() {
            return processes;
        }

        public List<GanttEntry> getGantt() {
            return gantt;
        }

        public double getAverageWaitingTime() {
            return averageWaitingTime;
        }

        public double getAverageTurnaroundTime() {
            return averageTurnaroundTime;
        }

        /** Time at which the last process finished (CPU time starts at 0). */
        public int getTotalTime() {
            return totalTime;
        }

        /** Percentage of total time the CPU was busy. */
        public double getCpuUtilization() {
            return cpuUtilization;
        }
    }

    // ------------------------------------------------------------------
    // Public algorithms
    // ------------------------------------------------------------------

    /** First Come First Serve: processes run in order of arrival. */
    public static Result fcfs(List<Process> processes) {
        return runNonPreemptive("FCFS", processes, new Comparator<Process>() {
            @Override
            public int compare(Process a, Process b) {
                return Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            }
        });
    }

    /** Shortest Job First (non-preemptive): shortest burst among arrived processes runs next. */
    public static Result sjf(List<Process> processes) {
        return runNonPreemptive("SJF (Non-preemptive)", processes, new Comparator<Process>() {
            @Override
            public int compare(Process a, Process b) {
                int c = Integer.compare(a.getBurstTime(), b.getBurstTime());
                return c != 0 ? c : Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            }
        });
    }

    /** Priority (non-preemptive): the lowest priority number runs first. */
    public static Result priority(List<Process> processes) {
        return runNonPreemptive("Priority (Non-preemptive)", processes, new Comparator<Process>() {
            @Override
            public int compare(Process a, Process b) {
                int c = Integer.compare(a.getPriority(), b.getPriority());
                return c != 0 ? c : Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            }
        });
    }

    /** Round Robin with the given time quantum. */
    public static Result roundRobin(List<Process> processes, int quantum) {
        validate(processes);
        if (quantum <= 0) {
            throw new IllegalArgumentException("Time quantum must be greater than zero.");
        }

        List<Process> work = copyOf(processes);
        List<Process> byArrival = new ArrayList<Process>(work);
        sortByArrival(byArrival);

        List<GanttEntry> gantt = new ArrayList<GanttEntry>();
        Queue<Process> readyQueue = new LinkedList<Process>();
        int n = byArrival.size();
        int time = 0;
        int next = 0;       // index of the next process that has not entered the queue yet
        int finished = 0;

        while (finished < n) {
            // Admit every process that has arrived by now
            while (next < n && byArrival.get(next).getArrivalTime() <= time) {
                readyQueue.add(byArrival.get(next));
                next++;
            }

            if (readyQueue.isEmpty()) {
                // CPU is idle until the next process arrives
                int nextArrival = byArrival.get(next).getArrivalTime();
                addGantt(gantt, IDLE, time, nextArrival);
                time = nextArrival;
                continue;
            }

            Process current = readyQueue.poll();
            if (current.getStartTime() < 0) {
                current.setStartTime(time);
            }
            int run = Math.min(quantum, current.getRemainingTime());
            addGantt(gantt, current.getId(), time, time + run);
            time += run;
            current.setRemainingTime(current.getRemainingTime() - run);

            // Processes that arrived while this one was running go in front of it
            while (next < n && byArrival.get(next).getArrivalTime() <= time) {
                readyQueue.add(byArrival.get(next));
                next++;
            }

            if (current.getRemainingTime() > 0) {
                readyQueue.add(current);
            } else {
                finish(current, time);
                finished++;
            }
        }

        return new Result("Round Robin (Quantum = " + quantum + ")", work, gantt);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Shared engine for FCFS, SJF and Priority. At every decision point it picks the
     * best arrived process according to the comparator (ties keep the input order).
     */
    private static Result runNonPreemptive(String name, List<Process> input, Comparator<Process> order) {
        validate(input);

        List<Process> work = copyOf(input);
        List<Process> remaining = new ArrayList<Process>(work);
        List<GanttEntry> gantt = new ArrayList<GanttEntry>();
        int time = 0;

        while (!remaining.isEmpty()) {
            Process best = null;
            for (Process p : remaining) {
                if (p.getArrivalTime() <= time && (best == null || order.compare(p, best) < 0)) {
                    best = p;
                }
            }

            if (best == null) {
                // Nobody has arrived yet: jump to the earliest arrival
                int nextArrival = Integer.MAX_VALUE;
                for (Process p : remaining) {
                    nextArrival = Math.min(nextArrival, p.getArrivalTime());
                }
                addGantt(gantt, IDLE, time, nextArrival);
                time = nextArrival;
                continue;
            }

            best.setStartTime(time);
            addGantt(gantt, best.getId(), time, time + best.getBurstTime());
            time += best.getBurstTime();
            best.setRemainingTime(0);
            finish(best, time);
            remaining.remove(best);
        }

        return new Result(name, work, gantt);
    }

    /** Fills completion, turnaround and waiting time for a finished process. */
    private static void finish(Process p, int completionTime) {
        p.setCompletionTime(completionTime);
        p.setTurnaroundTime(completionTime - p.getArrivalTime());
        p.setWaitingTime(p.getTurnaroundTime() - p.getBurstTime());
    }

    /** Appends a Gantt block, merging it with the previous one if it continues the same process. */
    private static void addGantt(List<GanttEntry> gantt, String processId, int start, int end) {
        if (end <= start) {
            return;
        }
        if (!gantt.isEmpty()) {
            GanttEntry last = gantt.get(gantt.size() - 1);
            if (last.getProcessId().equals(processId) && last.getEnd() == start) {
                gantt.set(gantt.size() - 1, new GanttEntry(processId, last.getStart(), end));
                return;
            }
        }
        gantt.add(new GanttEntry(processId, start, end));
    }

    private static void validate(List<Process> processes) {
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("There are no processes to schedule.");
        }
    }

    private static List<Process> copyOf(List<Process> processes) {
        List<Process> copies = new ArrayList<Process>();
        for (Process p : processes) {
            copies.add(p.copy());
        }
        return copies;
    }

    /** Stable sort by arrival time (equal arrivals keep their input order). */
    private static void sortByArrival(List<Process> list) {
        java.util.Collections.sort(list, new Comparator<Process>() {
            @Override
            public int compare(Process a, Process b) {
                return Integer.compare(a.getArrivalTime(), b.getArrivalTime());
            }
        });
    }
}