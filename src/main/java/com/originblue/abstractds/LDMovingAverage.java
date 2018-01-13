package com.originblue.abstractds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.Queue;

public class LDMovingAverage {
    private final Queue<BigDecimal> window = new LinkedList<BigDecimal>();
    private final int period;
    private BigDecimal sum = BigDecimal.ZERO;

    public LDMovingAverage(int period) {
        if (period <= 0)
            period = 10;
        this.period = period;
    }

    public void add(BigDecimal num) {
        sum = sum.add(num);
        window.add(num);
        if (window.size() > period) {
            sum = sum.subtract(window.remove());
        }
    }

    public BigDecimal getAverage() {
        if (window.isEmpty()) return BigDecimal.ZERO; // technically the average is undefined
        BigDecimal divisor = BigDecimal.valueOf(window.size());
        return sum.divide(divisor, 2, RoundingMode.HALF_UP);
    }
}
