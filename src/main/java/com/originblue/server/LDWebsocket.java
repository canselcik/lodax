package com.originblue.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.originblue.server.messages.LDMsgBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbitserver.*;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

public class LDWebsocket extends BaseWebSocketHandler {
    private Set<WebSocketConnection> connectionSet;
    private GsonBuilder builder;
    private Gson gson;
    private static Logger logger = LoggerFactory.getLogger(LDWebsocket.class);
    private final String host;
    private final int port;
    private WebServer webServer;

    public LDWebsocket(String host, int port) {
        this.host = host;
        this.connectionSet = new HashSet<>();
        this.port = port;
        this.builder = new GsonBuilder();
        this.gson = builder.create();
    }

    private boolean sendString(WebSocketConnection conn, String s) {
        try {
            byte[] b = s.getBytes("UTF-8");
            conn.send(b);
            return true;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int broadcast(LDMsgBase msg) {
        String serialized = gson.toJson(msg);
        int success = 0;
        if (serialized == null)
             return success;

        for (WebSocketConnection conn : connectionSet) {
            if (conn == null)
                continue;
            if (sendString(conn, serialized))
                success++;
        }
        return success;
    }

    public void onOpen(WebSocketConnection connection) {
        connectionSet.add(connection);
        sendString(connection, gson.toJson(LDMsgBase.generateStringMessage("welcome")));
    }

    public void onClose(WebSocketConnection connection) {
        connectionSet.remove(connection);
    }

    public void onMessage(WebSocketConnection connection, String message) {
        logger.debug("Received WS message from {}: {}", connection, message);
        sendString(connection, gson.toJson(LDMsgBase.generateACK()));
    }

    public void start() {
        webServer = WebServers.createWebServer(port).add("/api/v0/websocket", this);
        webServer.add((request, response, control) -> {
            InputStream res = LDWebsocket.class.getResourceAsStream("/index.html");
            response.status(200)
                    .content(res.readAllBytes())
                    .end();
        });
        webServer.start();
        logger.info("WS server running at " + webServer.getUri());
    }
}