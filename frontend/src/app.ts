
import ReconnectingWebSocket from './reconnecting-websocket';
import Chart = require('./Chart.bundle');

interface IPoint {
  x: Date;
  y: number;
}

class Poller {
  public static output: HTMLElement;
  public static decoder: any;
  public static marketSells: any = [];
  public static marketBuys: any = [];
  public static chartCtx: any;
  public static chartElement: HTMLCanvasElement;
  public static bestAsks: any = [];
  public static bestBids: any = [];
  public static lastTradePrices: any = [];
  public static scatterChart: any;
  public static websocket: any;

  private static wsUri = "ws://" + window.location.host + "/api/v0/websocket";
  // private static wsUri = "ws://localhost:9025/api/v0/websocket";

  private static histSize: number = 1250;
  public static adjustHistorySize(factor: number) {
    Poller.histSize = Poller.histSize * factor;
  }

  private static writeToScreen(message: string) {
    var pre = document.createElement("p");
    pre.style.wordWrap = "break-word";
    pre.innerHTML = message;
    Poller.output.appendChild(pre);
  }

  private static onMessage(evt: any) {
    var decoded = Poller.decoder.decode(evt.data);
    var parsed = JSON.parse(decoded);
    var msg = parsed['message'];
    if (msg == null)
      return;
 
    var type = msg['type'];
    if (type == null)
      return;
 
    while (Poller.lastTradePrices.length >= Poller.histSize) {
      Poller.lastTradePrices.shift();
    }
 
    if (Poller.lastTradePrices.length > 0) {
      var headDate = Poller.lastTradePrices[0]['x'].getTime();
      while (Poller.marketSells.length > 0 && headDate - Poller.marketSells[0]['x'].getTime() > 0) {
        Poller.marketSells.shift();
      }
      while (Poller.marketBuys.length > 0 && headDate - Poller.marketBuys[0]['x'].getTime() > 0) {
        Poller.marketBuys.shift();
      }
      while (Poller.bestAsks.length > 0 && headDate - Poller.bestAsks[0]['x'].getTime() > 0) {
        Poller.bestAsks.shift();
      }
      while (Poller.bestBids.length > 0 && headDate - Poller.bestBids[0]['x'].getTime() > 0) {
        Poller.bestBids.shift();
      }
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
        Poller.lastTradePrices.push({x: now, y: ltp});
        Poller.bestAsks.push({x: now, y: ba});
        Poller.bestBids.push({x: now, y: bb});
      }
    }
    else if (type == "m" && Poller.lastTradePrices.length > 0) {
        var side = msg['side'];
        if (side == null)
          return;
        if (side == "s") {
            var sellUsd = msg['amount'];
            if (sellUsd != null) {
              Poller.marketSells.push({x: now, y: sellUsd});
            }
        }
        else if (side == "b") {
           var buyUsd = msg['amount'];
           if (buyUsd != null) {
            Poller.marketBuys.push({x: now, y: buyUsd});
           }
        }
    }
 }


  public static main(): number {
    var incHist = document.getElementById('incHist');
    var decHist = document.getElementById('decHist');
    if (incHist != null) {
      incHist.onclick = function() {
        Poller.adjustHistorySize(2);   
      };
    };
    if (decHist != null) {
      decHist.onclick = function() {
        Poller.adjustHistorySize(1/2);   
      };
    };
    Poller.chartElement = <HTMLCanvasElement>document.getElementById("chart");
    Poller.chartCtx = Poller.chartElement.getContext('2d');
    Poller.scatterChart = new Chart(Poller.chartCtx, {
        type: 'bubble',
        data: {
            datasets: [
              {
                label: 'Market Sell [USD]',
                data: Poller.marketSells,
                yAxisID: "MarketOrders",
                radius: 30,
                backgroundColor: "rgba(196, 93, 105, 0.3)"
              },
              {
                label: 'Market Buy [USD]',
                data: Poller.marketBuys,
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
                data: Poller.bestBids,
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
                data: Poller.bestAsks,
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
                data: Poller.lastTradePrices,
              }
            ]
        },
        options: {
          events: ['click'],
          animation: {
            duration: 1,
            onComplete: function () {
                var chartInstance = Poller.scatterChart;
                var ctx = chartInstance.ctx;
                ctx.font = Chart.helpers.fontString(Chart.defaults.global.defaultFontSize, Chart.defaults.global.defaultFontStyle, Chart.defaults.global.defaultFontFamily);
                ctx.textAlign = 'center';
                ctx.textBaseline = 'bottom';
  
                var mss = chartInstance.controller.getDatasetMeta(0);
                var mbs = chartInstance.controller.getDatasetMeta(1);
                mbs.data.forEach(function (bar: any, index: any) {
                  if (index >= Poller.marketBuys.length)
                    return;
                  var data = Poller.marketBuys[index]['y'];
                  ctx.fillStyle = "#000000";
                  ctx.font = "bold 12px verdana, sans-serif";
                  ctx.fillText(Math.round(data), bar._model.x + 1, bar._model.y);
                });
                mss.data.forEach(function (bar: any, index: any) {
                  if (index >= Poller.marketSells.length)
                    return;
                  var data = Poller.marketSells[index]['y'];
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
                        position: "left",
                        id: "MarketOrders",
                      },
                      {
                        gridLines: {
                          display: false
                        },
                        type: "linear",
                        display: true,
                        position: "right",
                        id: "TradePrice",
                    }]
          }
        }
      });

      Poller.decoder = new (<any>window).TextDecoder("UTF-8");
      
      var optElem = document.getElementById("output");
      if (optElem != null)
        Poller.output = optElem;

      Poller.websocket = new WebSocket(Poller.wsUri);
      Poller.websocket.binaryType = 'arraybuffer';
      Poller.websocket.onopen = function(evt: any) {
        Poller.writeToScreen("Connection established");
      };
      Poller.websocket.onclose = function(evt: any) {
        Poller.writeToScreen("Not connected");
      };
      Poller.websocket.onmessage = function(evt: any) {
        Poller.onMessage(evt);
      };
      Poller.websocket.onerror = function(evt: any) {
        Poller.writeToScreen('<span style = "color: red;">ERROR:</span> ' + evt.data);
      };

      Poller.updateChart();
      return 0;
  }

  private static updateChart() {
    Poller.scatterChart.update(0);
    setTimeout(Poller.updateChart, 40);
  }
}
window.onload = function () {
  Poller.main();
}