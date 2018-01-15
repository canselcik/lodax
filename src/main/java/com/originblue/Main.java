package com.originblue;

import com.originblue.fix.LDFIXSession;
import com.originblue.gui.LDConfigurationUI;
import com.originblue.gui.LDGUI;
import com.originblue.rest.LDRestMetadataProvider;
import com.originblue.tracking.LDConstants;
import com.originblue.tracking.LDOrderbook;
import org.knowm.xchart.XYSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String GDAX_FIX_HOSTNAME = "192.168.1.155";
    private static final int GDAX_FIX_PORT = 4198;

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        LDFIXSession fixSession = null;
        LDRestMetadataProvider metaProvider = null;
        LDOrderbook orderbook;

        String secret = System.getenv("GDAX_SECRET");
        String key = System.getenv("GDAX_KEY");
        String passphrase = System.getenv("GDAX_PASSPHRASE");
        if (secret != null && key != null && passphrase != null) {
            logger.info("GDAX_SECRET, GDAX_KEY and GDAX_PASSPHRASE env variables are set. These values will be used and they won't be stored.");
        }
        else {
            Properties props = LDConfigurationUI.getProperties();
            if (!props.containsKey("GDAX_KEY") || !props.containsKey("GDAX_PASSPHRASE") || !props.containsKey("GDAX_SECRET")) {
                LDConfigurationUI configUI = new LDConfigurationUI();
                props = configUI.prompt();
            }
            secret = props.getProperty("GDAX_SECRET");
            key = props.getProperty("GDAX_KEY");
            passphrase = props.getProperty("GDAX_PASSPHRASE");
        }

        if (secret != null && key != null && passphrase != null) {
            secret = secret.trim();
            key = key.trim();
            passphrase = passphrase.trim();
        }
        else {
            logger.warn("Running without API keys. Trading features will be disabled. Passive monitoring is supported.");
        }

        orderbook = new LDOrderbook(LDConstants.TRADINGPAIR, "full", secret, key, passphrase);
        orderbook.setMarketOrderDisplayPanel();
        orderbook.sync();

        if (secret != null && key != null && passphrase != null) {
            metaProvider = new LDRestMetadataProvider(secret, key, passphrase);
            metaProvider.init(1100);

            fixSession = new LDFIXSession(GDAX_FIX_HOSTNAME, GDAX_FIX_PORT, secret, key, passphrase);
            fixSession.connect();
        }

        // TODO: Visualize market depth at price points
        LDGUI graph = new LDGUI(fixSession, orderbook, metaProvider);
        graph.createDataset("bestBid", XYSeries.XYSeriesRenderStyle.Area);
        graph.createDataset("bestAsk", XYSeries.XYSeriesRenderStyle.ReverseArea);
        graph.createDataset("lastPrice", XYSeries.XYSeriesRenderStyle.Line);
        graph.display();

        if (fixSession != null) {
            graph.setFixStatus(true);
        }
        boolean wsDisconnected = false;
        BigDecimal bb, ba;
        LDRestMetadataProvider.NDAccount btcAcc = null;
        LDRestMetadataProvider.NDAccount usdAcc = null;
        while (!wsDisconnected) {
            // 40ms await here means ~25 FPS data throughput
            wsDisconnected = orderbook.awaitClose(40, TimeUnit.MILLISECONDS);
            BigDecimal lp = orderbook.getLastTradePrice();
            bb = orderbook.getBestBid().getPrice();
            ba = orderbook.getBestAsk().getPrice();
            if (lp != null) {
                if (bb.subtract(lp).abs().compareTo(BigDecimal.valueOf(100)) > 0) {
                    logger.error("BestBidAnomaly found at price ${} while (lastTradePrice=${})", bb, lp);
                }
                if (ba.subtract(lp).abs().compareTo(BigDecimal.valueOf(100)) > 0) {
                    logger.error("BestAskAnomaly found at price ${} while (lastTradePrice=${})", bb, lp);
                }
                graph.acquireWriteLock();
                graph.pushData("lastPrice", lp);
                graph.pushData("bestBid", bb);
                graph.pushData("bestAsk", ba);
                graph.releaseWriteLock();
                if (usdAcc == null)
                    usdAcc = metaProvider.getAccountByCurrency(LDConstants.PRIMARY_FIAT);
                else
                    graph.setUsdBalance(usdAcc.balance, usdAcc.hold);

                if (btcAcc == null)
                    btcAcc = metaProvider.getAccountByCurrency(LDConstants.PRIMARY_CRYPTO);
                else
                    graph.setBtcBalance(btcAcc.balance, btcAcc.hold);

                graph.setWsStatus(orderbook.millisSinceLastMessage());
                graph.setBestBid(bb);
                graph.setBestAsk(ba);
                graph.setLastTradePrice(lp);
                graph.setFixStatus(fixSession != null && fixSession.connected());
            }
        }

        graph.setWsStatus(BigDecimal.ZERO);
        if (fixSession != null) {
            fixSession.sendLogout();
            fixSession.waitComplete();
        }
    }
}
