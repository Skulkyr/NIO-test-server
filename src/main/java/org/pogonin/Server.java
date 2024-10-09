package org.pogonin;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.pogonin.exception.ClientCommunicationException;
import org.pogonin.model.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server that handles client connections, receiving and transmitting messages using NIO.
 * <p>
 * This class manages network operations using non-blocking input/output (NIO),
 * allowing you to handle multiple client connections in a single thread.
 * It supports connecting and disconnecting clients, passing messages from clients, and sending messages to clients.
 * </p>
 *
 * <p>Author: Alexey Pogonin</p>
 */
@Getter
@Slf4j
public class Server {
    /**
     * Port on which the server will accept incoming connections.
     */
    private final int port;

    /**
     * IP address to which the server will be bound.
     * If {@code null}, the server will bind to all available addresses.
     */
    private final InetAddress addr;

    /**
     * Timeout for the {@link Selector#select()} method in milliseconds.
     */
    private final long TIME_OUT_MS = 5000;

    /**
     * An empty byte array used to indicate no data.
     */
    private final byte[] EMPTY_ARRAY = new byte[0];

    /**
     * Buffer for reading data from clients.
     */
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    /**
     * Map of connected clients, matching the client's address with its channel.
     */
    private final Map<SocketAddress, SocketChannel> clients = new HashMap<>();

    /**
     * Event queue for connecting new clients.
     */
    private final Queue<SocketAddress> connectedClientsEvent = new ConcurrentLinkedQueue<>();

    /**
     * Client disconnection event queue.
     */
    private final Queue<SocketAddress> disconnectedClientsEvent = new ConcurrentLinkedQueue<>();

    /**
     * Message queue to send to clients.
     */
    private final Queue<Message> messageForClients = new ArrayBlockingQueue<>(1000);

    /**
     * Queue of messages received from clients.
     */
    private final Queue<Message> messages = new ArrayBlockingQueue<>(1000);

    /**
     * Creates a new server bound to all available addresses on the specified port.
     *
     * @param port port to listen for incoming connections
     */
    public Server(int port) {
        this(null, port);
    }

    /**
     * Creates a new server bound to the specified address and port.
     *
     * @param addr IP address for server binding
     * @param port port to listen for incoming connections
     */
    public Server(InetAddress addr, int port) {
        this.port = port;
        this.addr = addr;
    }

