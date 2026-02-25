package GameHandlers;

import HTTPHandlers.PostAllUsersToLobby;
import HTTPHandlers.ServerLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class User extends Player{
    private static final String SCOPE = "User";
    private static final String DEFAULT_CARD_BACK = "aurora";
    private static final String DEFAULT_CARD_FRONT = "signal";
    private static final Set<String> ALLOWED_CARD_BACKS = Set.of(
        "aurora",
        "sunburst",
        "stitch"
    );
    private static final Set<String> ALLOWED_CARD_FRONTS = Set.of(
        "signal"
    );
    private static final Map<String, String> LEGACY_CARD_BACK_MAP = Map.ofEntries(
        Map.entry("default", "aurora"),
        Map.entry("onyx", "aurora"),
        Map.entry("scarlet", "sunburst"),
        Map.entry("tide", "stitch"),
        Map.entry("cedar", "sunburst"),
        Map.entry("slate", "aurora")
    );
    private static final Map<String, String> LEGACY_CARD_FRONT_MAP = Map.ofEntries(
        Map.entry("orbit", "signal"),
        Map.entry("skulls", "signal"),
        Map.entry("gilded", "signal"),
        Map.entry("heritage", "signal"),
        Map.entry("clear", "signal"),
        Map.entry("arcane", "signal")
    );
    protected static final HashMap<String,User> connectionList = new HashMap<>();
    protected static final Stack<User> onlineList = new Stack<>();
    protected static final HashMap<String,User> userList = new HashMap<>();
    protected String sub;
    protected ArrayList<String> connectionID;
    protected String cardBack;
    protected String cardFront;
    private final Set<Integer> forfeitedGameIDs;
    private int lastForfeitedGameID;

    public User(String sub,String username, String connectionID){
        super(username);
        this.connectionID = new ArrayList<>();
        this.sub=sub;
        this.connectionID.add(connectionID);
        this.cardBack = DEFAULT_CARD_BACK;
        this.cardFront = DEFAULT_CARD_FRONT;
        this.forfeitedGameIDs = new HashSet<>();
        this.lastForfeitedGameID = -1;
        User.connectionList.put(connectionID,this);
        this.state = State.LOBBY;
    }

    public static User getUser(String connectionID) {
        return User.connectionList.get(connectionID);
    }


    public static ArrayList<String> getLobbyConnections() {
        ArrayList<String> connections = new ArrayList<>();
        // for each player in lobby
        for(int i = 0; i < onlineList.size();i++){
            User u =onlineList.get(i);
            if(u.state==State.LOBBY || u.state==State.WAITING || u.state==State.INGAME){
                //For each connection this player has
                for(int j = 0; j <u.connectionID.size(); j++){
                    connections.add(u.connectionID.get(j));
                }
            }
        }
        return connections;
    }


    private User addConnection(String connectionID) {
        if (!this.connectionID.contains(connectionID)) {
            this.connectionID.add(connectionID);
        }
        User.connectionList.put(connectionID,this);
        ServerLog.info(SCOPE, "Connection " + connectionID + " mapped to " + getUsername());
        return this;
    }
    public String getSub() {
        return sub;
    }

    public String getUsername() {
        return super.getUsername();
    }

    public synchronized String getCardBack() {
        return cardBack;
    }

    public static String defaultCardBackKey() {
        return DEFAULT_CARD_BACK;
    }

    public static String defaultCardFrontKey() {
        return DEFAULT_CARD_FRONT;
    }

    public synchronized void setCardBack(String newCardBack) {
        cardBack = normalizeCardBackKey(newCardBack);
    }

    public synchronized String getCardFront() {
        return cardFront;
    }

    public synchronized void setCardFront(String newCardFront) {
        cardFront = normalizeCardFrontKey(newCardFront);
    }

    public synchronized void markGameForfeited(int gameID) {
        if (gameID < 0) {
            return;
        }
        forfeitedGameIDs.add(gameID);
        lastForfeitedGameID = gameID;
    }

    public synchronized boolean hasForfeitedGame(int gameID) {
        return gameID >= 0 && forfeitedGameIDs.contains(gameID);
    }

    public synchronized int getLastForfeitedGameID() {
        return lastForfeitedGameID;
    }

    public synchronized ArrayList<Integer> getForfeitedGameIDs() {
        return new ArrayList<>(forfeitedGameIDs);
    }

    public static String normalizeCardBackKey(String raw) {
        String normalized = String.valueOf(raw)
            .trim()
            .toLowerCase();
        if (LEGACY_CARD_BACK_MAP.containsKey(normalized)) {
            normalized = LEGACY_CARD_BACK_MAP.get(normalized);
        }
        if (ALLOWED_CARD_BACKS.contains(normalized)) {
            return normalized;
        }
        return DEFAULT_CARD_BACK;
    }

    public static String normalizeCardFrontKey(String raw) {
        String normalized = String.valueOf(raw)
            .trim()
            .toLowerCase();
        if (LEGACY_CARD_FRONT_MAP.containsKey(normalized)) {
            normalized = LEGACY_CARD_FRONT_MAP.get(normalized);
        }
        if (ALLOWED_CARD_FRONTS.contains(normalized)) {
            return normalized;
        }
        return DEFAULT_CARD_FRONT;
    }



    //STATIC FUNCTIONS

    //Returns a player object unless one is already made
    public static User getUser(String sub, String username, String connectionID){
        //if the player object exists, send it.
        if(User.userList.containsKey(sub)){
            return User.userList.get(sub).addConnection(connectionID);
        }
        //If not, we create the object
        User newPlayer = new User(sub,username,connectionID);
        userList.put(newPlayer.getSub(),newPlayer);
        return newPlayer;
    }


    public static void addUserOnline(User p){
        if(p==null) throw new IllegalArgumentException("Player was null");
        if(onlineList.contains(p)) {
            ServerLog.info(SCOPE, p.getUsername() + " already marked online.");
        }
        else{
            if (p.gameID == -1) {
                p.state = State.LOBBY;
            }
            User.onlineList.add(p);
        }

    }
    public static User removeConnection(String connectionID){
        User user = User.connectionList.get(connectionID);
        if (user == null) {
            ServerLog.warn(SCOPE, "Unknown connection remove request: " + connectionID);
            return null;
        }
        user.connectionID.remove(connectionID);
        if(user.connectionID.size()==0){
            user.state= State.OFFLINE;
            User.onlineList.remove(user);
            ServerLog.info(SCOPE, "Player " + user.getUsername() + " is now offline.");
            PostAllUsersToLobby.postAllUsersToLobby();
        }
        else{
            ServerLog.info(
                SCOPE,
                "Player " + user.getUsername() + " has " + user.connectionID.size() + " live connections"
            );
        }
        connectionList.remove(connectionID);
        return user;
    }
    public static JSONObject getUsersObject() {
        JSONObject users = new JSONObject();
        for(int i =0; i < onlineList.size();i++){
            User u = onlineList.elementAt(i);
            JSONObject userInfo = new JSONObject();
            userInfo.put("status", u.getState().toString());
            userInfo.put("cardBack", u.getCardBack());
            userInfo.put("cardFront", u.getCardFront());
            users.put(u.getUsername(), userInfo);
        }
        return users;
    }

    public static String getUsers(){
        return getUsersObject().toString();
    }


    public ArrayList<String> getConnections() {
        return new ArrayList<>(this.connectionID);
    }

    public boolean isOnline() {
        return this.connectionID != null && !this.connectionID.isEmpty();
    }



}
