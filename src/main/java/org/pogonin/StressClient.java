package org.pogonin;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
public class StressClient {

    private static final int REQUEST_COUNTER_LIMIT = 1000000;
    private static final byte[] DATA = "Hello, Server!".getBytes();
    private static final String HOST = "localhost";
    private static final int PORT = 8081;

    public static void main(String[] args) {
        new StressClient().startClients();
    }

    private void startClients() {
        int numberOfClients = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfClients);

        for (int i = 0; i < numberOfClients; i++) {
            final int clientId = i + 1;
            executorService.submit(() -> {
                Thread.currentThread().setName("Client-" + clientId);
                send(HOST, PORT, clientId);
            });
        }

        executorService.shutdown();
    }

    private void send(String host, int port, int clientId) {
        try (AsynchronousSocketChannel clientChannel = AsynchronousSocketChannel.open()) {

            CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
            clientChannel.connect(new InetSocketAddress(host, port), null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    connectionFuture.complete(null);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("Client-" + clientId + " failed to connect: " + exc.getMessage());
                    connectionFuture.completeExceptionally(exc);
                }
            });


            connectionFuture.get();

            CompletableFuture<Void> operationFuture = new CompletableFuture<>();

            sendDataAsync(clientChannel, 0, operationFuture, clientId);

            operationFuture.get();

        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println("Client-" + clientId + " encountered an error: " + e.getMessage());
        }
    }

    private void sendDataAsync(AsynchronousSocketChannel clientChannel, int count, CompletableFuture<Void> operationFuture, int clientId) {
        if (count >= REQUEST_COUNTER_LIMIT) {
            // Отправляем "stop" после завершения отправки данных
            ByteBuffer stopBuffer = ByteBuffer.wrap("stop".getBytes());
            clientChannel.write(stopBuffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    // Начинаем чтение ответа
                    readResponseAsync(clientChannel, new StringBuilder(), operationFuture, clientId);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {System.err.println("Client-" + clientId + " error sending stop message: " + exc.getMessage());
                    operationFuture.completeExceptionally(exc);
                }
            });
            return;
        }

        ByteBuffer dataBuffer = ByteBuffer.wrap(DATA);

        clientChannel.write(dataBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                int sentCount = count + 1;
                if (sentCount % 1000 == 0) {
                    System.out.println(Thread.currentThread().getName() + " sent count: " + sentCount);
                }
                sendDataAsync(clientChannel, sentCount, operationFuture, clientId);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("Client-" + clientId + " error sending data: " + exc.getMessage());
                operationFuture.completeExceptionally(exc);
            }
        });
    }

    private void readResponseAsync(AsynchronousSocketChannel clientChannel, StringBuilder responseBuilder, CompletableFuture<Void> operationFuture, int clientId) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        clientChannel.read(buffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (result == -1) {
                    System.out.println(Thread.currentThread().getName() + " received response: " + responseBuilder.toString());
                    operationFuture.complete(null);
                    return;
                }
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                responseBuilder.append(new String(bytes));
                buffer.clear();

                // Продолжаем чтение
                clientChannel.read(buffer, null, this);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("Client-" + clientId + " error reading response: " + exc.getMessage());
                operationFuture.completeExceptionally(exc);
            }
        });
    }
}
