import bc.GameController;

public class Player {
    public static void main(String[] args) {
        Ai ai = new Ai(new GameController());
        while (ai.shouldContinue()) {
            try {
                ai.run();
            } catch (Exception e) {
                System.err.println("Exception caught: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}