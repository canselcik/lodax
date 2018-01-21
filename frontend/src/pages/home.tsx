/**
 * In this file, we create a React component
 * which incorporates components provided by Material-UI.
 */
import * as React from 'react';
import * as ReactDOM from 'react';
import * as Foundation from 'react-foundation';
import { Button, Card, CardText, Header, Navigation, Drawer, Content, HeaderTabs, Tab, HeaderRow} from 'react-mdl';
import {Layout as RLayout} from 'react-mdl';
import * as PrimaryChart from "../components/PrimaryChart";
import * as Conn from "../components/Conn";
import { Responsive, WidthProvider, Layouts, Layout } from "react-grid-layout";

const ResponsiveReactGridLayout = WidthProvider(Responsive);

export default class Home extends React.Component {
  private windowHeight: number;
  private windowWidth: number;
  constructor(props: any, context: any) {
    super(props, context);
    this.windowHeight = 0;
    this.windowWidth = 0;
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.updateWindowDimensions);
  }

  updateWindowDimensions() {
    this.windowWidth = window.innerWidth;
    this.windowHeight = window.innerHeight;

  }

  private activeTab: any;
  render() {
    var lg : Layout = {i: 'chart', x: 0, y: 0, w: 14, h: 3, minW: 14, maxW: 14, minH: 3, maxH: 3};
    var ll : Layout = {i: 'logs',  x: 0, y: 3, w: 14, h: 1, minW: 2, maxW: 14, minH: 1, maxH: 8};
    var lay = {
      lg: [lg, ll], xxs: [], sm: [], xs: [], md: []
    };
   
    return (
      <ResponsiveReactGridLayout layouts={lay}>
        <div key="chart">
          <canvas id="chart"></canvas>
        </div>
        <div key="logs">Logs?</div>
      </ResponsiveReactGridLayout> 
    )
  }

  componentDidMount() {
    this.updateWindowDimensions();
    window.addEventListener('resize', this.updateWindowDimensions);

    var canvas: HTMLCanvasElement = document.getElementById("chart") as HTMLCanvasElement;
    if (canvas != null) {
      canvas.style.width ='100%';
      canvas.style.height='100%';
      canvas.style.pointerEvents = 'none';
      canvas.width  = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
    }

    var chart = new PrimaryChart.PrimaryChart(canvas);
    var poller = new Conn.Conn(function(evt: any) {
      chart.onMessage(evt);
    });
  }
}