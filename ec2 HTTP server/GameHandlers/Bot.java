package GameHandlers;

public class Bot extends Player {
    public Bot(String username) {
        super(username);
        this.state = State.INGAME;
    }
}
