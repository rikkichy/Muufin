import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/jellyfin_cache_manager.dart';
import '../../models/base_item.dart';
import '../../providers.dart';
import '../../player/playback_controller.dart';
import '../../ui/app_menu.dart';
import '../shared/item_art.dart';
import '../player/mini_player.dart';

import 'playlist_tracks_controller.dart';

final playlistItemProvider = FutureProvider.family<BaseItem, String>((ref, playlistId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.getItem(userId: auth.userId, itemId: playlistId);
});

class PlaylistDetailScreen extends ConsumerWidget {
  const PlaylistDetailScreen({super.key, required this.playlistId});
  final String playlistId;

  Future<List<BaseItem>> _ensureAllTracks(BuildContext context, WidgetRef ref) async {
    final current = ref.read(playlistTracksControllerProvider(playlistId));
    if (!current.hasMore && !current.isInitialLoading && !current.isLoadingMore) {
      return current.items;
    }



    final future = ref.read(playlistTracksControllerProvider(playlistId).notifier).ensureAllLoaded();

    var dialogShown = false;
    final timer = Timer(const Duration(milliseconds: 200), () {
      if (!context.mounted) return;
      dialogShown = true;
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) {
          return Consumer(
            builder: (context, ref, _) {
              final st = ref.watch(playlistTracksControllerProvider(playlistId));
              final total = st.totalRecordCount;
              return AlertDialog(
                title: const Text('Loading playlist…'),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    LinearProgressIndicator(value: st.ensureProgress),
                    const SizedBox(height: 12),
                    Text(
                      total != null
                          ? '${st.items.length} / $total'
                          : '${st.items.length} loaded',
                    ),
                  ],
                ),
              );
            },
          );
        },
      );
    });

    final tracks = await future;
    timer.cancel();
    if (dialogShown && context.mounted) {
      Navigator.of(context).pop();
    }
    return tracks;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playlistAsync = ref.watch(playlistItemProvider(playlistId));
    final tracksState = ref.watch(playlistTracksControllerProvider(playlistId));
    final tracks = tracksState.items;

    final total = tracksState.totalRecordCount;
    final tracksLabel = (total == null || total == tracks.length)
        ? '${tracks.length} tracks'
        : '${tracks.length} / $total tracks';

    return Scaffold(
      bottomNavigationBar: const MiniPlayer(),
      body: playlistAsync.when(
        data: (playlist) {
          final cs = Theme.of(context).colorScheme;
          return RefreshIndicator(
            onRefresh: () => ref.read(playlistTracksControllerProvider(playlistId).notifier).refresh(),
            child: NotificationListener<ScrollNotification>(
              onNotification: (n) {
                if (!tracksState.hasMore) return false;
                if (tracksState.isLoadingMore || tracksState.isRefreshing) return false;
                final m = n.metrics;
                if (m.maxScrollExtent <= 0) return false;
                if (m.pixels >= (m.maxScrollExtent - 800)) {
                  ref.read(playlistTracksControllerProvider(playlistId).notifier).loadMore();
                }
                return false;
              },
              child: CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                slivers: [
                  SliverAppBar(
                    pinned: true,
                    leading: const BackButton(),
                    expandedHeight: 280,
                    actions: const [AppMenuButton()],
                    flexibleSpace: FlexibleSpaceBar(
                      collapseMode: CollapseMode.parallax,
                      background: Stack(
                        fit: StackFit.expand,
                        children: [
                          ImageFiltered(
                            imageFilter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                            child: ItemArt(
                              item: playlist,
                              borderRadius: BorderRadius.zero,
                              placeholderIcon: Icons.queue_music_rounded,
                            ),
                          ),
                          DecoratedBox(
                            decoration: BoxDecoration(
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [
                                  cs.scrim.withOpacity(0.55),
                                  cs.surface.withOpacity(0.15),
                                  cs.surface,
                                ],
                              ),
                            ),
                          ),
                          Align(
                            alignment: Alignment.bottomLeft,
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Hero(
                                    tag: 'img_${playlist.id}',
                                    child: SizedBox(
                                      width: 92,
                                      height: 92,
                                      child: ItemArt(
                                        item: playlist,
                                        placeholderIcon: Icons.queue_music_rounded,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Column(
                                      mainAxisSize: MainAxisSize.min,
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          playlist.name,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: Theme.of(context)
                                              .textTheme
                                              .titleLarge
                                              ?.copyWith(fontWeight: FontWeight.w800),
                                        ),
                                        const SizedBox(height: 4),
                                        Text(
                                          playlist.subtitle(),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: Theme.of(context)
                                              .textTheme
                                              .bodyMedium
                                              ?.copyWith(color: cs.onSurfaceVariant),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
                    sliver: SliverToBoxAdapter(
                      child: Card.filled(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Wrap(
                            spacing: 10,
                            runSpacing: 10,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              Text(
                                tracksLabel,
                                style: Theme.of(context)
                                    .textTheme
                                    .labelLarge
                                    ?.copyWith(color: cs.onSurfaceVariant),
                              ),
                              if (tracks.isNotEmpty) ...[
                                FilledButton.icon(
                                  onPressed: () async {
                                    final all = await _ensureAllTracks(context, ref);
                                    await ref.read(playbackControllerProvider).playQueue(all, startIndex: 0);
                                    if (context.mounted) context.push('/player');
                                  },
                                  icon: const Icon(Icons.play_arrow_rounded),
                                  label: const Text('Play'),
                                ),
                                FilledButton.tonalIcon(
                                  onPressed: () async {
                                    final all = await _ensureAllTracks(context, ref);
                                    final shuffled = [...all]..shuffle();
                                    await ref.read(playbackControllerProvider).playQueue(shuffled, startIndex: 0);
                                    if (context.mounted) context.push('/player');
                                  },
                                  icon: const Icon(Icons.shuffle_rounded),
                                  label: const Text('Shuffle'),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),

                  if (tracksState.error != null)
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                      sliver: SliverToBoxAdapter(
                        child: Card.filled(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(
                              'Error: ${tracksState.error}',
                              style: Theme.of(context)
                                  .textTheme
                                  .bodyMedium
                                  ?.copyWith(color: cs.error),
                            ),
                          ),
                        ),
                      ),
                    ),

                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                    sliver: SliverList.separated(
                      itemCount: tracks.length + (tracksState.hasMore ? 1 : 0),
                      separatorBuilder: (_, __) => const SizedBox(height: 10),
                      itemBuilder: (context, i) {
                        if (i >= tracks.length) {
                          if (tracksState.isLoadingMore || tracksState.isInitialLoading) {
                            return const Padding(
                              padding: EdgeInsets.symmetric(vertical: 18),
                              child: Center(child: CircularProgressIndicator()),
                            );
                          }
                          return const SizedBox.shrink();
                        }

                        final t = tracks[i];
                        return Card.filled(
                          child: ListTile(
                            leading: SizedBox(
                              width: 34,
                              child: Center(
                                child: Text(
                                  (i + 1).toString().padLeft(2, '0'),
                                  style: Theme.of(context)
                                      .textTheme
                                      .labelLarge
                                      ?.copyWith(fontWeight: FontWeight.w800),
                                ),
                              ),
                            ),
                            title: Text(t.name),
                            subtitle: Text('${t.subtitle()} • ${t.durationLabel()}'),
                            onTap: () async {
                              final all = await _ensureAllTracks(context, ref);
                              await ref.read(playbackControllerProvider).playQueue(all, startId: t.id);
                              if (context.mounted) context.push('/player');
                            },
                          ),
                        );
                      },
                    ),
                  ),

                  const SliverToBoxAdapter(child: SizedBox(height: 124)),
                ],
              ),
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, st) => Center(child: Text('Error: $e')),
      ),
    );
  }
}
