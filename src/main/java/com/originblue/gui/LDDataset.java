package com.originblue.gui;

import java.math.BigDecimal;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class LDDataset {
    private boolean pristine;
    public AtomicInteger maxHistory;
    public CopyOnWriteArrayList<BigDecimal> list;

    public LDDataset(AtomicInteger maxHistory) {
        pristine = true;
        list = new CopyOnWriteArrayList<BigDecimal>();
        list.add(BigDecimal.ZERO);
        this.maxHistory = maxHistory;
    }

    public void add(BigDecimal decimal) {
        if (pristine) {
            pristine = false;
            list.clear();
        }
        list.add(decimal);
        while (list.size() > maxHistory.get()) {
            list.remove((int) 0);
        }
    }
}
