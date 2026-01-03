import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/app_settings.dart';
import '../../providers.dart';

class PlayerSettingsScreen extends ConsumerWidget {
  const PlayerSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(appSettingsProvider).valueOrNull ?? const AppSettings();

    return Scaffold(
      appBar: AppBar(title: const Text('Player')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          SwitchListTile(
            secondary: const Icon(Icons.info_outline_rounded),
            title: const Text('Show stream info'),
            subtitle: const Text('Codec/bit-depth/sample-rate on the player screen'),
            value: settings.showStreamInfo,
            onChanged: (v) => ref.read(appSettingsProvider.notifier).setShowStreamInfo(v),
          ),
        ],
      ),
    );
  }
}
