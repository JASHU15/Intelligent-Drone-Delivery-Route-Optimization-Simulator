package droneproject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AStarPathfinder {

    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private static class Node {
        final int x;
        final int y;
        final int g;
        final int f;
        final Node parent;

        Node(int x, int y, int g, int f, Node parent) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }

    private final Environment environment;
    private int lastExpandedNodes;

    public AStarPathfinder(Environment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment cannot be null.");
        }
        this.environment = environment;
    }

    public int getLastExpandedNodes() {
        return lastExpandedNodes;
    }

    public List<int[]> findPath(int startX, int startY, int targetX, int targetY) {
        lastExpandedNodes = 0;

        if (!environment.isInside(startX, startY)
                || !environment.isInside(targetX, targetY)) {
            return null;
        }

        if (environment.isBlocked(startX, startY)
                || environment.isBlocked(targetX, targetY)) {
            return null;
        }

        if (startX == targetX && startY == targetY) {
            List<int[]> path = new ArrayList<>();
            path.add(new int[]{startX, startY});
            return path;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>(
                (a, b) -> Integer.compare(a.f, b.f)
        );

        Map<Long, Integer> bestG = new HashMap<>();
        Node start = new Node(
                startX,
                startY,
                0,
                manhattan(startX, startY, targetX, targetY),
                null
        );

        openSet.add(start);
        bestG.put(key(startX, startY), 0);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            lastExpandedNodes++;

            if (current.x == targetX && current.y == targetY) {
                return reconstructPath(current);
            }

            if (current.g > bestG.getOrDefault(key(current.x, current.y), Integer.MAX_VALUE)) {
                continue;
            }

            for (int direction = 0; direction < 4; direction++) {
                int nextX = current.x + DX[direction];
                int nextY = current.y + DY[direction];

                if (!environment.isInside(nextX, nextY)) {
                    continue;
                }
                if (environment.isBlocked(nextX, nextY)) {
                    continue;
                }

                int tentativeG = current.g + 1;
                long nextKey = key(nextX, nextY);

                if (tentativeG < bestG.getOrDefault(nextKey, Integer.MAX_VALUE)) {
                    bestG.put(nextKey, tentativeG);

                    int heuristic = manhattan(nextX, nextY, targetX, targetY);
                    Node next = new Node(
                            nextX,
                            nextY,
                            tentativeG,
                            tentativeG + heuristic,
                            current
                    );

                    openSet.add(next);
                }
            }
        }

        return null;
    }

    public int calculatePathCost(List<int[]> path) {
        if (path == null || path.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return path.size() - 1;
    }

    private int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private List<int[]> reconstructPath(Node goal) {
        List<int[]> path = new ArrayList<>();
        Node current = goal;

        while (current != null) {
            path.add(new int[]{current.x, current.y});
            current = current.parent;
        }

        Collections.reverse(path);

        if (path.isEmpty() || environment.isBlocked(path.get(0)[0], path.get(0)[1])) {
            return null;
        }

        for (int[] cell : path) {
            if (environment.isBlocked(cell[0], cell[1])) {
                return null;
            }
        }

        return path;
    }
}