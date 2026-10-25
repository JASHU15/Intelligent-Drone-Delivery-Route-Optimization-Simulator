package droneproject;

import java.util.ArrayList;
import java.util.List;

public class KDTree {

    public static class KDPoint {
        public final int x;
        public final int y;
        public final Object payload;

        public KDPoint(int x, int y, Object payload) {
            this.x = x;
            this.y = y;
            this.payload = payload;
        }
    }

    private static class TreeNode {
        final KDPoint point;
        TreeNode left;
        TreeNode right;

        TreeNode(KDPoint point) {
            this.point = point;
        }
    }

    public static class NodeSnapshot {
        public final KDPoint point;
        public final int depth;
        public final NodeSnapshot left;
        public final NodeSnapshot right;

        private NodeSnapshot(
                KDPoint point,
                int depth,
                NodeSnapshot left,
                NodeSnapshot right
        ) {
            this.point = point;
            this.depth = depth;
            this.left = left;
            this.right = right;
        }
    }

    private TreeNode root;
    private int size;
    private int lastVisitedNodes;

    public void clear() {
        root = null;
        size = 0;
        lastVisitedNodes = 0;
    }

    public int size() {
        return size;
    }

    public int getLastVisitedNodes() {
        return lastVisitedNodes;
    }

    public void build(List<KDPoint> points) {
        clear();

        if (points == null || points.isEmpty()) {
            return;
        }

        for (KDPoint point : points) {
            if (point == null) {
                throw new IllegalArgumentException(
                        "KD-tree points cannot contain null."
                );
            }
        }

        root = buildRecursive(new ArrayList<>(points), 0);
        size = points.size();
    }

    private TreeNode buildRecursive(List<KDPoint> points, int depth) {
        if (points.isEmpty()) {
            return null;
        }

        final int axis = depth % 2;

        points.sort((first, second) -> {
            int comparison = axis == 0
                    ? Integer.compare(first.x, second.x)
                    : Integer.compare(first.y, second.y);

            if (comparison != 0) {
                return comparison;
            }

            return axis == 0
                    ? Integer.compare(first.y, second.y)
                    : Integer.compare(first.x, second.x);
        });

        int median = points.size() / 2;

        TreeNode node = new TreeNode(points.get(median));

        node.left = buildRecursive(
                new ArrayList<>(points.subList(0, median)),
                depth + 1
        );

        node.right = buildRecursive(
                new ArrayList<>(points.subList(median + 1, points.size())),
                depth + 1
        );

        return node;
    }

    public KDPoint findNearest(int queryX, int queryY) {
        lastVisitedNodes = 0;

        if (root == null) {
            return null;
        }

        return nearestRecursive(root, queryX, queryY, 0, root.point);
    }

    private KDPoint nearestRecursive(
            TreeNode node,
            int queryX,
            int queryY,
            int depth,
            KDPoint best
    ) {
        if (node == null) {
            return best;
        }

        lastVisitedNodes++;

        double bestDistance = squaredDistance(best, queryX, queryY);
        double currentDistance = squaredDistance(node.point, queryX, queryY);

        if (currentDistance < bestDistance) {
            best = node.point;
        }

        double difference = depth % 2 == 0
                ? (double) queryX - node.point.x
                : (double) queryY - node.point.y;

        TreeNode nearChild = difference < 0 ? node.left : node.right;
        TreeNode farChild = difference < 0 ? node.right : node.left;

        best = nearestRecursive(
                nearChild, queryX, queryY, depth + 1, best
        );

        bestDistance = squaredDistance(best, queryX, queryY);

        if (difference * difference <= bestDistance) {
            best = nearestRecursive(
                    farChild, queryX, queryY, depth + 1, best
            );
        }

        return best;
    }

    private double squaredDistance(KDPoint point, int queryX, int queryY) {
        double dx = (double) point.x - queryX;
        double dy = (double) point.y - queryY;

        return dx * dx + dy * dy;
    }

    public NodeSnapshot getRootSnapshot() {
        return snapshot(root, 0);
    }

    private NodeSnapshot snapshot(TreeNode node, int depth) {
        if (node == null) {
            return null;
        }

        return new NodeSnapshot(
                node.point,
                depth,
                snapshot(node.left, depth + 1),
                snapshot(node.right, depth + 1)
        );
    }

    public List<int[]> collectPartitions() {
        return collectPartitions(0, 100, 0, 100);
    }

    public List<int[]> collectPartitions(
            int minX,
            int maxX,
            int minY,
            int maxY
    ) {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Invalid partition boundaries.");
        }

        List<int[]> lines = new ArrayList<>();

        collectPartitionsRecursive(
                root, 0, minX, maxX, minY, maxY, lines
        );

        return lines;
    }

    private void collectPartitionsRecursive(
            TreeNode node,
            int depth,
            int minX,
            int maxX,
            int minY,
            int maxY,
            List<int[]> lines
    ) {
        if (node == null) {
            return;
        }

        if (depth % 2 == 0) {
            int splitX = node.point.x;

            lines.add(new int[]{splitX, minY, splitX, maxY});

            collectPartitionsRecursive(
                    node.left, depth + 1,
                    minX, splitX, minY, maxY, lines
            );

            collectPartitionsRecursive(
                    node.right, depth + 1,
                    splitX, maxX, minY, maxY, lines
            );
        } else {
            int splitY = node.point.y;

            lines.add(new int[]{minX, splitY, maxX, splitY});

            collectPartitionsRecursive(
                    node.left, depth + 1,
                    minX, maxX, minY, splitY, lines
            );

            collectPartitionsRecursive(
                    node.right, depth + 1,
                    minX, maxX, splitY, maxY, lines
            );
        }
    }
}