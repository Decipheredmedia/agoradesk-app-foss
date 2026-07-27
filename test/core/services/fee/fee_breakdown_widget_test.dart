import 'package:agoradesk/core/services/fee/fee_breakdown_widget.dart';
import 'package:agoradesk/features/ads/data/models/asset.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FeeBreakdownWidget', () {
    testWidgets('renders trade amount, fee, and net amount before confirmation', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: FeeBreakdownWidget(
              assetAmount: '100',
              asset: Asset.BTC,
            ),
          ),
        ),
      );

      expect(find.text('Fee breakdown'), findsOneWidget);
      expect(find.textContaining('100 BTC'), findsOneWidget);
      expect(find.textContaining('Counterparty receives'), findsOneWidget);
    });

    testWidgets('shows an explicit error instead of a misleading breakdown for invalid amounts', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: FeeBreakdownWidget(
              assetAmount: 'not-a-number',
              asset: Asset.XMR,
            ),
          ),
        ),
      );

      expect(find.textContaining('Unable to calculate the trading fee'), findsOneWidget);
      expect(find.text('Fee breakdown'), findsNothing);
    });

    testWidgets('does not fail on a zero trade amount', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: FeeBreakdownWidget(
              assetAmount: '0',
              asset: Asset.XMR,
            ),
          ),
        ),
      );

      expect(find.text('Fee breakdown'), findsOneWidget);
    });
  });
}
