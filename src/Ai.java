package src;

import bc.*;

import java.util.HashSet;
import java.util.Set;

public class Ai {
    private GameController gc;
    private Set<MapLocation> karboniteLocations = null;

    //Put no logic here in the constructor, exceptions can't be handled this early
    public Ai(GameController gc) {
        this.gc = gc;
    }

    //Called each turn by Player.java
    public void run() {
        if (karboniteLocations == null) {
            karboniteLocations = Util.getInitialKarboniteLocations(gc.startingMap(Planet.Earth));
        }

        System.out.println("Current round: " + gc.round());
        // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
        VecUnit units = gc.myUnits();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            switch (unit.unitType()) {
                //TODO: Make each unit type its own class
                case Worker:
                    if (unit.workerHasActed() != 0) {
                        break;
                    }
                    Set<Unit> myFactories = getMyUnits(UnitType.Factory);
                    Set<Unit> myRockets = getMyUnits(UnitType.Rocket);
                    UnitType wantedStructure = myFactories.size() < 2 * myRockets.size() ? UnitType.Factory : UnitType.Rocket;

                    for (Direction direction : Util.getDirections()) {
                        if (gc.karbonite() < bc.bcUnitTypeBlueprintCost(wantedStructure)) {
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
                    if (gc.karbonite() > 4 * bc.bcUnitTypeReplicateCost(UnitType.Worker)) {
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
                    if (gc.karbonite() > bc.bcUnitTypeFactoryCost(UnitType.Worker)) {
                        if (gc.canProduceRobot(unit.id(), UnitType.Worker)) {
                            gc.produceRobot(unit.id(), UnitType.Worker);
                        }
                    }
                    break;
                case Rocket:
                    if (unit.structureGarrison().size() == unit.structureMaxCapacity()) {
                        System.out.println("Rocket is full at: " + unit.location().mapLocation());
                        MapLocation target = Util.getRandomValidLocation(gc.startingMap(Planet.Mars));
                        if (gc.canLaunchRocket(unit.id(), target)) {
                            System.out.println("Launching rocket from: " + unit.location().mapLocation() + " to " + target);
                            gc.launchRocket(unit.id(), target);
                        }
                    } else {
                        System.out.println("Rocket is waiting for "
                                + (unit.structureMaxCapacity() - unit.structureGarrison().size())
                                + " more units until launch");
                    }
                    break;
            }
        }
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
        System.out.println("attemptToBuild called with " + unitType.name() + ": " + unit.id() + " to build a " + unitType.name());
        if (unit.workerHasActed() == 0) {
            for (Unit structure : getMyUnits(unitType)) {
                System.out.println("Attempting to build " + unitType.name() + ": " + unit.id() + " at "
                        + unit.location().mapLocation() + " to " + structure.location().mapLocation());
                if (structure.structureIsBuilt() == 0) {
                    if (gc.canBuild(unit.id(), structure.id())) {
                        gc.build(unit.id(), structure.id());
                        return true;
                    } else if (gc.isMoveReady(unit.id())) {
                        moveToward(unit, structure.location().mapLocation());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //Get all of my units of a particular type
    private Set<Unit> getMyUnits(UnitType unitType) {
        System.out.println("getMyUnits called with type: " + unitType.name());
        Set<Unit> units = new HashSet<>();
        for (int i = 0; i < gc.myUnits().size(); i++) {
            if (gc.myUnits().get(i).unitType().equals(unitType)) {
                units.add(gc.myUnits().get(i));
            }
        }
        System.out.println("getMyUnits result: " + units.size() + " " + unitType.name());
        return units;
    }

    //Move a unit toward the goal location
    private boolean moveToward(Unit unit, MapLocation goal) throws IllegalArgumentException {
        if (unit.movementHeat() != 0) {
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
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), heading)) {
            gc.moveRobot(unit.id(), heading);
            System.out.println("performMove result: " + unit.id() + " at "
                    + unit.location().mapLocation() + " moved " + heading.name());
            return true;
        }
        return false;
    }
}
