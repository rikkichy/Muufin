import 'package:flutter/material.dart';

class AppSettings {
  const AppSettings({
    this.themeMode = ThemeMode.system,
    this.useDynamicColor = true,
    this.showStreamInfo = true,
  });

  final ThemeMode themeMode;
  final bool useDynamicColor;
  final bool showStreamInfo;

  AppSettings copyWith({
    ThemeMode? themeMode,
    bool? useDynamicColor,
    bool? showStreamInfo,
  }) {
    return AppSettings(
      themeMode: themeMode ?? this.themeMode,
      useDynamicColor: useDynamicColor ?? this.useDynamicColor,
      showStreamInfo: showStreamInfo ?? this.showStreamInfo,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'themeMode': switch (themeMode) {
        ThemeMode.light => 'light',
        ThemeMode.dark => 'dark',
        ThemeMode.system => 'system',
      },
      'useDynamicColor': useDynamicColor,
      'showStreamInfo': showStreamInfo,
    };
  }

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    final tm = (json['themeMode'] as String?)?.toLowerCase().trim();
    final ThemeMode themeMode = switch (tm) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };

    return AppSettings(
      themeMode: themeMode,
      useDynamicColor: (json['useDynamicColor'] as bool?) ?? true,
      showStreamInfo: (json['showStreamInfo'] as bool?) ?? true,
    );
  }
}
