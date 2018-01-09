package com.originblue.tracking;

import java.math.BigDecimal;
import java.util.ArrayList;


public class LDOrder {
    private volatile BigDecimal price, size;
    private volatile char side;
    private volatile String id;
    private volatile NDOrderKind kind;
    private volatile ArrayList<String> tags;

    public enum NDOrderKind {
        LIMIT, STOP, MARKET
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public ArrayList<String> getTags() {
        return tags;
    }


    public LDOrder(String id, char side, BigDecimal price, BigDecimal size) {
        this(id, side, price, size, NDOrderKind.LIMIT);
    }

    public LDOrder(String id, char side, BigDecimal price, BigDecimal size, NDOrderKind kind) {
        this.price = price.setScale(2, BigDecimal.ROUND_HALF_EVEN);
        this.size = size;
        this.side = side;
        this.id = id;
        this.kind = kind;
    }

    @Override
    public String toString() {
        return getOrderId();
    }

    public synchronized char getSide() {
        return this.side;
    }

    public synchronized String getOrderId() {
        return this.id;
    }

    public synchronized void setPrice(BigDecimal price) {
        this.price = price.setScale(2, BigDecimal.ROUND_HALF_EVEN);
    }

    public synchronized BigDecimal getPrice() {
        return this.price;
    }

    public synchronized void setSize(BigDecimal size) {
        this.size = size;
    }

    public synchronized BigDecimal getSize() {
        return this.size;
    }
}
