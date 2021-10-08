import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.*;
import java.nio.channels.*;

import java.util.*;

public class Router {

    final static int BUF_LENGTH = 1024;

    public static void main(String[] args) {
    	
    	ServerSocketChannel atmServerChannel = null, bankServerChannel = null;

        Selector selector = null;

        if (args.length < 2) {
            System.out.println("Usage: java Router <Bank port> <ATM port>");
            System.exit(1);
        }
        
        try {
            selector = Selector.open();

            bankServerChannel = ServerSocketChannel.open();
            bankServerChannel.socket().bind(
                    new InetSocketAddress(Integer.parseInt(args[0])));

            atmServerChannel = ServerSocketChannel.open();
            atmServerChannel.socket().bind(
                    new InetSocketAddress(Integer.parseInt(args[1])));
        } catch (IOException e) {
            System.err.println("epic failure");
            System.exit(1);
        }

        SocketChannel bankChannel = null;
        try {
            bankChannel = bankServerChannel.accept();
            bankChannel.configureBlocking(false);
            System.out.println("Bank connected to the router.");
        } catch (IOException e) {
            System.err.println("Bank Socket Accept() failed.");
            System.exit(1);
        }

        SocketChannel atmChannel = null;
        try {
            atmChannel = atmServerChannel.accept();
            atmChannel.configureBlocking(false);
            System.out.println("ATM connected to the router.");
        } catch (IOException e) {
            System.err.println("ATM Socket Accept() failed.");
            System.exit(1);
        }

        try {
            bankChannel.register(selector, SelectionKey.OP_READ);
            atmChannel.register(selector, SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            System.err.println("Closed channel...");
            System.exit(1);
        }

        ByteBuffer bankBuf = ByteBuffer.allocate(BUF_LENGTH);
        ByteBuffer atmBuf = ByteBuffer.allocate(BUF_LENGTH);

        while (true) {
            int readyChannels = 0, i;

            try {
                readyChannels = selector.select();
            } catch (IOException e) {
                System.err.println("Select failed...");
                System.exit(1);
            }
            if (readyChannels == 0)
                continue;

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();

                try {
                    if (key.isReadable()) {
                        if (key.channel().equals(bankChannel)) {
                            bankChannel.read(atmBuf);
                            /* These ByteBuffer objects are so horrendous...
                             * They don't appear to have any "clear"
                             * functionality, requiring it to be done manually
                             */
                            for (i = atmBuf.position(); i < BUF_LENGTH; ++i)
                                atmBuf.put(i, (byte) 0x00);
                            atmBuf.clear();
                            atmChannel.write(atmBuf);
                            atmBuf.clear();
                        } else if (key.channel().equals(atmChannel)) {
                            atmChannel.read(bankBuf);
                            /* See comment above... */
                            for (i = bankBuf.position(); i < BUF_LENGTH; ++i)
                                bankBuf.put(i, (byte) 0x00);
                            bankBuf.clear();
                            bankChannel.write(bankBuf);
                            bankBuf.clear();
                        }
                    }
                } catch (IOException e) {
                    System.err.println("IO Exception!");
                }

                it.remove();
            }
        }
    }
}