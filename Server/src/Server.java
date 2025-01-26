import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private final ServerSocket SERVER_SOCKET;
    private final Map<String, ClientHandler> CLIENTS;

    public Server(int port) throws IOException {
        SERVER_SOCKET = new ServerSocket(port);
        CLIENTS = new HashMap<>();
        System.out.println("Ready on port: " + port);
    }

    public void listenForConnection() {
        try {
            while (true) {
                Socket clientSocket = SERVER_SOCKET.accept();
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
        if (CLIENTS.get(receiver) == null){
            return false;
        }
        CLIENTS.get(receiver).sendMessage("PRIVATE_MSG " + message);
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
                if (!client.getUsername().equals(username) && CLIENTS.get(username) != null){
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
            Server server = new Server(1234);
            server.listenForConnection();
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }
}
