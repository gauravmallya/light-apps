# 📈 Stocks

A small watchlist for the stocks you care about — search a ticker, add it to your list, and refresh to see the latest price. Powered by the [Alpaca](https://alpaca.markets) data API, with no ads and no notifications.

## 🔑 API keys

Stocks needs a free Alpaca market data API key to fetch prices. Keys are read at build time and never committed.

1. Create a free account at [Alpaca](https://alpaca.markets) and generate an **API Key ID** and **Secret Key** from your dashboard.
   - Use a **paper** endpoint if you just want market data without live trading.
2. Add them to `local.properties` (gitignored) in the repo root:
   ```properties
   alpacaApiKey=YOUR_API_KEY_ID
   alpacaSecret=YOUR_SECRET_KEY
   alpacaEndpoint=https://paper-api.alpaca.markets/v2
   ```
   (Alternatively, set the `ALPACA_API_KEY`, `ALPACA_SECRET`, and `ALPACA_ENDPOINT` environment variables.)
3. Rebuild the app.

If no keys are set, the app builds fine and just shows a "not configured" state.

## ▶️ Running it

Open this repo in Android Studio and run the `:stocks` module on an emulator or [the LightOS emulator](../docs/system_app).
