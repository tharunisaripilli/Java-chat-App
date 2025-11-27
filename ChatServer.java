import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

/**
 * Simple multi-client chat server (pure Java).
 * - Listens on a port
 * - Accepts clients who choose a unique username
 * - Broadcasts messages to all clients
 * - Supports private message: /pm <username> <message>
 * - Client exit: /quit
 */
public class ChatServer {
    private final int port;
    // username -> ClientHandler
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("ChatServer starting on port " + port + " ...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // handle client in separate thread
                new Thread(() -> handleNewClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleNewClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("WELCOME. Please enter a username:");
            String username = in.readLine();

            // simple username validation loop
            while (username == null || username.trim().isEmpty() || clients.containsKey(username.trim())) {
                if (username == null) {
                    socket.close();
                    return;
                }
                if (username.trim().isEmpty()) {
                    out.println("Username cannot be empty. Enter username:");
                } else {
                    out.println("Username taken. Choose another username:");
                }
                username = in.readLine();
            }
            username = username.trim();

            ClientHandler handler = new ClientHandler(username, socket, in, out);
            clients.put(username, handler);

            broadcast("SYSTEM", username + " has joined the chat. (" + clients.size() + " online)");

            new Thread(handler).start();

        } catch (IOException e) {
            System.err.println("Failed to setup client: " + e.getMessage());
        }
    }

    // Broadcast to all clients
    private void broadcast(String from, String message) {
        String line = from + ": " + message;
        for (ClientHandler ch : clients.values()) {
            ch.send(line);
        }
        System.out.println(line);
    }

    // Send private message
    private boolean sendPrivate(String from, String toUser, String message) {
        ClientHandler ch = clients.get(toUser);
        if (ch != null) {
            ch.send("[PM from " + from + "]: " + message);
            return true;
        }
        return false;
    }

    // Remove client
    private void removeClient(String username) {
        clients.remove(username);
        broadcast("SYSTEM", username + " has left the chat. (" + clients.size() + " online)");
    }

    // Inner class for per-client handling
    private class ClientHandler implements Runnable {
        private final String username;
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private volatile boolean running = true;

        ClientHandler(String username, Socket socket, BufferedReader in, PrintWriter out) {
            this.username = username;
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void send(String msg) {
            out.println(msg);
        }

        @Override
        public void run() {
            try {
                out.println("Welcome, " + username + "! Type /help for commands.");
                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("/quit")) {
                        out.println("Goodbye!");
                        break;
                    } else if (line.equalsIgnoreCase("/help")) {
                        out.println("Commands:\n"
                                + "/pm <username> <message>  - send private message\n"
                                + "/list                     - list online users\n"
                                + "/quit                     - exit chat\n"
                                + "/help                     - show this help");
                    } else if (line.equalsIgnoreCase("/list")) {
                        out.println("Online (" + clients.size() + "): " + String.join(", ", clients.keySet()));
                    } else if (line.startsWith("/pm ") || line.startsWith("/PM ")) {
                        // /pm user message
                        String[] parts = line.split("\\s+", 3);
                        if (parts.length < 3) {
                            out.println("Usage: /pm <username> <message>");
                        } else {
                            String to = parts[1];
                            String msg = parts[2];
                            boolean sent = sendPrivate(username, to, msg);
                            if (!sent) {
                                out.println("User '" + to + "' not found.");
                            } else {
                                out.println("[PM to " + to + "]: " + msg);
                            }
                        }
                    } else {
                        // broadcast
                        broadcast(username, line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error with " + username + ": " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                removeClient(username);
            }
        }
    }

    public static void main(String[] args) {
        int port = 12345;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        new ChatServer(port).start();
    }
}
