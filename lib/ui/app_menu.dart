import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers.dart';

/// Common overflow menu used across screens.
///
/// Keeps toolbars clean (Expressive principle: clear hierarchy + containment).
class AppMenuButton extends ConsumerWidget {
  const AppMenuButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return PopupMenuButton<_MenuAction>(
      tooltip: 'Menu',
      itemBuilder: (context) => const [
        PopupMenuItem(
          value: _MenuAction.logout,
          child: ListTile(
            dense: true,
            leading: Icon(Icons.logout_rounded),
            title: Text('Logout'),
          ),
        ),
      ],
      onSelected: (value) async {
        switch (value) {
          case _MenuAction.logout:
            await ref.read(authStateProvider.notifier).logout();
        }
      },
    );
  }
}

enum _MenuAction { logout }
