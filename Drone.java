package droneproject;

import java.util.ArrayList;
import java.util.List;

public class Drone {

    public static final String IDLE = "IDLE";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String DELIVERING = "DELIVERING";
    public static final String RETURNING = "RETURNING";
    public static final String TO_CHARGER = "TO_CHARGER";
    public static final String CHARGING = "CHARGING";
    public static final String WAITING_FOR_CHARGER = "WAITING_FOR_CHARGER";
    public static final String INSUFFICIENT_ENERGY = "INSUFFICIENT_ENERGY";

    public static final int FULL_BATTERY_ENERGY = 500;

    private final String id;
    private final int startX;
    private final int startY;
    private final int initialBattery;
    private final int maxPayload;

    private int x;
    private int y;
    private int battery;
    private int currentLoad;

    private String status = IDLE;
    private Delivery assignedDelivery;
    private boolean carryingPackage;

    private List<int[]> path = new ArrayList<>();
    private int pathIndex;

    private Environment.ChargingStation chargingStation;

    public Drone(
            String id,
            int x,
            int y,
            int initialBatteryPercent,
            int maxPayload
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Drone ID is required.");
        }

        if (initialBatteryPercent < 0 || initialBatteryPercent > 100) {
            throw new IllegalArgumentException("Battery percentage must be 0 to 100.");
        }

        if (maxPayload <= 0) {
            throw new IllegalArgumentException("Maximum payload must be positive.");
        }

        this.id = id.trim();
        this.x = x;
        this.y = y;
        this.startX = x;
        this.startY = y;
        this.maxPayload = maxPayload;

        this.initialBattery =
                FULL_BATTERY_ENERGY * initialBatteryPercent / 100;
        this.battery = initialBattery;
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

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public int getBattery() {
        return battery;
    }

    public int getMaxBattery() {
        return FULL_BATTERY_ENERGY;
    }

    public int getBatteryPercent() {
        return battery * 100 / FULL_BATTERY_ENERGY;
    }