    /**
     * Starts the server and begins processing connections and messages from clients.
     * <p>
     * This method opens a server channel, configures it in non-blocking mode,
     * binds to the specified address and port, and registers to accept new connections.
     * Then begins a loop to process selector events until the current thread is interrupted.
     * </p>
     */
    public void start() {

        try (var serverSocketChannel = ServerSocketChannel.open();
             var selector = Selector.open()) {

            serverSocketChannel.configureBlocking(false);
            var serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(addr, port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (!Thread.currentThread().isInterrupted())
                handleSelector(selector);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends data to the specified client.
     *
     * @param clientAddress address of the client to whom the data should be sent
     * @param data          byte array with data to send
     * @return {@code true} if the message was successfully added to the queue for sending,
     * {@code false} otherwise
     */
    public boolean send(SocketAddress clientAddress, byte[] data) {
        var result = messageForClients.offer(new Message(clientAddress, data));
        log.debug("Scheduled for sending to the client:{}", clientAddress);
        return result;
    }

    /**
     * Processes selector events and sends messages to clients.
     * <p>
     * The method waits for selector events to occur for {@link #TIME_OUT_MS} milliseconds,
     * then calls the I/O handler for each key and sends messages to clients.
     * </p>
     *
     * @param selector selector for handling I/O events
     */
    private void handleSelector(Selector selector) {
        try {
            selector.select(this::performIO, TIME_OUT_MS);
            sendMessageForClients();
        } catch (IOException ex) {
            log.error("Unexpected error:{}", ex.getMessage(), ex);
        } catch (ClientCommunicationException ex) {
            var clientAddress = getSocketAddress(ex.getSocketChannel());
            log.error("error in client communication:{}", clientAddress, ex);
            disconnect(clientAddress);
        }
    }

    /**
     * Performs I/O operations for the given selector key.
     *
     * @param key key of the selector on which to perform the operations
     */
    private void performIO(SelectionKey key) {
        if (key.isAcceptable()) {
            acceptConnection(key);
        } else if (key.isReadable()) {
            readFromClient(key);
        }
    }

    /**
     * Accepts a new client connection.
     * <p>
     * The method accepts a new connection, switches it to non-blocking mode,
     * registers for reading and writing, adds clients and connection event queue to the map.
     * </p>
     *
     * @param key selector key corresponding to the server channel
     */
    private void acceptConnection(SelectionKey key) {
        var serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            var clientSocketChannel = serverSocketChannel.accept();
            var selector = key.selector();
            log.debug(
                    "accept client connection, key:{}, selector:{}, clientSocketChannel:{}",
                    key,
                    selector,
                    clientSocketChannel);

            clientSocketChannel.configureBlocking(false);
            clientSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            var remoteAddress = clientSocketChannel.getRemoteAddress();
            clients.put(remoteAddress, clientSocketChannel);
            connectedClientsEvent.add(remoteAddress);
        } catch (IOException ex) {
            log.error("can't accept new client on:{}", key);
        }
    }

    /**
     * Reads data from the client.
     * <p>
     * The method reads data from the client channel.
     * If the number of bytes read is zero, the client is considered disconnected.
     * Otherwise, the message is added to the queue of received messages from clients.
     * </p>
     *
     * @param key selector key corresponding to the client channel
     */
    private void readFromClient(SelectionKey key) {
        var socketChannel = (SocketChannel) key.channel();
        log.debug("read from client:{}", socketChannel);

        var data = readRequest(socketChannel);
        if (data.length == 0) disconnect(getSocketAddress(socketChannel));
        else messages.add(new Message(getSocketAddress(socketChannel), socketChannel, data));
    }

    /**
     * Reads a request from a client from the specified channel.
     *
     * @param socketChannel client channel for reading data
     * @return an array of bytes containing the data read from the client
     * @throws ClientCommunicationException if an error occurs while reading data
     */
    private byte[] readRequest(SocketChannel socketChannel) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            buffer.clear();

            while (socketChannel.read(buffer) > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                baos.write(bytes);
                buffer.clear();
            }

            log.debug("Bytes read: {}", baos.size());
            if (baos.size() == 0) return EMPTY_ARRAY;
            return baos.toByteArray();

        } catch (Exception ex) {
            throw new ClientCommunicationException("Reading error", ex, socketChannel);
        }
    }

    /**
     * Sends messages from the {@link #messageForClients} queue to clients.
     */
    private void sendMessageForClients() {
        Message msg;
        while ((msg = messageForClients.poll()) != null) {
            log.debug("Try send message {}", msg);
            var client = clients.get(msg.getClientAddress());
            if (client == null) log.error("client {} not found", msg.getClientAddress());
            else write(client, msg.getMessage());
        }
    }

    /**
     * Writes a byte array to a channel
     *
     * @param clientChannel client channel for writing data
     * @throws ClientCommunicationException if an error occurs while writing data
     */
    private void write(SocketChannel clientChannel, byte[] data) {
        log.debug("write to client:{}, data.length:{}", clientChannel, data.length);
        buffer.put(data);
        buffer.flip();
        try {
            clientChannel.write(buffer);
            buffer.clear();
        } catch (IOException ex) {
            throw new ClientCommunicationException("Write to the client error", ex, clientChannel);
        }
    }

    /**
     * Disconnects the client and cleans up associated resources.
     *
     * @param clientAddress client address to disconnect
     */
    private void disconnect(SocketAddress clientAddress) {
        var clientChannel = clients.remove(clientAddress);
        if (clientChannel != null) {
            try {
                clientChannel.close();
                disconnectedClientsEvent.add(clientAddress);
            } catch (IOException ex) {
                log.error("can't disconnect client on:{}", clientAddress);
            }
        }
    }

    /**
     * Gets the socket address from the channel.
     *
     * @param socketChannel socket channel
     * @return socket address
     */
    private SocketAddress getSocketAddress(SocketChannel socketChannel) {
        try {
            return socketChannel.socket().getRemoteSocketAddress();
        } catch (Exception ex) {
            throw new ClientCommunicationException("get RemoteAddress error", ex, socketChannel);
        }
    }
}
