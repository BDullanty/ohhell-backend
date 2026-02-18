package GameHandlers;

import HTTPHandlers.PostAllUsersToLobby;
import HTTPHandlers.ServerLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class User extends Player{
    private static final String SCOPE = "User";
    protected static final HashMap<String,User> connectionList = new HashMap<>();
    protected static final Stack<User> onlineList = new Stack<>();
    protected static final HashMap<String,User> userList = new HashMap<>();
    protected String sub;
    protected ArrayList<String> connectionID;

    public User(String sub,String username, String connectionID){
        super(username);
        this.connectionID = new ArrayList<>();
        this.sub=sub;
        this.connectionID.add(connectionID);
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
            users.put(u.getUsername(),u.getState().toString());
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
