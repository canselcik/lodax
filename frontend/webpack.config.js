const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = {
  entry:
   { 
    "app": [
      path.resolve(__dirname, 'src/Chart.bundle.js'),
      path.resolve(__dirname, 'src/app.ts')
    ]
  },
  module: {
    loaders: [
      {
        test: /\.(t|j)s$/,
        loader: 'ts-loader',
        exclude: /node_modules/
      }
    ]
  },
  resolve: {
    extensions: [ ".tsx", ".ts", ".js", ".css" ]
  },
  plugins: [
    new webpack.optimize.CommonsChunkPlugin('app'),
    new HtmlWebpackPlugin({
      template: path.resolve(__dirname, "src/index.html"),
      filename: 'index.html',
    }),
    new CopyWebpackPlugin([
      {
        from: path.resolve(__dirname, "src/assets"),
        to: path.resolve(__dirname, "dist/assets")
      }
    ]),
    new webpack.NoEmitOnErrorsPlugin()
  ],
  output: {
    filename: 'webpack',
    path: path.resolve(__dirname, 'dist')
  }
};