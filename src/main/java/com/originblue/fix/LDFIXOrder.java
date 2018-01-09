package com.originblue.fix;


import com.originblue.tracking.LDConstants;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LDFIXOrder {
    private final LDConstants.OrderSide side;
    private BigDecimal size;
    private BigDecimal price;
    private Status status;
    private final CountDownLatch ackLatch;

    public LDConstants.OrderSide getSide() {
        return side;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.ACKNOWLEDGED)
            ackLatch.countDown();
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    private final String clientOrderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    private String orderId;
    public enum Status {
        DRAFT,
        SENT,
        ACKNOWLEDGED,
        EXECUTED,
        FILLED,
        REJECTED,
        CANCELLED
    }

    // TODO: Only refers to limit orders
    public LDFIXOrder(LDConstants.OrderSide side) {
        this.status = Status.DRAFT;
        this.price = null;
        this.size = null;
        this.side = side;
        this.clientOrderId = UUID.randomUUID().toString();
        ackLatch = new CountDownLatch(1);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void await() {
        try {
            ackLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean await(long t, TimeUnit tu) {
        try {
            return ackLatch.await(t, tu);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setPrice(BigDecimal price) {
        this.price = price.setScale(2, BigDecimal.ROUND_HALF_EVEN);
    }

    public BigDecimal getSize() {
        return size;
    }

    public boolean setSize(BigDecimal size) {
        if (size.compareTo(BigDecimal.valueOf(0.0001)) < 0)
            return false;
        this.size = size;
        return true;
    }


}
