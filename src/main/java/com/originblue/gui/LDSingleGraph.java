package com.originblue.gui;

import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class LDSingleGraph {
    private final AtomicInteger maxHistory;
    private Semaphore changing;

    private Map<String, LDDataset> datasets;
    private XYChart chart;
    private XChartPanel<XYChart> chartPanel;
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

    public LDSingleGraph(String title, int history) {
        changing = new Semaphore(1);
        datasets = new HashMap<String, LDDataset>();
        chart = new XYChart(800, 600);
        chart.setTitle(title);
        maxHistory = new AtomicInteger(history);
        chart.setXAxisTitle("Time");
        chart.setYAxisTitle("Data");
        chart.getStyler().setMarkerSize(0);

        chartPanel = new XChartPanel<XYChart>(chart);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Create and set up the window.
                JFrame frame = new JFrame("Realtime Chart");
                frame.addKeyListener(new KeyListener() {
                    public void keyTyped(KeyEvent e) {
                    }

                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                            maxHistory.set((int)((double)maxHistory.get() * 2.0));
                        }
                        else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                            maxHistory.set((int)((double)maxHistory.get() * 0.5));
                        }
                        else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
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
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.add(chartPanel);
                frame.setAlwaysOnTop(true);
                frame.pack();
                frame.setVisible(true);
            }
        });
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
                chartPanel.updateUI();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(chartUpdaterTask, 0, 40);
    }

    public boolean pushData(String datasetName, BigDecimal dataPoint) {
        LDDataset dataset = datasets.get(datasetName);
        if (dataset == null) {
            return false;
        }
        dataset.add(dataPoint);
        return true;
    }
}

