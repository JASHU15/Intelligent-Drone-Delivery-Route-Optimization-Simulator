package droneproject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpatialHash {

    private final int cellSize;
    private final Map<Long, List<Object>> cells = new HashMap<>();
    private final Map<Object, int[]> objectPositions = new HashMap<>();

    public SpatialHash(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("Cell size must be positive.");
        }
        this.cellSize = cellSize;
    }

    public void clear() {
        cells.clear();
        objectPositions.clear();
    }

    public void insert(int x, int y, Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Cannot insert a null object.");
        }

        remove(object);

        long key = cellKey(x, y);
        cells.computeIfAbsent(key, k -> new ArrayList<>()).add(object);
        objectPositions.put(object, new int[]{x, y});
    }

    public boolean remove(Object object) {
        int[] position = objectPositions.remove(object);

        if (position == null) {
            return false;
        }

        long key = cellKey(position[0], position[1]);
        List<Object> bucket = cells.get(key);

        if (bucket != null) {
            bucket.remove(object);
            if (bucket.isEmpty()) {
                cells.remove(key);
            }
        }

        return true;
    }

    public List<Object> queryRadius(int centerX, int centerY, int radius) {
        List<Object> results = new ArrayList<>();

        if (radius < 0) {
            return results;
        }

        int minCellX = floorDiv(centerX - radius, cellSize);
        int maxCellX = floorDiv(centerX + radius, cellSize);
        int minCellY = floorDiv(centerY - radius, cellSize);
        int maxCellY = floorDiv(centerY + radius, cellSize);

        int radiusSquared = radius * radius;

        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                List<Object> bucket = cells.get(cellKeyFromCell(cellX, cellY));

                if (bucket == null) {
                    continue;
                }

                for (Object object : bucket) {
                    int[] position = objectPositions.get(object);

                    if (position == null) {
                        continue;
                    }

                    int dx = position[0] - centerX;
                    int dy = position[1] - centerY;

                    if (dx * dx + dy * dy <= radiusSquared) {
                        results.add(object);
                    }
                }
            }
        }

        return results;
    }

    public List<Object> queryCell(int x, int y) {
        List<Object> bucket = cells.get(cellKey(x, y));
        return bucket == null ? new ArrayList<>() : new ArrayList<>(bucket);
    }

    public int getCellSize() {
        return cellSize;
    }

    public int[] getPosition(Object object) {
        int[] position = objectPositions.get(object);
        return position == null ? null : position.clone();
    }

    private long cellKey(int x, int y) {
        return cellKeyFromCell(floorDiv(x, cellSize), floorDiv(y, cellSize));
    }

    private long cellKeyFromCell(int cellX, int cellY) {
        return ((long) cellX << 32) | (cellY & 0xFFFFFFFFL);
    }

    private int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }
}