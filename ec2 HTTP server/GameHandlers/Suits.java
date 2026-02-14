package GameHandlers;

public enum Suits {
    HEART,
    DIAMOND,
    SPADE,
    CLUB;

    public String getDisplayName() {
        switch (this) {
            case HEART:
                return "Hearts";
            case DIAMOND:
                return "Diamonds";
            case SPADE:
                return "Spades";
            case CLUB:
                return "Clubs";
            default:
                return name();
        }
    }

    public String getKeyLetter() {
        switch (this) {
            case HEART:
                return "h";
            case DIAMOND:
                return "d";
            case SPADE:
                return "s";
            case CLUB:
                return "c";
            default:
                return "";
        }
    }
}
