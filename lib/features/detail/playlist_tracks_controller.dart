import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/jellyfin_api.dart';
import '../../core/playlist_tracks_cache.dart';
import '../../models/auth_state.dart';
import '../../models/base_item.dart';
import '../../providers.dart';

class PlaylistTracksState {
  const PlaylistTracksState({
    required this.items,
    required this.totalRecordCount,
    required this.isInitialLoading,
    required this.isLoadingMore,
    required this.isRefreshing,
    required this.isEnsuringAll,
    required this.reachedEnd,
    required this.error,
    required this.cacheSavedAt,
    required this.fromCache,
  });

  final List<BaseItem> items;
  final int? totalRecordCount;
  final bool isInitialLoading;
  final bool isLoadingMore;
  final bool isRefreshing;
  final bool isEnsuringAll;
  final bool reachedEnd;
  final String? error;
  final DateTime? cacheSavedAt;
  final bool fromCache;

  bool get hasMore {
    if (reachedEnd) return false;
    final total = totalRecordCount;
    if (total == null) return true; 
    return items.length < total;
  }

  double? get ensureProgress {
    final total = totalRecordCount;
    if (!isEnsuringAll || total == null || total <= 0) return null;
    return (items.length / total).clamp(0.0, 1.0);
  }

  PlaylistTracksState copyWith({
    List<BaseItem>? items,
    int? totalRecordCount,
    bool? isInitialLoading,
    bool? isLoadingMore,
    bool? isRefreshing,
    bool? isEnsuringAll,
    bool? reachedEnd,
    String? error,
    DateTime? cacheSavedAt,
    bool? fromCache,
  }) {
    return PlaylistTracksState(
      items: items ?? this.items,
      totalRecordCount: totalRecordCount ?? this.totalRecordCount,
      isInitialLoading: isInitialLoading ?? this.isInitialLoading,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      isEnsuringAll: isEnsuringAll ?? this.isEnsuringAll,
      reachedEnd: reachedEnd ?? this.reachedEnd,
      error: error,
      cacheSavedAt: cacheSavedAt ?? this.cacheSavedAt,
      fromCache: fromCache ?? this.fromCache,
    );
  }
}

final playlistTracksControllerProvider = StateNotifierProvider.autoDispose
    .family<PlaylistTracksController, PlaylistTracksState, String>((ref, playlistId) {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  final cache = ref.watch(playlistTracksCacheProvider);
  final c = PlaylistTracksController(
    api: api,
    auth: auth,
    cache: cache,
    playlistId: playlistId,
  );
  c.init();
  return c;
});

class PlaylistTracksController extends StateNotifier<PlaylistTracksState> {
  PlaylistTracksController({
    required JellyfinApi api,
    required AuthState auth,
    required PlaylistTracksCache cache,
    required this.playlistId,
  })  : _api = api,
        _auth = auth,
        _cache = cache,
        super(const PlaylistTracksState(
          items: [],
          totalRecordCount: null,
          isInitialLoading: true,
          isLoadingMore: false,
          isRefreshing: false,
          isEnsuringAll: false,
          reachedEnd: false,
          error: null,
          cacheSavedAt: null,
          fromCache: false,
        ));

  final JellyfinApi _api;
  final AuthState _auth;
  final PlaylistTracksCache _cache;
  final String playlistId;

  
  static const int _pageSize = 250;

  Timer? _saveDebounce;
  bool _disposed = false;

  Future<void> init() async {
    
    final cached = await _cache.read(
      baseUrl: _auth.baseUrl,
      userId: _auth.userId,
      playlistId: playlistId,
    );

    if (_disposed) return;

    if (cached != null && cached.items.isNotEmpty) {
      final cachedTotal = cached.totalRecordCount;
      final cachedEnd = cached.reachedEnd || (cachedTotal != null && cached.items.length >= cachedTotal);
      state = state.copyWith(
        items: cached.items,
        totalRecordCount: cachedTotal,
        reachedEnd: cachedEnd,
        cacheSavedAt: cached.savedAt,
        fromCache: true,
        isInitialLoading: false,
        error: null,
      );
    }

    
    unawaited(refresh());
  }

  @override
  void dispose() {
    
    unawaited(_saveCacheNow());

    _disposed = true;
    super.dispose();
  }

