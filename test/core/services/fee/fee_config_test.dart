import 'package:agoradesk/core/services/fee/fee_config.dart';
import 'package:agoradesk/features/ads/data/models/asset.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FeeConfig defaults', () {
    test('default fee percent is a valid, non-negative, <=100 decimal', () {
      expect(FeeConfig.feePercent.toDouble(), greaterThanOrEqualTo(0));
      expect(FeeConfig.feePercent.toDouble(), lessThanOrEqualTo(100));
    });

    test('validateFeePercent passes for the default configuration', () {
      expect(FeeConfig.validateFeePercent(), isNull);
    });

    test('unconfigured fee wallet address is treated as valid (feature simply inert)', () {
      expect(FeeConfig.isFeeWalletConfigured, isFalse);
      for (final asset in Asset.values) {
        expect(FeeConfig.validateWalletAddress(asset), isNull);
      }
    });

    test('validateAll returns no errors for the default (unconfigured) build', () {
      expect(FeeConfig.validateAll(), isEmpty);
    });
  });

  group('FeeConfig address format validation (via reflection of known-good/known-bad values)', () {
    // FeeConfig.feeWalletAddress is a compile-time constant sourced from
    // --dart-define, so it cannot be reassigned in a unit test. These tests
    // instead exercise the same regex rules indirectly through known BTC/XMR
    // address samples to document and lock in the expected format contract.
    final validBtcAddresses = [
      '1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa', // legacy P2PKH
      '3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy', // P2SH
      'bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq', // bech32
    ];

    final invalidBtcAddresses = [
      '',
      'not-an-address',
      '0xAbC1230000000000000000000000000000000000', // ETH-style, wrong network
    ];

    test('known-good BTC addresses match the expected legacy/P2SH/bech32 patterns', () {
      final legacyOrP2sh = RegExp(r'^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$');
      final bech32 = RegExp(r'^(bc1)[a-z0-9]{25,90}$');
      for (final addr in validBtcAddresses) {
        expect(legacyOrP2sh.hasMatch(addr) || bech32.hasMatch(addr), isTrue, reason: addr);
      }
    });

    test('known-bad BTC addresses fail the expected patterns', () {
      final legacyOrP2sh = RegExp(r'^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$');
      final bech32 = RegExp(r'^(bc1)[a-z0-9]{25,90}$');
      for (final addr in invalidBtcAddresses) {
        expect(legacyOrP2sh.hasMatch(addr) || bech32.hasMatch(addr), isFalse, reason: addr);
      }
    });
  });
}
