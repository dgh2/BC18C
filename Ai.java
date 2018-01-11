import bc.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class Ai {
    private GameController gc;
    private List<Direction> directions = new LinkedList<>();
    private Set<MapLocation> karboniteLocations = new HashSet<>();

    boolean shouldContinue() {
        return true;
    }

    Ai(GameController gc) {
        this.gc = gc;

        karboniteLocations = Util.getInitialKarboniteLocations(gc.startingMap(Planet.Earth));

        for (Direction direction : Direction.values()) {
            if (direction != Direction.Center) {
                directions.add(direction);
            }
        }
    }

    void run() {
        System.out.println("Current round: " + gc.round());
        // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
        VecUnit units = gc.myUnits();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            switch (unit.unitType()) {
                case Worker:
                    Set<Unit> myFactories = getMyUnits(UnitType.Factory);
                    Set<Unit> myRockets = getMyUnits(UnitType.Rocket);
                    UnitType wantedStructure = myFactories.size() < 2 * myRockets.size() ? UnitType.Factory : UnitType.Rocket;
                    for (Direction direction : directions) {
                        System.out.println("Attempting blueprint: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (direction != Direction.Center && gc.canBlueprint(unit.id(), wantedStructure, direction)) {
                            gc.blueprint(unit.id(), wantedStructure, direction);
                            break;
                        }
                    }
                    for (Unit rocket : myRockets) {
                        System.out.println("Attempting to build rocket: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + rocket.location().mapLocation());
                        if (rocket.health() < rocket.maxHealth() && rocket.structureIsBuilt() == 0) {
                            if (gc.canBuild(unit.id(), rocket.id())) {
                                gc.build(unit.id(), rocket.id());
                            } else if (gc.isMoveReady(unit.id())) {
                                moveToward(unit, rocket.location().mapLocation());
                            }
                        }
                    }
                    for (Unit factory : myFactories) {
                        System.out.println("Attempting to build Factory: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + factory.location().mapLocation());
                        if (factory.health() < factory.maxHealth() && factory.structureIsBuilt() == 0) {
                            if (gc.canBuild(unit.id(), factory.id())) {
                                gc.build(unit.id(), factory.id());
                            } else if (gc.isMoveReady(unit.id())) {
                                moveToward(unit, factory.location().mapLocation());
                            }
                        }
                    }
                    for (Direction direction : directions) {
                        System.out.println("Attempting to replicate: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (direction != Direction.Center && gc.canReplicate(unit.id(), direction)) {
                            gc.replicate(unit.id(), direction);
                            break;
                        }
                    }
                    for (Direction direction : directions) {
                        System.out.println("Attempting to move: " + unit.id() + " at "
                                + unit.location().mapLocation() + " to " + direction.name());
                        if (direction != Direction.Center && gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), direction)) {
                            gc.moveRobot(unit.id(), direction);
                            break;
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
                    for (Direction direction : directions) {
                        System.out.println("Attempting to produce worker from: " + unit.id() + " at " + unit.location().mapLocation());
                        if (direction != Direction.Center && gc.canProduceRobot(unit.id(), UnitType.Worker)) {
                            gc.produceRobot(unit.id(), UnitType.Worker);
                            break;
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
                    }
                    break;
            }
        }
        // Submit the actions we've done, and wait for our next turn.
        gc.nextTurn();
    }

    private Set<Unit> getMyUnits(UnitType unitType) {
        System.out.println("getMyUnits called with type: " + unitType.name());
        Set<Unit> units = new HashSet<>();
        for (int i = 0; i < gc.myUnits().size(); i++) {
            if (gc.myUnits().get(i).unitType().equals(unitType)) {
                units.add(gc.myUnits().get(i));
            }
        }
        System.out.println("getMyUnits result: " + units.size() + " " + unitType.name() + (units.size() > 1 ? "s" : ""));
        return units;
    }

    private void moveToward(Unit unit, MapLocation goal) throws IllegalArgumentException {
        System.out.println("Attempting to moveToward: " + unit.id() + " at "
                + unit.location().mapLocation() + " to " + goal);
        if (unit.location().mapLocation().getPlanet() != goal.getPlanet()) {
            throw new IllegalArgumentException("Cannot find path from "
                    + unit.location().mapLocation().getPlanet().name() + " to " + goal.getPlanet().name());
        }
        Direction heading = unit.location().mapLocation().directionTo(goal);
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), heading)) {
            gc.moveRobot(unit.id(), heading);
        } else if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), bc.bcDirectionRotateRight(heading))) {
            gc.moveRobot(unit.id(), bc.bcDirectionRotateRight(heading));
        } else if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), bc.bcDirectionRotateLeft(heading))) {
            gc.moveRobot(unit.id(), bc.bcDirectionRotateLeft(heading));
        } else if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), bc.bcDirectionRotateRight(bc.bcDirectionRotateRight(heading)))) {
            gc.moveRobot(unit.id(), bc.bcDirectionRotateRight(bc.bcDirectionRotateRight(heading)));
        } else if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), bc.bcDirectionRotateLeft(bc.bcDirectionRotateLeft(heading)))) {
            gc.moveRobot(unit.id(), bc.bcDirectionRotateLeft(bc.bcDirectionRotateLeft(heading)));
        }
        System.out.println("    moveToward result: " + unit.id() + " at "
                + unit.location().mapLocation() + " to " + heading.name());
    }
}
