package com.originblue.gui;

import com.originblue.fix.LDFIXOrder;
import com.originblue.fix.LDFIXSession;
import com.originblue.rest.LDRestMetadataProvider;
import com.originblue.tracking.LDConstants;
import com.originblue.tracking.LDOrderbook;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.originblue.tracking.LDConstants.MIN_PRICEDIFF;
import static com.originblue.tracking.LDConstants.PRIMARY_CRYPTO;
import static com.originblue.tracking.LDConstants.TRADINGPAIR;


public class LDGUI {
    private static Logger logger = LoggerFactory.getLogger(LDGUI.class);
    private final LDRestMetadataProvider metaProvider;
    private final LDFIXSession fixSession;
    private final LDOrderbook orderbook;
    private final Stack<LDFIXOrder> orderStack;
    private final Stack<LDFIXOrder> cancelledOrderStack;
    private JButton btnLogout;
    private JFrame frmNodaxTradingUi;
    private JPanel panelTop;
    private JPanel panelMiddle;
    private JPanel panelBottom;
    private final AtomicInteger maxHistory;
    private Semaphore changing;
    private Map<String, LDDataset> datasets;
    private XYChart chart;
    private LDChartPanel graphPanel;
    private JPanel panel;
    private JPanel panel_1;
    private JLabel lblNewLabel;
    private JLabel lblBalala;
    private JLabel lblUSDBalance;
    private JLabel lblUSDHold;
    private JLabel lblBTCHold;
    private JLabel lblBTCBalance;
    private JLabel lblWssT;
    private JLabel lblFix;
    private JLabel lblFFAP;
    private JLabel lblWs;
    private JTable tblOrders;
    private JTextField txtBTCAmount;
    private JLabel lblBestAsk;
    private JLabel lblLastTrade;
    private JLabel lblBestBid;
    private JButton btnBuy;
    private JButton btnCancel;
    private JButton btnSell;
    private DefaultTableModel tableModel;

