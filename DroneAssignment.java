package droneproject;

import java.util.ArrayList;
import java.util.List;

public class DroneAssignment {

    public static final int BATTERY_SAFETY_RESERVE = 10;

    private final Environment environment;
    private final AStarPathfinder pathfinder;
    private final KDTree droneTree;

    public DroneAssignment(
            Environment environment,
            AStarPathfinder pathfinder,
            KDTree droneTree
    ) {
        if (environment == null || pathfinder == null || droneTree == null) {
            throw new IllegalArgumentException(
                    "Assignment dependencies are required."
            );
        }

        this.environment = environment;
        this.pathfinder = pathfinder;
        this.droneTree = droneTree;
    }

    public String assignPendingDeliveries(
            List<Drone> drones,
            List<Delivery> deliveries
    ) {
        List<Delivery> ordered = new ArrayList<>(deliveries);

        ordered.sort((a, b) -> {
            int comparison =
                    Integer.compare(b.getPriority(), a.getPriority());

            return comparison != 0
                    ? comparison
                    : a.getId().compareTo(b.getId());
        });

        StringBuilder report = new StringBuilder();

        for (Delivery delivery : ordered) {
            if (!delivery.isPending()) {
                continue;
            }

            Drone best = null;
            List<int[]> bestPath = null;
            int bestCost = Integer.MAX_VALUE;

            for (Drone drone : drones) {
                if (!drone.isAvailable()
                        || !drone.canCarry(delivery.getWeight())
                        || drone.getBatteryPercent() <= 20) {
                    continue;
                }

                List<int[]> outbound = pathfinder.findPath(
                        drone.getX(), drone.getY(),
                        delivery.getX(), delivery.getY()
                );

                List<int[]> home = pathfinder.findPath(
                        delivery.getX(), delivery.getY(),
                        drone.getStartX(), drone.getStartY()
                );

                if (outbound == null || home == null) {
                    continue;
                }

                int outboundCost =
                        pathfinder.calculatePathCost(outbound);

                int required = outboundCost
                        + pathfinder.calculatePathCost(home)
                        + BATTERY_SAFETY_RESERVE;

                if (drone.hasBatteryFor(required)
                        && outboundCost < bestCost) {
                    best = drone;
                    bestPath = outbound;
                    bestCost = outboundCost;
                }
            }

            if (best != null) {
                best.assignDelivery(delivery);
                best.setPath(bestPath);

                report.append(delivery.getId())
                        .append(" -> ")
                        .append(best.getId())
                        .append("\n");
            }
        }

        return report.length() == 0
                ? "No new assignments."
                : report.toString();
    }

    public boolean needsCharging(
            Drone drone,
            List<Delivery> deliveries
    ) {
        if (!drone.isAvailable()) {
            return false;
        }

        if (drone.getBatteryPercent() <= 20) {
            return true;
        }

        boolean feasibleWithFullBattery = false;

        for (Delivery delivery : deliveries) {
            if (!delivery.isPending()
                    || !drone.canCarry(delivery.getWeight())) {
                continue;
            }

            List<int[]> outbound = pathfinder.findPath(
                    drone.getX(), drone.getY(),
                    delivery.getX(), delivery.getY()
            );

            List<int[]> home = pathfinder.findPath(
                    delivery.getX(), delivery.getY(),
                    drone.getStartX(), drone.getStartY()
            );

            if (outbound == null || home == null) {
                continue;
            }

            int required = pathfinder.calculatePathCost(outbound)
                    + pathfinder.calculatePathCost(home)
                    + BATTERY_SAFETY_RESERVE;

            if (drone.hasBatteryFor(required)) {
                return false;
            }

            if (required <= drone.getMaxBattery()) {
                feasibleWithFullBattery = true;
            }
        }

        return feasibleWithFullBattery
                && drone.getBattery() < drone.getMaxBattery();
    }

    public Environment getEnvironment() {
        return environment;
    }

    public AStarPathfinder getPathfinder() {
        return pathfinder;
    }

    public KDTree getDroneTree() {
        return droneTree;
    }
}