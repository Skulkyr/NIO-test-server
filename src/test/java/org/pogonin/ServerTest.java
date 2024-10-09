package org.pogonin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pogonin.model.Message;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class ServerTest {
    private Server server;
    private ExecutorService serverExecutor;
    private final int port = 8084;


    @BeforeEach
    void setUp() throws Exception {
         server = new Server(null, port);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> server.start());
        Thread.sleep(300);
    }

    @AfterEach
    void tearDown() throws Exception {
        serverExecutor.shutdownNow();
        Thread.sleep(300);
    }

    @Test
    void testServerAcceptsConnection() throws Exception {
        try (Socket clientSocket = new Socket("localhost", port)) {


            assertTrue(clientSocket.isConnected());
        }
    }

    @Test
    void testServerReceivesMessage() throws Exception {
        try (Socket clientSocket = new Socket("localhost", port)) {
            OutputStream out = clientSocket.getOutputStream();
            String message = "Hello, Server!";


            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(500);


            Message receivedMsg = server.getMessages().poll();
            assertNotNull(receivedMsg, "The server should have received the message");
            assertEquals(message, new String(receivedMsg.getMessage(), StandardCharsets.UTF_8),
                    "The message received must match the message sent");
        }
    }


    @Test
    void testServerSendsMessage() throws Exception {
        try (Socket clientSocket = new Socket("localhost", port)) {
            SocketAddress clientAddress = clientSocket.getLocalSocketAddress();
            InputStream in = clientSocket.getInputStream();
            String expectedMessage = "Hello, Client!";
            byte[] buffer = new byte[1024];


            boolean sendResult = server.send(clientAddress, expectedMessage.getBytes(StandardCharsets.UTF_8));
            Thread.sleep(100);


            assertTrue(sendResult, "The server should have scheduled the message to be sent");
            int bytesRead = in.read(buffer);
            assertTrue(bytesRead > 0, "The client should have received the data");
            String actualMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            assertEquals(expectedMessage, actualMessage, "The message received must match the one sent by the server");
        }
    }

    @Test
    void testServerHandlesClientDisconnection() throws Exception {
        Socket clientSocket = new Socket("localhost", port);
        SocketAddress clientAddress = clientSocket.getLocalSocketAddress();


        clientSocket.close();
        Thread.sleep(100);


        assertTrue(server.getDisconnectedClientsEvent().contains(clientAddress), "The server should have logged the client disconnection");
    }


    @Test
    void testServerHandlesMultipleClients() throws Exception {
        try (Socket clientSocket1 = new Socket("localhost", port);
             Socket clientSocket2 = new Socket("localhost", port)) {
            OutputStream out1 = clientSocket1.getOutputStream();
            OutputStream out2 = clientSocket2.getOutputStream();
            String message1 = "Message from Client 1";
            String message2 = "Message from Client 2";


            out1.write(message1.getBytes(StandardCharsets.UTF_8));
            out1.flush();
            out2.write(message2.getBytes(StandardCharsets.UTF_8));
            out2.flush();
            Thread.sleep(500);


            Message receivedMsg1 = server.getMessages().poll();
            Message receivedMsg2 = server.getMessages().poll();
            assertNotNull(receivedMsg1, "The server should have received a message from client 1");
            assertNotNull(receivedMsg2, "The server should have received a message from client 2");
            String receivedData1 = new String(receivedMsg1.getMessage(), StandardCharsets.UTF_8);
            String receivedData2 = new String(receivedMsg2.getMessage(), StandardCharsets.UTF_8);
            assertTrue(receivedData1.equals(message1) || receivedData1.equals(message2), "The received message must match one of the sent ones");
            assertTrue(receivedData2.equals(message1) || receivedData2.equals(message2), "The received message must match one of the sent ones");
            assertNotEquals(receivedData1, receivedData2, "Messages must be from different clients");
        }
    }


}
