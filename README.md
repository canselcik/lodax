# LoDax - Low Latency High Throughput Pipeline to GDAX

![Buy or sell, do whatever](https://i.imgur.com/lyCCvpA.png)

![Watch with no latency](https://i.imgur.com/qiHB4A8.png)

![Reveal Hidden Indicators](https://i.imgur.com/dAPYBo8.png)

- Subscribes to the authenticated websockets feed to create a `Treap` of asks and bids in realtime
- Utilizes the `Financial Information Exchange (FIX) API` instead of the `REST API` to leverage low latency and lenient rate limits.


## Setup for the `FIX API` 
```
➜  main git:() openssl s_client -showcerts -connect fix.gdax.com:4198 < /dev/null | \
                openssl x509 -outform PEM > /etc/fix.gdax.com.pem

➜  main cat /etc/stunnel/stunnel.conf
[GDAX]
client = yes
accept = 4198
connect = fix.gdax.com:4198
verify = 4
CAfile = /etc/stunnel/fix.gdax.com.pem
```
