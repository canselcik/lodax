package com.originblue.tracking;

import java.math.BigDecimal;

public class LDAskOrder extends LDOrder implements Comparable<LDAskOrder> {
    public LDAskOrder(String id, BigDecimal price, BigDecimal size) {
        this(id, price, size, NDOrderKind.LIMIT);
    }

    public LDAskOrder(String id, BigDecimal price, BigDecimal size, NDOrderKind kind) {
        super(id, 's', price, size, kind);
    }

    public int compareTo(LDAskOrder o) {
        return this.getPrice().compareTo(o.getPrice());
    }
}
