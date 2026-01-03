import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../providers.dart';

class AccountSettingsScreen extends ConsumerWidget {
  const AccountSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider).valueOrNull;
    final userAsync = ref.watch(currentUserProvider);

    Future<void> logout() async {
      await ref.read(authStateProvider.notifier).logout();
      if (context.mounted) context.go('/login');
    }

    Future<void> clearCache() async {
      await ref.read(diskCacheProvider).clear();
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Cache cleared')),
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          ListTile(
            leading: const Icon(Icons.dns_rounded),
            title: const Text('Server'),
            subtitle: Text(auth?.baseUrl.isNotEmpty == true ? auth!.baseUrl : 'Not connected'),
          ),
          userAsync.when(
            data: (u) {
              final name = u?.name.trim();
              return ListTile(
                leading: const Icon(Icons.badge_outlined),
                title: const Text('User'),
                subtitle: Text(
                  (name != null && name.isNotEmpty) ? name : (auth?.userId.isNotEmpty == true ? auth!.userId : '—'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              );
            },
            loading: () => const ListTile(
              leading: Icon(Icons.badge_outlined),
              title: Text('User'),
              subtitle: Text('Loading…'),
            ),
            error: (_, __) => const ListTile(
              leading: Icon(Icons.badge_outlined),
              title: Text('User'),
              subtitle: Text('Unable to load'),
            ),
          ),
          const Divider(height: 24),
          ListTile(
            leading: const Icon(Icons.delete_outline_rounded),
            title: const Text('Clear cache'),
            subtitle: const Text('Playlist/track cache (does not remove downloads)'),
            onTap: clearCache,
          ),
          const Divider(height: 24),
          ListTile(
            leading: const Icon(Icons.logout_rounded),
            title: const Text('Log out'),
            onTap: logout,
          ),
        ],
      ),
    );
  }
}
