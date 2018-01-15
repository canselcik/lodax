package com.originblue.abstractds;

import java.math.BigDecimal;

public class LDTimedMovingAverageEntry implements Comparable<LDTimedMovingAverageEntry> {
    public final long timestamp;
    public final BigDecimal value;

    public LDTimedMovingAverageEntry(BigDecimal value) {
        this.timestamp = System.currentTimeMillis();
        this.value = value;
    }

    public LDTimedMovingAverageEntry(BigDecimal value, long timestamp) {
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    public int compareTo(LDTimedMovingAverageEntry o) {
        long res = o.timestamp - this.timestamp;
        if (res > Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        else if (res < Integer.MIN_VALUE)
            return Integer.MIN_VALUE;
        return (int)res;
    }
}
