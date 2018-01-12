import bc.GameController;
import src.*;

public class Player {
    public static void main(String[] args) {
        Ai ai = new Ai(new GameController());
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                ai.run();
            } catch (Exception e) {
                System.err.println("Exception caught: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}