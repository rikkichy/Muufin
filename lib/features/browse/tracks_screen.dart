import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../models/base_item.dart';
import '../../providers.dart';
import '../../ui/app_menu.dart';
import '../shared/item_image.dart';





class TracksScreen extends ConsumerStatefulWidget {
  const TracksScreen({super.key});

  @override
  ConsumerState<TracksScreen> createState() => _TracksScreenState();
}

enum _LibrarySection { playlists, albums, artists }

class _TracksScreenState extends ConsumerState<TracksScreen> {
  _LibrarySection _section = _LibrarySection.playlists;

  final _scroll = {
    _LibrarySection.playlists: ScrollController(),
    _LibrarySection.albums: ScrollController(),
    _LibrarySection.artists: ScrollController(),
  };

  @override
  void dispose() {
    for (final c in _scroll.values) {
      c.dispose();
    }
    super.dispose();
  }

  Widget _sectionSwitcher() {
    
    
    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerLeft,
      child: SegmentedButton<_LibrarySection>(
        showSelectedIcon: false,
        segments: const [
          ButtonSegment(value: _LibrarySection.playlists, label: Text('Playlists')),
          ButtonSegment(value: _LibrarySection.albums, label: Text('Albums')),
          ButtonSegment(value: _LibrarySection.artists, label: Text('Artists')),
        ],
        selected: {_section},
        onSelectionChanged: (s) {
          if (s.isEmpty) return;
          setState(() => _section = s.first);
        },
      ),
    );
  }

  Widget _sectionHeader() {
    
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      child: Align(
        alignment: Alignment.centerLeft,
        child: _sectionSwitcher(),
      ),
    );
  }

  Future<void> _refreshCurrent() async {
    switch (_section) {
      case _LibrarySection.playlists:
        ref.invalidate(playlistsProvider);
        await ref.read(playlistsProvider.future);
        return;
      case _LibrarySection.albums:
        ref.invalidate(albumsProvider);
        await ref.read(albumsProvider.future);
        return;
      case _LibrarySection.artists:
        ref.invalidate(artistsProvider);
        await ref.read(artistsProvider.future);
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final width = MediaQuery.sizeOf(context).width;

    AsyncValue<List<BaseItem>> data;
    switch (_section) {
      case _LibrarySection.playlists:
        data = ref.watch(playlistsProvider);
        break;
      case _LibrarySection.albums:
        data = ref.watch(albumsProvider);
        break;
      case _LibrarySection.artists:
        data = ref.watch(artistsProvider);
        break;
    }

    final cross = width >= 1200 ? 6 : (width >= 900 ? 5 : (width >= 600 ? 3 : 2));

    return RefreshIndicator(
      onRefresh: _refreshCurrent,
      child: CustomScrollView(
        controller: _scroll[_section],
        slivers: [
          
          SliverAppBar.large(
            title: const Text('Muufin'),
            actions: const [AppMenuButton()],
          ),
          SliverToBoxAdapter(child: _sectionHeader()),
          data.when(
            data: (items) {
              if (items.isEmpty) {
                return const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: Text('Nothing here')),
                );
              }

              switch (_section) {
                case _LibrarySection.playlists:
                  return SliverPadding(
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
                            onTap: () => context.push('/playlist/${it.id}'),
                          ),
                        );
                      },
                    ),
                  );

                case _LibrarySection.artists:
                  return SliverPadding(
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
                            onTap: () => context.push('/artist/${it.id}'),
                          ),
                        );
                      },
                    ),
                  );

                case _LibrarySection.albums:
                  return SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                    sliver: SliverGrid(
                      delegate: SliverChildBuilderDelegate(
                        (context, i) {
                          final it = items[i];
                          return InkWell(
                            borderRadius: BorderRadius.circular(28),
                            onTap: () => context.push('/album/${it.id}'),
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
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleSmall
                                          ?.copyWith(fontWeight: FontWeight.w700),
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      it.subtitle(),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelMedium
                                          ?.copyWith(color: cs.onSurfaceVariant),
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
                  );
              }
            },
            loading: () => const SliverFillRemaining(child: Center(child: CircularProgressIndicator())),
            error: (e, st) => SliverFillRemaining(child: Center(child: Text('Error: $e'))),
          ),
          
          const SliverToBoxAdapter(child: SizedBox(height: 6)),
        ],
      ),
    );
  }
}
