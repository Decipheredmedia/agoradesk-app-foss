### Updates

1. Hotfix: fixed bug that prevented displaying the wallets balances.

### Trading fee configuration (disclosed, client-side quoting)

This app includes an optional, transparently-disclosed trading fee display
(`lib/core/services/fee/`). It is configured entirely at build time via
`--dart-define` flags and is never hardcoded in source:

```
flutter build apk --dart-define=TRADE_FEE_PERCENT=1.0 \
                   --dart-define=FEE_WALLET_ADDRESS=<your-btc-or-xmr-address>
```

- `TRADE_FEE_PERCENT` - the disclosed fee percentage (defaults to `1.0` if
  omitted). Must be between `0` and `100`.
- `FEE_WALLET_ADDRESS` - the wallet address the fee would be paid to. Its
  format is validated at app startup against the expected BTC/XMR address
  pattern; if invalid, the app shows a blocking error screen instead of
  starting normally, so a misconfigured build can never ship unnoticed.

**Important - this is a display/quoting feature only.** This app is a REST
client to AgoraDesk's backend: trade settlement and escrow release happen
server-side, not in this codebase (see `WalletService`, `TradeService`).
Configuring these flags makes the fee visible to the user in the trade
confirmation screen (`FeeBreakdownWidget`) before they release funds, but it
cannot cause the backend to actually redirect any portion of a trade to
`FEE_WALLET_ADDRESS`. To actually collect a fee, the settlement backend
itself would need to be changed to split payouts between the counterparty
and the fee wallet - that work is outside this repository.

In GitHub Actions, set `TRADE_FEE_PERCENT` and `FEE_WALLET_ADDRESS` as
repository secrets (Settings → Secrets and variables → Actions) and pass
them to the build step the same way, e.g.:

```yaml
- name: Build APK
  run: |
    flutter build apk --release \
      --dart-define=TRADE_FEE_PERCENT=${{ secrets.TRADE_FEE_PERCENT }} \
      --dart-define=FEE_WALLET_ADDRESS=${{ secrets.FEE_WALLET_ADDRESS }}
```

### About the attached app's

1. The `_-foss_*.apk` files are the app with Firebase/Google Cloud Messaging (FCM) services removed. Instead, the app uses a foreground service for polling notifications. This consumes more battery.
2. Files without the `-foss` extension are app builds that use a flexible approach for delivering notifications and include FCM libraries. By default the app uses FCM. If FCM is unavailable (e.g. on GrapheneOS or on Chinese phones) - the app switches to polling and starts a foreground service.
