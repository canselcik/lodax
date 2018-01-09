package com.originblue.fix;

import com.originblue.tracking.LDConstants;
import com.paritytrading.philadelphia.*;
import com.paritytrading.philadelphia.fix42.FIX42Enumerations;
import com.paritytrading.philadelphia.fix42.FIX42Tags;
import com.paritytrading.philadelphia.gdax.GDAX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.paritytrading.philadelphia.fix42.FIX42MsgTypes.*;
import static com.paritytrading.philadelphia.fix42.FIX42Tags.*;
import static com.paritytrading.philadelphia.gdax.GDAXTags.CancelOrdersOnDisconnect;
import static com.paritytrading.philadelphia.gdax.GDAXTags.Password;

public class LDFIXSession extends LDFIXMessageEnumerator implements FIXStatusListener, FIXMessageListener, Runnable {
    private static Logger logger = LoggerFactory.getLogger(LDFIXSession.class);
    private final int port;
    private final String secret, key, passphrase;
    private final InetAddress host;
    private CountDownLatch receivedLogon;
    private FIXSession session;
    private Thread thread;
    private SocketChannel channel;

    // By clientUuid
    private final HashMap<String, LDFIXOrder> knownOrders;

    // By serverUuid
    private final HashMap<String, LDFIXOrder> unknownOrders;

    private void queryAllOrders() throws IOException {
        queryOrderByIdSelector("*");
    }

    private void queryOrderByIdSelector(String selector) throws IOException {
        FIXMessage m = session.create();
        session.prepare(m, OrderStatusRequest);
        m.addField(OrderID).setString(selector);
        m.addField(Password).setString(passphrase);
        GDAX.sign(m, secret);
        session.send(m);
        // TODO: Set a flag here to expect execution report, and then parse it on receipt
        // TODO: Update your records with this execution report and keep a list of active knownOrders
    }


