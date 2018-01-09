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
package org.knowm.xchart.style.colors;

import java.awt.Color;

/**
 * @author timmolter
 */
public class MatlabSeriesColors implements SeriesColors {

  public static final Color BLUE = new Color(0, 0, 255, 255);
  public static final Color GREEN = new Color(0, 128, 0, 255);
  public static final Color RED = new Color(255, 0, 0, 255);
  public static final Color TURQUOISE = new Color(0, 191, 191, 255);
  public static final Color MAGENTA = new Color(191, 0, 191, 255);
  public static final Color YELLOW = new Color(191, 191, 0, 255);
  public static final Color DARK_GREY = new Color(64, 64, 64, 255);

  private final Color[] seriesColors;

  /**
   * Constructor
   */
  public MatlabSeriesColors() {

    seriesColors = new Color[]{BLUE, GREEN, RED, TURQUOISE, MAGENTA, YELLOW, DARK_GREY};
  }

  @Override
  public Color[] getSeriesColors() {

    return seriesColors;
  }
}
