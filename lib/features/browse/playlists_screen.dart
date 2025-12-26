import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../providers.dart';
import '../../ui/app_menu.dart';
import '../shared/item_image.dart';

class PlaylistsScreen extends ConsumerWidget {
  const PlaylistsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPlaylists = ref.watch(playlistsProvider);

    return asyncPlaylists.when(
      data: (items) => _PlaylistsList(
        items: items,
        onTap: (it) => context.push('/playlist/${it.id}'),
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, st) => Center(child: Text('Error: $e')),
    );
  }
}

class _PlaylistsList extends ConsumerWidget {
  const _PlaylistsList({required this.items, required this.onTap});

  final List<BaseItem> items;
  final void Function(BaseItem) onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cs = Theme.of(context).colorScheme;

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(playlistsProvider);
        await ref.read(playlistsProvider.future);
      },
      child: CustomScrollView(
        slivers: [
        SliverAppBar.large(
          title: const Text('Playlists'),
          actions: const [AppMenuButton()],
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
                  leading: Hero(
                    tag: 'img_${it.id}',
                    child: ItemImage(item: it, size: 52),
                  ),
                  title: Text(it.name, style: Theme.of(context).textTheme.titleMedium),
                  subtitle: Text(it.subtitle()),
                  trailing: Icon(Icons.chevron_right, color: cs.onSurfaceVariant),
                  onTap: () => onTap(it),
                ),
              );
            },
          ),
        ),
        ],
      ),
    );
  }
}