    public int getMaxPayload() {
        return maxPayload;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public String getStatus() {
        return status;
    }

    public Delivery getAssignedDelivery() {
        return assignedDelivery;
    }

    public boolean isCarryingPackage() {
        return carryingPackage;
    }

    public List<int[]> getPath() {
        return path;
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public Environment.ChargingStation getChargingStation() {
        return chargingStation;
    }

    public boolean isAvailable() {
        return IDLE.equals(status)
                && assignedDelivery == null
                && chargingStation == null;
    }

    public boolean canCarry(int weight) {
        return weight > 0 && weight <= maxPayload - currentLoad;
    }

    public boolean hasBatteryFor(int requiredEnergy) {
        return requiredEnergy >= 0 && battery >= requiredEnergy;
    }

    public void setStatus(String newStatus) {
        if (!IDLE.equals(newStatus)
                && !ASSIGNED.equals(newStatus)
                && !DELIVERING.equals(newStatus)
                && !RETURNING.equals(newStatus)
                && !TO_CHARGER.equals(newStatus)
                && !CHARGING.equals(newStatus)
                && !WAITING_FOR_CHARGER.equals(newStatus)
                && !INSUFFICIENT_ENERGY.equals(newStatus)) {
            throw new IllegalArgumentException("Unknown drone status.");
        }

        status = newStatus;
    }

    public void setPath(List<int[]> newPath) {
        List<int[]> copy = new ArrayList<>();

        if (newPath != null) {
            for (int[] cell : newPath) {
                if (cell == null || cell.length < 2) {
                    throw new IllegalArgumentException("Invalid path coordinate.");
                }
                copy.add(new int[]{cell[0], cell[1]});
            }
        }

        path = copy;
        pathIndex = 0;

        // A* includes the current cell. It costs no energy to stay there.
        while (pathIndex < path.size()
                && path.get(pathIndex)[0] == x
                && path.get(pathIndex)[1] == y) 
        {
            pathIndex++;
        }
    }

    public void clearPath() {
        path = new ArrayList<>();
        pathIndex = 0;
    }

    public boolean moveAlongPath() {
        if (pathIndex >= path.size()) {
            return false;
        }

        int[] next = path.get(pathIndex);

        int distance = Math.abs(next[0] - x)
                + Math.abs(next[1] - y);

        if (distance != 1) {
            throw new IllegalStateException(
                    "Path contains a non-adjacent movement."
            );
        }

        if (battery < 1) {
            return false;
        }

        x = next[0];
        y = next[1];
        battery--;
        pathIndex++;

        return true;
    }

    public void assignDelivery(Delivery delivery) {
        if (!isAvailable()
                || delivery == null
                || !delivery.isAssignable()
                || delivery.getAssignedDrone() != null
                || !canCarry(delivery.getWeight())) {
            throw new IllegalStateException("Invalid assignment.");
        }

        delivery.assignTo(this);

        assignedDelivery = delivery;
        currentLoad += delivery.getWeight();
        carryingPackage = true;
        status = ASSIGNED;
    }

    public void startDelivery() {
        if (!ASSIGNED.equals(status) || assignedDelivery == null) {
            throw new IllegalStateException(
                    "Drone has no delivery ready to start."
            );
        }

        assignedDelivery.markInProgress();
        status = DELIVERING;
    }

    public void completeDelivery() {
        if (assignedDelivery == null) {
            return;
        }

        Delivery delivery = assignedDelivery;

        if (!DELIVERING.equals(status)
                || !Delivery.IN_PROGRESS.equals(delivery.getStatus())
                || x != delivery.getX()
                || y != delivery.getY()) {
            throw new IllegalStateException(
                    "Delivery completion conditions are not satisfied."
            );
        }

        delivery.markCompleted();
        currentLoad -= delivery.getWeight();

        assignedDelivery = null;
        carryingPackage = false;
        clearPath();
        status = RETURNING;
    }

    public void failDelivery(String reason) {
        if (assignedDelivery != null) {
            Delivery delivery = assignedDelivery;
            delivery.markFailed(reason);

            if (carryingPackage) {
                currentLoad -= delivery.getWeight();
            }
        }

        assignedDelivery = null;
        carryingPackage = false;
        clearPath();
        status = INSUFFICIENT_ENERGY;
    }

    public void travelToCharger(
            Environment.ChargingStation station,
            List<int[]> route
    ) {
        if (station == null || route == null
                || assignedDelivery != null || chargingStation != null) {
            throw new IllegalStateException(
                    "Cannot begin charging journey."
            );
        }

        // The caller must reserve the slot before invoking this method.
        chargingStation = station;
        setPath(route);
        status = TO_CHARGER;
    }

    public void startCharging(Environment.ChargingStation station) {
        if (station == null
                || station != chargingStation
                || x != station.getX()
                || y != station.getY()) {
            throw new IllegalStateException(
                    "Drone is not at its reserved charging station."
            );
        }

        clearPath();
        status = CHARGING;
    }

    public void chargeOneUnit() {
        if (CHARGING.equals(status)) {
            battery = Math.min(FULL_BATTERY_ENERGY, battery + 1);
        }
    }

    public void releaseChargingStation() {
        if (chargingStation != null) {
            chargingStation.releaseSlot();
            chargingStation = null;
        }
    }

    public void markIdle() {
        if (assignedDelivery != null) {
            throw new IllegalStateException(
                    "Cannot become idle with an active delivery."
            );
        }

        releaseChargingStation();
        clearPath();
        status = IDLE;
    }

    public void reset() {
        releaseChargingStation();

        x = startX;
        y = startY;
        battery = initialBattery;
        currentLoad = 0;
        status = IDLE;
        assignedDelivery = null;
        carryingPackage = false;
        clearPath();
    }

    @Override
    public String toString() {
        return id + " at (" + x + ", " + y + ")"
                + ", battery " + getBatteryPercent() + "%"
                + ", load " + currentLoad + "/" + maxPayload + " kg"
                + ", status " + status;
    }
}