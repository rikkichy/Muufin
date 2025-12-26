import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../player/playback_controller.dart';
import '../../providers.dart';
import '../../ui/app_menu.dart';
import '../shared/item_image.dart';

final _searchTermProvider = StateProvider<String>((ref) => '');

final searchResultsProvider = FutureProvider<List<BaseItem>>((ref) async {
  final term = ref.watch(_searchTermProvider);
  if (term.trim().isEmpty) return [];
  final api = ref.watch(jellyfinApiProvider);
  final auth = ref.watch(authStateProvider).value!;
  return api.search(userId: auth.userId, term: term.trim());
});

class SearchScreen extends ConsumerWidget {
  const SearchScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final term = ref.watch(_searchTermProvider);
    final results = ref.watch(searchResultsProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(
          title: const Text('Search'),
          actions: const [AppMenuButton()],
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          sliver: SliverToBoxAdapter(
            child: SearchBar(
              leading: const Icon(Icons.search),
              hintText: 'Songs, albums, artists, playlists…',
              onChanged: (v) => ref.read(_searchTermProvider.notifier).state = v,
              trailing: [
                if (term.trim().isNotEmpty)
                  IconButton(
                    tooltip: 'Clear',
                    onPressed: () => ref.read(_searchTermProvider.notifier).state = '',
                    icon: const Icon(Icons.close),
                  ),
              ],
            ),
          ),
        ),
        results.when(
          data: (items) {
            if (term.trim().isEmpty) {
              return const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: Text('Type to search')),
              );
            }
            if (items.isEmpty) {
              return const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: Text('No results')),
              );
            }
            return SliverPadding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
              sliver: SliverList.separated(
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, i) {
                  final it = items[i];
                  return Card.filled(
                    child: ListTile(
                      leading: ItemImage(item: it, size: 52),
                      title: Text(it.type == 'Audio' ? it.titleWithTrackNumber() : it.name),
                      subtitle: Text(it.subtitle()),
                      onTap: () async {
                        if (it.type == 'Audio') {
                          await ref
                              .read(playbackControllerProvider)
                              .playQueue(items.where((x) => x.type == 'Audio').toList(), startId: it.id);
                          if (context.mounted) context.push('/player');
                          return;
                        }
                        if (it.type == 'MusicAlbum') context.push('/album/${it.id}');
                        if (it.type == 'MusicArtist') context.push('/artist/${it.id}');
                        if (it.type == 'Playlist') context.push('/playlist/${it.id}');
                      },
                    ),
                  );
                },
              ),
            );
          },
          loading: () => const SliverFillRemaining(child: Center(child: CircularProgressIndicator())),
          error: (e, st) => SliverFillRemaining(child: Center(child: Text('Error: $e'))),
        ),
      ],
    );
  }
}
