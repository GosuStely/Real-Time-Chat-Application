public class Game {
    private final String playerOne;
    private final String playerTwo;
    private String playerOneAction;
    private String playerTwoAction;

    public Game(String playerOne, String playerTwo) {
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
    }

    public String getPlayerOne() {
        return playerOne;
    }

    public String getPlayerTwo() {
        return playerTwo;
    }

    public String getPlayerOneAction() {
        return playerOneAction;
    }

    public void setPlayerOneAction(String playerOneAction) {
        this.playerOneAction = playerOneAction;
    }

    public String getGetPlayerTwoAction() {
        return playerTwoAction;
    }

    public void setGetPlayerTwoAction(String getPlayerTwoAction) {
        this.playerTwoAction = getPlayerTwoAction;
    }

    public String getWinner() {
        // Tie cases
        if (playerOneAction.equals(playerTwoAction)) {
            return "No";
        }

        // Player One wins cases
        if (playerOneAction.equals("ROCK") && playerTwoAction.equals("PAPER") ||
                playerOneAction.equals("PAPER") && playerTwoAction.equals("SCISSORS") ||
                playerOneAction.equals("SCISSORS") && playerTwoAction.equals("ROCK")) {
            return playerOne;
        }

        // Player Two wins cases
        return playerTwo;
    }
}
