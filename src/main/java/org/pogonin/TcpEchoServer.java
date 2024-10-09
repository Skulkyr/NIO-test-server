package org.pogonin;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
/**
 * TCP Echo Server, which listens on the specified port, accepts messages from clients
 * and sends them back ("echo" effect).
 * <p>
 * This class uses a {@link Server} instance to manage network operations.
 * It handles client connection, disconnection and message receiving events
 * in separate threads.
 * </p>
 *
 * <p>Author: Alexey Pogonin</p>
 */
@Slf4j
public class TcpEchoServer {
    /**
     * Port on which the server will listen for incoming connections.
     */
    private final int port;
    /**
     * IP address to which the server will be bound.
     * If {@code null}, the server will bind to all available addresses.
     */
    private final InetAddress addr;

    /**
     * Creates a new {@code TcpEchoServer} that will listen on the specified port.
     *
     * @param port the port on which the server will accept incoming connections
     */
    public TcpEchoServer(int port) {
        this.port = port;
        addr = null;
    }

    /**
     * Creates a new {@code TcpEchoServer} that will listen on the specified port and address.
     *
     * @param port the port on which the server will accept incoming connections
     * @param addr IP address to which the server will be bound
     */
    public TcpEchoServer(int port, InetAddress addr) {
        this.port = port;
        this.addr = addr;
    }

    /**
     * Starts the server.
     * <p>
     * Initializes the {@link Server} instance and starts processing client connections
     * and messages using a thread pool of two threads.
     * </p>
     */
    public void run() {
        var server = new Server(port);

        try (var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(server::start);

            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        handleConnectedClientsEvent(server);
                        handleDisconnectedClientsEvent(server);
                        handleClientMessagesEvent(server);
                    } catch (Exception e) {
                        log.error("error:{}", e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Processes connection events for new clients.
     * <p>
     * Retrieves new client connections from the server queue and logs information
     * about each new connection.
     * </p>
     *
     * @param server instance of {@link Server} managing client connections
     */
    private void handleConnectedClientsEvent(Server server) {
        var newClient = server.getConnectedClientsEvent().poll();
        if (newClient != null) log.info("New client connected: {}", newClient);
    }

    /**
     * Handles client disconnect events.
     * <p>
     * Retrieves client disconnection facts from the server queue and logs information
     * about each outage.
     * </p>
     *
     * @param server instance of {@link Server} managing client connections
     */
    private void handleDisconnectedClientsEvent(Server server) {
        var disconnectedClient = server.getDisconnectedClientsEvent().poll();
        if (disconnectedClient != null) log.info("Client disconnected: {}", disconnectedClient);
    }

    /**
     * Processes incoming messages from clients.
     * <p>
     * Retrieves new messages from the server queue, logs them and sends them
     * back to the client (echo effect).
     * </p>
     *
     * @param server instance of {@link Server} that manages client connections and messages
     */
    private void handleClientMessagesEvent(Server server) {
        var messageFromClient = server.getMessages().poll();

        if (messageFromClient == null) return;

        String messageAsString = new String(messageFromClient.getMessage(), StandardCharsets.UTF_8);
        log.info("from:{}, message:{}", messageFromClient.getClientAddress(), messageAsString);
        boolean result = server.send(messageFromClient.getClientAddress(), messageFromClient.getMessage());
        log.info("echo message: {}", result);
    }
}
