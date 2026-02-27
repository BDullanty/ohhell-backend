package GameHandlers;

import HTTPHandlers.PostGameState;
import HTTPHandlers.PostUserInfo;
import HTTPHandlers.ServerLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Game {
    private static final String SCOPE = "Game";
    public static int IDTracker = 0;
    private static final int MAX_PLAYERS = 5;
    private static final int MAX_HAND_CARDS = 10;
    private static final int EXTRA_NO_TRUMP_ROUND = 11;
    private static final int EXTRA_TRUMP_ROUND = 12;
    private static final int TOTAL_ROUNDS = 21;
    private static final long BOT_TURN_DELAY_MS = 2_000;
    private static final long PLAYER_TURN_DELAY_MS = 30_000;
    private static final long OFFLINE_BOT_DELAY_MS = 10_000;
    private static final long ALL_PLAYERS_DISCONNECTED_END_DELAY_MS = TimeUnit.MINUTES.toMillis(3);
    private static final long TRICK_GLOW_MS = 2_000;
    private static final long TRICK_SWIPE_MS = 650;
    private static final long TRICK_PAUSE_MS = TRICK_GLOW_MS + TRICK_SWIPE_MS;
    private static final long FINAL_CARD_DELAY_MS = 2_000;
    private static final long ROUND_DEAL_CARD_STAGGER_MS = 130;
    private static final long ROUND_DEAL_TRUMP_DELAY_MS = 260;
    private static final long ROUND_DEAL_TRUMP_FLIP_MS = 760;
    private static final long ROUND_DEAL_BUFFER_MS = 220;
    private static final long BET_SUMMARY_PAUSE_MS = 2_400;
    private static final String[] BOT_FIRST_NAMES = {
        "Ava", "Mia", "Nora", "Lena", "Sofia", "Emma", "Olivia", "Hazel",
        "Amelia", "Aria", "Ivy", "Lucy", "Chloe", "Zoey", "Noah", "Liam",
        "Ethan", "Mason", "Lucas", "Owen", "Levi", "Caleb", "Isaac", "Eli",
        "Julian", "Silas", "Asher", "Wyatt", "Jasper", "Miles", "Nolan", "Theo"
    };

    private final int gameID;
    private final ArrayList<Player> players;
    private State state;
    private GamePhase phase;
    private Deck deck;
    private int round;
    private int initiatorIndex;
    private int currentTurnIndex;
    private Card trump;
    private Suits leadSuit;
    private Card topDog;
    private final ArrayList<Card> tableCards;
    private final ArrayList<Card> displayCards;
    private int lastTrickWinnerIndex;
    private int trickCounter;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> turnTimeout;
    private ScheduledFuture<?> botOnlyTimeout;
    private ScheduledFuture<?> trickCompleteTimeout;
    private ScheduledFuture<?> unlockNotifyTimeout;
    private ScheduledFuture<?> betSummaryTimeout;
    private long turnToken;
    private long pauseUntilMs;
    private long turnDeadlineMs;
    private long roundDealStartMs;
    private long roundDealEndMs;
    private String bidResultStatus;
    private long bidResultUntilMs;
    private boolean betSummaryPending;
    private boolean trickPending;

    public Game(User host) {
        this.gameID = ++IDTracker;
        this.players = new ArrayList<>();
        this.state = State.LOBBY;
        this.phase = GamePhase.WAITING;
        this.round = 0;
        this.initiatorIndex = 0;
        this.currentTurnIndex = 0;
        this.tableCards = new ArrayList<>();
        this.displayCards = new ArrayList<>();
        this.lastTrickWinnerIndex = -1;
        this.trickCounter = 0;
        this.pauseUntilMs = 0;
        this.turnDeadlineMs = 0;
        this.roundDealStartMs = 0;
        this.roundDealEndMs = 0;
        this.bidResultStatus = null;
        this.bidResultUntilMs = 0;
        this.betSummaryPending = false;
        this.trickPending = false;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        addPlayer(host);
    }

    public int getGameID() {
        return this.gameID;
    }

    public synchronized ArrayList<Player> getPlayers() {
        return players;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized GamePhase getPhase() {
        return phase;
    }

    public synchronized int getRound() {
        return round;
    }

    public synchronized int getCardsDealt() {
        return cardsForRound(round);
    }

    public synchronized Card getTrump() {
        return trump;
    }

    public synchronized Suits getLeadSuit() {
        return leadSuit;
    }

    public synchronized List<Card> getTableCards() {
        if (!tableCards.isEmpty()) {
            return new ArrayList<>(tableCards);
        }
        return new ArrayList<>(displayCards);
    }

    public synchronized int getCurrentTurnIndex() {
        return currentTurnIndex;
    }

    public synchronized int getInitiatorIndex() {
        return initiatorIndex;
    }

    public synchronized int getLastTrickWinnerIndex() {
        return lastTrickWinnerIndex;
    }

    public synchronized int getTrickCounter() {
        return trickCounter;
    }

    public synchronized long getTurnDeadlineMs() {
        return turnDeadlineMs;
    }

    public synchronized int getTurnTimeLimitSeconds() {
        return (int) TimeUnit.MILLISECONDS.toSeconds(PLAYER_TURN_DELAY_MS);
    }

    public synchronized long getRoundDealStartMs() {
        return roundDealStartMs;
    }

    public synchronized long getRoundDealEndMs() {
        return roundDealEndMs;
    }

    public synchronized long getRoundDealCardStaggerMs() {
        return ROUND_DEAL_CARD_STAGGER_MS;
    }

    public synchronized long getRoundDealTrumpDelayMs() {
        return ROUND_DEAL_TRUMP_DELAY_MS;
    }

    public synchronized long getRoundDealTrumpFlipMs() {
        return ROUND_DEAL_TRUMP_FLIP_MS;
    }

    public synchronized String getBidResultStatus() {
        return bidResultStatus;
    }

    public synchronized long getBidResultUntilMs() {
        return bidResultUntilMs;
    }

    public synchronized void addPlayer(Player p) {
        if (p == null) {
            return;
        }
        if (players.contains(p)) {
            p.setGameID(this.gameID);
            p.setSeatIndex(players.indexOf(p));
            ServerLog.warn(
                SCOPE,
                "Skipped duplicate add for player " + p.getUsername() + " in game " + gameID
            );
            return;
        }
        if (players.size() >= MAX_PLAYERS) {
            return;
        }
        players.add(p);
        p.setGameID(this.gameID);
        p.setSeatIndex(players.size() - 1);
        ServerLog.info(SCOPE, "Player added to game " + gameID + ": " + p.getUsername());
    }

    public synchronized void removePlayer(Player p) {
        players.remove(p);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setSeatIndex(i);
        }
        ServerLog.info(
            SCOPE,
            "Removed " + p.getUsername() + " from game " + gameID + ". Remaining players=" + players.size()
        );
    }

    public synchronized boolean replaceUserWithBot(User user) {
        if (user == null || state != State.INGAME) {
            return false;
        }
        int index = players.indexOf(user);
        if (index < 0) {
            return false;
        }

        Bot replacement = new Bot(generateBotName());
        replacement.setGameID(gameID);
        replacement.setSeatIndex(index);
        replacement.setHand(new ArrayList<>(user.getHand()));
        replacement.setBet(user.getBet());
        replacement.handsWon = user.getHandsWon();
        replacement.score = user.getScore();
        replacement.hasVoted = user.hasVoted();
        replacement.setCardBack(user.getCardBack());
        replacement.setCardFront(user.getCardFront());

        user.getHand().clear();
        players.set(index, replacement);
        ServerLog.info(
            SCOPE,
            "Replaced forfeiting user " + user.getUsername() + " with " + replacement.getUsername() + " in game " + gameID
        );
        return true;
    }

    public synchronized void setState(State state) {
        this.state = state;
        State userState = state.equals(State.COMPLETED) ? State.LOBBY : state;
        ServerLog.info(SCOPE, "Game " + gameID + " state changed to " + state);
        for (Player player : players) {
            if (player instanceof User) {
                User user = (User) player;
                user.setState(userState);
                PostUserInfo.postUserInfo(user);
            }
        }
    }

    public synchronized void startGame() {
        if (state == State.INGAME) {
            return;
        }
        fillBots();
        for (Player player : players) {
            player.resetForGame();
        }
        setState(State.INGAME);
        phase = GamePhase.BETTING;
        round = 1;
        initiatorIndex = 0;
        startRound();
        updateBotOnlyTimer();
    }

    private void fillBots() {
        int count = players.size();
        String botCardBack = preferredBotCardBack();
        String botCardFront = preferredBotCardFront();
        for (int i = count; i < MAX_PLAYERS; i++) {
            Bot bot = new Bot(generateBotName());
            bot.setCardBack(botCardBack);
            bot.setCardFront(botCardFront);
            addPlayer(bot);
        }
    }

    private String generateBotName() {
        Set<String> takenNames = new HashSet<>();
        for (Player player : players) {
            takenNames.add(player.getUsername());
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 64; attempt++) {
            String candidate = BOT_FIRST_NAMES[random.nextInt(BOT_FIRST_NAMES.length)];
            if (!takenNames.contains(candidate)) {
                return candidate;
            }
        }
        String fallbackBase = BOT_FIRST_NAMES[random.nextInt(BOT_FIRST_NAMES.length)];
        int suffix = 2;
        String candidate = fallbackBase + " " + suffix;
        while (takenNames.contains(candidate)) {
            suffix++;
            candidate = fallbackBase + " " + suffix;
        }
        return candidate;
    }

    private String preferredBotCardBack() {
        for (Player player : players) {
            if (player instanceof User) {
                return ((User) player).getCardBack();
            }
        }
        return User.defaultCardBackKey();
    }

    private String preferredBotCardFront() {
        for (Player player : players) {
            if (player instanceof User) {
                return ((User) player).getCardFront();
            }
        }
        return User.defaultCardFrontKey();
    }

    private void startRound() {
        for (Player player : players) {
            player.resetForRound();
        }
        deck = new Deck();
        tableCards.clear();
        displayCards.clear();
        leadSuit = null;
        topDog = null;
        trump = null;
        pauseUntilMs = 0;
        trickPending = false;
        trickCounter = 0;
        turnDeadlineMs = 0;
        bidResultStatus = null;
        bidResultUntilMs = 0;
        betSummaryPending = false;
        if (betSummaryTimeout != null) {
            betSummaryTimeout.cancel(false);
            betSummaryTimeout = null;
        }

        int cardsThisRound = cardsForRound(round);
        dealCards(cardsThisRound);
        boolean hasTrumpThisRound = hasTrumpForRound(round);
        if (hasTrumpThisRound) {
            trump = deck.draw();
        } else {
            trump = null;
        }
        roundDealStartMs = System.currentTimeMillis();
        roundDealEndMs =
            roundDealStartMs + computeRoundDealDurationMs(cardsThisRound, hasTrumpThisRound);
        pauseUntilMs = roundDealEndMs;
        phase = GamePhase.BETTING;
        currentTurnIndex = initiatorIndex;
        lastTrickWinnerIndex = -1;
        advanceAfterStateChange();
    }

    private void dealCards(int count) {
        for (int i = 0; i < count; i++) {
            for (Player player : players) {
                Card card = deck.draw();
                if (card != null) {
                    player.getHand().add(card);
                }
            }
        }
    }

    public synchronized boolean handleBet(Player player, int bet) {
        if (state != State.INGAME || phase != GamePhase.BETTING) {
            return false;
        }
        if (betSummaryPending) {
            return false;
        }
        if (player == null || player != players.get(currentTurnIndex)) {
            return false;
        }
        if (System.currentTimeMillis() < pauseUntilMs) {
            return false;
        }
        if (trickPending) {
            return false;
        }
        if (bet < 0) {
            bet = 0;
        }
        int maxBet = cardsForRound(round);
        if (bet > maxBet) {
            bet = maxBet;
        }
        cancelTurnTimeout();
        player.setBet(bet);
        advanceAfterBet();
        advanceAfterStateChange();
        return true;
    }

    public synchronized boolean handlePlay(Player player, String cardKey) {
        if (state != State.INGAME || phase != GamePhase.PLAYING) {
            return false;
        }
        if (player == null || player != players.get(currentTurnIndex)) {
            return false;
        }
        if (System.currentTimeMillis() < pauseUntilMs) {
            return false;
        }
        if (trickPending) {
            return false;
        }
        Card chosen = findCardInHand(player, cardKey);
        if (chosen == null) {
            return false;
        }
        if (!isValidPlay(player, chosen)) {
            return false;
        }
        cancelTurnTimeout();
        playCard(player, chosen);
        advanceAfterPlay();
        advanceAfterStateChange();
        return true;
    }

    private boolean roundComplete() {
        for (Player player : players) {
            if (!player.getHand().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void endRound() {
        for (Player player : players) {
            int delta = scoreChange(player);
            player.addScore(delta);
        }
        lastTrickWinnerIndex = -1;
        if (round >= TOTAL_ROUNDS) {
            phase = GamePhase.COMPLETED;
            notifyState();
            GameHandler.end(this);
            return;
        }
        initiatorIndex = round % players.size();
        round += 1;
        startRound();
    }

    private int scoreChange(Player player) {
        if (player.getBet() > player.getHandsWon()) {
            return -player.getBet();
        }
        if (player.getBet() < player.getHandsWon()) {
            return -player.getHandsWon();
        }
        return player.getBet() == 0 ? 20 : player.getBet() * 10;
    }

    private void playCard(Player player, Card card) {
        if (tableCards.isEmpty() && !displayCards.isEmpty()) {
            displayCards.clear();
        }
        player.getHand().remove(card);
        card.setPlayedByIndex(player.getSeatIndex());
        tableCards.add(card);
        if (leadSuit == null) {
            leadSuit = card.getSuit();
        }
        updateTopDog(card);
    }

    private void updateTopDog(Card card) {
        if (topDog == null) {
            topDog = card;
            return;
        }
        int candidateValue = card.getTrickValue(trump != null ? trump.getSuit() : null, leadSuit);
        int topValue = topDog.getTrickValue(trump != null ? trump.getSuit() : null, leadSuit);
        if (candidateValue > topValue) {
            topDog = card;
        }
    }

    private void endSubRound() {
        if (topDog == null) {
            return;
        }
        int winnerIndex = topDog.getPlayedByIndex();
        if (winnerIndex >= 0 && winnerIndex < players.size()) {
            players.get(winnerIndex).incrementHandsWon();
            initiatorIndex = winnerIndex;
            lastTrickWinnerIndex = winnerIndex;
        }
        displayCards.clear();
        displayCards.addAll(tableCards);
        tableCards.clear();
        leadSuit = null;
        topDog = null;
        pauseUntilMs = System.currentTimeMillis() + TRICK_PAUSE_MS;
        trickCounter += 1;
    }

    private boolean allBetsPlaced() {
        for (Player player : players) {
            if (!player.hasPlacedBet()) {
                return false;
            }
        }
        return true;
    }

    private int nextIndex(int current) {
        int next = current + 1;
        if (next >= players.size()) {
            next = 0;
        }
        return next;
    }

    private boolean isValidPlay(Player player, Card card) {
        if (leadSuit == null) {
            return true;
        }
        if (card.getSuit() == leadSuit) {
            return true;
        }
        for (Card handCard : player.getHand()) {
            if (handCard.getSuit() == leadSuit) {
                return false;
            }
        }
        return true;
    }

    private Card findCardInHand(Player player, String cardKey) {
        if (cardKey == null) {
            return null;
        }
        String normalized = cardKey.trim().toLowerCase();
        for (Card card : player.getHand()) {
            if (card.getKey().equals(normalized)) {
                return card;
            }
        }
        return null;
    }

    private void scheduleTurn() {
        cancelTurnTimeout();
        turnDeadlineMs = 0;
        if (state != State.INGAME || phase == GamePhase.COMPLETED) {
            return;
        }
        if (betSummaryPending) {
            return;
        }
        if (!hasOnlineHuman()) {
            return;
        }
        if (trickPending) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = pauseUntilMs > now ? pauseUntilMs - now : 0;
        Player current = players.get(currentTurnIndex);
        long token = ++turnToken;
        if (isAutoPlayer(current)) {
            long autoDelay = delay + getTurnDelayMs(current);
            turnDeadlineMs = now + autoDelay;
            turnTimeout = scheduler.schedule(() -> onAutoTurn(token), autoDelay, TimeUnit.MILLISECONDS);
        } else {
            long timeoutDelay = delay + PLAYER_TURN_DELAY_MS;
            turnDeadlineMs = now + timeoutDelay;
            turnTimeout = scheduler.schedule(() -> onTurnTimeout(token), timeoutDelay, TimeUnit.MILLISECONDS);
        }
    }

    private void onTurnTimeout(long token) {
        synchronized (this) {
            if (token != turnToken) {
                return;
            }
            if (System.currentTimeMillis() < pauseUntilMs) {
                return;
            }
            Player current = players.get(currentTurnIndex);
            if (phase == GamePhase.BETTING) {
                int bet = calculateAIBet(current);
                handleBet(current, bet);
            } else if (phase == GamePhase.PLAYING) {
                Card choice = chooseAICard(current);
                if (choice != null) {
                    handlePlay(current, choice.getKey());
                }
            }
        }
    }

    private void onAutoTurn(long token) {
        synchronized (this) {
            if (token != turnToken) {
                return;
            }
            if (System.currentTimeMillis() < pauseUntilMs) {
                return;
            }
            if (state != State.INGAME || phase == GamePhase.COMPLETED) {
                return;
            }
            Player current = players.get(currentTurnIndex);
            if (!isAutoPlayer(current)) {
                return;
            }
            if (phase == GamePhase.BETTING) {
                int bet = calculateAIBet(current);
                handleBet(current, bet);
            } else if (phase == GamePhase.PLAYING) {
                Card choice = chooseAICard(current);
                if (choice != null) {
                    handlePlay(current, choice.getKey());
                }
            }
        }
    }

    private long getTurnDelayMs(Player player) {
        if (player instanceof Bot) {
            return BOT_TURN_DELAY_MS;
        }
        if (player instanceof User) {
            User user = (User) player;
            if (!user.isOnline()) {
                return OFFLINE_BOT_DELAY_MS;
            }
        }
        return PLAYER_TURN_DELAY_MS;
    }

    private int calculateAIBet(Player player) {
        int bidCounter = 0;
        for (Card card : player.getHand()) {
            int value = card.getTrickValue(trump != null ? trump.getSuit() : null, null);
            if (value == 14 || value > 26 || (value > 20 && player.getHand().size() < 3)) {
                bidCounter++;
            }
        }
        int maxBet = cardsForRound(round);
        if (bidCounter > maxBet) {
            bidCounter = maxBet;
        }
        return bidCounter;
    }

    private int cardsForRound(int roundNumber) {
        if (roundNumber <= MAX_HAND_CARDS) {
            return roundNumber;
        }
        if (roundNumber == EXTRA_NO_TRUMP_ROUND || roundNumber == EXTRA_TRUMP_ROUND) {
            return MAX_HAND_CARDS;
        }
        int descendingIndex = roundNumber - EXTRA_TRUMP_ROUND;
        int cards = MAX_HAND_CARDS - descendingIndex;
        return Math.max(1, cards);
    }

    private boolean hasTrumpForRound(int roundNumber) {
        return roundNumber != EXTRA_NO_TRUMP_ROUND;
    }

    private Card chooseAICard(Player player) {
        if (player.getHand().isEmpty()) {
            return null;
        }
        if (topDog == null) {
            if (player.getHandsWon() == player.getBet()) {
                return chooseLowestValidCard(player);
            }
            return chooseInitiatorWinCard(player);
        }
        if (player.getHandsWon() == player.getBet()) {
            return chooseHighestNonWinningCard(player);
        }
        return chooseLowestWinningCard(player);
    }

    private Card chooseInitiatorWinCard(Player player) {
        boolean hasNonTrump = false;
        Suits trumpSuit = trump != null ? trump.getSuit() : null;
        for (Card card : player.getHand()) {
            if (trumpSuit == null || card.getSuit() != trumpSuit) {
                hasNonTrump = true;
                break;
            }
        }
        Card best = null;
        int bestValue = -1;
        for (Card card : player.getHand()) {
            if (!isValidPlay(player, card)) {
                continue;
            }
            if (hasNonTrump && trumpSuit != null && card.getSuit() == trumpSuit) {
                continue;
            }
            int value = card.getTrickValue(trumpSuit, leadSuit);
            if (value > bestValue) {
                best = card;
                bestValue = value;
            }
        }
        if (best == null) {
            return chooseLowestWinningCard(player);
        }
        return best;
    }

    private Card chooseLowestValidCard(Player player) {
        Card best = null;
        int bestValue = Integer.MAX_VALUE;
        Suits trumpSuit = trump != null ? trump.getSuit() : null;
        for (Card card : player.getHand()) {
            if (!isValidPlay(player, card)) {
                continue;
            }
            int value = card.getTrickValue(trumpSuit, leadSuit);
            if (value < bestValue) {
                best = card;
                bestValue = value;
            }
        }
        return best;
    }

    private Card chooseHighestNonWinningCard(Player player) {
        if (topDog == null) {
            return chooseLowestValidCard(player);
        }
        Suits trumpSuit = trump != null ? trump.getSuit() : null;
        int topValue = topDog.getTrickValue(trumpSuit, leadSuit);
        Card best = null;
        int bestValue = -1;
        for (Card card : player.getHand()) {
            if (!isValidPlay(player, card)) {
                continue;
            }
            int value = card.getTrickValue(trumpSuit, leadSuit);
            if (value < topValue && value > bestValue) {
                best = card;
                bestValue = value;
            }
        }
        if (best == null) {
            return chooseLowestValidCard(player);
        }
        return best;
    }

    private Card chooseLowestWinningCard(Player player) {
        if (topDog == null) {
            return chooseLowestValidCard(player);
        }
        Suits trumpSuit = trump != null ? trump.getSuit() : null;
        int topValue = topDog.getTrickValue(trumpSuit, leadSuit);
        Card best = null;
        int bestValue = Integer.MAX_VALUE;
        for (Card card : player.getHand()) {
            if (!isValidPlay(player, card)) {
                continue;
            }
            int value = card.getTrickValue(trumpSuit, leadSuit);
            if (value > topValue && value < bestValue) {
                best = card;
                bestValue = value;
            }
        }
        if (best == null) {
            return chooseLowestValidCard(player);
        }
        return best;
    }

    private void cancelTurnTimeout() {
        if (turnTimeout != null) {
            turnTimeout.cancel(false);
            turnTimeout = null;
        }
        turnDeadlineMs = 0;
    }

    public synchronized void shutdown() {
        cancelTurnTimeout();
        if (betSummaryTimeout != null) {
            betSummaryTimeout.cancel(false);
            betSummaryTimeout = null;
        }
        if (trickCompleteTimeout != null) {
            trickCompleteTimeout.cancel(false);
            trickCompleteTimeout = null;
        }
        if (unlockNotifyTimeout != null) {
            unlockNotifyTimeout.cancel(false);
            unlockNotifyTimeout = null;
        }
        if (botOnlyTimeout != null) {
            botOnlyTimeout.cancel(false);
            botOnlyTimeout = null;
        }
        scheduler.shutdownNow();
    }

    public synchronized void refreshTurnTimer() {
        if (state != State.INGAME || phase == GamePhase.COMPLETED) {
            return;
        }
        scheduleTurn();
    }

    public synchronized void updateBotOnlyTimer() {
        if (state != State.INGAME) {
            return;
        }
        if (hasOnlineHuman()) {
            if (botOnlyTimeout != null) {
                botOnlyTimeout.cancel(false);
                botOnlyTimeout = null;
                ServerLog.info(
                    SCOPE,
                    "Cleared disconnected game-end timer for game " + gameID + " after a user reconnected."
                );
            }
            return;
        }
        if (botOnlyTimeout != null) {
            return;
        }
        botOnlyTimeout = scheduler.schedule(() -> {
            synchronized (this) {
                botOnlyTimeout = null;
                if (state != State.INGAME) {
                    return;
                }
                if (hasOnlineHuman()) {
                    return;
                }
                ServerLog.info(
                    SCOPE,
                    "All users disconnected for 3 minutes in game " + gameID + ". Ending game."
                );
                GameHandler.end(this);
            }
        }, ALL_PLAYERS_DISCONNECTED_END_DELAY_MS, TimeUnit.MILLISECONDS);
        ServerLog.info(
            SCOPE,
            "Scheduled disconnected game-end timer for game " + gameID + " in "
                + TimeUnit.MILLISECONDS.toSeconds(ALL_PLAYERS_DISCONNECTED_END_DELAY_MS)
                + " seconds."
        );
    }

    public synchronized boolean hasHumanPlayers() {
        for (Player player : players) {
            if (player instanceof User && ((User) player).getGameID() == gameID) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasOnlineHumanPlayers() {
        return hasOnlineHuman();
    }

    private void notifyState() {
        PostGameState.postGameState(this);
    }

    private void advanceAfterBet() {
        if (allBetsPlaced()) {
            currentTurnIndex = initiatorIndex;
            scheduleBetSummaryPause();
        } else {
            currentTurnIndex = nextIndex(currentTurnIndex);
        }
    }

    private void advanceAfterPlay() {
        if (tableCards.size() >= players.size()) {
            scheduleTrickResolution();
            return;
        }
        currentTurnIndex = nextIndex(currentTurnIndex);
    }

    private void advanceAfterStateChange() {
        scheduleTurn();
        scheduleUnlockNotification();
        notifyState();
    }

    private long computeRoundDealDurationMs(int cardsThisRound, boolean hasTrumpThisRound) {
        long handDealDuration = Math.max(0, cardsThisRound) * ROUND_DEAL_CARD_STAGGER_MS;
        long trumpDuration = hasTrumpThisRound ? ROUND_DEAL_TRUMP_DELAY_MS + ROUND_DEAL_TRUMP_FLIP_MS : 0;
        return handDealDuration + trumpDuration + ROUND_DEAL_BUFFER_MS;
    }

    private int totalBets() {
        int total = 0;
        for (Player player : players) {
            if (!player.hasPlacedBet()) {
                continue;
            }
            total += player.getBet();
        }
        return total;
    }

    private String resolveBidResultStatus() {
        int total = totalBets();
        int dealt = cardsForRound(round);
        if (total == dealt) {
            return "EVEN_BID";
        }
        if (total > dealt) {
            return "OVER_BID";
        }
        return "UNDER_BID";
    }

    private void scheduleBetSummaryPause() {
        betSummaryPending = true;
        bidResultStatus = resolveBidResultStatus();
        long now = System.currentTimeMillis();
        bidResultUntilMs = now + BET_SUMMARY_PAUSE_MS;
        pauseUntilMs = Math.max(pauseUntilMs, bidResultUntilMs);
        if (betSummaryTimeout != null) {
            betSummaryTimeout.cancel(false);
        }
        betSummaryTimeout = scheduler.schedule(() -> {
            synchronized (this) {
                betSummaryTimeout = null;
                if (state != State.INGAME || phase != GamePhase.BETTING || !betSummaryPending) {
                    return;
                }
                betSummaryPending = false;
                bidResultStatus = null;
                bidResultUntilMs = 0;
                phase = GamePhase.PLAYING;
                currentTurnIndex = initiatorIndex;
                advanceAfterStateChange();
            }
        }, BET_SUMMARY_PAUSE_MS, TimeUnit.MILLISECONDS);
    }

    private boolean isAutoPlayer(Player player) {
        if (player instanceof Bot) {
            return true;
        }
        if (player instanceof User) {
            return !((User) player).isOnline();
        }
        return false;
    }

    private boolean hasOnlineHuman() {
        for (Player player : players) {
            if (
                player instanceof User
                    && ((User) player).getGameID() == gameID
                    && ((User) player).isOnline()
            ) {
                return true;
            }
        }
        return false;
    }

    private void scheduleTrickResolution() {
        if (trickPending) {
            return;
        }
        trickPending = true;
        long now = System.currentTimeMillis();
        pauseUntilMs = Math.max(pauseUntilMs, now + FINAL_CARD_DELAY_MS + TRICK_PAUSE_MS);
        if (trickCompleteTimeout != null) {
            trickCompleteTimeout.cancel(false);
        }
        trickCompleteTimeout = scheduler.schedule(() -> {
            synchronized (this) {
                if (state != State.INGAME || phase == GamePhase.COMPLETED) {
                    trickPending = false;
                    return;
                }
                endSubRound();
                if (roundComplete()) {
                    advanceAfterStateChange();
                    scheduler.schedule(() -> {
                        synchronized (this) {
                            if (state != State.INGAME || phase == GamePhase.COMPLETED) {
                                trickPending = false;
                                return;
                            }
                            trickPending = false;
                            endRound();
                        }
                    }, TRICK_PAUSE_MS, TimeUnit.MILLISECONDS);
                    return;
                }
                currentTurnIndex = initiatorIndex;
                trickPending = false;
                advanceAfterStateChange();
            }
        }, FINAL_CARD_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized boolean isActionLocked() {
        return System.currentTimeMillis() < pauseUntilMs;
    }

    private void scheduleUnlockNotification() {
        if (unlockNotifyTimeout != null) {
            unlockNotifyTimeout.cancel(false);
            unlockNotifyTimeout = null;
        }
        long now = System.currentTimeMillis();
        long delay = pauseUntilMs > now ? pauseUntilMs - now : 0;
        if (delay <= 0) {
            return;
        }
        unlockNotifyTimeout = scheduler.schedule(() -> {
            synchronized (this) {
                if (state != State.INGAME || phase == GamePhase.COMPLETED) {
                    return;
                }
                if (System.currentTimeMillis() < pauseUntilMs) {
                    return;
                }
                if (tableCards.isEmpty() && !displayCards.isEmpty()) {
                    displayCards.clear();
                }
                notifyState();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
