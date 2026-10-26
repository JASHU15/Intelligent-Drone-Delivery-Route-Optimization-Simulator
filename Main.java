package droneproject;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final int MAP_WIDTH = 100;
    private static final int MAP_HEIGHT = 100;
    private static final int MAX_DRONES = 10;
    private static final int MAX_DELIVERIES = 20;
    private static final int MAX_PAYLOAD_KG = 20;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println(
                    "=== Intelligent Drone Delivery Simulator ==="
            );
            System.out.println("Map: 100 x 100");
            System.out.println("Coordinates: 0 to 99");
            System.out.println("Maximum payload: 20 kg per drone");
            System.out.println(
                    "Enter battery percentage separately for each drone."
            );

            final Environment environment = createEnvironment();
            final List<Drone> drones = readDrones(scanner, environment);
            final List<Delivery> deliveries =
                    readDeliveries(scanner, environment);

            System.out.println("\nDRONES");
            for (Drone drone : drones) {
                System.out.println(drone);
            }

            System.out.println("\nDELIVERIES");
            for (Delivery delivery : deliveries) {
                System.out.println(delivery);
            }

            SwingUtilities.invokeLater(() -> {
                DroneDeliveryGUI gui = new DroneDeliveryGUI(
                        drones, deliveries, environment
                );
                gui.setVisible(true);
            });

        } catch (IllegalStateException exception) {
            System.err.println(exception.getMessage());
        }
    }

    private static Environment createEnvironment() {
        Environment environment =
                new Environment(MAP_WIDTH, MAP_HEIGHT);

        environment.addNoFlyZone(15, 15, 30, 35);
        environment.addNoFlyZone(50, 25, 70, 40);
        environment.addNoFlyZone(25, 60, 45, 80);
        environment.addNoFlyZone(70, 65, 90, 85);

        environment.addChargingStation(5, 5, 3);
        environment.addChargingStation(50, 5, 3);
        environment.addChargingStation(95, 5, 3);
        environment.addChargingStation(5, 50, 3);
        environment.addChargingStation(50, 50, 3);
        environment.addChargingStation(95, 50, 3);
        environment.addChargingStation(5, 95, 3);
        environment.addChargingStation(50, 95, 3);
        environment.addChargingStation(95, 95, 3);

        return environment;
    }

    private static List<Drone> readDrones(
            Scanner scanner,
            Environment environment
    ) {
        int count = readInt(
                scanner, "Number of drones (1-10): ", 1, MAX_DRONES
        );

        List<int[]> freeCells = new ArrayList<>();

        for (int y = 0; y < environment.getHeight(); y++) {
            for (int x = 0; x < environment.getWidth(); x++) {
                if (!environment.isBlocked(x, y)) {
                    freeCells.add(new int[]{x, y});
                }
            }
        }

        if (freeCells.size() < count) {
            throw new IllegalStateException(
                    "Not enough free cells to place drones."
            );
        }

        Collections.shuffle(freeCells, new Random(42));

        List<Drone> drones = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String id = "D" + (i + 1);
            int[] position = freeCells.get(i);

            System.out.printf(
                    "%n%s generated at (%d, %d)%n",
                    id, position[0], position[1]
            );

            int batteryPercent = readInt(
                    scanner,
                    "Initial battery for " + id + " (0-100%): ",
                    0, 100
            );

            drones.add(new Drone(
                    id,
                    position[0],
                    position[1],
                    batteryPercent,
                    MAX_PAYLOAD_KG
            ));
        }

        return drones;
    }

    private static List<Delivery> readDeliveries(
            Scanner scanner,
            Environment environment
    ) {
        int count = readInt(
                scanner,
                "\nNumber of deliveries (1-20): ",
                1, MAX_DELIVERIES
        );

        List<Delivery> deliveries = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < count; i++) {
            System.out.println("\nDelivery " + (i + 1));

            String id;

            while (true) {
                id = readLine(scanner, "Delivery ID: ").trim();

                if (!id.isEmpty() && ids.add(id)) {
                    break;
                }

                System.out.println("Enter a nonempty, unique ID.");
            }

            int x;
            int y;

            while (true) {
                x = readInt(scanner, "Destination X (0-99): ",
                        0, environment.getWidth() - 1);
                y = readInt(scanner, "Destination Y (0-99): ",
                        0, environment.getHeight() - 1);

                if (!environment.isBlocked(x, y)) {
                    break;
                }

                System.out.println(
                        "Destination is blocked. Enter another."
                );
            }

            int weight = readInt(
                    scanner, "Package weight (1-20 kg): ",
                    1, MAX_PAYLOAD_KG
            );

            int priority = readInt(
                    scanner, "Priority (1=low, 5=high): ", 1, 5
            );

            deliveries.add(new Delivery(
                    id, x, y, weight, priority
            ));
        }

        return deliveries;
    }

    private static int readInt(
            Scanner scanner,
            String prompt,
            int minimum,
            int maximum
    ) {
        while (true) {
            String input = readLine(scanner, prompt).trim();

            try {
                int value = Integer.parseInt(input);

                if (value >= minimum && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // A validation message is printed below.
            }

            System.out.printf(
                    "Enter a whole number between %d and %d.%n",
                    minimum, maximum
            );
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);

        if (!scanner.hasNextLine()) {
            throw new IllegalStateException(
                    "Input ended before setup was complete."
            );
        }

        return scanner.nextLine();
    }
}