import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../providers.dart';
import '../../ui/app_menu.dart';
import '../shared/item_art.dart';
import '../shared/item_image.dart';
import '../player/mini_player.dart';

final artistItemProvider = FutureProvider.family<BaseItem, String>((ref, artistId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.getItem(userId: auth.userId, itemId: artistId);
});

final artistAlbumsProvider = FutureProvider.family<List<BaseItem>, String>((ref, artistId) async {
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.getArtistAlbums(userId: auth.userId, artistId: artistId);
});

class ArtistDetailScreen extends ConsumerWidget {
  const ArtistDetailScreen({super.key, required this.artistId});
  final String artistId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final artistAsync = ref.watch(artistItemProvider(artistId));
    final asyncAlbums = ref.watch(artistAlbumsProvider(artistId));
    return Scaffold(
      bottomNavigationBar: const MiniPlayer(),
      body: artistAsync.when(
        data: (artist) => asyncAlbums.when(
          data: (items) {
            final cs = Theme.of(context).colorScheme;
            return CustomScrollView(
              slivers: [
                SliverAppBar(
                  pinned: true,
                  leading: const BackButton(),
                  expandedHeight: 260,
                  actions: const [AppMenuButton()],
                  flexibleSpace: FlexibleSpaceBar(
                    collapseMode: CollapseMode.parallax,
                    background: Stack(
                      fit: StackFit.expand,
                      children: [
                        ItemArt(
                          item: artist,
                          borderRadius: BorderRadius.zero,
                          placeholderIcon: Icons.person_rounded,
                        ),
                        DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                cs.scrim.withOpacity(0.55),
                                cs.surface.withOpacity(0.2),
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
                                  tag: 'img_${artist.id}',
                                  child: SizedBox(
                                    width: 92,
                                    height: 92,
                                    child: ItemArt(
                                      item: artist,
                                      placeholderIcon: Icons.person_rounded,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Text(
                                    artist.name,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge
                                        ?.copyWith(fontWeight: FontWeight.w800),
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
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
                  sliver: SliverToBoxAdapter(
                    child: Text('Albums', style: Theme.of(context).textTheme.titleMedium),
                  ),
                ),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                  sliver: SliverList.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (context, i) {
                      final it = items[i];
                      return Card.filled(
                        child: ListTile(
                          leading: Hero(tag: 'img_${it.id}', child: ItemImage(item: it, size: 52)),
                          title: Text(it.name),
                          subtitle: Text(it.subtitle()),
                          trailing: Icon(Icons.chevron_right, color: cs.onSurfaceVariant),
                          onTap: () => context.push('/album/${it.id}'),
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
