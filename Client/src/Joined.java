import com.fasterxml.jackson.annotation.JsonProperty;

public class Joined {
    private String username;

    public Joined(@JsonProperty("username") String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
