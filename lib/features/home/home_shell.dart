import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../browse/tracks_screen.dart';
import '../browse/search_screen.dart';
import '../../providers.dart';
import '../player/mini_player.dart';
import 'nav_pill.dart';

class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final pages = const [
      TracksScreen(),
      SearchScreen(),
    ];

    final width = MediaQuery.sizeOf(context).width;
    final wide = width >= 900;

    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      
      body: Row(
        children: [
          if (wide)
            SafeArea(
              right: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 0, 12),
                child: Material(
                  color: cs.surfaceVariant,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                  child: NavigationRail(
                    selectedIndex: _index,
                    onDestinationSelected: (i) => setState(() => _index = i),
                    extended: true,
                    labelType: NavigationRailLabelType.none,
                    destinations: const [
                      NavigationRailDestination(icon: Icon(Icons.music_note), label: Text('Library')),
                      NavigationRailDestination(icon: Icon(Icons.search), label: Text('Search')),
                    ],
                    trailing: Expanded(
                      child: Align(
                        alignment: Alignment.bottomLeft,
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: FilledButton.tonalIcon(
                            onPressed: () => ref.read(authStateProvider.notifier).logout(),
                            icon: const Icon(Icons.logout),
                            label: const Text('Logout'),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          Expanded(
            child: Column(
              children: [
                Expanded(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 260),
                    switchInCurve: Curves.easeOutBack,
                    switchOutCurve: Curves.easeIn,
                    child: KeyedSubtree(
                      key: ValueKey(_index),
                      child: pages[_index],
                    ),
                  ),
                ),
                const MiniPlayer(),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: wide
          ? null
          : SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                child: NavPill(
                  index: _index,
                  onChanged: (i) => setState(() => _index = i),
                ),
              ),
            ),
    );
  }
}
