import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio_media_kit/just_audio_media_kit.dart';

import 'app.dart';
import 'core/auth_storage.dart';
import 'core/http_overrides.dart'; // Import the new file
import 'player/jellyfin_audio_handler.dart';
import 'providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    const storage = AuthStorage();
    final data = await storage.read();
    if (data != null && data.disableTls) {
      HttpOverrides.global = BadCertHttpOverrides();
    }
  } catch (e) {
    debugPrint('Error reading auth storage during init: $e');
  }

  if (!kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
    JustAudioMediaKit.ensureInitialized();
  }

  late final JellyfinAudioHandler audioHandler;
  try {
    audioHandler = await initAudioHandler();
  } catch (e, st) {
    debugPrint('Audio handler init failed: $e\n$st');
    audioHandler = JellyfinAudioHandler();
  }

  runApp(
    ProviderScope(
      overrides: [
        audioHandlerProvider.overrideWithValue(audioHandler),
      ],
      child: const JfMusicApp(),
    ),
  );
}
