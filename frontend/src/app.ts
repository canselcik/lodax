
import Chart = require('./Chart.bundle');
import * as BoundedBuffer from "./BoundedBuffer";
import * as Conn from "./Conn"

class PrimaryChart {
  public output: HTMLElement;
  public static decoder: any = new (<any>window).TextDecoder("UTF-8");
  public chartCtx: any;
  public chartElement: HTMLCanvasElement;
  public marketSells: BoundedBuffer.BoundedBuffer;
  public marketBuys: BoundedBuffer.BoundedBuffer;
  public bestAsks: BoundedBuffer.BoundedBuffer;
  public bestBids: BoundedBuffer.BoundedBuffer;
  public lastTradePrices: BoundedBuffer.BoundedBuffer;
  public scatterChart: any;

  constructor(canvas: HTMLCanvasElement) {
    this.marketBuys = new BoundedBuffer.BoundedBuffer(1200);
    this.marketSells = new BoundedBuffer.BoundedBuffer(1200);
    this.bestAsks = new BoundedBuffer.BoundedBuffer(1200);
    this.bestBids = new BoundedBuffer.BoundedBuffer(1200);
    this.lastTradePrices = new BoundedBuffer.BoundedBuffer(1200);

    this.chartElement = canvas;
    this.chartCtx = canvas.getContext("2d");

    var parent = this;
    this.scatterChart = new Chart(this.chartCtx, {
      type: 'bubble',
      data: {
          datasets: [
            {
              label: 'Market Sell [USD]',
              data: this.marketSells.getArray(),
              yAxisID: "MarketOrders",
              radius: 30,
              backgroundColor: "rgba(196, 93, 105, 0.3)"
            },
            {
              label: 'Market Buy [USD]',
              data: this.marketBuys.getArray(),
              yAxisID: "MarketOrders",
              radius: 30,
              backgroundColor: "rgba(32, 162, 219, 0.3)"
            },
            {
              type: 'line',
              fill: 'start',
              steppedLine: 'after',
              backgroundColor: "rgba(70, 80, 191, 0.3)",
              label: 'Best Bid',
              pointRadius: 0,
              pointHoverRadius: 0,
              yAxisID: "TradePrice",
              data: this.bestBids.getArray(),
            },
            {
              type: 'line',
              fill: 'end',
              steppedLine: 'after',
              backgroundColor: "rgba(237, 179, 99, 0.3)",
              label: 'Best Ask',
              yAxisID: "TradePrice",
              pointRadius: 0,
              pointHoverRadius: 0,
              data: this.bestAsks.getArray(),
            },
            {
              type: 'line',
              fill: false,
              backgroundColor: "rgb(110, 53, 224)",
              borderColor: "rgb(110, 53, 224)",
              pointRadius: 0,
              pointHoverRadius: 0,
              borderDash: [5, 5],
              label: 'Last Trade Price',
              yAxisID: "TradePrice",
              data: this.lastTradePrices.getArray(),
            }
          ]
      },
      options: {
        events: ['click'],
        animation: {
          duration: 1,
          onComplete: function () {
              var chartInstance = parent.scatterChart;
              var ctx = chartInstance.ctx;
              ctx.font = Chart.helpers.fontString(Chart.defaults.global.defaultFontSize, Chart.defaults.global.defaultFontStyle, Chart.defaults.global.defaultFontFamily);
              ctx.textAlign = 'center';
              ctx.textBaseline = 'bottom';

              var mss = chartInstance.controller.getDatasetMeta(0);
              var mbs = chartInstance.controller.getDatasetMeta(1);
              mbs.data.forEach(function (bar: any, index: any) {
                var data = parent.marketBuys.getAt(index);
                if (data == null)
                  return;
                ctx.fillStyle = "#000000";
                ctx.font = "bold 12px verdana, sans-serif";
                ctx.fillText(Math.round(data), bar._model.x + 1, bar._model.y);
              });
              mss.data.forEach(function (bar: any, index: any) {
                var data = parent.marketSells.getAt(index);
                if (data == null)
                  return;
                ctx.fillStyle = "#000000";
                ctx.font = "bold 12px verdana, sans-serif";
                ctx.fillText(Math.round(data), bar._model.x + 1, bar._model.y);
              });
            }
          },
          tooltips: {
            enabled: false
          },
          scales: {
            xAxes: [{ gridLines: { 
                        display: false
                      },
                      ticks: {
                        autoSkip: false,
                        maxRotation: 0,
                        minRotation: 0,
                        steps: 5,
                        stepValue: 2
                      },
                      type: 'time',
                      distribution: 'series',
                      position: 'bottom',
                      barThickness: 20
                    }],
            yAxes: [{ gridLines: {
                        display: false
                      },
                      type: "linear",
                      display: true,
                      ticks: {
                        min: 0,
                        suggestedMax: 100000
                      },
                      position: "left",
                      id: "MarketOrders",
                    },
                    {
                      gridLines: {
                        display: false
                      },
                      type: "linear",
                      display: true,
                      ticks: {
                        min: 0,
                        suggestedMax: 100000
                      },
                      position: "right",
                      id: "TradePrice",
                  }]
        }
      }
    });
    this.updateChart();
  }

  public onMessage(evt: any) {
    var decoded = PrimaryChart.decoder.decode(evt.data);
    var parsed = JSON.parse(decoded);
    var msg = parsed['message'];
    if (msg == null)
      return;
 
    var type = msg['type'];
    if (type == null)
      return;
 
    var ltDate = this.lastTradePrices.getDateAt(0);
    if (ltDate != null) {
      this.marketSells.shiftUntilGeq(ltDate);
      this.marketBuys.shiftUntilGeq(ltDate);
      this.bestAsks.shiftUntilGeq(ltDate);
      this.bestBids.shiftUntilGeq(ltDate);
    }
 
    var now = new Date(parsed['timestamp']);
    if (type == "t") {
      var ltp = msg['price'];
      var bb = msg["bestBid"];
      var ba = msg["bestAsk"];
      if (ltp != null && bb != null && ba != null) {
        // TODO: workaround
        if (Math.abs(ltp-bb) > 150 || Math.abs(ltp-ba) > 150 ) {
          console.log("ignored");
          return;
        }
        this.lastTradePrices.push(now, ltp);
        this.bestAsks.push(now, ba);
        this.bestBids.push(now, bb);
      }
    }
    else if (type == "m" && this.lastTradePrices.getLength() > 0) {
        var side = msg['side'];
        if (side == null)
          return;
        if (side == "s") {
            var sellUsd = msg['amount'];
            if (sellUsd != null) {
              this.marketSells.push(now, sellUsd);
            }
        }
        else if (side == "b") {
           var buyUsd = msg['amount'];
           if (buyUsd != null) {
            this.marketBuys.push(now, buyUsd);
           }
        }
    }
  }

  private updateChart() {
    this.scatterChart.update();
    setTimeout(() => { this.updateChart() }, 40);
  }
}

window.onload = function () {
  var chart = new PrimaryChart(<HTMLCanvasElement>document.getElementById("chart"));
  var poller = new Conn.Conn(function(evt: any) {
    chart.onMessage(evt);
  });
}