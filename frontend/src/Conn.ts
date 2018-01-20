export class Conn {
    private static wsUri = "ws://" + window.location.host + "/api/v0/websocket";
    // private static wsUri = "ws://localhost:9025/api/v0/websocket";
    public websocket: any;
    constructor(onMessageHandler: Function) {
      this.websocket = new WebSocket(Conn.wsUri);
      this.websocket.binaryType = 'arraybuffer';
      this.websocket.onopen = function(evt: any) {
        console.log('Connected');
      };
      this.websocket.onclose = function(evt: any) {
        console.log('Not connected');
      };
      this.websocket.onmessage = onMessageHandler;
      this.websocket.onerror = function(evt: any) {
        console.log('ERROR: ' + evt.data);
      };
    }
  }