    public boolean submitFIXOrder(LDFIXOrder order, String tradePair) {
        if (order.getStatus() != LDFIXOrder.Status.DRAFT)
            return false;
        if (order.getPrice() == null || order.getSize() == null)
            return false;

        try {
            knownOrders.put(order.getClientOrderId(), order);
            submitLimitOrder(order.getClientOrderId(),
                    tradePair,
                    order.getSide(),
                    order.getPrice().doubleValue(),
                    order.getSize().doubleValue(),
                    LDConstants.TimeInForce.POSTONLY);
            order.setStatus(LDFIXOrder.Status.SENT);
            return true;
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
    }

    public boolean cancelFIXOrder(LDFIXOrder order, String tradePair, boolean byClientUuid, boolean byUuid) {
        if (!byUuid && !byClientUuid) {
            return false;
        }
        try {
            this.submitOrderCancelRequest(UUID.randomUUID().toString(), tradePair,
                    byClientUuid ? order.getClientOrderId() : null,
                    byUuid ? order.getOrderId() : null);
            return true;
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
    }

    // 0.0001 BTC is the minimum
    private String submitLimitOrder(String uuid, String symbol, LDConstants.OrderSide side, double price, double qty, LDConstants.TimeInForce tif) throws IOException {
        FIXMessage order = session.create();
        session.prepare(order, OrderSingle);
        order.addField(HandlInst).setChar('1');
        order.addField(ClOrdID).setString(uuid);
        order.addField(Symbol).setString(symbol);
        order.addField(Side).setChar(side.getValue());
        order.addField(OrderQty).setFloat(qty, 8);
        order.addField(Price).setFloat(price, 8);
        order.addField(OrdType).setChar(LDConstants.OrderType.LIMIT.getValue());
        order.addField(TimeInForce).setChar(tif.getValue());
        order.addField(Password).setString(passphrase);
        GDAX.sign(order, secret);
        session.send(order);
        return uuid;
    }

    private void submitOrderCancelRequest(String uuid, String symbol, String origUuid, String orderId) throws IOException {
        FIXMessage order = session.create();
        session.prepare(order, OrderCancelRequest);
        order.addField(Password).setString(passphrase);
        if (uuid != null)
            order.addField(ClOrdID).setString(uuid);
        order.addField(Symbol).setString(symbol);
        if (origUuid != null)
            order.addField(OrigClOrdID).setString(origUuid);
        if (orderId != null)
            order.addField(OrderID).setString(orderId);
        GDAX.sign(order, secret);
        session.send(order);
    }

    public LDFIXOrder getByClientOrderId(String clientUuid) {
        return knownOrders.get(clientUuid);
    }

    public LDFIXOrder getByOrderId(String uuid, boolean includeUnknowns) {
        LDFIXOrder o = knownOrders.get(uuid);
        if (o != null)
            return o;
        if (includeUnknowns)
            return unknownOrders.get(uuid);
        return null;
    }

    public LDFIXSession(String host, int port, String secret, String key, String passphrase) throws UnknownHostException {
        this.host = InetAddress.getByName(host);
        this.port = port;
        this.secret = secret;
        this.channel = null;
        this.key = key;
        this.receivedLogon = new CountDownLatch(1);
        this.passphrase = passphrase;
        this.knownOrders = new HashMap<String, LDFIXOrder>();
        this.unknownOrders = new HashMap<String, LDFIXOrder>();
    }

    public boolean connected() {
        if (session == null || channel == null)
            return false;
        if (!channel.isConnected())
            return false;
        if (receivedLogon.getCount() == 1)
            return false;
        return true;
    }

    public void waitComplete() throws InterruptedException {
        this.thread.join();
    }

    public boolean connect() throws IOException {
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(this.host, this.port));

        FIXConfig.Builder builder = new FIXConfig.Builder()
                .setVersion(FIXVersion.FIX_4_2)
                .setSenderCompID(key)
                .setTargetCompID("Coinbase")
                .setHeartBtInt(30);

        session = new FIXSession(channel, builder.build(), this, this);

        // Construct login message
        FIXMessage message = session.create();
        session.prepare(message, Logon);
        message.addField(EncryptMethod).setInt(FIX42Enumerations.EncryptMethodValues.None);
        message.addField(HeartBtInt).setInt(30);
        message.addField(Password).setString(passphrase);
        message.addField(CancelOrdersOnDisconnect).setString("Y");

        this.thread = new Thread(this);
        thread.start();

        // Sign and send the message
        GDAX.sign(message, secret);
        session.send(message);
        logger.info("Sent FIX logon message, blocking connect() for 10 seconds until login confirmed");

        boolean loggedIn = false;
        try {
            loggedIn = receivedLogon.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) { }

        return loggedIn;
    }

    public void run() {
        // Main loop
        while (true) {
            try {
                if (session.receive() < 0) {
                    logger.info("FIX Session ended.");
                    break;
                }
            } catch (IOException e) {
                logger.info("FIX Session lost.");
                e.printStackTrace();
                break;
            }
        }
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    public void message(FIXMessage message) {
        switch (message.getMsgType().asChar()) {
            case ExecutionReport:
                String clientOrderId = asString(message, FIX42Tags.ClOrdID, false, false, "ClOrdID");
                String orderId = asString(message, FIX42Tags.OrderID, false, false, "OrderID");
                LDFIXOrder ndfixOrder = knownOrders.get(clientOrderId);
                if (ndfixOrder == null) {
                    // TODO: Handle cancel acks well
                    // TODO: Handle unknown orders from past sessions well -- import them into the unknownOrders etc. after wrapping into LDFIXOrder
                    logger.error("Received unexpected FIX ExecReport for message with UUID " + clientOrderId);
                    return;
                }
                ndfixOrder.setOrderId(orderId);
                ndfixOrder.setStatus(LDFIXOrder.Status.ACKNOWLEDGED);
                break;
            default:
                break;
        }
    }

    public void close(FIXSession session, String message) {
        logger.warn("FIX Session Close: {}", message);
    }

    public void sequenceReset(FIXSession session) {
        logger.warn("FIX Received Sequence Reset");
    }

    public void tooLowMsgSeqNum(FIXSession session, long receivedMsgSeqNum,
                                long expectedMsgSeqNum) {
        logger.warn("FIX Received too low MsgSeqNum: received {}, expected {}",
                receivedMsgSeqNum, expectedMsgSeqNum);
    }

    public void heartbeatTimeout(FIXSession session) {
        logger.warn("Heartbeat timeout");
    }

    public void reject(FIXSession session, FIXMessage message) {
        logger.info("=== Received Reject ({}) ===", message.getMsgType().asString());
        asString(message, RefTagID, false,true, "RefTagID");
        asString(message, RefMsgType, false,true, "RefMsgType");
        asString(message, RefSeqNum, false,true, "RefSeqNum");
        asString(message, SessionRejectReason, false,true, "SessionRejectReason");
        asString(message, Text, false,true, "Text");
        logger.info("==== End of Reject ({}) ====", message.getMsgType().asString());
    }

    public void logon(FIXSession session, FIXMessage message) {
        logger.info("Received FIX Logon");
        receivedLogon.countDown();
    }

    public void logout(FIXSession session, FIXMessage message) {
        logger.info("Received FIX Logout, responding with it and then closing session.");
        try {
            sendLogout();
            session.close();
        } catch (IOException e) {
            logger.error("Failed to respond with FIX logout to a logout message. " +
                    "This likely won't have any consequences as we are closing down anyway.");
        }
        this.session = null;
        this.receivedLogon = new CountDownLatch(1);
    }

    public void sendLogout() throws IOException {
        FIXMessage m = session.create();
        session.prepare(m, Logout);
        m.addField(Password).setString(passphrase);
        GDAX.sign(m, secret);
        session.send(m);
        logger.debug("Sent FIX logout message");
    }
}