  Future<void> refresh() async {
    if (_disposed) return;
    if (state.isRefreshing) return;

    state = state.copyWith(
      isRefreshing: true,
      isInitialLoading: state.items.isEmpty,
      error: null,
    );

    try {
      final page = await _api.getPlaylistTracksPage(
        userId: _auth.userId,
        playlistId: playlistId,
        startIndex: 0,
        limit: _pageSize,
      );

      if (_disposed) return;

      var nextItems = _mergeCachedWithPage(state.items, page.items);
      final nextTotal = page.totalRecordCount ?? state.totalRecordCount;
      if (nextTotal != null && nextItems.length > nextTotal) {
        nextItems = nextItems.take(nextTotal).toList(growable: false);
      }

      state = state.copyWith(
        items: nextItems,
        totalRecordCount: nextTotal,
        reachedEnd: () {
          if (page.items.isEmpty) return true;
          if (nextTotal != null) return nextItems.length >= nextTotal;
          return state.reachedEnd;
        }(),
        isRefreshing: false,
        isInitialLoading: false,
        fromCache: false,
        error: null,
      );

      _scheduleCacheWrite();
    } catch (e) {
      if (_disposed) return;
      state = state.copyWith(
        isRefreshing: false,
        isInitialLoading: false,
        error: e.toString(),
      );
    }
  }

  List<BaseItem> _mergeCachedWithPage(List<BaseItem> cached, List<BaseItem> pageItems) {
    if (cached.isEmpty) return pageItems;
    if (pageItems.isEmpty) return pageItems;
    if (cached.length <= pageItems.length) return pageItems;
    return [
      ...pageItems,
      ...cached.skip(pageItems.length),
    ];
  }

  Future<void> loadMore() async {
    if (_disposed) return;
    if (state.isLoadingMore || state.isRefreshing) return;
    if (!state.hasMore) return;

    state = state.copyWith(isLoadingMore: true, error: null);

    try {
      final startIndex = state.items.length;
      final page = await _api.getPlaylistTracksPage(
        userId: _auth.userId,
        playlistId: playlistId,
        startIndex: startIndex,
        limit: _pageSize,
      );

      if (_disposed) return;

      final merged = [...state.items, ...page.items];
      final mergedTotal = page.totalRecordCount ?? state.totalRecordCount;
      
      
      final end = page.items.isEmpty ||
          page.items.length < _pageSize ||
          (mergedTotal != null && merged.length >= mergedTotal);
      state = state.copyWith(
        items: merged,
        totalRecordCount: mergedTotal,
        reachedEnd: end,
        isLoadingMore: false,
        isInitialLoading: false,
      );

      _scheduleCacheWrite();
    } catch (e) {
      if (_disposed) return;
      state = state.copyWith(isLoadingMore: false, error: e.toString());
    }
  }

  
  Future<List<BaseItem>> ensureAllLoaded() async {
    if (_disposed) return state.items;
    if (state.isEnsuringAll) return state.items;

    
    if (!state.hasMore && !state.isInitialLoading && !state.isLoadingMore) {
      return state.items;
    }

    state = state.copyWith(isEnsuringAll: true, error: null);
    try {
      
      
      
      final cached = await _cache.read(
        baseUrl: _auth.baseUrl,
        userId: _auth.userId,
        playlistId: playlistId,
      );

      if (!_disposed && cached != null && cached.items.isNotEmpty) {
        final cachedTotal = cached.totalRecordCount;
        final cachedComplete = cached.reachedEnd || (cachedTotal != null && cached.items.length >= cachedTotal);
        if (cachedComplete) {
          state = state.copyWith(
            items: cached.items,
            totalRecordCount: cachedTotal,
            reachedEnd: true,
            cacheSavedAt: cached.savedAt,
            fromCache: true,
            isEnsuringAll: false,
            isInitialLoading: false,
            isLoadingMore: false,
            isRefreshing: false,
            error: null,
          );
          return state.items;
        }
      }

      
      while (!_disposed && state.hasMore) {
        await loadMore();
        
        
        await Future<void>.delayed(const Duration(milliseconds: 1));
      }

      if (_disposed) return state.items;

      state = state.copyWith(isEnsuringAll: false);

      
      
      await _saveCacheNow();

      return state.items;
    } catch (e) {
      if (_disposed) return state.items;
      state = state.copyWith(isEnsuringAll: false, error: e.toString());
      return state.items;
    }
  }

  Future<void> _saveCacheNow() async {
    _saveDebounce?.cancel();
    if (_disposed) return;
    try {
      final total = state.totalRecordCount;
      final complete = state.reachedEnd || (total != null && state.items.length >= total);
      await _cache.write(
        baseUrl: _auth.baseUrl,
        userId: _auth.userId,
        playlistId: playlistId,
        items: state.items,
        totalRecordCount: total,
        reachedEnd: complete,
      );
      if (_disposed) return;
      state = state.copyWith(cacheSavedAt: DateTime.now().toUtc());
    } catch (_) {
      
    }
  }

  void _scheduleCacheWrite() {
    _saveDebounce?.cancel();
    _saveDebounce = Timer(const Duration(milliseconds: 700), () async {
      if (_disposed) return;
      try {
        await _cache.write(
          baseUrl: _auth.baseUrl,
          userId: _auth.userId,
          playlistId: playlistId,
          items: state.items,
          totalRecordCount: state.totalRecordCount,
          reachedEnd: state.reachedEnd,
        );
      } catch (_) {
        
      }
    });
  }
}
