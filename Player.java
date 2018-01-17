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
                System.out.println("Exception caught: " + e.getMessage());
                e.printStackTrace(System.out);
            }
            gc.nextTurn();
        }
    }
}