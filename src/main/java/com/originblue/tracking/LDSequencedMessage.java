package com.originblue.tracking;

import com.google.gson.JsonObject;

import java.math.BigInteger;

public class LDSequencedMessage implements Comparable<LDSequencedMessage> {
    public final JsonObject message;
    public final BigInteger sequenceId;
    public final String type;

    public LDSequencedMessage(JsonObject message) {
        this.sequenceId = message.get("sequence").getAsBigInteger();
        this.message = message;
        this.type = message.get("type").getAsString();
    }

    public LDSequencedMessage(BigInteger sequenceId) {
        // Stub for cuttoff
        this.sequenceId = sequenceId;
        this.message = null;
        this.type = null;
    }
    public int compareTo(LDSequencedMessage o) {
        return this.sequenceId.compareTo(o.sequenceId);
    }
}
