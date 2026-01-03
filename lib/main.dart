import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio_media_kit/just_audio_media_kit.dart';

import 'app.dart';
import 'player/jellyfin_audio_handler.dart';
import 'providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  
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
