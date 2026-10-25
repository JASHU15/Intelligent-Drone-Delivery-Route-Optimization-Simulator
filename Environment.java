package droneproject;

import java.util.ArrayList;
import java.util.List;

public class Environment {

    public static class Rect {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;

        public Rect(int minX, int minY, int maxX, int maxY) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
        }

        public boolean intersects(Rect other) {
            return minX <= other.maxX
                    && maxX >= other.minX
                    && minY <= other.maxY
                    && maxY >= other.minY;
        }

        public boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        public int centerX() {
            return (minX + maxX) / 2;
        }

        public int centerY() {
            return (minY + maxY) / 2;
        }
    }

    public static class ChargingStation {
        private final int x;
        private final int y;
        private final int totalSlots;
        private int availableSlots;
        private final int initialAvailableSlots;

        public ChargingStation(int x, int y, int totalSlots) {
            if (totalSlots <= 0) {
                throw new IllegalArgumentException("A charging station needs at least one slot.");
            }
            this.x = x;
            this.y = y;
            this.totalSlots = totalSlots;
            this.availableSlots = totalSlots;
            this.initialAvailableSlots = totalSlots;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getTotalSlots() {
            return totalSlots;
        }

        public int getAvailableSlots() {
            return availableSlots;
        }

        public boolean hasAvailableSlot() {
            return availableSlots > 0;
        }

        public boolean reserveSlot() {
            if (availableSlots <= 0) {
                return false;
            }
            availableSlots--;
            return true;
        }

        public void releaseSlot() {
            if (availableSlots < totalSlots) {
                availableSlots++;
            }
        }

        public void reset() {
            availableSlots = initialAvailableSlots;
        }
    }

    private final int width;
    private final int height;
    private final boolean[][] blocked;

    private final List<Rect> noFlyZones = new ArrayList<>();
    private final List<ChargingStation> chargingStations = new ArrayList<>();

    public Environment(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Map dimensions must be positive.");
        }

        this.width = width;
        this.height = height;
        this.blocked = new boolean[height][width];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Rect> getNoFlyZones() {
        return noFlyZones;
    }

    public List<ChargingStation> getChargingStations() {
        return chargingStations;
    }

    public boolean isInside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isBlocked(int x, int y) {
        if (!isInside(x, y)) {
            return true;
        }
        return blocked[y][x];
    }

    public void addNoFlyZone(int x1, int y1, int x2, int y2) {
        int minX = Math.max(0, Math.min(x1, x2));
        int minY = Math.max(0, Math.min(y1, y2));
        int maxX = Math.min(width - 1, Math.max(x1, x2));
        int maxY = Math.min(height - 1, Math.max(y1, y2));

        Rect zone = new Rect(minX, minY, maxX, maxY);
        noFlyZones.add(zone);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                blocked[y][x] = true;
            }
        }
    }

    public void addChargingStation(int x, int y, int slots) {
        if (!isInside(x, y)) {
            throw new IllegalArgumentException("Charging station is outside the map.");
        }
        if (isBlocked(x, y)) {
            throw new IllegalArgumentException("Charging station cannot be placed in a no-fly zone.");
        }
        chargingStations.add(new ChargingStation(x, y, slots));
    }

    public void resetChargingStations() {
        for (ChargingStation station : chargingStations) {
            station.reset();
        }
    }
}