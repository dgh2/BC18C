package src;

import bc.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Ai {
    private GameController gc;
    private Map<UnitType, Set<Unit>> myUnits = new HashMap<>();
    private Map<Planet, PlanetMap> startingMaps = new HashMap<>();

    private Set<MapLocation> karboniteLocations = null;
    private Long round = null;
    private Long karbonite = null;
    private Planet planet = null;


    //Put no logic here in the constructor, exceptions can't be handled this early
    public Ai(GameController gc) {
        this.gc = gc;
    }

    //initialization method that should only run on the first turn
    private void runOnce() {
        karboniteLocations = Util.getInitialKarboniteLocations(gc.startingMap(Planet.Earth));
        for (Planet planet : Planet.values()) {
            startingMaps.put(planet, gc.startingMap(planet));
        }
        round = gc.round() - 1;
    }

    //Set variables once per round to prevent excessive calls to gc
    private void initialize() {
        if (round == null) {
            runOnce();
        }
        karbonite = gc.karbonite();
        planet = gc.planet();
        myUnits.clear();
        VecUnit myUnitsVc = gc.myUnits();
        for (int i = 0; i < myUnitsVc.size(); i++) {
            System.out.println("getMyUnits getting unit at index: " + i + " of type " + myUnitsVc.get(i).unitType());
            if (!myUnits.containsKey(myUnitsVc.get(i).unitType())) {
                myUnits.put(myUnitsVc.get(i).unitType(), new HashSet<>());
            }
            myUnits.get(myUnitsVc.get(i).unitType()).add(myUnitsVc.get(i));
        }
        System.out.println("Current round: " + round);
    }

    //Called each turn by Player.java
    public void run() {
        initialize();
        // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
        VecUnit units = gc.myUnits();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            System.out.println("Processing " + unit.unitType().name() + " " + unit.id());
            switch (unit.unitType()) {
                //TODO: Make each unit type its own class
                case Worker:
                    if (!unit.location().isOnPlanet(planet)) {
                        break;
                    }
                    if (unit.workerHasActed() != 0) {
                        break;
                    }
                    Set<Unit> myFactories = getMyUnits(UnitType.Factory);
                    Set<Unit> myRockets = getMyUnits(UnitType.Rocket);
                    UnitType wantedStructure = myFactories.size() <= 2 * myRockets.size() ? UnitType.Factory : UnitType.Rocket;

                    for (Direction direction : Util.getDirections()) {
                        if (karbonite < bc.bcUnitTypeBlueprintCost(wantedStructure)) {
                            break;
                        }
                        System.out.println("Attempting blueprint: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (gc.canBlueprint(unit.id(), wantedStructure, direction)) {
                            gc.blueprint(unit.id(), wantedStructure, direction);
                            break;
                        }
                    }
                    if (unit.workerHasActed() != 0) {
                        break;
                    }
                    if (attemptToBuild(unit, UnitType.Rocket)) {
                        break;
                    }
                    if (attemptToBuild(unit, UnitType.Factory)) {
                        break;
                    }
                    if (karbonite > 7 * bc.bcUnitTypeReplicateCost(UnitType.Worker)) {
                        for (Direction direction : Util.getDirections()) {
                            System.out.println("Attempting to replicate: " + unit.id() + " at "
                                    + unit.location().mapLocation() + " to " + direction.name());
                            if (gc.canReplicate(unit.id(), direction)) {
                                gc.replicate(unit.id(), direction);
                                break;
                            }
                        }
                    }
                    if (unit.movementHeat() == 0) {
                        for (Direction direction : Util.getDirections()) {
                            System.out.println("Attempting to move: " + unit.id() + " at "
                                    + unit.location().mapLocation() + " to " + direction.name());
                            if (performMove(unit, direction)) {
                                break;
                            }
                        }
                    }
                    break;
                case Healer:
                    break;
                case Ranger:
                    break;
                case Knight:
                    break;
                case Mage:
                    break;
                case Factory:
                    System.out.println("Attempting to produce worker from Factory: " + unit.id() + " at " + unit.location().mapLocation());
                    if (karbonite > bc.bcUnitTypeFactoryCost(UnitType.Worker)) {
                        if (gc.canProduceRobot(unit.id(), UnitType.Worker)) {
                            gc.produceRobot(unit.id(), UnitType.Worker);
                        }
                    }
                    break;
                case Rocket:
                    if (planet == Planet.Earth && unit.location().isOnPlanet(planet)) {
                        if (unit.structureGarrison().size() == unit.structureMaxCapacity()) {
                            System.out.println("Rocket is full at: " + unit.location().mapLocation());
                            MapLocation target = Util.getRandomValidLocation(startingMaps.get(Planet.Mars));
                            if (gc.canLaunchRocket(unit.id(), target)) {
                                System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                                gc.launchRocket(unit.id(), target);
                            }
                        } else {
                            System.out.println("Rocket is waiting for "
                                    + (unit.structureMaxCapacity() - unit.structureGarrison().size())
                                    + " more units until launch");
                        }
                    }
                    break;
            }
        }
        round++;
        // Submit the actions we've done, and wait for our next turn.
        gc.nextTurn();
    }

    //Get all of my units of a particular type
    private boolean attemptToBuild(Unit unit, UnitType unitType) throws IllegalArgumentException {
        if (unitType != UnitType.Factory && unitType != UnitType.Rocket) {
            throw new IllegalArgumentException(unitType.name() + " can not be built");
        }
        if (unit.unitType() != UnitType.Worker) {
            throw new IllegalArgumentException("Unit " + unit.id() + " is not a worker and cannot build");
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        System.out.println("attemptToBuild called with " + unitType.name() + ": " + unit.id() + " to build a " + unitType.name());
        for (Unit structure : getMyUnits(unitType)) {
            System.out.println("Attempting to build " + unitType.name() + ": " + unit.id() + " at "
                    + unit.location() + " to " + structure.location());
            if (structure.structureIsBuilt() == 0 && structure.location().isOnPlanet(unit.location().mapLocation().getPlanet())) {
                if (gc.canBuild(unit.id(), structure.id())) {
                    gc.build(unit.id(), structure.id());
                    return true;
                } else if (moveToward(unit, structure.location().mapLocation())) {
                    return true;
                }
            }
        }
        return false;
    }

    //Get all of my units of a particular type
    private Set<Unit> getMyUnits(UnitType unitType) {
        System.out.println("getMyUnits called for unit type: " + unitType);
        if (!myUnits.containsKey(unitType)) {
            System.out.println("getMyUnits size: 0");
            return new HashSet<Unit>();
        }
        System.out.println("getMyUnits size: " + myUnits.get(unitType).size());
        return myUnits.get(unitType);
    }

    //Move a unit toward the goal location
    private boolean moveToward(Unit unit, MapLocation goal) throws IllegalArgumentException {
        if (unit.movementHeat() != 0) {
            return false;
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        System.out.println("Attempting to moveToward: " + unit.id() + " at "
                + unit.location().mapLocation() + " to " + goal);
        if (unit.location().mapLocation().getPlanet() != goal.getPlanet()) {
            throw new IllegalArgumentException("Cannot path from "
                    + unit.location().mapLocation().getPlanet().name() + " to " + goal.getPlanet().name());
        }
        if (unit.unitType() == UnitType.Factory || unit.unitType() == UnitType.Rocket) {
            throw new IllegalArgumentException("Unit " + unit.id() + " of type " + unit.unitType().name() + " cannot move");
        }
        Direction heading = unit.location().mapLocation().directionTo(goal);
        return performMove(unit, heading)
                || performMove(unit, bc.bcDirectionRotateRight(heading))
                || performMove(unit, bc.bcDirectionRotateRight(bc.bcDirectionRotateRight(heading)))
                || performMove(unit, bc.bcDirectionRotateLeft(heading))
                || performMove(unit, bc.bcDirectionRotateLeft(bc.bcDirectionRotateLeft(heading)));
    }

    //move a unit in the given direction
    private boolean performMove(Unit unit, Direction heading) {
        if (unit.movementHeat() != 0) {
            return false;
        }
        if (!unit.location().isOnPlanet(planet)) {
            return false;
        }
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), heading)) {
            gc.moveRobot(unit.id(), heading);
            System.out.println("performMove result: " + unit.id() + " at "
                    + unit.location().mapLocation() + " moved " + heading.name());
            return true;
        }
        return false;
    }
}
