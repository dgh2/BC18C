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
                e.printStackTrace(System.out);
            } finally {
                System.runFinalization();
                System.gc();
            }
            gc.nextTurn();
        }
    }
}