package com.originblue.gui;

import org.knowm.xchart.XYChart;

import javax.swing.*;
import java.awt.*;

public class LDChartPanel extends JPanel {
    private final XYChart chart;
    public LDChartPanel(final XYChart chart) {
        this.chart = chart;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        chart.paint(g2d, getWidth(), getHeight());
        g2d.dispose();
    }
}
