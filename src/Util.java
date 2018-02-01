package src;

import bc.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

//If the method can be written without changing a state or needing anything from gc, it goes in here

@SuppressWarnings("WeakerAccess")
class Util {
    private static Random rand = new Random();
    private static List<Direction> directions;

    //Only runs once, used to instantiate static variables such as directions
    static {
        List<Direction> allDirections = new LinkedList<>();
        for (Direction direction : Direction.values()) {
            if (direction != Direction.Center) {
                allDirections.add(direction);
            }
        }
        directions = Collections.unmodifiableList(allDirections);
    }

    //Do not allow creating instances of this class
    private Util() {
    }

    static void reseedRandom(long seed) {
        rand = new Random(seed);
    }

    //Inclusive, so getRandomInt(3) returns 1, 2, or 3
    static int getRandomInt(int max) {
        return rand.nextInt(max) + 1;
    }

    //Inclusive, so getRandomInt(2,4) returns 2, 3, or 4
    static int getRandomInt(int min, int max) {
        return getRandomInt(1 + max - min) - 1 + min;
    }

    //Returns all directions EXCEPT Direction.Center
    static List<Direction> getDirections() {
        return directions;
    }

    //Returns a random direction EXCEPT Direction.Center
    static Direction getRandomDirection() {
        return directions.get(rand.nextInt(directions.size() - 1));
    }

    //Loop through all locations on the PlanetMap, return all MapLocations of initial Karbonite deposits
    static Map<MapLocation, Long> getInitialKarboniteAmounts(PlanetMap planetMap) {
        Map<MapLocation, Long> initialKarboniteLocationMap = new HashMap<>();
        MapLocation temp;
        for (int i = 0; i < planetMap.getWidth(); i++) {
            for (int j = 0; j < planetMap.getHeight(); j++) {
                temp = new MapLocation(planetMap.getPlanet(), i, j);
                if (planetMap.initialKarboniteAt(temp) > 0) {
                    initialKarboniteLocationMap.put(temp, planetMap.initialKarboniteAt(temp));
                }
            }
        }
        return initialKarboniteLocationMap;
    }

    static Set<MapLocation> getNeighbors(MapLocation center) {
        Set<MapLocation> neighbors = new HashSet<>();
        for (Direction direction : getDirections()) {
            neighbors.add(center.add(direction));
        }
        return neighbors;
    }

//    static double getPathCost(List<Direction> path) {
//        //Uniform cost
//        return path.size();
//    }
//
//    static double distanceBetween(MapLocation a, MapLocation b) {
//        //Uniform cost
//        long dx = Math.abs(a.getX() - b.getX());
//        long dy = Math.abs(a.getY() - b.getY());
//        return Math.max(dx, dy);
//    }
//
//    static double getPathCost(List<Direction> path) {
//        //Diagonals cost sqrt(2) extra
//        double pathCost = 0;
//        for (Direction step : path) {
//            switch (step) {
//                case North:
//                case East:
//                case South:
//                case West:
//                    pathCost++;
//                    break;
//                case Northeast:
//                case Southeast:
//                case Southwest:
//                case Northwest:
//                    pathCost += 1.41421356237;
//                    break;
//                case Center:
//                    break;
//            }
//        }
//        return pathCost;
//    }
//
//    static double distanceBetween(MapLocation a, MapLocation b) {
//        //Diagonals cost sqrt(2) extra
//        long dx = Math.abs(a.getX() - b.getX());
//        long dy = Math.abs(a.getY() - b.getY());
//        long min = Math.min(dx, dy);
//        long max = Math.max(dx, dy);
//        return 1.41421356237 * min + max - min;
//    }
//
    static double getPathCost(List<Direction> path) {
        //Turns cost sqrt(2) extra
        double pathCost = 0;
        Direction previousStep = null;
        for (Direction step : path) {
            if (step.equals(previousStep)) {
                pathCost++;
            } else {
                pathCost += 1.41421356237;
            }
            previousStep = step;
        }
        return pathCost;
    }

    static double distanceBetween(MapLocation a, MapLocation b) {
        //Turns cost sqrt(2) extra
        long dx = Math.abs(a.getX() - b.getX());
        long dy = Math.abs(a.getY() - b.getY());
        return Math.max(dx, dy) + (Math.min(dx, dy) == 0 ? 0 : 1.41421356237);
    }
//
//    static double getPathCost(List<Direction> path) {
//        //Euclidean distance squared - Beware of infinite loop
//        long dx = 0;
//        long dy = 0;
//        for (Direction step : path) {
//            switch (step) {
//                case North:
//                case Northeast:
//                case Northwest:
//                case South:
//                case Southeast:
//                case Southwest:
//                    dy++;
//                default:
//                    break;
//            }
//            switch (step) {
//                case Northeast:
//                case East:
//                case Southeast:
//                case Northwest:
//                case West:
//                case Southwest:
//                    dx++;
//                default:
//                    break;
//            }
//        }
//        return (dx * dx) + (dy * dy);
//    }
//
//    static double distanceBetween(MapLocation a, MapLocation b) {
//        //Euclidean distance squared - Beware of infinite loop
//        return a.distanceSquaredTo(b);
//    }
}
