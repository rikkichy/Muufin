import 'dart:async';
import 'dart:io' show Platform;
import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:just_audio/just_audio.dart';

import '../models/auth_state.dart';
import '../models/base_item.dart';
import '../core/jellyfin_api.dart';
import 'jellyfin_stream_audio_source.dart';

Future<JellyfinAudioHandler> initAudioHandler() async {
  final session = await AudioSession.instance;
  await session.configure(const AudioSessionConfiguration.music());

  return AudioService.init(
    builder: () => JellyfinAudioHandler(),
    config: const AudioServiceConfig(
      androidNotificationChannelId: 'com.rii.muufin.channel.audio',
      androidNotificationChannelName: 'Muufin playback',
      androidNotificationOngoing: true,
    ),
  );
}

class JellyfinAudioHandler extends BaseAudioHandler with QueueHandler, SeekHandler {
  final _player = AudioPlayer();
  ConcatenatingAudioSource? _playlistSource;
  JellyfinApi? _api;
  AuthState? _auth;

  Stream<Duration> get positionStream => _player.positionStream;
  Stream<double> get volumeStream => _player.volumeStream;
  Stream<bool> get shuffleModeEnabledStream => _player.shuffleModeEnabledStream;
  Stream<LoopMode> get loopModeStream => _player.loopModeStream;

  double get volume => _player.volume;
  bool get shuffleEnabled => _player.shuffleModeEnabled;
  LoopMode get loopMode => _player.loopMode;

  Future<void> setVolume(double volume) => _player.setVolume(volume.clamp(0.0, 1.0));

  Future<void> toggleShuffle() async {
    final next = !_player.shuffleModeEnabled;
    await setShuffleMode(next ? AudioServiceShuffleMode.all : AudioServiceShuffleMode.none);
  }

  @override
  Future<void> setShuffleMode(AudioServiceShuffleMode shuffleMode) async {
    final enable = shuffleMode != AudioServiceShuffleMode.none;
    if (enable == _player.shuffleModeEnabled) return;

    if (!enable) {
      await _player.setShuffleModeEnabled(false);
      return;
    }

    final currentTag = _player.sequenceState?.currentSource?.tag;
    final currentItem = (currentTag is MediaItem) ? currentTag : mediaItem.value;
    final currentPos = _player.position;

    await _player.shuffle();
    await _player.setShuffleModeEnabled(true);

    if (currentItem != null) {
      final seq = _player.sequence;
      if (seq != null) {
        final idx = seq.indexWhere(
          (s) => s.tag is MediaItem && (s.tag as MediaItem).id == currentItem.id,
        );
        if (idx != -1 && _player.currentIndex != idx) {
          await _player.seek(currentPos, index: idx);
        } else {
          await _player.seek(currentPos);
        }
      } else {
        await _player.seek(currentPos);
      }
    }
  }

  Future<void> cycleRepeatMode() async {
    final next = switch (_player.loopMode) {
      LoopMode.off => LoopMode.all,
      LoopMode.all => LoopMode.one,
      LoopMode.one => LoopMode.off,
    };
    await _player.setLoopMode(next);
  }

  JellyfinAudioHandler() {
    _notifyAudioHandlerAboutPlaybackEvents();
  }

  void configure({required JellyfinApi api, required AuthState auth}) {
    _api = api;
    _auth = auth;
  }

  @override
  Future<void> play() => _player.play();

  @override
  Future<void> pause() => _player.pause();

  @override
  Future<void> stop() => _player.stop();

  @override
  Future<void> seek(Duration position) => _player.seek(position);

  @override
  Future<void> skipToNext() => _player.seekToNext();

  @override
  Future<void> skipToPrevious() => _player.seekToPrevious();

  @override
  Future<void> skipToQueueItem(int index) async {
    await _player.seek(Duration.zero, index: index);
    if (!_player.playing) await _player.play();
  }

