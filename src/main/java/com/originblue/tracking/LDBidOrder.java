package com.originblue.tracking;

import java.math.BigDecimal;

public class LDBidOrder extends LDOrder implements Comparable<LDBidOrder> {
    public LDBidOrder(String id, BigDecimal price, BigDecimal size) {
        this(id, price, size, NDOrderKind.LIMIT);
    }

    public LDBidOrder(String id, BigDecimal price, BigDecimal size, NDOrderKind kind) {
        super(id, 'b', price, size, kind);
    }

    public int compareTo(LDBidOrder o) {
        return o.getPrice().compareTo(this.getPrice());
    }
}
