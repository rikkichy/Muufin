import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/deps.dart';
import 'core/disk_cache.dart';
import 'core/jellyfin_api.dart';
import 'core/playlist_tracks_cache.dart';
import 'models/app_settings.dart';
import 'models/app_settings_notifier.dart';
import 'models/base_item.dart';
import 'models/current_user.dart';
import 'models/auth_state.dart';
import 'models/auth_state_notifier.dart';

export 'player/audio_handler_provider.dart' show audioHandlerProvider;


final authStateProvider = AsyncNotifierProvider<AuthStateNotifier, AuthState>(
  AuthStateNotifier.new,
);

final jellyfinApiProvider = Provider<JellyfinApi>((ref) {
  final auth = ref.watch(authStateProvider).valueOrNull;
  if (auth == null) {
    throw StateError('AuthState not ready');
  }
  return JellyfinApi(auth: auth);
});

final currentUserProvider = FutureProvider<CurrentUser?>((ref) async {
  final auth = ref.watch(authStateProvider).valueOrNull;
  if (auth == null || !auth.isLoggedIn) return null;
  final api = ref.watch(jellyfinApiProvider);
  try {
    final json = await api.getCurrentUser();
    return CurrentUser.fromJson(json);
  } catch (_) {
    return null;
  }
});

final diskCacheProvider = Provider<DiskCache>((ref) => DiskCache());
final appSettingsProvider = AsyncNotifierProvider<AppSettingsNotifier, AppSettings>(
  AppSettingsNotifier.new,
);

final playlistTracksCacheProvider = Provider<PlaylistTracksCache>((ref) {
  return PlaylistTracksCache(diskCache: ref.watch(diskCacheProvider));
});

class StreamInfo {
  const StreamInfo({
    required this.codec,
    required this.container,
    required this.bitDepth,
    required this.sampleRate,
  });

  final String? codec;
  final String? container;
  final int? bitDepth;
  final int? sampleRate;

  String get label {
    final parts = <String>[];

    final format = (codec?.trim().isNotEmpty == true ? codec : container)?.toUpperCase();
    if (format != null && format.isNotEmpty) parts.add(format);

    final bd = bitDepth;
    if (bd != null && bd > 0) parts.add('$bd-bit');

    final sr = sampleRate;
    if (sr != null && sr > 0) {
      final khz = sr / 1000.0;
      final show1dp = (sr % 1000) != 0;
      parts.add('${khz.toStringAsFixed(show1dp ? 1 : 0)} kHz');
    }

    return parts.join(' • ');
  }
}

final streamInfoProvider = FutureProvider.family<StreamInfo?, String>((ref, itemId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).valueOrNull;
  if (auth == null || !auth.isLoggedIn) return null;

  final item = await api.getItemForPlaybackUi(userId: auth.userId, itemId: itemId);
  if (item == null) return null;

  final container = item['Container'] as String?;

  Map<String, dynamic>? audioStream;
  final streams = item['MediaStreams'];
  if (streams is List) {
    for (final s in streams) {
      if (s is Map) {
        final type = s['Type'];
        if (type == 'Audio') {
          audioStream = Map<String, dynamic>.from(s as Map);
          break;
        }
      }
    }
  }

  return StreamInfo(
    codec: audioStream?['Codec'] as String?,
    container: container,
    bitDepth: audioStream?['BitDepth'] as int?,
    sampleRate: audioStream?['SampleRate'] as int?,
  );
});


final musicViewProvider = FutureProvider<BaseItem>((ref) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final views = await api.getUserViews(userId: auth.userId);
  final music = views.where((v) => v.collectionType == 'music').toList();
  if (music.isEmpty) {
    throw StateError('No music library found in /UserViews');
  }
  return music.first;
});

final playlistsViewProvider = FutureProvider<BaseItem?>((ref) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final views = await api.getUserViews(userId: auth.userId);
  final pl = views.where((v) => v.collectionType == 'playlists').toList();
  return pl.isEmpty ? null : pl.first;
});

final artistsProvider = FutureProvider<List<BaseItem>>((ref) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final musicView = await ref.watch(musicViewProvider.future);
  return api.getArtists(
    userId: auth.userId,
    parentId: musicView.id,
    startIndex: 0,
    limit: 200,
  );
});

final albumsProvider = FutureProvider<List<BaseItem>>((ref) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final musicView = await ref.watch(musicViewProvider.future);
  return api.getAlbums(
    userId: auth.userId,
    parentId: musicView.id,
    startIndex: 0,
    limit: 200,
  );
});

final playlistsProvider = FutureProvider<List<BaseItem>>((ref) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final maybePlView = await ref.watch(playlistsViewProvider.future);

  return api.getPlaylists(
    userId: auth.userId,
    parentId: maybePlView?.id,
    startIndex: 0,
    limit: 200,
  );
});


final itemProvider = FutureProvider.family<BaseItem?, String>((ref, id) async {
  final auth = ref.watch(authStateProvider).valueOrNull;
  if (auth == null || !auth.isLoggedIn) return null;
  final api = ref.watch(jellyfinApiProvider);
  try {
    return await api.getItem(userId: auth.userId, itemId: id);
  } catch (_) {
    return null;
  }
});