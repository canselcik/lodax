package com.originblue.tracking;

import java.math.BigDecimal;

public class LDConstants {
    public final static BigDecimal MIN_PRICEDIFF = BigDecimal.valueOf(0.01);
    public static final String PRIMARY_CRYPTO = "BTC";
    public static final String PRIMARY_FIAT = "USD";
    public static String TRADINGPAIR = PRIMARY_CRYPTO + "-" + PRIMARY_FIAT;

    public enum TimeInForce {
        GOODTILLCANCEL('1'), IMMEDIATEORCANCEL('3'), FILLORKILL('4'), POSTONLY('P');
        private final char value;

        TimeInForce(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }

        public static TimeInForce derive(char c) {
            if (c == GOODTILLCANCEL.getValue())
                return GOODTILLCANCEL;
            if (c == IMMEDIATEORCANCEL.getValue())
                return IMMEDIATEORCANCEL;
            if (c == FILLORKILL.getValue())
                return FILLORKILL;
            if (c == POSTONLY.getValue())
                return POSTONLY;
            return null;
        }
    }
    public enum OrderSide {
        BUY('1'), SELL('2');
        private final char value;

        OrderSide(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }
    }
    public enum OrderType {
        MARKET('1'), LIMIT('2'), STOP_MARKET('3');
        private final char value;

        OrderType(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }
    }
}
