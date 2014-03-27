/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.net;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.core.config.ConfigurationFactory;

/**
 * Listens for events over a socket connection.
 */
public class TCPSocketServer extends AbstractSocketServer implements Runnable {

    /**
     * Thread that processes the events.
     */
    private class SocketHandler extends Thread {
        private final LogEventInput logEventInput;

        private final InputStream inputStream;

        private boolean shutdown = false;

        public SocketHandler(final Socket socket, LogEventInput logEventInput) throws IOException {
            this.inputStream = new ObjectInputStream(socket.getInputStream());
            this.logEventInput = logEventInput;
        }

        @Override
        public void run() {
            boolean closed = false;
            try {
                try {
                    while (!shutdown) {
                        log(logEventInput.readLogEvent(inputStream));
                    }
                } catch (final EOFException e) {
                    closed = true;
                } catch (final OptionalDataException e) {
                    logger.error("OptionalDataException eof=" + e.eof + " length=" + e.length, e);
                } catch (final IOException e) {
                    logger.error("IOException encountered while reading from socket", e);
                }
                if (!closed) {
                    try {
                        inputStream.close();
                    } catch (final Exception ex) {
                        // Ignore the exception;
                    }
                }
            } finally {
                handlers.remove(Long.valueOf(getId()));
            }
        }

        public void shutdown() {
            this.shutdown = true;
            interrupt();
        }
    }

    /**
     * Creates a socket server that reads JSON log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static TCPSocketServer createJsonSocketServer(int port) throws IOException {
        return new TCPSocketServer(port, new JSONLogEventInput());
    }

    /**
     * Creates a socket server that reads serialized log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static TCPSocketServer createSerializedSocketServer(int port) throws IOException {
        return new TCPSocketServer(port, new SerializedLogEventInput());
    }

    /**
     * Creates a socket server that reads XML log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static TCPSocketServer createXmlSocketServer(int port) throws IOException {
        return new TCPSocketServer(port, new XMLLogEventInput());
    }

    /**
     * Main startup for the server.
     * 
     * @param args The command line arguments.
     * @throws Exception if an error occurs.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Incorrect number of arguments");
            printUsage();
            return;
        }
        final int port = Integer.parseInt(args[0]);
        if (port <= 0 || port >= MAX_PORT) {
            System.err.println("Invalid port number");
            printUsage();
            return;
        }
        if (args.length == 2 && args[1].length() > 0) {
            ConfigurationFactory.setConfigurationFactory(new ServerConfigurationFactory(args[1]));
        }
        final TCPSocketServer socketServer = TCPSocketServer.createSerializedSocketServer(port);
        ;
        final Thread serverThread = new Thread(socketServer);
        serverThread.start();
        final Charset enc = Charset.defaultCharset();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, enc));
        while (true) {
            final String line = reader.readLine();
            if (line == null || line.equalsIgnoreCase("Quit") || line.equalsIgnoreCase("Stop") || line.equalsIgnoreCase("Exit")) {
                socketServer.shutdown();
                serverThread.join();
                break;
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ServerSocket port configFilePath");
    }

    private final ConcurrentMap<Long, SocketHandler> handlers = new ConcurrentHashMap<Long, SocketHandler>();

    private final ServerSocket server;

    /**
     * Constructor.
     * 
     * @param port to listen.
     * @param logEventInput the log even input
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public TCPSocketServer(final int port, LogEventInput logEventInput) throws IOException {
        super(port, logEventInput);
        this.server = new ServerSocket(port);
    }

    /**
     * Accept incoming events and processes them.
     */
    @Override
    public void run() {
        while (isActive()) {
            try {
                // Accept incoming connections.
                final Socket clientSocket = server.accept();
                clientSocket.setSoLinger(true, 0);

                // accept() will block until a client connects to the server.
                // If execution reaches this point, then it means that a client
                // socket has been accepted.

                final SocketHandler handler = new SocketHandler(clientSocket, this.logEventInput);
                handlers.put(Long.valueOf(handler.getId()), handler);
                handler.start();
            } catch (final IOException ioe) {
                System.out.println("Exception encountered on accept. Ignoring. Stack Trace :");
                ioe.printStackTrace();
            }
        }
        for (final Map.Entry<Long, SocketHandler> entry : handlers.entrySet()) {
            final SocketHandler handler = entry.getValue();
            handler.shutdown();
            try {
                handler.join();
            } catch (final InterruptedException ie) {
                // Ignore the exception
            }
        }
    }

    /**
     * Shutdown the server.
     */
    public void shutdown() {
        setActive(false);
        Thread.currentThread().interrupt();
    }
}