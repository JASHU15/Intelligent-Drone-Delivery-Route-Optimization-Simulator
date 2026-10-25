package droneproject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DroneDeliveryGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final int TICK_MS = 120;
    private static final int CHARGE_UNITS_PER_TICK = 5;

    private final List<Drone> drones;
    private final List<Delivery> deliveries;
    private final Environment environment;

    private final AStarPathfinder pathfinder;
    private final KDTree droneTree = new KDTree();
    private final RTree zoneTree = new RTree();
    private final SpatialHash stationHash = new SpatialHash(10);
    private final DroneAssignment assignmentManager;

    private final MapPanel mapPanel = new MapPanel();
    private final JTextArea deliveryInfo = new JTextArea();
    private final JLabel statusLabel = new JLabel("Ready. Click Start.");

    private final Map<Drone, JProgressBar> batteryBars =
            new LinkedHashMap<>();
    private final Map<Drone, JLabel> droneLabels =
            new LinkedHashMap<>();

    private final javax.swing.Timer timer;

    private boolean showPartitions;
    private boolean showRTree;
    private boolean showHash;

    private int queryX = -1;
    private int queryY = -1;
    private Drone nearestDrone;
    private String lastReport = "";

    public DroneDeliveryGUI(
            List<Drone> drones,
            List<Delivery> deliveries,
            Environment environment
    ) {
        super("Intelligent Drone Delivery & Route Optimization Simulator");

        if (drones == null || deliveries == null || environment == null) {
            throw new IllegalArgumentException(
                    "Drones, deliveries, and environment are required."
            );
        }

        this.drones = drones;
        this.deliveries = deliveries;
        this.environment = environment;

        validateInitialPositions();

        pathfinder = new AStarPathfinder(environment);

        assignmentManager = new DroneAssignment(
                environment, pathfinder, droneTree
        );

        timer = new javax.swing.Timer(TICK_MS, event -> tick());

        rebuildIndexes();

        setLayout(new BorderLayout(8, 8));
        add(createControls(), BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);
        add(createDashboard(), BorderLayout.EAST);
        add(statusLabel, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                timer.stop();

                for (Drone drone : drones) {
                    drone.releaseChargingStation();
                }
            }
        });

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        setSize(
                Math.min(1350, Math.max(600, screen.width - 60)),
                Math.min(850, Math.max(450, screen.height - 80))
        );

        setLocationRelativeTo(null);
        refreshDashboard();
    }

    private void validateInitialPositions() {
        for (Drone drone : drones) {
            if (!environment.isInside(drone.getX(), drone.getY())
                    || environment.isBlocked(drone.getX(), drone.getY())) {
                throw new IllegalArgumentException(
                        "Invalid start position for " + drone.getId()
                );
            }
        }

        for (Delivery delivery : deliveries) {
            if (!environment.isInside(delivery.getX(), delivery.getY())
                    || environment.isBlocked(
                            delivery.getX(), delivery.getY())) {
                throw new IllegalArgumentException(
                        "Invalid destination for " + delivery.getId()
                );
            }
        }
    }

    private JPanel createControls() {
        JPanel panel = new JPanel(new GridLayout(2, 5, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        addButton(panel, "Start", this::startSimulation);
        addButton(panel, "Pause", this::pauseSimulation);
        addButton(panel, "Reset", this::resetSimulation);
        addButton(panel, "Show KD-Tree", this::showKDTree);

        addButton(panel, "Show Partitions", () -> {
            pauseSimulation();
            showPartitions = !showPartitions;
            rebuildDroneTree();
            mapPanel.repaint();
        });

        addButton(panel, "Find Nearest", this::findNearest);

        addButton(panel, "Show R-Tree", () -> {
            pauseSimulation();
            showRTree = !showRTree;
            mapPanel.repaint();
            showRTreeEntries();
        });

        addButton(panel, "Spatial Hash", () -> {
            pauseSimulation();
            showHash = !showHash;
            mapPanel.repaint();
            showHashBuckets();
        });

        addButton(panel, "Analysis", this::showAnalysis);

        return panel;
    }

    private void addButton(
            JPanel panel,
            String text,
            Runnable action
    ) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        panel.add(button);
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new BorderLayout(5, 5));
        dashboard.setPreferredSize(new Dimension(350, 0));
        dashboard.setBorder(
                BorderFactory.createTitledBorder("Drone Dashboard")
        );

        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));

        for (Drone drone : drones) {
            JPanel card = new JPanel(new BorderLayout(3, 3));
            card.setBorder(
                    BorderFactory.createTitledBorder(drone.getId())
            );

            JLabel label = new JLabel();
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setStringPainted(true);
            bar.setPreferredSize(new Dimension(260, 23));

            droneLabels.put(drone, label);
            batteryBars.put(drone, bar);

            card.add(label, BorderLayout.NORTH);
            card.add(bar, BorderLayout.CENTER);
            cards.add(card);
        }

        deliveryInfo.setEditable(false);
        deliveryInfo.setFont(
                new Font(Font.MONOSPACED, Font.PLAIN, 12)
        );

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(cards),
                new JScrollPane(deliveryInfo)
        );

        split.setResizeWeight(0.6);
        dashboard.add(split, BorderLayout.CENTER);

        return dashboard;
    }

    private void startSimulation() {
        if (timer.isRunning()) {
            return;
        }

        scheduleWork();
        rebuildDroneTree();
        refreshDashboard();
        mapPanel.repaint();

        if (hasActiveDrones()) {
            timer.start();
            statusLabel.setText("Simulation running.");
        } else {
            statusLabel.setText(
                    "No active work. Inspect battery, routes, and pending deliveries."
            );
        }
    }

    private void pauseSimulation() {
        timer.stop();
        statusLabel.setText("Paused.");
    }

    private void resetSimulation() {
        timer.stop();

        for (Drone drone : drones) {
            drone.reset();
        }

        for (Delivery delivery : deliveries) {
            delivery.reset();
        }

        environment.resetChargingStations();

        showPartitions = false;
        showRTree = false;
        showHash = false;

        nearestDrone = null;
        queryX = -1;
        queryY = -1;
        lastReport = "";

        rebuildIndexes();
        refreshDashboard();
        mapPanel.repaint();

        statusLabel.setText(
                "Reset: original positions and entered battery levels restored."
        );
    }

    private void scheduleWork() {
        // Retry waiting drones whenever scheduling is triggered.
        for (Drone drone : drones) {
            if (Drone.WAITING_FOR_CHARGER.equals(drone.getStatus())
                    || Drone.INSUFFICIENT_ENERGY.equals(
                            drone.getStatus())) {
                drone.markIdle();
            }
        }

        lastReport = assignmentManager.assignPendingDeliveries(
                drones, deliveries
        );

        for (Drone drone : drones) {
            if (Drone.ASSIGNED.equals(drone.getStatus())) {
                drone.startDelivery();
            } else if (assignmentManager.needsCharging(
                    drone, deliveries)) {
                sendToCharger(drone);
            }
        }
    }

    private boolean hasActiveDrones() {
        for (Drone drone : drones) {
            String state = drone.getStatus();

            if (Drone.DELIVERING.equals(state)
                    || Drone.RETURNING.equals(state)
                    || Drone.TO_CHARGER.equals(state)
                    || Drone.CHARGING.equals(state)) {
                return true;
            }
        }

        return false;
    }

    private void tick() {
        boolean schedulingNeeded = false;
        boolean moved = false;

        for (Drone drone : drones) {
            int oldX = drone.getX();
            int oldY = drone.getY();

            schedulingNeeded |= updateDrone(drone);

            moved |= oldX != drone.getX() || oldY != drone.getY();
        }

        if (schedulingNeeded) {
            scheduleWork();
        }

        if (moved) {
            rebuildDroneTree();
            updateNearestResult();
        }

        refreshDashboard();
        mapPanel.repaint();

        if (!hasActiveDrones()) {
            timer.stop();
            statusLabel.setText(
                    "Simulation stopped: completed or no feasible active work."
            );
        }
    }

    private boolean updateDrone(Drone drone) {
        String state = drone.getStatus();

        if (Drone.CHARGING.equals(state)) {
            for (int i = 0; i < CHARGE_UNITS_PER_TICK; i++) {
                drone.chargeOneUnit();
            }

            if (drone.getBattery() >= drone.getMaxBattery()) {
                drone.releaseChargingStation();
                drone.markIdle();

                // Deliveries will be reconsidered from this station.
                return true;
            }

            return false;
        }

        if (!Drone.DELIVERING.equals(state)
                && !Drone.RETURNING.equals(state)
                && !Drone.TO_CHARGER.equals(state)) {
            return false;
        }

        if (drone.getPathIndex() < drone.getPath().size()) {
            int[] next = drone.getPath().get(drone.getPathIndex());

            if (environment.isBlocked(next[0], next[1])) {
                return handleMovementFailure(
                        drone, "Route entered a blocked cell."
                );
            }

            try {
                if (!drone.moveAlongPath()) {
                    return handleMovementFailure(
                            drone, "Battery exhausted during travel."
                    );
                }
            } catch (IllegalStateException exception) {
                return handleMovementFailure(
                        drone, exception.getMessage()
                );
            }
        }

        if (drone.getPathIndex() < drone.getPath().size()) {
            return false;
        }

        if (Drone.DELIVERING.equals(state)) {
            drone.completeDelivery();
            return sendHome(drone);
        }

        if (Drone.TO_CHARGER.equals(state)) {
            drone.startCharging(drone.getChargingStation());
            return false;
        }

        drone.markIdle();
        return true;
    }

    private boolean handleMovementFailure(
            Drone drone,
            String reason
    ) {
        if (drone.getAssignedDelivery() != null) {
            drone.failDelivery(reason);
        }

        drone.releaseChargingStation();
        drone.clearPath();
        drone.setStatus(Drone.INSUFFICIENT_ENERGY);

        statusLabel.setText(drone.getId() + ": " + reason);
        return false;
    }

    private boolean sendHome(Drone drone) {
        List<int[]> route = pathfinder.findPath(
                drone.getX(), drone.getY(),
                drone.getStartX(), drone.getStartY()
        );

        if (route != null
                && drone.hasBatteryFor(
                        pathfinder.calculatePathCost(route))) {
            drone.setPath(route);

            if (drone.getPathIndex() >= drone.getPath().size()) {
                drone.markIdle();
                return true;
            }

            drone.setStatus(Drone.RETURNING);
        } else {
            // Recovery path; ordinary assignments already check return energy.
            drone.markIdle();
            sendToCharger(drone);
        }

        return false;
    }

    private void sendToCharger(Drone drone) {
        Environment.ChargingStation bestStation = null;
        List<int[]> bestRoute = null;
        int bestCost = Integer.MAX_VALUE;

        boolean reachableStationExists = false;

        int radius = (int) Math.ceil(Math.hypot(
                environment.getWidth(),
                environment.getHeight()
        ));

        List<Object> candidates = stationHash.queryRadius(
                drone.getX(), drone.getY(), radius
        );

        for (Object object : candidates) {
            Environment.ChargingStation station =
                    (Environment.ChargingStation) object;

            List<int[]> route = pathfinder.findPath(
                    drone.getX(), drone.getY(),
                    station.getX(), station.getY()
            );

            if (route == null) {
                continue;
            }

            int cost = pathfinder.calculatePathCost(route);

            if (!drone.hasBatteryFor(cost)) {
                continue;
            }

            reachableStationExists = true;

            if (station.hasAvailableSlot() && cost < bestCost) {
                bestStation = station;
                bestRoute = route;
                bestCost = cost;
            }
        }

        if (bestStation == null) {
            drone.clearPath();

            drone.setStatus(reachableStationExists
                    ? Drone.WAITING_FOR_CHARGER
                    : Drone.INSUFFICIENT_ENERGY);

            return;
        }

        int freeBefore = bestStation.getAvailableSlots();

        if (!bestStation.reserveSlot()) {
            drone.setStatus(Drone.WAITING_FOR_CHARGER);
            return;
        }

        System.out.printf(
                "%s reserved charger (%d, %d): free slots %d -> %d%n",
                drone.getId(),
                bestStation.getX(),
                bestStation.getY(),
                freeBefore,
                bestStation.getAvailableSlots()
        );

        try {
            drone.travelToCharger(bestStation, bestRoute);
        } catch (RuntimeException exception) {
            bestStation.releaseSlot();
            throw exception;
        }

        if (drone.getPathIndex() >= drone.getPath().size()) {
            drone.startCharging(bestStation);
        }
    }

    private void rebuildIndexes() {
        rebuildDroneTree();

        zoneTree.clear();
        for (Environment.Rect zone : environment.getNoFlyZones()) {
            zoneTree.insert(zone, zone);
        }

        stationHash.clear();
        for (Environment.ChargingStation station
                : environment.getChargingStations()) {
            stationHash.insert(
                    station.getX(), station.getY(), station
            );
        }
    }

    private void rebuildDroneTree() {
        List<KDTree.KDPoint> points = new ArrayList<>();

        for (Drone drone : drones) {
            points.add(new KDTree.KDPoint(
                    drone.getX(), drone.getY(), drone
            ));
        }

        droneTree.build(points);
    }

    private void refreshDashboard() {
        for (Drone drone : drones) {
            JProgressBar bar = batteryBars.get(drone);
            int percent = drone.getBatteryPercent();

            bar.setValue(percent);
            bar.setString(percent + "%");

            bar.setForeground(
                    percent > 50 ? new Color(35, 160, 65)
                            : percent > 20 ? new Color(225, 175, 15)
                            : new Color(210, 45, 45)
            );

            droneLabels.get(drone).setText(
                    "<html>"
                            + drone.getStatus()
                            + "<br>Position: (" + drone.getX()
                            + ", " + drone.getY() + ")"
                            + " | Load: " + drone.getCurrentLoad()
                            + "/" + drone.getMaxPayload()
                            + " kg</html>"
            );
        }

        StringBuilder text = new StringBuilder("DELIVERIES\n");

        for (Delivery delivery : deliveries) {
            text.append(delivery.getId())
                    .append(" | ").append(delivery.getWeight())
                    .append(" kg | ").append(delivery.getStatus())
                    .append("\n");
        }

        text.append("\nCHARGERS: FREE / TOTAL\n");

        for (Environment.ChargingStation station
                : environment.getChargingStations()) {
            text.append("(").append(station.getX())
                    .append(",").append(station.getY())
                    .append(") ")
                    .append(station.getAvailableSlots())
                    .append("/")
                    .append(station.getTotalSlots())
                    .append("\n");
        }

        if (!text.toString().equals(deliveryInfo.getText())) {
            int caret = deliveryInfo.getCaretPosition();
            deliveryInfo.setText(text.toString());
            deliveryInfo.setCaretPosition(
                    Math.min(caret, deliveryInfo.getDocument().getLength())
            );
        }
    }

    private void findNearest() {
        pauseSimulation();
        rebuildDroneTree();

        String input = JOptionPane.showInputDialog(
                this, "Enter query X Y, for example: 50 50"
        );

        if (input == null) {
            return;
        }

        try {
            String[] values = input.trim().split("\\s+");

            if (values.length != 2) {
                throw new IllegalArgumentException();
            }

            int x = Integer.parseInt(values[0]);
            int y = Integer.parseInt(values[1]);

            if (!environment.isInside(x, y)) {
                throw new IllegalArgumentException();
            }

            queryX = x;
            queryY = y;
            updateNearestResult();
            mapPanel.repaint();

            showText("Nearest Drone",
                    nearestDrone == null
                            ? "No drones indexed."
                            : nearestDrone.toString());

        } catch (IllegalArgumentException exception) {
            showText("Input Error",
                    "Enter two integers inside the map boundaries.");
        }
    }

    private void updateNearestResult() {
        if (queryX < 0) {
            return;
        }

        KDTree.KDPoint point =
                droneTree.findNearest(queryX, queryY);

        nearestDrone = point == null ? null : (Drone) point.payload;
    }

    private void showKDTree() {
        pauseSimulation();
        rebuildDroneTree();

        JDialog dialog = new JDialog(
                this, "KD-Tree Structure Visualization", false
        );

        JPanel content = new JPanel(new BorderLayout());

        JLabel title = new JLabel(
                "2D KD-Tree | Nodes: " + droneTree.size()
                        + " | Blue: X split | Orange: Y split",
                JLabel.CENTER
        );

        title.setBorder(
                BorderFactory.createEmptyBorder(12, 8, 12, 8)
        );

        content.add(title, BorderLayout.NORTH);
        content.add(
                new TreePanel(droneTree.getRootSnapshot()),
                BorderLayout.CENTER
        );

        dialog.setContentPane(content);
        dialog.setSize(
                Math.max(400, getWidth() - 100),
                Math.max(350, getHeight() - 80)
        );
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

    private void showRTreeEntries() {
        StringBuilder text = new StringBuilder(
                "R-tree bounding boxes are highlighted on the map.\n\n"
                        + "Indexed obstacle rectangles:\n"
        );

        List<Object> entries = zoneTree.search(
                new Environment.Rect(
                        0, 0,
                        environment.getWidth() - 1,
                        environment.getHeight() - 1
                )
        );

        for (Object object : entries) {
            Environment.Rect zone = (Environment.Rect) object;

            text.append("(").append(zone.minX).append(",")
                    .append(zone.minY).append(") to (")
                    .append(zone.maxX).append(",")
                    .append(zone.maxY).append(")\n");
        }

        showText("R-Tree", text.toString());
    }

    private void showHashBuckets() {
        StringBuilder text = new StringBuilder(
                "Spatial hash charging-station buckets\n\n"
        );

        int size = stationHash.getCellSize();

        for (int y = 0; y < environment.getHeight(); y += size) {
            for (int x = 0; x < environment.getWidth(); x += size) {
                List<Object> bucket = stationHash.queryCell(x, y);

                if (bucket.isEmpty()) {
                    continue;
                }

                text.append("Cell (").append(x / size)
                        .append(",").append(y / size).append("): ");

                for (Object object : bucket) {
                    Environment.ChargingStation station =
                            (Environment.ChargingStation) object;

                    text.append("(").append(station.getX())
                            .append(",").append(station.getY())
                            .append(") ");
                }

                text.append("\n");
            }
        }

        showText("Spatial Hash", text.toString());
    }

    private void showAnalysis() {
        pauseSimulation();

        int completed = 0;
        for (Delivery delivery : deliveries) {
            if (delivery.isCompleted()) {
                completed++;
            }
        }

        StringBuilder text = new StringBuilder();

        text.append("Completed: ").append(completed)
                .append("/").append(deliveries.size())
                .append("\nKD-tree points: ").append(droneTree.size())
                .append("\n\n");

        for (Drone drone : drones) {
            text.append(drone).append("\n");
        }

        text.append("\nLast assignment pass:\n").append(lastReport);
        text.append("\n\nThese are simulation statistics, "
                + "not a benchmark comparison.");

        showText("Analysis", text.toString());
    }

    private void showText(String title, String text) {
        JTextArea area = new JTextArea(text, 16, 52);
        area.setEditable(false);
        area.setCaretPosition(0);

        JOptionPane.showMessageDialog(
                this, new JScrollPane(area),
                title, JOptionPane.INFORMATION_MESSAGE
        );
    }

    private class MapPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        MapPanel() {
            setBackground(Color.WHITE);
            setDoubleBuffered(true);
        }
        
        private void drawChargingStationsWithLabels(
                Graphics2D graphics,
                double cellSize
        ) {
            Graphics2D stationGraphics =
                    (Graphics2D) graphics.create();

            try {
                stationGraphics.setFont(
                        new Font(Font.SANS_SERIF, Font.BOLD, 11)
                );

                double mapWidthPixels =
                        environment.getWidth() * cellSize;

                double mapHeightPixels =
                        environment.getHeight() * cellSize;

                FontMetrics fontMetrics =
                        stationGraphics.getFontMetrics();

                for (Environment.ChargingStation chargingStation
                        : environment.getChargingStations()) {

                    double centerX =
                            (chargingStation.getX() + 0.5) * cellSize;

                    double centerY =
                            (chargingStation.getY() + 0.5) * cellSize;

                    double markerSize = Math.max(
                            4.0,
                            cellSize * 0.85
                    );

                    stationGraphics.setColor(Color.BLUE);

                    stationGraphics.fill(
                            new java.awt.geom.Ellipse2D.Double(
                                    centerX - markerSize / 2.0,
                                    centerY - markerSize / 2.0,
                                    markerSize,
                                    markerSize
                            )
                    );

                    String slotLabel =
                            chargingStation.getAvailableSlots()
                                    + "/"
                                    + chargingStation.getTotalSlots()
                                    + " free";

                    int textWidth =
                            fontMetrics.stringWidth(slotLabel);

                    double preferredX =
                            centerX + markerSize / 2.0 + 4.0;

                    double textX = Math.max(
                            3.0,
                            Math.min(
                                    preferredX,
                                    mapWidthPixels - textWidth - 5.0
                            )
                    );

                    double textY = Math.max(
                            fontMetrics.getAscent() + 3.0,
                            Math.min(
                                    centerY,
                                    mapHeightPixels
                                            - fontMetrics.getDescent()
                                            - 3.0
                            )
                    );

                    int backgroundX =
                            (int) Math.floor(textX) - 2;

                    int backgroundY =
                            (int) Math.floor(textY)
                                    - fontMetrics.getAscent()
                                    - 2;

                    stationGraphics.setColor(
                            new Color(255, 255, 255, 235)
                    );

                    stationGraphics.fillRect(
                            backgroundX,
                            backgroundY,
                            textWidth + 6,
                            fontMetrics.getHeight() + 4
                    );

                    stationGraphics.setColor(
                            new Color(0, 65, 170)
                    );

                    stationGraphics.drawString(
                            slotLabel,
                            (float) textX,
                            (float) textY
                    );
                }
            } finally {
                stationGraphics.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();

            try {
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                double availableWidth = getWidth() - 40.0;
                double availableHeight = getHeight() - 70.0;

                if (availableWidth <= 0 || availableHeight <= 0) {
                    return;
                }

                double cell = Math.min(
                        availableWidth / environment.getWidth(),
                        availableHeight / environment.getHeight()
                );

                double width = environment.getWidth() * cell;
                double height = environment.getHeight() * cell;

                g.setColor(Color.DARK_GRAY);
                g.drawString(
                        "Blue: charger | Orange square: delivery"
                                + " | Purple lines: KD partitions",
                        10, 18
                );

                g.translate(
                        (getWidth() - width) / 2.0,
                        35 + (availableHeight - height) / 2.0
                );

                g.setColor(new Color(238, 238, 238));

                for (int x = 0; x <= environment.getWidth(); x++) {
                    line(g, x * cell, 0, x * cell, height);
                }

                for (int y = 0; y <= environment.getHeight(); y++) {
                    line(g, 0, y * cell, width, y * cell);
                }

                for (Environment.Rect zone
                        : environment.getNoFlyZones()) {
                    g.setColor(new Color(255, 190, 190));
                    g.fill(new Rectangle2D.Double(
                            zone.minX * cell,
                            zone.minY * cell,
                            (zone.maxX - zone.minX + 1) * cell,
                            (zone.maxY - zone.minY + 1) * cell
                    ));
                }

                drawOverlays(g, cell, width, height);

                g.setStroke(new BasicStroke(1.5f));

                for (Drone drone : drones) {
                    g.setColor(new Color(20, 110, 220));

                    double previousX = (drone.getX() + 0.5) * cell;
                    double previousY = (drone.getY() + 0.5) * cell;

                    for (int i = drone.getPathIndex();
                         i < drone.getPath().size(); i++) {
                        int[] point = drone.getPath().get(i);
                        double nextX = (point[0] + 0.5) * cell;
                        double nextY = (point[1] + 0.5) * cell;

                        line(g, previousX, previousY, nextX, nextY);

                        previousX = nextX;
                        previousY = nextY;
                    }
                }

                drawChargingStationsWithLabels(g, cell);

                for (Delivery delivery : deliveries) {
                    Color color = delivery.isCompleted()
                            ? new Color(30, 150, 60)
                            : delivery.isFailed()
                            ? Color.GRAY : Color.ORANGE;

                    marker(g, delivery.getX(), delivery.getY(),
                            cell, color, true);
                }

                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

                for (Drone drone : drones) {
                    Color color = Drone.CHARGING.equals(drone.getStatus())
                            ? Color.MAGENTA
                            : Drone.TO_CHARGER.equals(drone.getStatus())
                            ? new Color(0, 150, 170)
                            : Color.BLACK;

                    marker(g, drone.getX(), drone.getY(),
                            cell, color, false);

                    double labelX = Math.min(
                            (drone.getX() + 1) * cell,
                            Math.max(0, width - 45)
                    );

                    g.setColor(Color.BLACK);
                    g.drawString(
                            drone.getId(),
                            (float) labelX,
                            (float) Math.max(
                                    12, (drone.getY() + 0.5) * cell)
                    );
                }

                if (nearestDrone != null && queryX >= 0) {
                    g.setColor(Color.RED);

                    line(g,
                            (queryX + 0.5) * cell,
                            (queryY + 0.5) * cell,
                            (nearestDrone.getX() + 0.5) * cell,
                            (nearestDrone.getY() + 0.5) * cell);
                }

            } finally {
                g.dispose();
            }
        }

        private void drawOverlays(
                Graphics2D g,
                double cell,
                double width,
                double height
        ) {
            g.setStroke(new BasicStroke(1.5f));

            if (showPartitions) {
                g.setColor(new Color(130, 30, 210));

                for (int[] p : droneTree.collectPartitions(
                        0, environment.getWidth(),
                        0, environment.getHeight())) {
                    line(g, p[0] * cell, p[1] * cell,
                            p[2] * cell, p[3] * cell);
                }
            }

            if (showRTree) {
                g.setColor(Color.MAGENTA);

                for (Environment.Rect box : zoneTree.collectNodeBoxes()) {
                    g.draw(new Rectangle2D.Double(
                            box.minX * cell,
                            box.minY * cell,
                            (box.maxX - box.minX + 1) * cell,
                            (box.maxY - box.minY + 1) * cell
                    ));
                }
            }

            if (showHash) {
                g.setColor(new Color(0, 165, 170));

                int size = stationHash.getCellSize();

                for (int x = 0; x <= environment.getWidth(); x += size) {
                    line(g, x * cell, 0, x * cell, height);
                }

                for (int y = 0; y <= environment.getHeight(); y += size) {
                    line(g, 0, y * cell, width, y * cell);
                }
            }
        }

        private void marker(
                Graphics2D g,
                int x,
                int y,
                double cell,
                Color color,
                boolean square
        ) {
            double size = Math.max(4, cell * 0.85);
            double px = (x + 0.5) * cell - size / 2;
            double py = (y + 0.5) * cell - size / 2;

            g.setColor(color);

            if (square) {
                g.fill(new Rectangle2D.Double(px, py, size, size));
            } else {
                g.fill(new Ellipse2D.Double(px, py, size, size));
            }
        }

        private void line(
                Graphics2D g,
                double x1, double y1,
                double x2, double y2
        ) {
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }
    }

    private static class TreePanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final KDTree.NodeSnapshot root;

        TreePanel(KDTree.NodeSnapshot root) {
            this.root = root;
            setBackground(Color.WHITE);
        }

        private int count(KDTree.NodeSnapshot node) {
            return node == null ? 0
                    : 1 + count(node.left) + count(node.right);
        }

        private int levels(KDTree.NodeSnapshot node) {
            return node == null ? 0
                    : 1 + Math.max(levels(node.left), levels(node.right));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            if (root == null) {
                graphics.drawString("No drones indexed.", 20, 30);
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();

            try {
            	
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );
                
                

                double slot = (getWidth() - 40.0) / (count(root) + 1);
                double gap = Math.max(
                        1,
                        (getHeight() - 120.0)
                                / Math.max(1, levels(root) - 1)
                );

                double radius = Math.max(
                        5, Math.min(35, Math.min(slot * 0.38, gap * 0.22))
                );

                draw(g, root, 0, slot, gap, radius);
            } finally {
                g.dispose();
            }
        }

        private double x(
                KDTree.NodeSnapshot node,
                int preceding,
                double slot
        ) {
            return 20 + (preceding + count(node.left) + 1) * slot;
        }

        private void draw(
                Graphics2D g,
                KDTree.NodeSnapshot node,
                int preceding,
                double slot,
                double gap,
                double radius
        ) {
            if (node == null) {
                return;
            }

            int leftCount = count(node.left);
            double cx = x(node, preceding, slot);
            double cy = 55 + node.depth * gap;

            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(2));

            if (node.left != null) {
                g.draw(new Line2D.Double(
                        cx, cy,
                        x(node.left, preceding, slot),
                        55 + node.left.depth * gap
                ));
            }

            if (node.right != null) {
                g.draw(new Line2D.Double(
                        cx, cy,
                        x(node.right, preceding + leftCount + 1, slot),
                        55 + node.right.depth * gap
                ));
            }

            draw(g, node.left, preceding, slot, gap, radius);
            draw(g, node.right, preceding + leftCount + 1,
                    slot, gap, radius);

            Ellipse2D circle = new Ellipse2D.Double(
                    cx - radius, cy - radius,
                    radius * 2, radius * 2
            );

            g.setColor(node.depth % 2 == 0
                    ? new Color(96, 165, 250)
                    : new Color(255, 181, 99));

            g.fill(circle);
            g.setColor(Color.BLACK);
            g.draw(circle);

            String id = node.point.payload instanceof Drone
                    ? ((Drone) node.point.payload).getId() : "Point";

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            centered(g, id, cx, cy + 4);

            centered(g,
                    node.depth % 2 == 0 ? "X split" : "Y split",
                    cx, cy - radius - 8);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            centered(g,
                    "(" + node.point.x + ", " + node.point.y + ")",
                    cx, cy + radius + 18);
        }

        private void centered(
                Graphics2D g,
                String text,
                double x,
                double y
        ) {
            g.drawString(
                    text,
                    (float) (x - g.getFontMetrics().stringWidth(text) / 2.0),
                    (float) y
            );
        }
    }
}