import java.io.*;
import java.net.*;

/**
 * Simple chat client (pure Java console).
 * - Connects to server
 * - Sends console input to server
 * - Prints messages from server asynchronously
 * - Commands: /pm, /list, /quit, /help
 */
public class ChatClient {
    private final String host;
    private final int port;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in))) {

            // Thread: print server messages
            Thread readerThread = new Thread(() -> {
                try {
                    String s;
                    while ((s = serverIn.readLine()) != null) {
                        System.out.println(s);
                    }
                } catch (IOException e) {
                    System.err.println("Disconnected from server.");
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // Read initial prompts from server and respond (username flow)
            // We'll simply forward user input lines to serverLines
            String userLine;
            while ((userLine = userIn.readLine()) != null) {
                serverOut.println(userLine);
                // if user types /quit locally, we exit client
                if (userLine.trim().equalsIgnoreCase("/quit")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Cannot connect to " + host + ":" + port + " -> " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        new ChatClient(host, port).start();
    }
}
