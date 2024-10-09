package org.pogonin.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

@Data
@AllArgsConstructor
public final class Message {
    private final SocketAddress clientAddress;
    private final SocketChannel clientChannel;
    private final byte[] message;

    public Message(SocketAddress clientAddress, byte[] data) {
        this.clientAddress = clientAddress;
        this.message = data;
        clientChannel = null;
    }
}
