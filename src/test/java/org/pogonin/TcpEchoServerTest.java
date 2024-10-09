package org.pogonin;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class TcpEchoServerTest {

    private TcpEchoServer tcpEchoServer;
    private ExecutorService serverExecutor;
    private final int port = 8084;

    @BeforeEach
    void setUp() throws Exception {
        tcpEchoServer = new TcpEchoServer(port);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> tcpEchoServer.run());
        Thread.sleep(300);
    }

    @AfterEach
    void tearDown() throws Exception {
        serverExecutor.shutdownNow();
        Thread.sleep(300);
    }

    @Test
    void testServerStartsAndAcceptsConnection() throws Exception {
        try (Socket clientSocket = new Socket("localhost", port)) {


            assertTrue(clientSocket.isConnected(), "The client must be connected to the server");
        }
    }

    @Test
    void testServerEchoesMessage() throws Exception {
        try (Socket clientSocket = new Socket("localhost", port)) {
            OutputStream out = clientSocket.getOutputStream();
            InputStream in = clientSocket.getInputStream();
            String expectedMessage = "Hello, Echo Server!";
            byte[] buffer = new byte[1024];


            out.write(expectedMessage.getBytes(StandardCharsets.UTF_8));
            out.flush();


            int bytesRead = in.read(buffer);
            assertTrue(bytesRead > 0, "The client should receive a response from the server");
            String echoedMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            assertEquals(expectedMessage, echoedMessage, "The echoed expectedMessage must match the sent expectedMessage");
        }
    }

    @Test
    void testServerWorksAfterClientDisconnection() throws Exception {
        Socket clientSocket = new Socket("localhost", port);


        clientSocket.close();
        Thread.sleep(500);


        assertFalse(serverExecutor.isTerminated(), "The server must continue to work after the client disconnects");
    }
}
