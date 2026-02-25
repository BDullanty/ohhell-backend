package GameHandlers;

public class Bot extends Player {
    private String cardBack;
    private String cardFront;

    public Bot(String username) {
        super(username);
        this.state = State.INGAME;
        this.cardBack = User.defaultCardBackKey();
        this.cardFront = User.defaultCardFrontKey();
    }

    public String getCardBack() {
        return cardBack;
    }

    public void setCardBack(String cardBack) {
        this.cardBack = User.normalizeCardBackKey(cardBack);
    }

    public String getCardFront() {
        return cardFront;
    }

    public void setCardFront(String cardFront) {
        this.cardFront = User.normalizeCardFrontKey(cardFront);
    }
}
