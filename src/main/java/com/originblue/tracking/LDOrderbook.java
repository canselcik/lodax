package com.originblue.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.originblue.abstractds.LDRBTree;
import com.originblue.abstractds.LDTreap;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.jglue.fluentjson.JsonBuilderFactory;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


// 1MB Max Size
@WebSocket(maxTextMessageSize = 1024 * 1024 * 1024)
public class LDOrderbook {
    // Customizable at ctor
    private final String productId, level;
    private final String key, secret, passphrase;

    private CountDownLatch closeLatch, unsubscribedLatch;
    private final JsonParser parser;
    private AtomicBoolean live;
    private AtomicLong lastWsMessageMillis;
    private static Logger logger = LoggerFactory.getLogger(LDOrderbook.class);

    // Global WS session
    private volatile Session session;
    private volatile WebSocketClient client;
    private volatile LDRBTree<LDSequencedMessage> messageQueue;

    // Orderbook
    private volatile LDTreap<LDAskOrder> asks;
    private volatile LDTreap<LDBidOrder> bids;

    // Last Information
    private AtomicReference<BigInteger> lastSequence;
    private AtomicReference<String> lastTradeId;
    private AtomicLong lastMessageTime;
    private AtomicReference<BigDecimal> lastBestBid, lastBestAsk, lastTradeSize, lastTradePrice;
    private AtomicReference<String> lastTradeSide;

    public Long millisSinceLastMessage() {
        return System.currentTimeMillis() - lastWsMessageMillis.get();
    }

    public BigInteger getLastSequenceNumber() {
        return lastSequence.get();
    }

    public LDOrderbook(String productId, String level, String secret, String key, String passphrase) {
        this.productId = productId;
        this.parser = new JsonParser();
        this.lastWsMessageMillis = new AtomicLong(0);
        this.level = level;
        this.messageQueue = new LDRBTree<LDSequencedMessage>();
        this.live = new AtomicBoolean(false);
        this.closeLatch = new CountDownLatch(1);
        this.unsubscribedLatch = new CountDownLatch(1);
        this.client = new WebSocketClient(new SslContextFactory());
        this.secret = secret;
        this.passphrase = passphrase;
        this.lastTradePrice = new AtomicReference<BigDecimal>(null);
        this.lastBestAsk = new AtomicReference<BigDecimal>(null);
        this.lastBestBid = new AtomicReference<BigDecimal>(null);
        this.lastTradeSize = new AtomicReference<BigDecimal>(null);
        this.lastTradeSide = new AtomicReference<String>(null);
        this.lastTradeId = new AtomicReference<String>(null);
        this.lastSequence = new AtomicReference<BigInteger>(null);
        this.lastMessageTime = new AtomicLong();
        this.key = key;
        this.asks = new LDTreap<LDAskOrder>();
        this.bids = new LDTreap<LDBidOrder>();
    }

