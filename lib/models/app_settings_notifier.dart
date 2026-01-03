import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/disk_cache.dart';
import 'app_settings.dart';

class AppSettingsNotifier extends AsyncNotifier<AppSettings> {
  static const _key = 'app_settings:v1';
  final DiskCache _disk = DiskCache(subdir: 'muufin_prefs');

  @override
  Future<AppSettings> build() async {
    final json = await _disk.readJson(_key);
    if (json == null) return const AppSettings();
    try {
      return AppSettings.fromJson(json);
    } catch (_) {
      return const AppSettings();
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final current = state.value ?? const AppSettings();
    final next = current.copyWith(themeMode: mode);
    state = AsyncData(next);
    await _disk.writeJson(_key, next.toJson());
  }

  Future<void> setUseDynamicColor(bool value) async {
    final current = state.value ?? const AppSettings();
    final next = current.copyWith(useDynamicColor: value);
    state = AsyncData(next);
    await _disk.writeJson(_key, next.toJson());
  }

  Future<void> setShowStreamInfo(bool value) async {
    final current = state.value ?? const AppSettings();
    final next = current.copyWith(showStreamInfo: value);
    state = AsyncData(next);
    await _disk.writeJson(_key, next.toJson());
  }
}
