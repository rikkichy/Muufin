import 'jellyfin_audio_handler.dart';
import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/base_item.dart';
import '../providers.dart';

class PlaybackSnapshot {
  const PlaybackSnapshot({
    required this.mediaItem,
    required this.playing,
    required this.duration,
    required this.queueIndex,
    required this.shuffleMode,
    required this.repeatMode,
  });

  final MediaItem? mediaItem;
  final bool playing;
  final Duration? duration;
  final int? queueIndex;
  final AudioServiceShuffleMode shuffleMode;
  final AudioServiceRepeatMode repeatMode;
}

final playbackControllerProvider = Provider<PlaybackController>((ref) {
  final handler = ref.watch(audioHandlerProvider);
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  handler.configure(api: api, auth: auth);
  return PlaybackController(handler: handler);
});

final playbackStateProvider = StreamProvider<PlaybackSnapshot>((ref) {
  final handler = ref.watch(audioHandlerProvider);

  return handler.playbackState
      .map((s) => PlaybackSnapshot(
            mediaItem: handler.mediaItem.value,
            playing: s.playing,
            duration: handler.mediaItem.value?.duration,
            queueIndex: s.queueIndex,
            shuffleMode: s.shuffleMode ?? AudioServiceShuffleMode.none,
            repeatMode: s.repeatMode ?? AudioServiceRepeatMode.none,
          ))
      .distinct((a, b) =>
          a.mediaItem?.id == b.mediaItem?.id &&
          a.playing == b.playing &&
          a.duration == b.duration &&
          a.queueIndex == b.queueIndex &&
          a.shuffleMode == b.shuffleMode &&
          a.repeatMode == b.repeatMode);
});

class PlaybackController {
  PlaybackController({required this.handler});
  final JellyfinAudioHandler handler;

  Stream<Duration> get positionStream => handler.positionStream;

  Stream<double> get volumeStream => handler.volumeStream;
  double get volume => handler.volume;
  Future<void> setVolume(double v) => handler.setVolume(v);

  Stream<bool> get shuffleEnabledStream => handler.shuffleModeEnabledStream;
  Stream<dynamic> get loopModeStream => handler.loopModeStream;

  Future<void> toggleShuffle() => handler.toggleShuffle();
  Future<void> cycleRepeatMode() => handler.cycleRepeatMode();
  Future<void> skipToQueueItem(int index) => handler.skipToQueueItem(index);

  Future<void> playQueue(List<BaseItem> tracks, {int? startIndex, String? startId}) async {
    int index = startIndex ?? 0;
    if (startId != null) {
      final i = tracks.indexWhere((t) => t.id == startId);
      if (i >= 0) index = i;
    }
    await handler.setQueueFromItems(tracks, startIndex: index);
  }

  Future<void> togglePlayPause() async {
    final playing = handler.playbackState.value.playing;
    if (playing) {
      await handler.pause();
    } else {
      await handler.play();
    }
  }

  Future<void> seek(Duration position) async => handler.seek(position);
  Future<void> skipToNext() async => handler.skipToNext();
  Future<void> skipToPrevious() async => handler.skipToPrevious();
}
