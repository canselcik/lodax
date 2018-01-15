package com.originblue.abstractds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class LDTimedMovingAverage {
    private final int maxAgeMillis;
    private final LDTreap<LDTimedMovingAverageEntry> values;

    public LDTimedMovingAverage(int maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis <= 0 ? 1 : maxAgeMillis;
        this.values = new LDTreap<>();
    }

    public void add(BigDecimal num) {
        LDTimedMovingAverageEntry entry = new LDTimedMovingAverageEntry(num);
        this.values.insert(entry);
        LDTimedMovingAverageEntry worst = values.findWorst();
        while (worst != null && entry.timestamp - worst.timestamp >= maxAgeMillis) {
            this.values.remove(worst);
            worst = values.findWorst();
        }
    }

    public BigDecimal getAverage() {
        BigDecimal sum = BigDecimal.ZERO;
        if (values.isEmpty())
            return sum;

        List<LDTimedMovingAverageEntry> items = values.getItems();
        BigDecimal divisor = BigDecimal.valueOf(items.size());
        for (LDTimedMovingAverageEntry e : items) {
            sum = sum.add(e.value);
        }
        return sum.divide(divisor, 2, RoundingMode.HALF_UP);
    }
}
