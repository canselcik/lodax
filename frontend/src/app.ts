import * as React from "react";
import * as ReactDOM from "react-dom";
import Home from "./pages/home"

window.onload = function () {
  ReactDOM.render(
      React.createElement(Home),
      document.querySelector('#content')
  );
}