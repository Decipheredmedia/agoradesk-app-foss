import 'package:decimal/decimal.dart';

///
/// Result of a [FeeCalculator.calculate] call: a disclosed, precomputed
/// breakdown of a trade amount into the fee portion and the net portion the
/// counterparty would receive.
///
/// All amounts use [Decimal] (never `double`) to avoid floating point
/// rounding errors with cryptocurrency-precision values.
class FeeCalculationResult {
  const FeeCalculationResult({
    required this.tradeAmount,
    required this.feePercent,
    required this.feeAmount,
    required this.netAmount,
  });

  /// The original, full trade amount.
  final Decimal tradeAmount;

  /// The fee percentage applied (e.g. `1.0` for 1%).
  final Decimal feePercent;

  /// The computed fee amount, always rounded down (floor) to [scale] decimal
  /// places so the fee is never silently over-collected due to rounding.
  final Decimal feeAmount;

  /// `tradeAmount - feeAmount`. This is always exact (no separate rounding),
  /// so `feeAmount + netAmount == tradeAmount` always holds.
  final Decimal netAmount;

  @override
  String toString() {
    return 'FeeCalculationResult(tradeAmount: $tradeAmount, feePercent: $feePercent, '
        'feeAmount: $feeAmount, netAmount: $netAmount)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is FeeCalculationResult &&
        other.tradeAmount == tradeAmount &&
        other.feePercent == feePercent &&
        other.feeAmount == feeAmount &&
        other.netAmount == netAmount;
  }

  @override
  int get hashCode => Object.hash(tradeAmount, feePercent, feeAmount, netAmount);
}

///
/// Pure, precision-safe calculator for the disclosed trading fee. Contains
/// no I/O and no side effects, so it can be used both for the trade
/// confirmation UI and for tests.
///
class FeeCalculator {
  FeeCalculator._();

  /// Number of decimal places the fee amount is rounded to by default. Chosen
  /// to comfortably cover both BTC (8 decimals) and XMR (12 decimals) without
  /// losing precision for display purposes; callers that need
  /// asset-specific precision can pass an explicit [scale].
  static const int defaultScale = 12;

  /// Calculates the fee/net breakdown for [tradeAmount] at [feePercent].
  ///
  /// Edge cases handled explicitly:
  /// - `tradeAmount == 0` -> feeAmount and netAmount are both zero.
  /// - `feePercent == 0` -> feeAmount is zero, netAmount equals tradeAmount.
  /// - Dust amounts that would round to a fee of `0` at [scale] simply
  ///   produce a zero fee (no fee is ever "rounded up" out of thin air).
  /// - Negative [tradeAmount] or negative/over-100 [feePercent] throw an
  ///   [ArgumentError] rather than silently producing a nonsensical result.
  static FeeCalculationResult calculate({
    required Decimal tradeAmount,
    required Decimal feePercent,
    int scale = defaultScale,
  }) {
    if (tradeAmount < Decimal.zero) {
      throw ArgumentError.value(tradeAmount, 'tradeAmount', 'Trade amount cannot be negative.');
    }
    if (feePercent < Decimal.zero || feePercent > Decimal.fromInt(100)) {
      throw ArgumentError.value(feePercent, 'feePercent', 'Fee percent must be between 0 and 100.');
    }

    if (tradeAmount == Decimal.zero || feePercent == Decimal.zero) {
      return FeeCalculationResult(
        tradeAmount: tradeAmount,
        feePercent: feePercent,
        feeAmount: Decimal.zero,
        netAmount: tradeAmount,
      );
    }

    final rawFee = (tradeAmount * feePercent / Decimal.fromInt(100)).toDecimal();
    // Floor (round down) to `scale` decimal places so the fee never exceeds
    // the mathematically exact amount - protecting the trade counterparty
    // from ever receiving less than `tradeAmount - exactFee`.
    final feeAmount = rawFee.floor(scale: scale);

    final netAmount = tradeAmount - feeAmount;

    return FeeCalculationResult(
      tradeAmount: tradeAmount,
      feePercent: feePercent,
      feeAmount: feeAmount,
      netAmount: netAmount,
    );
  }
}
