package org.pogonin.exception;

import lombok.Getter;

import java.nio.channels.SocketChannel;

@Getter
public class ClientCommunicationException extends RuntimeException {
    private final SocketChannel socketChannel;
    public ClientCommunicationException(String message, Exception ex, SocketChannel socketChannel) {
        super(message, ex);
        this.socketChannel = socketChannel;
    }
}
