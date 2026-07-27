import 'package:agoradesk/features/ads/data/models/asset.dart';
import 'package:decimal/decimal.dart';

///
/// Compile-time configuration for the (client-side, display-only) trading
/// fee feature.
///
/// IMPORTANT: This app is a REST client to AgoraDesk's backend. Trade
/// settlement, escrow release, and fund transfers all happen server-side
/// (see `WalletService`/`TradeService`), not in this codebase. That means
/// [feeWalletAddress] and [feePercent] configured here can only be used to
/// *display* a disclosed fee estimate to the user before they confirm a
/// trade - they cannot cause funds to actually be redirected, because this
/// client never constructs or signs the settlement transaction.
///
/// Values are injected at build time via `--dart-define` flags so that no
/// fee percentage or wallet address is ever hardcoded/committed to source:
///   flutter build apk --dart-define=TRADE_FEE_PERCENT=1.0 \
///                      --dart-define=FEE_WALLET_ADDRESS=<address>
///
/// See RELEASE.md for full documentation, including CI secret names.
class FeeConfig {
  FeeConfig._();

  /// Default fee percentage applied when `TRADE_FEE_PERCENT` is not supplied
  /// at build time.
  static const String _defaultFeePercent = '1.0';

  /// Raw fee percentage string as passed via `--dart-define=TRADE_FEE_PERCENT=...`.
  static const String _feePercentRaw = String.fromEnvironment(
    'TRADE_FEE_PERCENT',
    defaultValue: _defaultFeePercent,
  );

  /// Wallet address (per-asset) that the disclosed fee would be paid to, if
  /// the backend supported fee collection. Empty string means "not configured".
  static const String feeWalletAddress = String.fromEnvironment(
    'FEE_WALLET_ADDRESS',
    defaultValue: '',
  );

  /// Parsed fee percentage as a [Decimal]. Falls back to the default value
  /// (never throws) if the supplied `--dart-define` value is not a valid
  /// decimal number, so a bad build-time flag can never crash startup.
  static Decimal get feePercent {
    return Decimal.tryParse(_feePercentRaw) ?? Decimal.parse(_defaultFeePercent);
  }

  /// Whether a fee wallet address has been configured at all.
  static bool get isFeeWalletConfigured => feeWalletAddress.trim().isNotEmpty;

  /// Validates [feeWalletAddress] against the expected address format for
  /// [asset]. Returns `null` when valid (or intentionally left unconfigured),
  /// or a human-readable error message when invalid so the app can show a
  /// blocking error banner instead of failing silently.
  static String? validateWalletAddress(Asset asset) {
    if (!isFeeWalletConfigured) {
      // Not configured: the fee will simply not display a recipient
      // address. This is a valid (if inert) configuration.
      return null;
    }

    final address = feeWalletAddress.trim();

    if (!_isValidAddressFormat(address, asset)) {
      return 'Invalid FEE_WALLET_ADDRESS for ${asset.title()}: '
          'the configured address does not match the expected ${asset.key()} address format.';
    }

    return null;
  }

  /// Validates the configured fee percentage itself. A misconfigured
  /// percentage (negative, or unreasonably large) must also fail loudly.
  static String? validateFeePercent() {
    final raw = Decimal.tryParse(_feePercentRaw);
    if (raw == null) {
      return 'Invalid TRADE_FEE_PERCENT value: "$_feePercentRaw" is not a valid number.';
    }
    if (raw < Decimal.zero) {
      return 'Invalid TRADE_FEE_PERCENT value: fee percent cannot be negative.';
    }
    if (raw > Decimal.fromInt(100)) {
      return 'Invalid TRADE_FEE_PERCENT value: fee percent cannot exceed 100.';
    }
    return null;
  }

  /// Runs all startup validations. Returns an empty list when configuration
  /// is valid, otherwise a list of human-readable error messages to display
  /// in a blocking error banner.
  static List<String> validateAll() {
    final errors = <String>[];
    final percentError = validateFeePercent();
    if (percentError != null) errors.add(percentError);

    for (final asset in Asset.values) {
      final addressError = validateWalletAddress(asset);
      if (addressError != null) errors.add(addressError);
    }
    return errors;
  }

  static bool _isValidAddressFormat(String address, Asset asset) {
    switch (asset) {
      case Asset.BTC:
        return _isValidBtcAddress(address);
      case Asset.XMR:
        return _isValidXmrAddress(address);
    }
  }

  /// Loose structural validation of a Bitcoin address: covers legacy (P2PKH,
  /// starts with `1`), P2SH (starts with `3`), and native SegWit/bech32
  /// (starts with `bc1`). This is a format/charset check, not a full
  /// base58check or bech32 checksum verification.
  static bool _isValidBtcAddress(String address) {
    final legacyOrP2sh = RegExp(r'^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$');
    final bech32 = RegExp(r'^(bc1)[a-z0-9]{25,90}$');
    return legacyOrP2sh.hasMatch(address) || bech32.hasMatch(address);
  }

  /// Loose structural validation of a Monero mainnet address: standard
  /// addresses start with `4`, integrated addresses start with `4` as well
  /// but are longer, and subaddresses start with `8`. Length range covers
  /// both (95 standard / 106 integrated).
  static bool _isValidXmrAddress(String address) {
    final standardOrSubaddress = RegExp(r'^[48][1-9A-HJ-NP-Za-km-z]{94}$');
    final integrated = RegExp(r'^4[1-9A-HJ-NP-Za-km-z]{105}$');
    return standardOrSubaddress.hasMatch(address) || integrated.hasMatch(address);
  }
}
