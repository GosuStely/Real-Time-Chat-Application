import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private final ServerSocket SERVER_SOCKET;
    private final Map<String, ClientHandler> CLIENTS;
    private static final int SERVER_PORT = 1234;
    private static final int SERVER_FILE_PORT = 1235;
    private final Map<String, StreamPair> fileTransferMap;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();
    private Game game = null;

    public Server(int port) throws IOException {
        this.SERVER_SOCKET = new ServerSocket(port);
        this.CLIENTS = new HashMap<>();
        this.fileTransferMap = new HashMap<>();
        System.out.println("Ready on port: " + port);
    }

    public void listenForConnection() {
        try {
            while (true) {
                Socket clientSocket = this.SERVER_SOCKET.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (IOException e) {
            System.out.println("Error accepting client connection: " + e.getMessage());
        }
    }

    public synchronized void broadcast(String message, String senderUsername) {
        CLIENTS.values().forEach(client -> {
            if (!client.getUsername().equals(senderUsername)) {
                client.sendMessage(message);
            }
        });
    }

    public synchronized boolean sendPrivateMessage(String receiver, String message) {
        if (CLIENTS.get(receiver) == null) {
            return false;
        }
        CLIENTS.get(receiver).sendMessage("PRIVATE_MSG " + message);
        return true;
    }

    public synchronized boolean sendGameInvite(String receiver, String message) {
        if (CLIENTS.get(receiver) == null) {
            return false;
        }
        CLIENTS.get(receiver).sendMessage("GAME_INVITE  " + message);
        return true;
    }

    public synchronized void ping(String senderUsername) {
        CLIENTS.values().forEach(client -> {
            if (client.getUsername().equals(senderUsername)) {
                client.sendMessage("PING");
            }
        });
    }

    public synchronized String getClients(String receiverUsername) {
        String listOfClients = null;
        try {
            if (receiverUsername != null) {
                Map<String, ArrayList<String>> jsonMap = new HashMap<>();

                jsonMap.put("clients", new ArrayList<>());
                CLIENTS.values().forEach(client -> {
                    if (!client.getUsername().equals(receiverUsername)) {
                        jsonMap.get("clients").add(client.getUsername());
                    }
                });
                listOfClients = new ObjectMapper().writeValueAsString(jsonMap);
            } else {
                Map<String, Object> jsonMap = new HashMap<>();

                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "9000");
                listOfClients = new ObjectMapper().writeValueAsString(jsonMap);
            }
        } catch (JsonProcessingException ex) {
            System.out.println(ex.getMessage());
        }
        return listOfClients;

    }

    public synchronized boolean addClient(String username, ClientHandler clientHandler) {
        if (CLIENTS.containsKey(username)) {
            return false;
        }
        CLIENTS.put(username, clientHandler);
        return true;
    }

    public synchronized void removeClient(String username) {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("username", username);
            String sentMessage = MessageHandler.toJson(jsonMap);
            CLIENTS.values().forEach(client -> {
                if (!client.getUsername().equals(username) && CLIENTS.get(username) != null) {
                    client.sendMessage("LEFT " + sentMessage);
                }
            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        CLIENTS.remove(username);
    }

    public static void main(String[] args) {
        try {
            Server server = new Server(SERVER_PORT);
            server.listenForConnection();
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    public void handleGame(String sender, String status, String receiver) {
        String sentMessage;
        if (status.equals("REJECTED")) {
            try {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("status", "ERROR");
                jsonMap.put("code", "11004");
                sentMessage = MessageHandler.toJson(jsonMap);
                CLIENTS.get(sender).sendMessage("GAME_PREPARE_RESP " + sentMessage);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        } else {
            try {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("playerOne", sender);
                jsonMap.put("playerTwo", receiver);
                sentMessage = MessageHandler.toJson(jsonMap);
                CLIENTS.values().forEach(client -> {
                    client.sendMessage("GAME_START_RESP " + sentMessage);
                });
                game = new Game(sender, receiver);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }
    private boolean isUserInGame(String username){
        return username.equals(game.getPlayerOne()) || username.equals(game.getPlayerTwo());
    }
    private void userHaveDoneActionAlreadyError(String username){
        String sentMessage;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("status", "ERROR");
        jsonMap.put("code", "12002");
        try {
            sentMessage = MessageHandler.toJson(jsonMap);
            CLIENTS.get(username).sendMessage("GAME_PREPARE_RESP " + sentMessage);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
    private boolean isActionAllowed(String action){
        String[] actions = {"ROCK","PAPER","SCISSORS"};
        boolean flag = false;
        for (int i = 0; i < actions.length; i++) {
            if (action.toUpperCase().equals(actions[i])){
                flag = true;
                break;
            }
        }
        return flag;
    }
    public void handleAction(String action, String username) {
        String sentMessage;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("status", "ERROR");
        if (!isActionAllowed(action)){
            jsonMap.put("code", "12003");
            try {
                sentMessage = MessageHandler.toJson(jsonMap);
                CLIENTS.get(username).sendMessage("GAME_PREPARE_RESP " + sentMessage);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            return;
        }

        if (game == null || !isUserInGame(username)){
            jsonMap.put("code", "12001");
            try {
                sentMessage = MessageHandler.toJson(jsonMap);
                CLIENTS.get(username).sendMessage("GAME_PREPARE_RESP " + sentMessage);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
            return;
        }
        if (username.equals(game.getPlayerOne()) && game.getPlayerOneAction() != null){
            userHaveDoneActionAlreadyError(username);
            return;
        }
        if (username.equals(game.getPlayerTwo()) && game.getGetPlayerTwoAction() != null){
            userHaveDoneActionAlreadyError(username);
            return;
        }
        if (username.equals(game.getPlayerOne()) && game.getPlayerOneAction() == null){
            game.setPlayerOneAction(action);
        }
        if (username.equals(game.getPlayerTwo()) && game.getGetPlayerTwoAction() == null){
            game.setGetPlayerTwoAction(action);
        }
        if (game.getPlayerOneAction() != null && game.getGetPlayerTwoAction() != null){
            jsonMap.clear();
            String winner = game.getWinner();
            jsonMap.put("winner", winner);
            try {
                sentMessage = MessageHandler.toJson(jsonMap);
                CLIENTS.values().forEach(client -> {
                    client.sendMessage("GAME_WINNER " + sentMessage);
                });

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
