import bc.GameController;
import src.*;

public class Player {
    public static void main(String[] args) {
        GameController gc = new GameController();
        Ai ai = new Ai(gc);
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                ai.run();
            } catch (Exception e) {
                System.err.println("Exception caught: " + e.getMessage());
                e.printStackTrace();
            }
            gc.nextTurn();
        }
    }
}