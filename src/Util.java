package src;

import bc.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
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
    static Set<MapLocation> getInitialKarboniteLocations(PlanetMap planetMap) {
        Set<MapLocation> initialKarboniteLocations = new HashSet<>();
        MapLocation temp;
        for (int i = 0; i < planetMap.getWidth(); i++) {
            for (int j = 0; j < planetMap.getHeight(); j++) {
                temp = new MapLocation(planetMap.getPlanet(), i, j);
                if (planetMap.initialKarboniteAt(temp) > 0) {
                    initialKarboniteLocations.add(temp);
                }
            }
        }
        return initialKarboniteLocations;
    }

    //Get a random passable location from the PlanetMap
    static MapLocation getRandomValidLocation(PlanetMap planetMap) {
        MapLocation random;
        do {
            random = new MapLocation(planetMap.getPlanet(),
                    getRandomInt((int) planetMap.getWidth()),
                    getRandomInt((int) planetMap.getHeight()));
            System.out.println("Random location: " + random);
        } while (planetMap.isPassableTerrainAt(random) != 0);
        System.out.println("Returning valid random location: " + random);
        return random;
    }
}
