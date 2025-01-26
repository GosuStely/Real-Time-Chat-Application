import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private volatile boolean didPong;
    private volatile boolean isRunning;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            didPong = true;
            isRunning = true;

            while (true) {
                String message = in.readLine();
                if (message == null) continue;
                String user = this.username != null ? this.username : this.socket.getRemoteSocketAddress().toString();
                String[] parts = message.split(" ", 2);
                String header = parts[0];
                String content = parts.length > 1 ? parts[1] : "";
                System.out.printf("\n%s >>>> %s",user,message);
                switch (header) {
                    case "ENTER":
                        handleEnter(content);
                        break;
                    case "BROADCAST_REQ":
                        handleBroadcast(content);
                        break;
                    case "BYE":
                        handleBye();
                        return;
                    case "PONG":
                        handlePong();
                        break;
                    case "LIST_REQ":
                        handleListRequest();
                        break;
                    case "PRIVATE_MSG_REQ":
                        handlePrivateMessageRequest(content);
                        break;
                    default:
                        sendMessage("UNKNOWN_COMMAND");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handlePrivateMessageRequest(String content) {
        try {
            Map<String, Object> message = MessageHandler.fromJson(content);
            String receiver = (String) message.get("receiver");
            String privateMessage = (String) message.get("message");
            Map<String,Object> jsonMap = new HashMap<>();
            String sentMessage;
            if (this.username == null){
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "10001");
                sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("PRIVATE_MSG_RESP  " + sentMessage);

                return;
            }
            if (receiver.equals(this.username)){
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "10003");
                sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("PRIVATE_MSG_RESP  " + sentMessage);
                return;
            }
            jsonMap.put("sender",this.username);
            jsonMap.put("message",privateMessage);
            sentMessage = MessageHandler.toJson(jsonMap);
            boolean flag = server.sendPrivateMessage(receiver,sentMessage);
            if (!flag){
                jsonMap.clear();
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "10002");
                sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("PRIVATE_MSG_RESP  " + sentMessage);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void handleListRequest() {
        if (username == null){
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("status", "ERROR");
            jsonMap.put("code", "9000");
            String sentMessage;
            try {
                sentMessage = MessageHandler.toJson(jsonMap);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sendMessage("LIST_RESP " + sentMessage);
            return;
        }
        String jsonList = server.getClients(this.username);
        sendMessage("LIST_RESP " + jsonList);
    }

    private void handlePong(){
        if (didPong){
            try {
                Map<String,Object> jsonMap = new HashMap<>();
                jsonMap.put("code","8000");
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("PONG_ERROR " + sentMessage);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        } else{
            didPong = true;
        }
    }
    private void pingTheClient() {
        // Schedule periodic pings every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (didPong) {
                didPong = false; // Reset the flag to expect a pong
                server.ping(username);

                // Schedule a one-off task to check for pong response after 3 seconds
                scheduler.schedule(() -> {
                    if (!didPong && isRunning) { // If no pong was received, handle timeout
                        handleTimeout();
                    }
                }, 3, TimeUnit.SECONDS);
            }
        }, 0, 10, TimeUnit.SECONDS); // Start immediately, repeat every 10 seconds
    }

    private void handleTimeout() {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("reason", "7000");
            String sentMessage = MessageHandler.toJson(jsonMap);
            sendMessage("HANGUP " + sentMessage);

            // Remove the client from the server
            server.removeClient(username);

            // Stop further tasks for this client
            scheduler.shutdownNow();

        } catch (Exception ex) {
            System.out.println("Error during timeout handling: " + ex.getMessage());
        }
    }

    private void handleEnter(String content) {
        try {
            Map<String, Object> message = MessageHandler.fromJson(content);
            String requestedUsername = (String) message.get("username");

            if (requestedUsername.isBlank() || requestedUsername.length() > 20 || requestedUsername.length() < 3) {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "5001");
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("ENTER_RESP " + sentMessage);
                return;
            }

            boolean success = server.addClient(requestedUsername, this);
            if (!success) {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "5000");
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("ENTER_RESP " + sentMessage);
            } else {
                this.username = requestedUsername;
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("status", "OK");
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("ENTER_RESP " + sentMessage);
                jsonMap.clear();
                jsonMap.put("username", username);
                sentMessage = MessageHandler.toJson(jsonMap);
                server.broadcast("JOINED " + sentMessage, this.username);
                jsonMap.clear();
                jsonMap.put("version", "1.1.1");
                sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("READY " + sentMessage);
//                Thread pinging = new Thread(this::pingTheClient);
//                pinging.start();
                pingTheClient();
            }
        } catch (Exception e) {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("status", "ERROR");
            jsonMap.put("code", "5002");
            try {
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("ENTER_RESP " + sentMessage);
            } catch (Exception ex) {
                System.out.println("json parsing made on the error catched for enter response failed.");
            }
        }
    }

    private void handleBroadcast(String content) {
        try {
            Map<String, Object> message = MessageHandler.fromJson(content);
            String userMessage = (String) message.get("message");
            if (userMessage != null) {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("message", userMessage);
                jsonMap.put("username", username);
                String sentMessage = MessageHandler.toJson(jsonMap);

                server.broadcast("BROADCAST " + sentMessage, username);
            }
            if (username.isBlank()) {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "6000");
                String sentMessage = MessageHandler.toJson(jsonMap);
                sendMessage("BROADCAST_RESP " + sentMessage);
            }
        } catch (Exception e) {
            sendMessage("PARSE_ERROR");
        }
    }

    private void handleBye() {
        isRunning = false;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("status", "OK");
        try {
            String sentMessage = MessageHandler.toJson(jsonMap);
            sendMessage("BYE_RESP " + sentMessage);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        cleanup();
    }

    private void cleanup() {
        try {
            if (username != null) server.removeClient(username);
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            System.out.println("Error closing client: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        out.println(message);
        out.flush();
        System.out.printf("\n%s <<<< %s",this.username,message);
    }

    public String getUsername() {
        return username;
    }
}
