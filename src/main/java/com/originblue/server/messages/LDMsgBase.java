package com.originblue.server.messages;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.originblue.tracking.LDConstants;

import java.math.BigDecimal;

public class LDMsgBase {
    @SerializedName("timestamp")
    public long timestamp;

    @SerializedName("message")
    public Object message;

    @SerializedName("error")
    public String error;

    public static LDMsgBase generateWithObject(Object o) {
        LDMsgBase m = new LDMsgBase();
        m.timestamp = System.currentTimeMillis();
        m.message = o;
        m.error = null;
        return m;
    }

    private static class LDMsgInternal {
        char side;
        char type;
        BigDecimal amount, price, bestBid, bestAsk;
    }

    public static LDMsgBase generateLTPMessage(BigDecimal ltp, BigDecimal bestBid, BigDecimal bestAsk) {
        LDMsgInternal m = new LDMsgInternal();
        m.type = 't';
        m.price = ltp;
        m.bestAsk = bestAsk;
        m.bestBid = bestBid;
        return generateWithObject(m);
    }

    public static LDMsgBase generateMarketOrderRecv(char side, BigDecimal amount) {
        LDMsgInternal m = new LDMsgInternal();
        m.amount = amount;
        m.side = side;
        m.type = 'm';
        return generateWithObject(m);
    }

    public static LDMsgBase generateStringMessage(String s) {
        LDMsgBase m = new LDMsgBase();
        m.timestamp = System.currentTimeMillis();
        m.message = s;
        m.error = null;
        return m;
    }

    public static LDMsgBase generateStringError(String errBody) {
        LDMsgBase m = new LDMsgBase();
        m.timestamp = System.currentTimeMillis();
        m.message = null;
        m.error = errBody;
        return m;
    }

    public static LDMsgBase generateACK() {
        LDMsgBase m = new LDMsgBase();
        m.timestamp = System.currentTimeMillis();
        m.message = "ack";
        m.error = null;
        return m;
    }
}
