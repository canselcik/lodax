/**
 * Copyright 2015-2017 Knowm Inc. (http://knowm.org) and contributors.
 * Copyright 2011-2015 Xeiam LLC (http://xeiam.com) and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.knowm.xchart.internal.series;

import java.awt.Color;

import org.knowm.xchart.internal.chartpart.RenderableSeries.LegendRenderType;

/**
 * A Series to be plotted on a Chart
 *
 * @author timmolter
 */
public abstract class Series {


  public enum DataType {

    Number, Date, String
  }

  public abstract LegendRenderType getLegendRenderType();

  private final String name;

  private Color fillColor;

  private boolean showInLegend = true;

  private boolean isEnabled = true;

  private int yAxisGroup = 0;

  /**
   * Constructor
   *
   * @param name the name of the series
   */
  protected Series(String name) {

    if (name == null || name.length() < 1) {
      throw new IllegalArgumentException("Series name cannot be null or zero-length!!!");
    }
    this.name = name;
  }

  public Color getFillColor() {

    return fillColor;
  }

  public void setFillColor(Color fillColor) {

    this.fillColor = fillColor;
  }

  public String getName() {

    return name;
  }

  public boolean isShowInLegend() {

    return showInLegend;
  }

  public void setShowInLegend(boolean showInLegend) {

    this.showInLegend = showInLegend;
  }

  public boolean isEnabled() {

    return isEnabled;
  }

  public void setEnabled(boolean isEnabled) {

    this.isEnabled = isEnabled;
  }

  public int getYAxisGroup() {

    return yAxisGroup;
  }

  /**
   * Set the Y Axis Group the series should belong to
   *
   * @param yAxisGroup
   */
  public void setYAxisGroup(int yAxisGroup) {

    this.yAxisGroup = yAxisGroup;
  }
}
