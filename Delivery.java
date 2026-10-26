package droneproject;

public class Delivery {

    public static final String PENDING = "PENDING";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private final String id;
    private final int x;
    private final int y;
    private final int weight;
    private final int priority;

    private String status;
    private Drone assignedDrone;
    private String failureReason;

    public Delivery(String id, int x, int y, int weight, int priority) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Delivery ID cannot be empty.");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Delivery weight must be greater than zero.");
        }
        if (priority < 1 || priority > 5) {
            throw new IllegalArgumentException("Priority must be between 1 and 5.");
        }

        this.id = id.trim();
        this.x = x;
        this.y = y;
        this.weight = weight;
        this.priority = priority;
        this.status = PENDING;
    }

    public String getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWeight() {
        return weight;
    }

    public int getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public Drone getAssignedDrone() {
        return assignedDrone;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public boolean isAssignable() {
        return PENDING.equals(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    public boolean isFailed() {
        return FAILED.equals(status);
    }

    public void assignTo(Drone drone) {
        if (!isAssignable()) {
            throw new IllegalStateException("Delivery " + id + " cannot be assigned again.");
        }
        if (drone == null) {
            throw new IllegalArgumentException("Assigned drone cannot be null.");
        }
        if (assignedDrone != null) {
            throw new IllegalStateException("Delivery " + id + " is already assigned.");
        }

        this.assignedDrone = drone;
        this.status = ASSIGNED;
    }

    public void markInProgress() {
        if (!ASSIGNED.equals(status)) {
            throw new IllegalStateException("Delivery " + id + " is not in ASSIGNED state.");
        }
        this.status = IN_PROGRESS;
    }

    public void markCompleted() {
        if (COMPLETED.equals(status)) {
            return;
        }
        if (FAILED.equals(status)) {
            throw new IllegalStateException("A failed delivery cannot be completed.");
        }
        this.status = COMPLETED;
    }

    public void markFailed(String reason) {
        if (COMPLETED.equals(status)) {
            throw new IllegalStateException("A completed delivery cannot be marked failed.");
        }
        this.status = FAILED;
        this.failureReason = reason == null ? "Unknown failure" : reason;
        this.assignedDrone = null;
    }

    public void reset() {
        this.status = PENDING;
        this.assignedDrone = null;
        this.failureReason = null;
    }

    @Override
    public String toString() {
        return id + " to (" + x + ", " + y + "), weight " + weight
                + ", priority " + priority + ", status " + status;
    }
}