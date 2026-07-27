import 'package:agoradesk/core/services/fee/fee_calculator.dart';
import 'package:decimal/decimal.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FeeCalculator.calculate', () {
    test('zero trade amount yields zero fee and zero net amount', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.zero,
        feePercent: Decimal.parse('1.0'),
      );

      expect(result.feeAmount, Decimal.zero);
      expect(result.netAmount, Decimal.zero);
    });

    test('zero fee percent yields zero fee and full net amount', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('10'),
        feePercent: Decimal.zero,
      );

      expect(result.feeAmount, Decimal.zero);
      expect(result.netAmount, Decimal.parse('10'));
    });

    test('computes exact fee/net split for a simple round amount', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('100'),
        feePercent: Decimal.parse('1.0'),
      );

      expect(result.feeAmount, Decimal.parse('1'));
      expect(result.netAmount, Decimal.parse('99'));
    });

    test('dust amounts that round to zero at the given scale produce zero fee, not a crash', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('0.000000000001'),
        feePercent: Decimal.parse('1.0'),
        scale: 8,
      );

      expect(result.feeAmount, Decimal.zero);
      expect(result.netAmount, result.tradeAmount);
    });

    test('very large amounts keep fee + net exactly equal to trade amount', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('123456789.123456789'),
        feePercent: Decimal.parse('4.0'),
        scale: 8,
      );

      expect(result.feeAmount + result.netAmount, result.tradeAmount);
    });

    test('rounding edge case: fee is floored, never rounded up beyond the exact value', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('0.1'),
        feePercent: Decimal.parse('3.0'),
        scale: 8,
      );

      // exact fee is 0.003, representable exactly at scale 8
      expect(result.feeAmount, Decimal.parse('0.003'));
      expect(result.netAmount, Decimal.parse('0.097'));
      expect(result.feeAmount + result.netAmount, result.tradeAmount);
    });

    test('fee amount never exceeds the trade amount for any valid percent', () {
      final amounts = ['0', '0.00000001', '1', '7.777777', '1000000.5'];
      final percents = ['0', '0.5', '1', '4', '50', '100'];

      for (final amountStr in amounts) {
        for (final percentStr in percents) {
          final result = FeeCalculator.calculate(
            tradeAmount: Decimal.parse(amountStr),
            feePercent: Decimal.parse(percentStr),
            scale: 8,
          );

          expect(result.feeAmount + result.netAmount, result.tradeAmount,
              reason: 'invariant broken for $amountStr @ $percentStr%');
          expect(result.feeAmount >= Decimal.zero, isTrue);
          expect(result.netAmount >= Decimal.zero, isTrue);
        }
      }
    });

    test('throws ArgumentError for a negative trade amount', () {
      expect(
        () => FeeCalculator.calculate(
          tradeAmount: Decimal.parse('-1'),
          feePercent: Decimal.parse('1.0'),
        ),
        throwsArgumentError,
      );
    });

    test('throws ArgumentError for a negative fee percent', () {
      expect(
        () => FeeCalculator.calculate(
          tradeAmount: Decimal.parse('1'),
          feePercent: Decimal.parse('-1'),
        ),
        throwsArgumentError,
      );
    });

    test('throws ArgumentError for a fee percent above 100', () {
      expect(
        () => FeeCalculator.calculate(
          tradeAmount: Decimal.parse('1'),
          feePercent: Decimal.parse('101'),
        ),
        throwsArgumentError,
      );
    });

    test('100% fee sends the entire amount to fee, none to counterparty', () {
      final result = FeeCalculator.calculate(
        tradeAmount: Decimal.parse('50'),
        feePercent: Decimal.parse('100'),
      );

      expect(result.feeAmount, Decimal.parse('50'));
      expect(result.netAmount, Decimal.zero);
    });
  });
}