    public boolean acquireWriteLock() {
        try {
            changing.acquire();
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void releaseWriteLock() {
        changing.release();
    }

    public boolean createDataset(String datasetName, XYSeries.XYSeriesRenderStyle renderStyle) {
        LDDataset ds = new LDDataset(maxHistory);
        datasets.put(datasetName, ds);
        chart.addSeries(datasetName, null, ds.list);

        XYSeries xySeries = chart.getSeriesMap().get(datasetName);
        if (xySeries == null)
            return false;
        xySeries.setXYSeriesRenderStyle(renderStyle);
        return true;
    }

    public void display() {
        TimerTask chartUpdaterTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    changing.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (Map.Entry<String, LDDataset> entry : datasets.entrySet()) {
                    chart.updateXYSeries(entry.getKey(), null, entry.getValue().list, null);
                }
                changing.release();
                graphPanel.updateUI();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(chartUpdaterTask, 0, 40);

        frmNodaxTradingUi.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {
            }

            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                    maxHistory.set((int) ((double) maxHistory.get() * 2.0));
                } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                    maxHistory.set((int) ((double) maxHistory.get() * 0.5));
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    final int histSize = maxHistory.get();
                    maxHistory.set(1);
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e1) {
                            }
                            maxHistory.set(histSize);
                        }
                    }).start();
                }
            }

            public void keyReleased(KeyEvent e) {

            }
        });

        panelMiddle.add(graphPanel);
        frmNodaxTradingUi.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frmNodaxTradingUi.setVisible(true);

        KeyEventDispatcher keyEventDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_RELEASED)
                return false;
            BigDecimal amount = new BigDecimal(txtBTCAmount.getText());
            switch (e.getKeyCode()) {
                case KeyEvent.VK_PAGE_DOWN:
                    lightItUpStrategy(LDConstants.OrderSide.BUY, amount);
                    break;
                case KeyEvent.VK_PAGE_UP:
                    lightItUpStrategy(LDConstants.OrderSide.SELL, amount);
                    break;
                case KeyEvent.VK_B:
                    buyInvoked();
                    break;
                case KeyEvent.VK_S:
                    sellInvoked();
                    break;
                case KeyEvent.VK_C:
                    cancelInvoked();
                    break;
            }
            return true;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
        frmNodaxTradingUi.setVisible(true);
    }

    private AtomicBoolean strategyRunning;
    private void cancelInvoked() {
        strategyRunning.set(false);
        if (orderStack.empty()) {
            return;
        }
        tableModel.removeRow(0);

        LDFIXOrder lastOrder = orderStack.pop();
        boolean ok = fixSession.cancelFIXOrder(lastOrder, LDConstants.TRADINGPAIR,false, true);
        if (ok) {
            cancelledOrderStack.push(lastOrder);
        }
        else {
            logger.error("Failed to cancel FIXOrder (clientOrderId:{})", lastOrder.getClientOrderId());
        }
    }

    // TODO: Push it when it is very thin strategy
    public BigDecimal determineCompetitivePrice(LDConstants.OrderSide side, BigDecimal bbp, BigDecimal ltp, BigDecimal bap) {
        if (side == LDConstants.OrderSide.BUY) {
            if (ltp.subtract(bbp).compareTo(MIN_PRICEDIFF) > 0)
                return bbp.add(MIN_PRICEDIFF);
            else
                return bbp;
        }
        else if (side == LDConstants.OrderSide.SELL) {
            if (bap.subtract(ltp).compareTo(MIN_PRICEDIFF) > 0)
                return bap.subtract(MIN_PRICEDIFF);
            else
                return bap;
        }
        return null;
    }

    // Light it up strategy... Lights up the orderbook
    public void lightItUpStrategy(final LDConstants.OrderSide side, final BigDecimal amount) {
        new Thread(new Runnable() {
            public void run() {
                while(true) {
                    Queue<LDFIXOrder> orders = new LinkedList<LDFIXOrder>();
                    strategyRunning.set(true);
                    for (int i = 0; i < 500; i++) {
                        if (!strategyRunning.get())
                            break;
                        // int offset = ThreadLocalRandom.current().nextInt(0, 11);
                        int offset = 1 + (i % 30);
                        LDFIXOrder order = new LDFIXOrder(side);
                        BigDecimal bbp = orderbook.getBestBid().getPrice();
                        BigDecimal bap = orderbook.getBestAsk().getPrice();

                        BigDecimal price;
                        if (side == LDConstants.OrderSide.SELL) {
                            price = bap.add(MIN_PRICEDIFF.multiply(BigDecimal.valueOf(offset)));
                        } else {
                            price = bbp.subtract(MIN_PRICEDIFF.multiply(BigDecimal.valueOf(offset)));
                        }
                        order.setPrice(price);
                        order.setSize(amount);
                        fixSession.submitFIXOrder(order, TRADINGPAIR);
                        try {
                            Thread.sleep(35);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (!orders.isEmpty()) {
                            fixSession.cancelFIXOrder(orders.remove(), TRADINGPAIR, false, true);
                        }
                        orders.add(order);
                        try {
                            Thread.sleep(35);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    while (!orders.isEmpty()) {
                        fixSession.cancelFIXOrder(orders.remove(), TRADINGPAIR, true, true);
                        try {
                            Thread.sleep(65);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();

    }


    // TODO: Wallseeker strategy -- seek for large walls that would support the desired position and place order above/below it
    public void rapidFireStrategy(final LDConstants.OrderSide side, final BigDecimal amount) {
        new Thread(new Runnable() {
            public void run() {
                Queue<LDFIXOrder> orders = new LinkedList<LDFIXOrder>();
                for (int i = 0; i < 500; i++) {
                    LDFIXOrder order = defaultStrategyRunner(side, amount, false);
                    try {
                        Thread.sleep(77);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(!orders.isEmpty()) {
                        fixSession.cancelFIXOrder(orders.remove(), TRADINGPAIR, false, true);
                    }
                    orders.add(order);
                    try {
                        Thread.sleep(70);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (!orders.isEmpty()) {
                    fixSession.cancelFIXOrder(orders.remove(), TRADINGPAIR,false, true);
                }
            }
        }).start();

    }

    public boolean pushData(String datasetName, BigDecimal dataPoint) {
        LDDataset dataset = datasets.get(datasetName);
        if (dataset == null) {
            return false;
        }
        dataset.add(dataPoint);
        return true;
    }

    private void defaultStrategy(final LDConstants.OrderSide side, final BigDecimal amount) {
        new Thread(new Runnable() {
            public void run() {
                LDFIXOrder order = defaultStrategyRunner(side, amount, true);

                // TODO: Invoke later on GUI thread
                orderStack.push(order);
                tableModel.insertRow(0, new Object[]{order.getSide(), order.getSize(), order.getPrice()});
            }
        }).start();
    }

    private LDFIXOrder defaultStrategyRunner(LDConstants.OrderSide side, BigDecimal amount, boolean await) {
        LDFIXOrder order = new LDFIXOrder(side);
        BigDecimal bbp = orderbook.getBestBid().getPrice();
        BigDecimal bap = orderbook.getBestAsk().getPrice();
        BigDecimal ltp = orderbook.getLastTradePrice();

        order.setPrice(determineCompetitivePrice(side, bbp, ltp, bap));
        order.setSize(amount);
        fixSession.submitFIXOrder(order, TRADINGPAIR);

        if (await)
            order.await(2, TimeUnit.SECONDS);

        return order;
    }

    private static final DecimalFormat formatter = new DecimalFormat("0.00");
    public LDGUI(LDFIXSession fixSession, LDOrderbook orderbook, LDRestMetadataProvider metaProvider) {
        initialize();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        this.strategyRunning = new AtomicBoolean(false);
        this.metaProvider = metaProvider;
        this.frmNodaxTradingUi.setTitle("NoDax Trading UI");
        maxHistory = new AtomicInteger(3000);
        changing = new Semaphore(1);
        datasets = new HashMap<String, LDDataset>();
        chart = new XYChart(200, 200);
        chart.setTitle("Live Data");
        chart.setXAxisTitle("Time");
        chart.setYAxisTitle("Data");
        chart.getStyler().setMarkerSize(0);

        panelMiddle = new JPanel();
        graphPanel = new LDChartPanel(chart);
        this.panelMiddle.setPreferredSize(new Dimension(640, 480));
        this.frmNodaxTradingUi.getContentPane().add(panelMiddle, BorderLayout.CENTER);
        this.panelMiddle.setLayout(new CardLayout(0, 0));

        this.clearPriceFields();
        this.fixSession = fixSession;
        this.orderbook = orderbook;
        orderStack = new Stack<LDFIXOrder>();
        cancelledOrderStack = new Stack<LDFIXOrder>();
    }


    public void setBestBid(BigDecimal decimal) {
        String currText = lblBestBid.getText();
        Color newColor = Color.GRAY;
        if (currText.startsWith("$")) {
            BigDecimal delta = decimal.subtract(new BigDecimal(currText.substring(1)));
            int compres = delta.compareTo(BigDecimal.ZERO);
            if (compres > 0)
                newColor = Color.GREEN;
            else if (compres < 0)
                newColor = Color.RED;
            else
                newColor = Color.BLACK;
        }
        if (newColor != Color.BLACK)
            lblBestBid.setForeground(newColor);
        lblBestBid.setText("$" + formatter.format(decimal));
    }

    public void setBestAsk(BigDecimal decimal) {
        String currText = lblBestAsk.getText();
        Color newColor = Color.GRAY;
        if (currText.startsWith("$")) {
            BigDecimal delta = decimal.subtract(new BigDecimal(currText.substring(1)));
            int compres = delta.compareTo(BigDecimal.ZERO);
            if (compres > 0)
                newColor = Color.GREEN;
            else if (compres < 0)
                newColor = Color.RED;
            else
                newColor = Color.BLACK;
        }
        if (newColor != Color.BLACK)
            lblBestAsk.setForeground(newColor);
        lblBestAsk.setText("$" + formatter.format(decimal));
    }

    public void setLastTradePrice(BigDecimal decimal) {
        String currText = lblLastTrade.getText();
        Color newColor = Color.GRAY;
        if (currText.startsWith("$")) {
            BigDecimal delta = decimal.subtract(new BigDecimal(currText.substring(1)));
            int compres = delta.compareTo(BigDecimal.ZERO);
            if (compres > 0)
                newColor = Color.GREEN;
            else if (compres < 0)
                newColor = Color.RED;
            else
                newColor = Color.BLACK;
        }
        if (newColor != Color.BLACK)
            lblLastTrade.setForeground(newColor);
        lblLastTrade.setText("$" + formatter.format(decimal));
    }

    public void setUsdBalance(BigDecimal full, BigDecimal held) {
        this.lblUSDBalance.setText(full.setScale(4, RoundingMode.HALF_EVEN).toString());
        this.lblUSDHold.setText(held.setScale(4, RoundingMode.HALF_EVEN).toString());
    }

    public void setBtcBalance(BigDecimal full, BigDecimal held) {
        this.lblBTCBalance.setText(full.setScale(8, RoundingMode.HALF_EVEN).toString());
        this.lblBTCHold.setText(held.setScale(4, RoundingMode.HALF_EVEN).toString());
    }

    private void clearPriceFields() {
        this.lblBestAsk.setText("");
        this.lblBestBid.setText("");
        this.lblLastTrade.setText("");
    }

    public void setWsStatus(long ms) {
        this.lblWs.setText(ms < 0 ? "N/A" : (String.valueOf(ms) + " ms"));
    }

    public void setFixStatus(boolean connected) {
        this.lblFix.setText(connected ? "OK" : "N/A");
        this.btnLogout.setEnabled(connected);
    }


    private void initialize() {
        this.frmNodaxTradingUi = new JFrame();
        this.frmNodaxTradingUi.setAlwaysOnTop(true);
        this.frmNodaxTradingUi.setTitle("NoDax Trading UI");
        this.frmNodaxTradingUi.setBounds(100, 100, 643, 688);
        this.frmNodaxTradingUi.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        panelTop = new JPanel();
        this.panelTop.setPreferredSize(new Dimension(10, 120));
        this.frmNodaxTradingUi.getContentPane().add(panelTop, BorderLayout.NORTH);
        this.panelTop.setLayout(new BorderLayout(0, 0));
        
        this.panel = new JPanel();
        this.panel.setPreferredSize(new Dimension(300, 10));
        this.panelTop.add(this.panel, BorderLayout.WEST);
        this.panel.setLayout(null);
        
        this.lblNewLabel = new JLabel(LDConstants.PRIMARY_FIAT + " Balance");
        this.lblNewLabel.setFont(new Font("Tahoma", Font.BOLD, 14));
        this.lblNewLabel.setBounds(27, 25, 101, 14);
        this.panel.add(this.lblNewLabel);
        
        this.lblBalala = new JLabel(PRIMARY_CRYPTO + " Balance");
        this.lblBalala.setFont(new Font("Tahoma", Font.BOLD, 14));
        this.lblBalala.setBounds(194, 25, 108, 14);
        this.panel.add(this.lblBalala);
        
        this.lblUSDBalance = new JLabel("0.00000");
        this.lblUSDBalance.setFont(new Font("Tahoma", Font.PLAIN, 17));
        this.lblUSDBalance.setBounds(37, 50, 115, 14);
        this.panel.add(this.lblUSDBalance);
        
        this.lblUSDHold = new JLabel("0.00000");
        this.lblUSDHold.setBounds(37, 68, 130, 14);
        this.panel.add(this.lblUSDHold);
        
        this.lblBTCHold = new JLabel("0.00000");
        this.lblBTCHold.setBounds(204, 68, 130, 14);
        this.panel.add(this.lblBTCHold);
        
        this.lblBTCBalance = new JLabel("0.00000");
        this.lblBTCBalance.setFont(new Font("Tahoma", Font.PLAIN, 17));
        this.lblBTCBalance.setBounds(204, 50, 130, 14);
        this.panel.add(this.lblBTCBalance);
        
        this.panel_1 = new JPanel();
        this.panel_1.setPreferredSize(new Dimension(220, 10));
        this.panelTop.add(this.panel_1, BorderLayout.EAST);
        this.panel_1.setLayout(null);
        
        this.lblWssT = new JLabel("WS Latency:");
        this.lblWssT.setFont(new Font("Tahoma", Font.BOLD, 14));
        this.lblWssT.setBounds(10, 27, 108, 14);
        this.panel_1.add(this.lblWssT);
        
        this.lblFix = new JLabel("0.00000");
        this.lblFix.setFont(new Font("Tahoma", Font.PLAIN, 17));
        this.lblFix.setBounds(121, 52, 75, 14);
        this.panel_1.add(this.lblFix);
        
        this.lblFFAP = new JLabel("FIX API:");
        this.lblFFAP.setFont(new Font("Tahoma", Font.BOLD, 14));
        this.lblFFAP.setBounds(10, 52, 101, 14);
        this.panel_1.add(this.lblFFAP);
        
        this.lblWs = new JLabel("0.00000");
        this.lblWs.setFont(new Font("Tahoma", Font.PLAIN, 17));
        this.lblWs.setBounds(121, 26, 75, 14);
        this.panel_1.add(this.lblWs);
        
        this.btnLogout = new JButton("Logout");
        this.btnLogout.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        	    if (fixSession != null) {
                    try {
                        fixSession.sendLogout();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
        	}
        });
        btnLogout.setBounds(10, 77, 171, 23);
        this.panel_1.add(btnLogout);

        panelBottom = new JPanel();
        this.panelBottom.setPreferredSize(new Dimension(10, 200));
        this.panelBottom.setBounds(new Rectangle(0, 0, 0, 200));
        this.frmNodaxTradingUi.getContentPane().add(panelBottom, BorderLayout.SOUTH);
        this.panelBottom.setLayout(new BorderLayout(0, 0));
        
        final JPanel panel_3 = new JPanel();
        panel_3.setPreferredSize(new Dimension(300, 10));
        this.panelBottom.add(panel_3, BorderLayout.WEST);
        panel_3.setLayout(null);
        
        final JLabel lblBtcAmount = new JLabel(PRIMARY_CRYPTO +" Amount:");
        lblBtcAmount.setBounds(23, 23, 86, 14);
        panel_3.add(lblBtcAmount);
        
        this.txtBTCAmount = new JTextField();
        this.txtBTCAmount.setHorizontalAlignment(SwingConstants.CENTER);
        this.txtBTCAmount.setText("0.0001");
        this.txtBTCAmount.setBounds(100, 20, 86, 20);
        panel_3.add(this.txtBTCAmount);
        this.txtBTCAmount.setColumns(10);
        
        btnBuy = new JButton("(B)uy");
        this.btnBuy.addActionListener(e -> buyInvoked());
        btnBuy.setBounds(196, 19, 89, 23);
        panel_3.add(btnBuy);
        
        btnSell = new JButton("(S)ell");
        btnSell.addActionListener(arg0 -> sellInvoked());
        btnSell.setBounds(196, 47, 89, 23);
        panel_3.add(btnSell);
        
        btnCancel = new JButton("(C)ancel");
        this.btnCancel.addActionListener(arg0 -> cancelInvoked());
        btnCancel.setBounds(23, 47, 166, 23);
        panel_3.add(btnCancel);
        
        lblBestAsk = new JLabel("0.00000");
        lblBestAsk.setFont(new Font("Tahoma", Font.PLAIN, 16));
        lblBestAsk.setBounds(146, 100, 96, 14);
        panel_3.add(lblBestAsk);
        
        final JLabel lblasd = new JLabel("Best Ask:");
        lblasd.setFont(new Font("Tahoma", Font.PLAIN, 17));
        lblasd.setBounds(34, 100, 75, 14);
        panel_3.add(lblasd);
        
        final JLabel lblTasd = new JLabel("Last Trade:");
        lblTasd.setFont(new Font("Tahoma", Font.PLAIN, 17));
        lblTasd.setBounds(34, 125, 96, 14);
        panel_3.add(lblTasd);
        
        final JLabel lblBB = new JLabel("Best Bid:");
        lblBB.setFont(new Font("Tahoma", Font.PLAIN, 17));
        lblBB.setBounds(34, 150, 75, 14);
        panel_3.add(lblBB);
        
        lblLastTrade = new JLabel("0.00000");
        lblLastTrade.setFont(new Font("Tahoma", Font.PLAIN, 16));
        lblLastTrade.setBounds(146, 128, 96, 14);
        panel_3.add(lblLastTrade);
        
        lblBestBid = new JLabel("0.00000");
        lblBestBid.setFont(new Font("Tahoma", Font.PLAIN, 16));
        lblBestBid.setBounds(146, 153, 96, 14);
        panel_3.add(lblBestBid);


        String[] columns = new String[] {
                "Type", "Size", "Price"
        };
        Object[][] data = new Object[][] {};
        tableModel = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                //all cells false
                return false;
            }
        };

        this.tblOrders = new JTable();
        this.tblOrders.setModel(tableModel);
        this.panelBottom.add(new JScrollPane(this.tblOrders));
        this.tblOrders.setMinimumSize(new Dimension(250, 200));
        this.tblOrders.setPreferredSize(new Dimension(325, 200));
    }

    private void sellInvoked() {
        BigDecimal amount = new BigDecimal(txtBTCAmount.getText());
        defaultStrategy(LDConstants.OrderSide.SELL, amount);
    }

    private void buyInvoked() {
        BigDecimal amount = new BigDecimal(txtBTCAmount.getText());
        defaultStrategy(LDConstants.OrderSide.BUY, amount);
    }
}
