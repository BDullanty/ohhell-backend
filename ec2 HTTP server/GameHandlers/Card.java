package GameHandlers;

public class Card {
    private final int rank;
    private final Suits suit;
    private int playedByIndex = -1;

    public Card(int rank, Suits suit) {
        if (rank < 2 || rank > 14) {
            throw new IllegalArgumentException("Rank must be between 2 and 14.");
        }
        if (suit == null) {
            throw new IllegalArgumentException("Suit cannot be null.");
        }
        this.rank = rank;
        this.suit = suit;
    }

    public int getRank() {
        return rank;
    }

    public Suits getSuit() {
        return suit;
    }

    public int getPlayedByIndex() {
        return playedByIndex;
    }

    public void setPlayedByIndex(int playedByIndex) {
        this.playedByIndex = playedByIndex;
    }

    public String getKey() {
        return rankToKey(rank) + suit.getKeyLetter();
    }

    public int getTrickValue(Suits trumpSuit, Suits leadSuit) {
        if (leadSuit != null && trumpSuit != suit && suit != leadSuit) {
            return 0;
        }
        int value = rank;
        if (trumpSuit != null && suit == trumpSuit) {
            value += 20;
        }
        return value;
    }

    private static String rankToKey(int rank) {
        switch (rank) {
            case 11:
                return "j";
            case 12:
                return "q";
            case 13:
                return "k";
            case 14:
                return "a";
            default:
                return String.valueOf(rank);
        }
    }

    private static String rankToLabel(int rank) {
        switch (rank) {
            case 11:
                return "Jack";
            case 12:
                return "Queen";
            case 13:
                return "King";
            case 14:
                return "Ace";
            default:
                return String.valueOf(rank);
        }
    }

    @Override
    public String toString() {
        return rankToLabel(rank) + " of " + suit.getDisplayName();
    }
}