    public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
        return this.closeLatch.await(duration, unit);
    }

    public void awaitClose() throws InterruptedException {
        this.closeLatch.await();
    }

    public Session getUnderlyingSession() {
        return this.session;
    }

    public boolean sync() throws Exception {
        client.start();
        Future<Session> wssf = client.connect(this, new URI("wss://ws-feed.gdax.com/"));
        this.session = wssf.get();

        // We need to wait for subscribe here...
        if (!unsubscribedLatch.await(5, TimeUnit.SECONDS)) {
            logger.error("Failed to receive the WS subscription message within 5 seconds");
            return false;
        }

        JsonObject snapshot = this.getSnapshot(LDConstants.TRADINGPAIR, 3);
        if (snapshot == null) {
            logger.error("Failed to get the orderbook snapshot");
            return false;
        }
        BigInteger loadedSequence = this.loadSnapshot(snapshot);
        if (loadedSequence == null) {
            logger.error("Failed to load the orderbook snapshot");
            return false;
        }
        this.live.set(true);
        messageQueue = new LDRBTree<LDSequencedMessage>();
        logger.info("Replay complete. Reset the message queue and went live.");
        return true;
    }

    private JsonObject getSnapshot(String pair, int level) {
        // TODO: This should be moved into the NDRestMetadataProvider
        try {
            URL obj = new URL("https://api.gdax.com/products/" + pair + "/book?level=" + level);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "GDAXDataSync (not polling)");

            int respCode = con.getResponseCode();
            if (respCode != HttpURLConnection.HTTP_OK) {
                logger.error("Received non-200 when getting orderbook snapshot (status: {})", respCode);
                return null;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return parser.parse(response.toString()).getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private BigInteger loadSnapshot(JsonObject snapshot) {
        JsonElement sequence = snapshot.get("sequence");
        if (sequence == null)
            return null;

        // Create the base orders
        BigInteger seq = sequence.getAsBigInteger();
        JsonArray bids = snapshot.get("bids").getAsJsonArray();
        for (int i = 0; i < bids.size(); i++) {
            JsonArray bid = bids.get(i).getAsJsonArray();
            LDBidOrder order = new LDBidOrder(bid.get(2).getAsString(),
                    bid.get(0).getAsBigDecimal(),
                    bid.get(1).getAsBigDecimal());
            this.bids.insert(order);
        }
        JsonArray asks = snapshot.get("asks").getAsJsonArray();
        for (int i = 0; i < asks.size(); i++) {
            JsonArray ask = asks.get(i).getAsJsonArray();
            LDAskOrder order = new LDAskOrder(ask.get(2).getAsString(),
                    ask.get(0).getAsBigDecimal(),
                    ask.get(1).getAsBigDecimal());
            this.asks.insert(order);
        }

        logger.info("Imported {} bids and {} asks from snapshot", bids.size(), asks.size());

        // Now we playback and then go live and reset the message queue
        LDSequencedMessage cutoff = new LDSequencedMessage(seq);
        List<LDSequencedMessage> messagesToReplay = messageQueue.getGreaterThan(cutoff, messageQueue.size());
        logger.info("Replaying from sequence id {}, {} messages to go...", seq, messagesToReplay.size());
        for (LDSequencedMessage replayTarget : messagesToReplay) {
            this.applySequencedMessage(replayTarget, true);
        }
        return seq;
    }

    public boolean isSubscribed() {
        return unsubscribedLatch.getCount() == 0;
    }

    public void stopSync() throws Exception {
        client.stop();
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        // TODO: session resumption
        logger.info("Websocket connection lost (lastSeqNum: {}, status: {}, reason: {})", lastSequence, statusCode, reason);
        this.session = null;
        this.closeLatch.countDown();
        this.live.set(false);

        logger.info("Attempting to reconnect");
        while (this.session == null) {
            this.client = new WebSocketClient(new SslContextFactory());
            this.unsubscribedLatch = new CountDownLatch(1);
            this.closeLatch = new CountDownLatch(1);
            try {
                if (!sync()) {
                    logger.error("Failed to reconnect");
                    this.session = null;
                    this.closeLatch.countDown();
                    this.unsubscribedLatch.countDown();
                    this.live.set(false);
                }
                else
                    logger.info("Connection established at seqNum: {}", lastSequence);
            } catch (Exception e) {
                e.printStackTrace();
                this.session = null;
                this.live.set(false);
                this.closeLatch.countDown();
            }
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        logger.info("Established WebSocket connection {}", session);
        try {
            String s = JsonBuilderFactory.buildObject()
                    .add("type", "subscribe")
                    .add("secret", secret)
                    .add("passphrase", passphrase)
                    .add("key", key)
                    .addArray("product_ids")
                    .add(productId)
                    .end()
                    .addArray("channels")
                    .add(level)
                    .addObject()
                    .add("name", "heartbeat")
                    .addArray("product_ids")
                    .add(productId)
                    .end()
                    .end()
                    .addObject()
                    .add("name", "ticker")
                    .addArray("product_ids")
                    .add(productId)
                    .end()
                    .end()
                    .end()
                    .toString();
            session.getRemote().sendString(s);
            this.session = session;
        } catch (IOException e) {
            logger.error("Failed to send SUBSCRIBE message to GDAX WebSocket endpoint: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        lastWsMessageMillis.set(System.currentTimeMillis());

        JsonObject parsed = parser.parse(msg).getAsJsonObject();
        String type = parsed.get("type").getAsString();
        if (type.equals("subscriptions")) {
            unsubscribedLatch.countDown();
            return;
        }

        LDSequencedMessage sequenced = new LDSequencedMessage(parsed);
        if (this.live.get())
            applySequencedMessage(sequenced, false);
        else
            this.messageQueue.insert(sequenced, sequenced.sequenceId.toString());
    }

    private synchronized void applySequencedMessage(LDSequencedMessage msg, boolean fromReplay) {
        this.lastSequence.set(msg.message.get("sequence").getAsBigInteger());
        this.lastMessageTime.set(ISODateTimeFormat.dateTime().parseMillis(msg.message.get("time").getAsString()));

        if (msg.type.equals("done")) {
            applyDoneType(msg.message, fromReplay);
        } else if (msg.type.equals("received")) {
            applyReceivedType(msg.message, fromReplay);
        } else if (msg.type.equals("open")) {
            applyOpenType(msg.message, fromReplay);
        } else if (msg.type.equals("heartbeat")) {
            applyHeartbeatType(msg.message, fromReplay);
        } else if (msg.type.equals("change")) {
            applyChangeType(msg.message, fromReplay);
        } else if (msg.type.equals("match")) {
            applyMatchType(msg.message, fromReplay);
        } else if (msg.type.equals("ticker")) {
            applyTickerType(msg.message, fromReplay);
        } else {
            logger.warn("Received unknown WebSocket message (from_replay: {}): {}", fromReplay, msg.message);
        }
    }

    private synchronized void applyTickerType(JsonObject parsed, boolean fromReplay) {
        /*
            {
                "type": "ticker",
                "trade_id": 20153558,
                "sequence": 3262786978,
                "time": "2017-09-02T17:05:49.250000Z",
                "product_id": "BTC-USD",
                "price": "4388.01000000",
                "side": "buy", // Taker side
                "last_size": "0.03000000",
                "best_bid": "4388",
                "best_ask": "4388.01"
            }
         */
        this.lastTradeId.set(parsed.get("trade_id").getAsString());
        this.lastBestBid.set(parsed.get("best_bid").getAsBigDecimal());
        this.lastBestAsk.set(parsed.get("best_ask").getAsBigDecimal());
        this.lastTradeSize.set(parsed.get("last_size").getAsBigDecimal());
        this.lastTradePrice.set(parsed.get("price").getAsBigDecimal());
        this.lastTradeSide.set(parsed.get("side").getAsString());
    }

    private synchronized void applyHeartbeatType(JsonObject parsed, boolean fromReplay) {
         /*
             "type",
             "last_trade_id",
             "product_id",
             "sequence",
             "time"
         */
        this.lastTradeId.set(parsed.get("last_trade_id").getAsString());
    }


    // A valid order has been received and is now active.
    // This message is emitted for every single valid order as soon as the
    // matching engine receives it whether it fills immediately or not.
    private synchronized void applyReceivedType(JsonObject parsed, boolean fromReplay) {
        /*
          {
            "type": "received",
            "time": "2014-11-07T08:19:27.028459Z",
            "product_id": "BTC-USD",
            "sequence": 10,
            "order_id": "d50ec984-77a8-460a-b958-66f114b0de9b",
            "size": "1.34",
            "price": "502.1",
            "side": "buy",
            "order_type": "limit"
          }
         */
        String side = parsed.get("side").getAsString();
        String orderId = parsed.get("order_id").getAsString();
        String orderType = parsed.get("order_type").getAsString();
        if (orderType.equals("market")) {
            BigDecimal f = BigDecimal.ZERO;
            BigDecimal s = BigDecimal.ZERO;
            JsonElement funds = parsed.get("funds");
            if (funds != null)
                f = funds.getAsBigDecimal();
            JsonElement size = parsed.get("size");
            if (size != null)
                s = size.getAsBigDecimal();
            // TODO: maybe we need to add market orders?
            logger.debug("Received {} MARKET ORDER {} (funds: {}, size: {})",
                    side.toUpperCase(), orderId, f.toString(), s.toString());
            return;
        }

        BigDecimal size = parsed.get("size").getAsBigDecimal();
        BigDecimal price = parsed.get("price").getAsBigDecimal();
        if (side.equals("sell")) {
            // Entering an ask order
            LDAskOrder order = new LDAskOrder(orderId, price, size);
            this.asks.insert(order);
        } else if (side.equals("buy")) {
            // Entering a bid order
            LDBidOrder order = new LDBidOrder(orderId, price, size);
            this.bids.insert(order);
        } else {
            logger.warn("Received a (type=received) WS message with unknown side (side={})", side);
        }
    }

    // Will always result in a change in orderbook
    // The order is now open on the order book. This message will only be sent for orders
    // which are not fully filled immediately. remaining_size will indicate how much of the order is
    // unfilled and going on the book.
    private synchronized void applyOpenType(JsonObject parsed, boolean fromReplay) {
        /*
          {
            "type": "open",
            "time": "2014-11-07T08:19:27.028459Z",
            "product_id": "BTC-USD",
            "sequence": 10,
            "order_id": "d50ec984-77a8-460a-b958-66f114b0de9b",
            "price": "200.2",
            "remaining_size": "1.00",
            "side": "sell"
          }
         */
        String orderId = parsed.get("order_id").getAsString();
        BigDecimal remaining_size = parsed.get("remaining_size").getAsBigDecimal();
        BigDecimal price = parsed.get("price").getAsBigDecimal();
        String side = parsed.get("side").getAsString();

        if (side.equals("sell")) {
            // Sell means ask. was an ask, we will alter asks
            LDAskOrder node = this.asks.constantLookup(orderId);
            if (node == null) {
                logger.debug("Got an open message for an order not in the books (orderId: {})", orderId);
                return;
            }
            node.setSize(remaining_size);
        } else if (side.equals("buy")) {
            LDBidOrder node = this.bids.constantLookup(orderId);
            if (node == null) {
                logger.debug("Got an open message for an order not in the books (orderId: {})", orderId);
                return;
            }
            node.setSize(remaining_size);
        } else {
            logger.warn("Received a (type=open) WS message with unknown side (side={})", side);
        }
    }

    // Wont always result in changing the order book.
    // (when calls are for received orders which are not yet on the order book)
    private synchronized void applyChangeType(JsonObject parsed, boolean fromReplay) {
        /*
          {
            "type": "change",
            "time": "2014-11-07T08:19:27.028459Z",
            "sequence": 80,
            "order_id": "ac928c66-ca53-498f-9c13-a110027a60e8",
            "product_id": "BTC-USD",
            "new_size": "5.23512",
            "old_size": "12.234412",
            "price": "400.23",
            "side": "sell"
          }
         */
        String orderId = parsed.get("order_id").getAsString();
        BigDecimal newSize = parsed.get("new_size").getAsBigDecimal();
        BigDecimal oldSize = parsed.get("old_size").getAsBigDecimal();
        BigDecimal price = parsed.get("price").getAsBigDecimal();
        String side = parsed.get("side").getAsString();

        if (price == null) {
            logger.warn("Got a change message with null price (market order?) (orderId: {})", orderId);
            return;
        }

        if (side.equals("sell")) {
            // Sell means ask. was an ask, we will alter asks
            LDAskOrder node = this.asks.constantLookup(orderId);
            if (node == null) {
                logger.debug("Got a change message for an order not in the books (orderId: {})", orderId);
                return;
            }
            BigDecimal orderSize = node.getSize();
            if (!orderSize.equals(oldSize)) {
                logger.warn("Order got a change message with incorrect oldSize (expected: {0}, got: {1})",
                        oldSize, orderSize);
            }
            node.setSize(newSize);
        } else if (side.equals("buy")) {
            LDBidOrder node = this.bids.constantLookup(orderId);
            if (node == null) {
                logger.debug("Got a change message for an order not in the books (orderId: {})", orderId);
                return;
            }
            BigDecimal orderSize = node.getSize();
            if (!orderSize.equals(oldSize)) {
                logger.warn("Order got a change message with incorrect oldSize (expected: {0}, got: {1})",
                        oldSize, orderSize);
            }
            node.setSize(newSize);
        } else {
            logger.warn("Received a (type=match) WS message with unknown side (side={})", side);
        }
    }

    // Will always result in a change in orderbook
    private synchronized void applyMatchType(JsonObject parsed, boolean fromReplay) {
        /*
          {
            "type": "match",
            "trade_id": 10,
            "sequence": 50,
            "maker_order_id": "ac928c66-ca53-498f-9c13-a110027a60e8",
            "taker_order_id": "132fb6ae-456b-4654-b4e0-d681ac05cea1",
            "time": "2014-11-07T08:19:27.028459Z",
            "product_id": "BTC-USD",
            "size": "5.23512",
            "price": "400.23",
            "side": "sell"
          }

          The side field indicates the maker order side. If the side is sell this
          indicates the maker was a sell order and the match is considered an
          up-tick. A buy side match is a down-tick.
         */

        // Taker was never on the books, maker's order will be altered
        String side = parsed.get("side").getAsString();
        String makerOrderId = parsed.get("maker_order_id").getAsString();
        String takerOrderId = parsed.get("taker_order_id").getAsString();
        String tradeId = parsed.get("trade_id").getAsString();

        BigDecimal size = parsed.get("size").getAsBigDecimal();
        BigDecimal price = parsed.get("price").getAsBigDecimal();

        this.lastTradePrice.set(price);
        this.lastTradeId.set(tradeId);

        if (side.equals("sell")) {
            // Maker was an ask, we will alter asks. Uptick.
            LDAskOrder node = this.asks.constantLookup(makerOrderId);
            if (node == null) {
                logger.debug("Received match message for an ask order that doesn't exist. (maker_order_id: {})", makerOrderId);
                return;
            }

            // TODO: Likely this check is not needed here as we would have received a `done` instead
            if (node.getSize().equals(size)) {
                // Consumes the entire resting order
                this.asks.remove(node);
            } else {
                // Partial fill
                node.setSize(node.getSize().subtract(size));
            }
        } else if (side.equals("buy")) {
            // Maker was a bid, we will alter bids. Downtick.
            LDBidOrder node = this.bids.constantLookup(makerOrderId);
            if (node == null) {
                logger.debug("Received match message for a bid order that doesn't exist. (maker_order_id: {})", makerOrderId);
                return;
            }

            if (node.getSize().equals(size)) {
                // Consumes the entire resting order
                this.bids.remove(node);
            } else {
                // Partial fill
                node.setSize(node.getSize().subtract(size));
            }
        } else {
            logger.warn("Received a (type=match) WS message with unknown side (side={})", side);
        }
    }

    // Wont always result in changing the order book.
    // (when calls are for received orders which are not yet on the order book)
    private synchronized void applyDoneType(JsonObject parsed, boolean fromReplay) {
        /*
          {
            "type": "done",
            "time": "2014-11-07T08:19:27.028459Z",
            "product_id": "BTC-USD",
            "sequence": 10,
            "price": "200.2",
            "order_id": "d50ec984-77a8-460a-b958-66f114b0de9b",
            "reason": "filled", // or "canceled"
            "side": "sell",
            "remaining_size": "0"
          }
        */
        String orderId = parsed.get("order_id").getAsString();
        String reason = parsed.get("reason").getAsString();
        String side = parsed.get("side").getAsString();

        BigDecimal remainingSize = BigDecimal.ZERO;
        if (!reason.equals("filled"))
            remainingSize = parsed.get("remaining_size").getAsBigDecimal();

        // Market order, was never on the books
        JsonElement priceElement = parsed.get("price");
        JsonElement fundsElement = parsed.get("funds");
        BigDecimal funds = BigDecimal.ZERO;
        BigDecimal price = BigDecimal.ZERO;
        if (priceElement != null) {
            price = priceElement.getAsBigDecimal();
        }
        if (fundsElement != null) {
            funds = fundsElement.getAsBigDecimal();
        }

        if (side.equals("sell")) {
            // Maker was an ask, we will alter asks. Uptick
            LDAskOrder node = this.asks.constantLookup(orderId);
            if (node == null) {
                logger.debug("Received SELL DONE for an unknown order_id {} (remaining_size: {}, funds: {}, price: {})",
                        orderId, remainingSize, funds, price);
            } else {
                // remainingSize of the order went unfulfilled
                this.asks.remove(node);
            }
        } else if (side.equals("buy")) {
            // Maker was a bid, we will alter bids. Downtick.
            LDBidOrder node = this.bids.constantLookup(orderId);
            if (node == null) {
                logger.debug("Received BUY DONE for an unknown order_id {} (remaining_size: {}, funds: {}, price: {})",
                        orderId, remainingSize, funds, price);
            } else {
                // remainingSize of the order went unfulfilled
                this.bids.remove(node);
            }
        } else {
            logger.warn("Received a (type=done) WS message with unknown side (side={})", side);
        }
    }

    public synchronized int getAskDepth() {
        return asks.size();
    }

    public synchronized int getBidDepth() {
        return bids.size();
    }

    public synchronized LDAskOrder getBestAsk() {
        LDAskOrder order = asks.findBest();
        // TODO: Look into this. We are definitely ahead of the feed in a positive fashion. However more investigation is needed to take advantage.
        // BigDecimal ba = lastBestAsk.get();
        // if (order != null && ba != null && order.getPrice().subtract(ba).abs().compareTo(LDConstants.MIN_PRICEDIFF) > 0)
        //     logger.warn("BestAsk from message history doesn't match orderbook. (orderbook: {}, msgHist: {})", order.getPrice(), ba);
        return order;
    }

    public synchronized LDBidOrder getBestBid() {
        LDBidOrder order = bids.findBest();
        // BigDecimal bb = lastBestBid.get();
        // if (order != null && bb != null && order.getPrice().subtract(bb).abs().compareTo(LDConstants.MIN_PRICEDIFF) > 0)
        //     logger.warn("BestBid from message history doesn't match orderbook. (orderbook: {}, msgHist: {})", order.getPrice(), bb);
        return order;
    }

    public synchronized BigDecimal getLastTradePrice() {
        return lastTradePrice.get();
    }

}












