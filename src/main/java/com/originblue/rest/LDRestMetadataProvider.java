package com.originblue.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;


public class LDRestMetadataProvider {
    private final HashMap<String, NDAccount> accounts;
    private String secret, key, passphrase;
    private static final JsonParser parser = new JsonParser();
    private static final String GDAX_API = "https://api.gdax.com";

    public LDRestMetadataProvider(String secret, String key, String passphrase) {
        this.secret = secret;
        this.key = key;
        this.passphrase = passphrase;
        this.accounts = new HashMap<String, NDAccount>();
    }

    public void init(final int checkIntervalMillis) {
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    updateAccounts();
                    try {
                        Thread.sleep(checkIntervalMillis);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public class NDAccount {
        public final String id, currency, profileId;
        public volatile BigDecimal balance, available, hold;

        private NDAccount(String id, String currency, String profileId) {
            this.id = id;
            this.currency = currency;
            this.profileId = profileId;
        }
    }

    public NDAccount getAccountByCurrency(String currency) {
        for (NDAccount acc : accounts.values()) {
            if (acc.currency.equals(currency))
                return acc;
        }
        return null;
    }

    private String getURL(String s) throws Exception {
        URL url = new URL(s);
        HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
        con.setRequestMethod("GET");

        String now = String.valueOf(System.currentTimeMillis() / 1000);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Host", url.getHost());
        con.setRequestProperty("CB-ACCESS-KEY", key);
        String signature = sign(now, "GET", url.getPath(), "");
        con.setRequestProperty("CB-ACCESS-SIGN", signature);
        con.setRequestProperty("CB-ACCESS-TIMESTAMP", now);
        con.setRequestProperty("CB-ACCESS-PASSPHRASE", passphrase);
        con.setUseCaches(false);
        con.setDoInput(true);
        con.setDoOutput(true);

        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String input;
        StringBuilder out = new StringBuilder();
        while ((input = br.readLine()) != null){
            out.append(input);
        }
        br.close();

        return out.toString();
    }

    public boolean updateAccounts() {
        try {
            String body = getURL(GDAX_API + "/accounts");
            JsonArray array = parser.parse(body).getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonObject accObj = array.get(i).getAsJsonObject();
                String id = accObj.get("id").getAsString();
                NDAccount acc = accounts.get(id);
                if (acc == null)
                    acc = new NDAccount(id, accObj.get("currency").getAsString(), accObj.get("profile_id").getAsString());
                acc.available = accObj.get("available").getAsBigDecimal();
                acc.balance = accObj.get("balance").getAsBigDecimal();
                acc.hold = accObj.get("hold").getAsBigDecimal();
                accounts.put(id, acc);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String sign(String timestamp, String method, String requestPath, String body) {
        try {
            String payload = timestamp + method + requestPath + body;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(Base64.decodeBase64(secret), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return Base64.encodeBase64String(sha256_HMAC.doFinal(payload.getBytes("UTF-8")));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
