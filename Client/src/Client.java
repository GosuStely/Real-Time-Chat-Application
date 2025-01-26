import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;

public class Client {
    private Socket clientSocket;
    private PrintWriter sender;
    private BufferedReader receiver;
    private Scanner input;
    private volatile boolean isRunning = true;
    private boolean isLogged = false;

    public void startConnection(String ip, int port) {
        try {
            clientSocket = new Socket(ip, port);
            sender = new PrintWriter(clientSocket.getOutputStream(), true);
            receiver = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            input = new Scanner(System.in);
            Thread serverMessageSender = new Thread(this::filterMessage);
            serverMessageSender.start();
            showMenuOptions();
            while (isRunning) {
                if (!isLogged) {
                    System.out.println("Enter username:");
                }
                receiveMessageFromServer();
            }
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
        } finally {
            closeConnection();
        }

    }

    private void filterMessage() {
        while (isRunning) {
            try {
                String userInput = input.nextLine();
                String jsonMessage;
                String header;
                HashMap<String, Object> messageMap = new HashMap<>();
                if (!isLogged) {
                    isLogged = true;
                    messageMap.put("username", userInput);
                    header = "ENTER ";
                } else if (userInput.equals("/quit")) {
                    sendMessageToServer("BYE");
                    return;
                } else if (userInput.equals("/userList")) {
                    sendMessageToServer("LIST_REQ");
                    continue;
                } else if (userInput.split(" ")[0].equals("/dm")) {
                    if(userInput.split(" ").length < 2){
                        System.err.println("you need to write a receiver's username");
                        continue;
                    }
                    if (userInput.split(" ").length < 3){
                        System.err.println("you need to write message to the receiver");
                        continue;
                    }
                    String receiver = userInput.split(" ")[1];
                    String message = userInput.split(" ", 3)[2];
                    messageMap.put("receiver", receiver);
                    messageMap.put("message", message);
                    header = "PRIVATE_MSG_REQ ";
                    jsonMessage = MessageHandler.toJson(messageMap);
                    sendMessageToServer(header + jsonMessage);
                    continue;
                }else{
                    messageMap.put("message", userInput);
                    header = "BROADCAST_REQ ";
                }
                jsonMessage = MessageHandler.toJson(messageMap);
                sendMessageToServer(header + jsonMessage);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private void receiveMessageFromServer() throws IOException {
        String serverMessage = receiver.readLine();
        if (serverMessage.contains("BYE_RESP")) {
            System.out.println("You have disconnected");
            isRunning = false;
            closeConnection();
        } else if (serverMessage.equals("PING")) {
            sendMessageToServer("PONG");
            return;
        } else if (serverMessage.equals("UNKNOWN_COMMAND")){
            return;
        }
        String[] splitMessage = serverMessage.split(" ", 2);
        String action = splitMessage[0];
        String content = splitMessage[1];
        switch (action) {
            case "ENTER_RESP" -> {
                if (serverMessage.contains("5000")) {
                    System.out.println("User with this name already exists");
                    isLogged = false;
                } else if (serverMessage.contains("5001")) {
                    System.out.println("Username has an invalid format or length");
                    isLogged = false;
                }
            }
            case "BROADCAST" -> {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    Message message = objectMapper.readValue(content, Message.class);
                    System.out.printf("%s: %s \n", message.getUsername(), message.getMessage());
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
            case "JOINED" -> {
                ObjectMapper objectMapper = new ObjectMapper();
                Joined joined = objectMapper.readValue(content, Joined.class);
                System.out.println("User " + joined.getUsername() + " joined the chat.");
            }
            case "LEFT" -> {
                ObjectMapper objectMapper = new ObjectMapper();
                Left left = objectMapper.readValue(content, Left.class);
                System.out.println("User " + left.username() + " left the chat.");
            }
            case "LIST_RESP" -> {
                ObjectMapper objectMapper = new ObjectMapper();
                ListResp listResp = objectMapper.readValue(content, ListResp.class);
                System.out.print("Active users: ");
                for (String clientName : listResp.clients()) {
                    System.out.print(clientName + " ");
                }
                System.out.println();
            }
            case "PRIVATE_MSG" -> {
                ObjectMapper objectMapper = new ObjectMapper();
                PrivateMessage privateMessage = objectMapper.readValue(content, PrivateMessage.class);
                System.out.printf("%s whispered: %s%n",privateMessage.sender(),privateMessage.message());
            }
        }
    }

    private void showMenuOptions() {
        System.out.println("Type /quit to exit the program");
        System.out.println("Type /userList to get list of clients");
        System.out.println("Type /dm <user> <text> ");
        System.out.println("Type /4 ");
    }

    private void closeConnection() {
        try {
            if (clientSocket != null) clientSocket.close();
            if (sender != null) sender.close();
            if (receiver != null) receiver.close();
            if (input != null) input.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendMessageToServer(String text) {
        sender.println(text);
        sender.flush();
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.startConnection("localhost", 1234);
    }
}
