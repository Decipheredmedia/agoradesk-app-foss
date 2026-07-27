import 'package:agoradesk/core/services/fee/fee_calculator.dart';
import 'package:agoradesk/core/services/fee/fee_config.dart';
import 'package:agoradesk/core/theme/theme.dart';
import 'package:agoradesk/core/widgets/branded/container_surface2_radius12_border1.dart';
import 'package:agoradesk/features/ads/data/models/asset.dart';
import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';

///
/// Displays a disclosed fee breakdown (trade amount / fee / net amount) so
/// the user can see exactly what portion of a trade the platform fee would
/// apply to *before* they confirm the trade.
///
/// NOTE: This widget is purely informational/quoting. Because trade
/// settlement happens on AgoraDesk's backend (see `WalletService`,
/// `TradeService`), this widget cannot itself cause any funds to move - it
/// only discloses what the fee would be so the user is never surprised.
/// A backend that actually splits settlement between the counterparty and
/// [FeeConfig.feeWalletAddress] is required to collect the fee for real.
class FeeBreakdownWidget extends StatelessWidget {
  const FeeBreakdownWidget({
    Key? key,
    required this.assetAmount,
    required this.asset,
  }) : super(key: key);

  /// The full trade amount, in the smallest human-readable unit the app
  /// already uses elsewhere (e.g. `tradeForScreen.assetAmount`).
  final String assetAmount;

  final Asset asset;

  @override
  Widget build(BuildContext context) {
    final amount = Decimal.tryParse(assetAmount);

    // If the amount can't be parsed, don't show a misleading fee breakdown -
    // fail visibly instead of silently displaying zero/garbage values.
    if (amount == null) {
      return ContainerSurface2Radius12Border1(
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Text(
            'Unable to calculate the trading fee: invalid trade amount.',
            style: context.txtBodyXSmallN80N30,
          ),
        ),
      );
    }

    final result = FeeCalculator.calculate(
      tradeAmount: amount,
      feePercent: FeeConfig.feePercent,
    );

    return ContainerSurface2Radius12Border1(
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Fee breakdown',
              style: context.txtBodyMediumN90N10,
            ),
            const SizedBox(height: 8),
            _row(context, 'Trade amount', '${result.tradeAmount} ${asset.key()}'),
            const SizedBox(height: 4),
            _row(
              context,
              'Platform fee (${_formatPercent(result.feePercent)}%)',
              '${result.feeAmount} ${asset.key()}',
            ),
            const SizedBox(height: 4),
            _row(context, 'Counterparty receives', '${result.netAmount} ${asset.key()}', emphasize: true),
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value, {bool emphasize = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: context.txtBodyXSmallN80N30),
        Text(
          value,
          style: emphasize ? context.txtBodyMediumN90N10 : context.txtBodyXSmallN80N30,
        ),
      ],
    );
  }

  String _formatPercent(Decimal percent) {
    // Trim a trailing ".0" for cleaner display (e.g. "1" instead of "1.0")
    // while preserving meaningful decimals (e.g. "1.5").
    final str = percent.toString();
    if (str.endsWith('.0')) {
      return str.substring(0, str.length - 2);
    }
    return str;
  }
}
