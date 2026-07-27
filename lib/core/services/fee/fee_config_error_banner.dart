import 'package:agoradesk/core/services/fee/fee_config.dart';
import 'package:flutter/material.dart';

///
/// Wraps the app and blocks all further interaction with a visible error
/// banner if the build-time fee configuration (`TRADE_FEE_PERCENT`,
/// `FEE_WALLET_ADDRESS`) is invalid.
///
/// This intentionally fails loudly rather than silently: a misconfigured
/// fee wallet address or an out-of-range fee percentage must never be
/// allowed to pass unnoticed into a release build.
class FeeConfigErrorBanner extends StatelessWidget {
  const FeeConfigErrorBanner({
    Key? key,
    required this.child,
  }) : super(key: key);

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final errors = FeeConfig.validateAll();
    if (errors.isEmpty) {
      return child;
    }

    return Directionality(
      textDirection: TextDirection.ltr,
      child: Material(
        color: Colors.black,
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Center(
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.error, color: Colors.redAccent, size: 48),
                    const SizedBox(height: 16),
                    const Text(
                      'Fee configuration error',
                      style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'This build cannot start because the trading fee configuration is invalid. '
                      'This app must be rebuilt with a valid TRADE_FEE_PERCENT and FEE_WALLET_ADDRESS.',
                      style: TextStyle(color: Colors.white70, fontSize: 14),
                    ),
                    const SizedBox(height: 16),
                    for (final error in errors)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Text(
                          '• $error',
                          style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
