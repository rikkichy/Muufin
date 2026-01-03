import '../models/base_item.dart';

import 'disk_cache.dart';

class PlaylistTracksCacheEntry {
  const PlaylistTracksCacheEntry({
    required this.savedAt,
    required this.items,
    required this.totalRecordCount,
    required this.reachedEnd,
  });

  final DateTime savedAt;
  final List<BaseItem> items;
  final int? totalRecordCount;
  final bool reachedEnd;
}





class PlaylistTracksCache {
  PlaylistTracksCache({DiskCache? diskCache}) : _disk = diskCache ?? DiskCache();

  final DiskCache _disk;

  static const int _schemaVersion = 2;

  
  
  
  
  
  
  static String normalizeBaseUrl(String baseUrl) {
    final trimmed = baseUrl.trim().replaceAll(RegExp(r'/+$'), '');
    try {
      final uri = Uri.parse(trimmed);
      final scheme = uri.scheme;
      final host = uri.host.toLowerCase();
      final port = uri.hasPort ? uri.port : null;
      final isDefaultPort = port != null &&
          ((scheme == 'http' && port == 80) || (scheme == 'https' && port == 443));
      final normalizedPath = uri.path.replaceAll(RegExp(r'/+$'), '');

      return Uri(
        scheme: scheme,
        userInfo: uri.userInfo,
        host: host,
        port: (port != null && !isDefaultPort) ? port : null,
        path: normalizedPath,
      ).toString();
    } catch (_) {
      return trimmed;
    }
  }

  String _key({
    required String baseUrl,
    required String userId,
    required String playlistId,
  }) {
    
    final b = normalizeBaseUrl(baseUrl);
    return 'playlistTracks:v$_schemaVersion|$b|$userId|$playlistId';
  }

  Future<PlaylistTracksCacheEntry?> read({
    required String baseUrl,
    required String userId,
    required String playlistId,
  }) async {
    
    final key = _key(baseUrl: baseUrl, userId: userId, playlistId: playlistId);
    Map<String, dynamic>? json = await _disk.readJson(key);

    
    final normalized = normalizeBaseUrl(baseUrl);
    if (json == null && normalized != baseUrl) {
      final rawKey = 'playlistTracks:v$_schemaVersion|$baseUrl|$userId|$playlistId';
      json = await _disk.readJson(rawKey);
    }
    if (json == null) return null;
    
    
    final Map<String, dynamic> payload = json;

    try {
      final savedAt = DateTime.tryParse(payload['savedAt'] as String? ?? '');
      final itemsRaw = payload['items'];
      final cachedReachedEnd = payload['reachedEnd'] as bool?;

      final items = <BaseItem>[];
      if (itemsRaw is List) {
        for (final v in itemsRaw) {
          if (v is Map<String, dynamic>) items.add(BaseItem.fromJson(v));
          if (v is Map && v is! Map<String, dynamic>) {
            items.add(BaseItem.fromJson(v.cast<String, dynamic>()));
          }
        }
      }

      final totalRecordCount = (payload['totalRecordCount'] as num?)?.toInt();
      final reachedEnd = cachedReachedEnd ??
          (totalRecordCount != null ? items.length >= totalRecordCount : false);

      return PlaylistTracksCacheEntry(
        savedAt: savedAt ?? DateTime.fromMillisecondsSinceEpoch(0),
        items: items,
        totalRecordCount: totalRecordCount,
        reachedEnd: reachedEnd,
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> write({
    required String baseUrl,
    required String userId,
    required String playlistId,
    required List<BaseItem> items,
    required int? totalRecordCount,
    required bool reachedEnd,
  }) async {
    final key = _key(baseUrl: baseUrl, userId: userId, playlistId: playlistId);
    await _disk.writeJson(key, {
      'v': _schemaVersion,
      'savedAt': DateTime.now().toUtc().toIso8601String(),
      'totalRecordCount': totalRecordCount,
      'reachedEnd': reachedEnd,
      'items': items.map((e) => e.toJson()).toList(growable: false),
    });

    
    final normalized = normalizeBaseUrl(baseUrl);
    if (normalized != baseUrl) {
      final rawKey = 'playlistTracks:v$_schemaVersion|$baseUrl|$userId|$playlistId';
      await _disk.remove(rawKey);
    }
  }

  Future<void> remove({
    required String baseUrl,
    required String userId,
    required String playlistId,
  }) async {
    final key = _key(baseUrl: baseUrl, userId: userId, playlistId: playlistId);
    await _disk.remove(key);

    final normalized = normalizeBaseUrl(baseUrl);
    if (normalized != baseUrl) {
      final rawKey = 'playlistTracks:v$_schemaVersion|$baseUrl|$userId|$playlistId';
      await _disk.remove(rawKey);
    }
  }
}
