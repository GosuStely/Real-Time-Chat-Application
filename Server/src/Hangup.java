import com.fasterxml.jackson.annotation.JsonProperty;

public class Hangup {

    private String reason;

    public Hangup(@JsonProperty("username") String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