  Future<void> setQueueFromItems(List<BaseItem> tracks, {int startIndex = 0}) async {
    final api = _api;
    final auth = _auth;
    if (api == null || auth == null || !auth.isLoggedIn) {
      throw StateError('Audio handler not configured with auth');
    }

    final mediaItems = <MediaItem>[];
    final sources = <AudioSource>[];

    for (final t in tracks) {
      Uri? art;
      final primaryTag = t.imageTags['Primary'];
      if (primaryTag != null && primaryTag.isNotEmpty) {
        art = api.itemImageUri(itemId: t.id, tag: primaryTag, maxWidth: 512);
      }

      final mi = MediaItem(
        id: t.id,
        title: t.name,
        artist: t.artists.isNotEmpty ? t.artists.join(', ') : (t.albumArtist ?? ''),
        album: t.album,
        duration: t.duration,
        artUri: art,
      );

      mediaItems.add(mi);

      final container = (t.container ?? 'mp3').toLowerCase();

      AudioSource source;

      if (auth.disableTls) {
        source = JellyfinStreamAudioSource(
          api: api,
          itemId: t.id,
          container: container,
          tag: mi,
        );
      } else {
        final uri = Platform.isAndroid
            ? api.audioStreamUri(itemId: t.id, container: container, static: true)
            : api.universalAudioUri(
                itemId: t.id,
                userId: auth.userId,
                deviceId: auth.deviceId,
                container: 'mp3',
                audioCodec: 'mp3',
                transcodingContainer: 'mp3',
                maxStreamingBitrate: 999999999,
              );

        source = AudioSource.uri(
          uri,
          tag: mi,
          headers: api.authHeaders(),
        );
      }

      sources.add(source);
    }

    queue.add(mediaItems);

    _playlistSource = ConcatenatingAudioSource(children: sources);
    await _player.setAudioSource(
      _playlistSource!,
      initialIndex: startIndex.clamp(0, sources.length - 1),
    );
    if (_player.shuffleModeEnabled) {
      await _player.shuffle();
    }
    mediaItem.add(mediaItems.isEmpty ? null : mediaItems[startIndex.clamp(0, mediaItems.length - 1)]);
    await _player.play();
  }

  void _notifyAudioHandlerAboutPlaybackEvents() {
    _player.playbackEventStream.listen((event) {
      final playing = _player.playing;
      final q = queue.value;

      final currentTag = _player.sequenceState?.currentSource?.tag;
      final itemFromPlayer = currentTag is MediaItem ? currentTag : null;
      final prevItem = mediaItem.value;
      final stableItem = itemFromPlayer ?? prevItem;

      final mappedQueueIndex = () {
        if (stableItem != null && q.isNotEmpty) {
          final i = q.indexWhere((m) => m.id == stableItem.id);
          if (i >= 0) return i;
        }
        return event.currentIndex;
      }();

      final processingState = switch (_player.processingState) {
        ProcessingState.idle => AudioProcessingState.idle,
        ProcessingState.loading => AudioProcessingState.loading,
        ProcessingState.buffering => AudioProcessingState.buffering,
        ProcessingState.ready => AudioProcessingState.ready,
        ProcessingState.completed => AudioProcessingState.completed,
      };

      playbackState.add(
        playbackState.value.copyWith(
          controls: [
            MediaControl.skipToPrevious,
            if (playing) MediaControl.pause else MediaControl.play,
            MediaControl.skipToNext,
            MediaControl.stop,
          ],
          systemActions: const {
            MediaAction.seek,
            MediaAction.seekForward,
            MediaAction.seekBackward,
          },
          androidCompactActionIndices: const [0, 1, 2],
          processingState: processingState,
          playing: playing,
          updatePosition: event.updatePosition,
          bufferedPosition: event.bufferedPosition,
          speed: _player.speed,
          queueIndex: mappedQueueIndex,
          shuffleMode: _player.shuffleModeEnabled
              ? AudioServiceShuffleMode.all
              : AudioServiceShuffleMode.none,
          repeatMode: switch (_player.loopMode) {
            LoopMode.off => AudioServiceRepeatMode.none,
            LoopMode.all => AudioServiceRepeatMode.all,
            LoopMode.one => AudioServiceRepeatMode.one,
          },
        ),
      );

      if (itemFromPlayer != null) {
        if (prevItem?.id != itemFromPlayer.id) {
          mediaItem.add(itemFromPlayer);
        }
      } else if (prevItem == null) {
        final idx = mappedQueueIndex;
        if (idx != null && idx >= 0 && idx < q.length) {
          mediaItem.add(q[idx]);
        }
      }
    });

    _player.durationStream.listen((duration) {
      final current = mediaItem.value;
      if (current == null) return;
      if (duration == null) return;

      final existing = current.duration;
      if (existing != null && existing > Duration.zero) {
        return;
      }

      mediaItem.add(current.copyWith(duration: duration));
    });
  }
}
