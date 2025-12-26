import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../providers.dart';
import '../../ui/app_menu.dart';
import '../shared/item_image.dart';

class AlbumsScreen extends ConsumerWidget {
  const AlbumsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncAlbums = ref.watch(albumsProvider);

    return asyncAlbums.when(
      data: (items) => _AlbumsGrid(
        items: items,
        onTap: (it) => context.push('/album/${it.id}'),
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, st) => Center(child: Text('Error: $e')),
    );
  }
}

class _AlbumsGrid extends ConsumerWidget {
  const _AlbumsGrid({required this.items, required this.onTap});

  final List<BaseItem> items;
  final void Function(BaseItem) onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final width = MediaQuery.sizeOf(context).width;
    final cs = Theme.of(context).colorScheme;
    final cross = width >= 1200 ? 6 : (width >= 900 ? 5 : (width >= 600 ? 3 : 2));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(albumsProvider);
        await ref.read(albumsProvider.future);
      },
      child: CustomScrollView(
        slivers: [
        SliverAppBar.large(
          title: const Text('Albums'),
          actions: const [AppMenuButton()],
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          sliver: SliverGrid(
            delegate: SliverChildBuilderDelegate(
              (context, i) {
                final it = items[i];
                return InkWell(
                  borderRadius: BorderRadius.circular(28),
                  onTap: () => onTap(it),
                  child: Card.filled(
                    child: Padding(
                      padding: const EdgeInsets.all(10),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Center(
                            child: Hero(
                              tag: 'img_${it.id}',
                              child: ItemImage(item: it, size: 120),
                            ),
                          ),
                          const SizedBox(height: 10),
                          Text(
                            it.name,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            it.subtitle(),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.labelMedium?.copyWith(color: cs.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
              childCount: items.length,
            ),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: cross,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.85,
            ),
          ),
        ),
        ],
      ),
    );
  }
}
