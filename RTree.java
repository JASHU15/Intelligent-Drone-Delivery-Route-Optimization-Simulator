package droneproject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RTree {

    public static final int MAX_ENTRIES = 4;
    public static final int MIN_ENTRIES = 2;

    public static class Entry {
        public final Environment.Rect rect;
        public final Object payload;

        public Entry(Environment.Rect rect, Object payload) {
            this.rect = rect;
            this.payload = payload;
        }
    }

    private static class Node {
        boolean leaf;
        List<Entry> entries = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        Environment.Rect boundingBox;

        void updateBoundingBox() {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            if (leaf) {
                for (Entry entry : entries) {
                    minX = Math.min(minX, entry.rect.minX);
                    minY = Math.min(minY, entry.rect.minY);
                    maxX = Math.max(maxX, entry.rect.maxX);
                    maxY = Math.max(maxY, entry.rect.maxY);
                }
            } else {
                for (Node child : children) {
                    minX = Math.min(minX, child.boundingBox.minX);
                    minY = Math.min(minY, child.boundingBox.minY);
                    maxX = Math.max(maxX, child.boundingBox.maxX);
                    maxY = Math.max(maxY, child.boundingBox.maxY);
                }
            }

            boundingBox = new Environment.Rect(minX, minY, maxX, maxY);
        }
    }

    private Node root;

    public void clear() {
        root = null;
    }

    public boolean isEmpty() {
        return root == null;
    }

    public void insert(Environment.Rect rect, Object payload) {
        if (rect == null) {
            throw new IllegalArgumentException("Cannot insert a null rectangle.");
        }

        Entry entry = new Entry(rect, payload);

        if (root == null) {
            root = new Node();
            root.leaf = true;
            root.entries.add(entry);
            root.updateBoundingBox();
            return;
        }

        Node leaf = chooseLeaf(root, rect);
        leaf.entries.add(entry);
        leaf.updateBoundingBox();

        if (leaf.entries.size() > MAX_ENTRIES) {
            splitLeaf(leaf);
        }

        updateBoundingBoxes(root);
    }

    private Node chooseLeaf(Node node, Environment.Rect rect) {
        while (!node.leaf) {
            Node bestChild = null;
            long bestArea = Long.MAX_VALUE;

            for (Node child : node.children) {
                long enlargedArea = enlargedArea(child.boundingBox, rect);
                if (enlargedArea < bestArea) {
                    bestArea = enlargedArea;
                    bestChild = child;
                }
            }

            node = bestChild;
        }

        return node;
    }

    private long enlargedArea(Environment.Rect current, Environment.Rect addition) {
        int minX = Math.min(current.minX, addition.minX);
        int minY = Math.min(current.minY, addition.minY);
        int maxX = Math.max(current.maxX, addition.maxX);
        int maxY = Math.max(current.maxY, addition.maxY);

        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;

        return width * height;
    }

    private void splitLeaf(Node leaf) {
        leaf.entries.sort(Comparator.comparingInt(entry -> entry.rect.centerX()));

        List<Entry> firstGroup = new ArrayList<>(
                leaf.entries.subList(0, MIN_ENTRIES)
        );

        List<Entry> secondGroup = new ArrayList<>(
                leaf.entries.subList(MIN_ENTRIES, leaf.entries.size())
        );

        Node sibling = new Node();
        sibling.leaf = true;
        sibling.entries.addAll(secondGroup);

        leaf.entries.clear();
        leaf.entries.addAll(firstGroup);

        leaf.updateBoundingBox();
        sibling.updateBoundingBox();

        Node parent = findParent(root, leaf);

        if (parent == null) {
            Node newRoot = new Node();
            newRoot.leaf = false;
            newRoot.children.add(leaf);
            newRoot.children.add(sibling);
            newRoot.updateBoundingBox();
            root = newRoot;
        } else {
            parent.children.add(sibling);
            parent.updateBoundingBox();

            if (parent.children.size() > MAX_ENTRIES) {
                splitInternal(parent);
            }
        }
    }

    private void splitInternal(Node node) {
        node.children.sort(Comparator.comparingInt(child -> child.boundingBox.centerX()));

        List<Node> firstGroup = new ArrayList<>(
                node.children.subList(0, MIN_ENTRIES)
        );

        List<Node> secondGroup = new ArrayList<>(
                node.children.subList(MIN_ENTRIES, node.children.size())
        );

        Node sibling = new Node();
        sibling.leaf = false;
        sibling.children.addAll(secondGroup);

        node.children.clear();
        node.children.addAll(firstGroup);

        node.updateBoundingBox();
        sibling.updateBoundingBox();

        Node parent = findParent(root, node);

        if (parent == null) {
            Node newRoot = new Node();
            newRoot.leaf = false;
            newRoot.children.add(node);
            newRoot.children.add(sibling);
            newRoot.updateBoundingBox();
            root = newRoot;
        } else {
            parent.children.add(sibling);
            parent.updateBoundingBox();

            if (parent.children.size() > MAX_ENTRIES) {
                splitInternal(parent);
            }
        }
    }

    private Node findParent(Node current, Node target) {
        if (current == null || current.leaf) {
            return null;
        }

        if (current.children.contains(target)) {
            return current;
        }

        for (Node child : current.children) {
            Node parent = findParent(child, target);
            if (parent != null) {
                return parent;
            }
        }

        return null;
    }

    private void updateBoundingBoxes(Node node) {
        if (node == null) {
            return;
        }

        if (!node.leaf) {
            for (Node child : node.children) {
                updateBoundingBoxes(child);
            }
        }

        node.updateBoundingBox();
    }

    public List<Object> search(Environment.Rect queryRect) {
        List<Object> results = new ArrayList<>();

        if (queryRect == null || root == null) {
            return results;
        }

        searchRecursive(root, queryRect, results);
        return results;
    }

    private void searchRecursive(Node node, Environment.Rect query, List<Object> results) {
        if (node == null || !node.boundingBox.intersects(query)) {
            return;
        }

        if (node.leaf) {
            for (Entry entry : node.entries) {
                if (entry.rect.intersects(query)) {
                    results.add(entry.payload);
                }
            }
        } else {
            for (Node child : node.children) {
                searchRecursive(child, query, results);
            }
        }
    }

    public List<Environment.Rect> collectNodeBoxes() {
        List<Environment.Rect> boxes = new ArrayList<>();
        collectNodeBoxesRecursive(root, boxes);
        return boxes;
    }

    private void collectNodeBoxesRecursive(Node node, List<Environment.Rect> boxes) {
        if (node == null) {
            return;
        }

        boxes.add(node.boundingBox);

        for (Node child : node.children) {
            collectNodeBoxesRecursive(child, boxes);
        }
    }
}