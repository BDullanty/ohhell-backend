package GameHandlers;

import java.util.ArrayList;

// Can be a bot or a user.
public abstract class Player {
    protected String username;
    protected int gameID;
    protected int seatIndex;
    protected ArrayList<Card> hand;
    protected int bet;
    protected int handsWon;
    protected int score;
    protected Enum state;
    protected boolean hasVoted;

    public Player(String username) {
        this.username = username;
        this.gameID = -1;
        this.seatIndex = -1;
        this.hand = new ArrayList<>();
        this.bet = -1;
        this.handsWon = 0;
        this.score = 0;
        this.hasVoted = false;
    }

    public String getUsername() {
        return this.username;
    }

    public int getGameID() {
        return gameID;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Enum getState() {
        return this.state;
    }

    public void setGameID(int gameID) {
        this.gameID = gameID;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(int seatIndex) {
        this.seatIndex = seatIndex;
    }

    public ArrayList<Card> getHand() {
        return hand;
    }

    public void setHand(ArrayList<Card> hand) {
        this.hand = hand;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
    }

    public boolean hasPlacedBet() {
        return bet >= 0;
    }

    public void resetForRound() {
        this.hand.clear();
        this.bet = -1;
        this.handsWon = 0;
    }

    public int getHandsWon() {
        return handsWon;
    }

    public void incrementHandsWon() {
        this.handsWon += 1;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    public void setVoted() {
        this.hasVoted = true;
    }

    public boolean hasVoted() {
        return this.hasVoted;
    }

    public void unsetVote() {
        this.hasVoted = false;
    }
}
