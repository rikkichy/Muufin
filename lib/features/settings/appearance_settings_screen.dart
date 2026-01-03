import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/app_settings.dart';
import '../../providers.dart';

class AppearanceSettingsScreen extends ConsumerWidget {
  const AppearanceSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(appSettingsProvider).valueOrNull ?? const AppSettings();

    return Scaffold(
      appBar: AppBar(title: const Text('Appearance')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text('Theme'),
          ),
          RadioListTile<ThemeMode>(
            value: ThemeMode.system,
            groupValue: settings.themeMode,
            onChanged: (v) {
              if (v != null) ref.read(appSettingsProvider.notifier).setThemeMode(v);
            },
            title: const Text('System'),
          ),
          RadioListTile<ThemeMode>(
            value: ThemeMode.light,
            groupValue: settings.themeMode,
            onChanged: (v) {
              if (v != null) ref.read(appSettingsProvider.notifier).setThemeMode(v);
            },
            title: const Text('Light'),
          ),
          RadioListTile<ThemeMode>(
            value: ThemeMode.dark,
            groupValue: settings.themeMode,
            onChanged: (v) {
              if (v != null) ref.read(appSettingsProvider.notifier).setThemeMode(v);
            },
            title: const Text('Dark'),
          ),
          const Divider(height: 24),
          SwitchListTile(
            secondary: const Icon(Icons.auto_awesome_rounded),
            title: const Text('Dynamic color'),
            subtitle: const Text('Use system accent color when available'),
            value: settings.useDynamicColor,
            onChanged: (v) => ref.read(appSettingsProvider.notifier).setUseDynamicColor(v),
          ),
        ],
      ),
    );
  }
}
