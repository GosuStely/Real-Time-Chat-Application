import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class MessageHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Map<String, Object> data) throws Exception {
        return objectMapper.writeValueAsString(data);
    }

}