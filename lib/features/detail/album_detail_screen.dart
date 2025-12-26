import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../providers.dart';
import '../../player/playback_controller.dart';
import '../../ui/app_menu.dart';
import '../shared/item_art.dart';
import '../player/mini_player.dart';

final albumItemProvider = FutureProvider.family<BaseItem, String>((ref, albumId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.getItem(userId: auth.userId, itemId: albumId);
});

final albumTracksProvider = FutureProvider.family<List<BaseItem>, String>((ref, albumId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.getAlbumTracks(userId: auth.userId, albumId: albumId);
});

class AlbumDetailScreen extends ConsumerWidget {
  const AlbumDetailScreen({super.key, required this.albumId});
  final String albumId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final albumAsync = ref.watch(albumItemProvider(albumId));
    final tracksAsync = ref.watch(albumTracksProvider(albumId));

    return Scaffold(
      bottomNavigationBar: const MiniPlayer(),
      body: albumAsync.when(
        data: (album) => tracksAsync.when(
          data: (tracks) {
            final cs = Theme.of(context).colorScheme;

            return CustomScrollView(
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
                        ItemArt(
                          item: album,
                          borderRadius: BorderRadius.zero,
                          placeholderIcon: Icons.album_rounded,
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
                                  tag: 'img_${album.id}',
                                  child: SizedBox(
                                    width: 92,
                                    height: 92,
                                    child: ItemArt(item: album, placeholderIcon: Icons.album_rounded),
                                  ),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        album.name,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleLarge
                                            ?.copyWith(fontWeight: FontWeight.w800),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        album.subtitle(),
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
                              '${tracks.length} tracks',
                              style: Theme.of(context).textTheme.labelLarge?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            if (tracks.isNotEmpty) ...[
                              FilledButton.icon(
                                onPressed: () async {
                                  await ref.read(playbackControllerProvider).playQueue(tracks, startIndex: 0);
                                  if (context.mounted) context.push('/player');
                                },
                                icon: const Icon(Icons.play_arrow_rounded),
                                label: const Text('Play'),
                              ),
                              FilledButton.tonalIcon(
                                onPressed: () async {
                                  final shuffled = [...tracks]..shuffle();
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
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                  sliver: SliverList.separated(
                    itemCount: tracks.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, i) {
                      final t = tracks[i];
                      return Card.filled(
                        child: ListTile(
                          leading: SizedBox(
                            width: 34,
                            child: Center(
                              child: Text(
                                (t.indexNumber ?? (i + 1)).toString().padLeft(2, '0'),
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
                            await ref.read(playbackControllerProvider).playQueue(tracks, startId: t.id);
                            if (context.mounted) context.push('/player');
                          },
                        ),
                      );
                    },
                  ),
                ),
              
                const SliverToBoxAdapter(child: SizedBox(height: 124)),],
            );
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, st) => Center(child: Text('Error: $e')),
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, st) => Center(child: Text('Error: $e')),
      ),
    );
  }
}
