package GameHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;

    public Deck() {
        this.cards = new ArrayList<>();
        loadDeck();
        shuffle();
    }

    private void loadDeck() {
        cards.clear();
        for (Suits suit : Suits.values()) {
            for (int rank = 2; rank <= 14; rank++) {
                cards.add(new Card(rank, suit));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.remove(cards.size() - 1);
    }

    public int size() {
        return cards.size();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Card card : cards) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(card.toString());
            first = false;
        }
        return builder.toString();
    }
}
