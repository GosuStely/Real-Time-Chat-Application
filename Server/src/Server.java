import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients;

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        clients = new HashMap<>();
        System.out.println("Ready on port: " + port);
    }

    public void listenForConnection() {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
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
        clients.values().forEach(client -> {
            if (!client.getUsername().equals(senderUsername)) {
                client.sendMessage(message);
            }
        });
    }

    public synchronized void ping(String senderUsername) {
        clients.values().forEach(client -> {
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
                clients.values().forEach(client -> {
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
        if (clients.containsKey(username)) {
            return false;
        }
        clients.put(username, clientHandler);
        return true;
    }

    public synchronized void removeClient(String username) {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("username", username);
            String sentMessage = MessageHandler.toJson(jsonMap);
            clients.values().forEach(client -> {
                client.sendMessage("LEFT " + sentMessage);
            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        clients.remove(username);

//        System.out.println("Client " + username + " disconnected.");
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
