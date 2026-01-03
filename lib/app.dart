import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animations/animations.dart';
import 'package:dynamic_color/dynamic_color.dart';

import 'features/auth/login_screen.dart';
import 'features/home/home_shell.dart';
import 'features/detail/album_detail_screen.dart';
import 'features/detail/artist_detail_screen.dart';
import 'features/detail/playlist_detail_screen.dart';
import 'features/player/player_screen.dart';
import 'features/settings/account_settings_screen.dart';
import 'features/settings/appearance_settings_screen.dart';
import 'features/settings/player_settings_screen.dart';
import 'features/settings/settings_screen.dart';
import 'models/app_settings.dart';
import 'providers.dart';
import 'ui/expressive_theme.dart';

class JfMusicApp extends ConsumerWidget {
  const JfMusicApp({super.key});

  CustomTransitionPage<T> _page<T>(
    GoRouterState state,
    Widget child,
  ) {
    return CustomTransitionPage<T>(
      key: state.pageKey,
      child: child,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return SharedAxisTransition(
          animation: animation,
          secondaryAnimation: secondaryAnimation,
          transitionType: SharedAxisTransitionType.scaled,
          child: child,
        );
      },
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider);
    final settings = ref.watch(appSettingsProvider).valueOrNull ?? const AppSettings();

    final router = GoRouter(
      initialLocation: '/login',
      refreshListenable: GoRouterRefreshStream(ref.watch(authStateProvider.notifier).stream),
      routes: [
        GoRoute(
          path: '/login',
          pageBuilder: (context, state) => _page(state, const LoginScreen()),
        ),
        GoRoute(
          path: '/home',
          pageBuilder: (context, state) => _page(state, const HomeShell()),
        ),
        GoRoute(
          path: '/artist/:id',
          pageBuilder: (context, state) => _page(
            state,
            ArtistDetailScreen(artistId: state.pathParameters['id']!),
          ),
        ),
        GoRoute(
          path: '/album/:id',
          pageBuilder: (context, state) => _page(
            state,
            AlbumDetailScreen(albumId: state.pathParameters['id']!),
          ),
        ),
        GoRoute(
          path: '/playlist/:id',
          pageBuilder: (context, state) => _page(
            state,
            PlaylistDetailScreen(playlistId: state.pathParameters['id']!),
          ),
        ),
        GoRoute(
          path: '/player',
          pageBuilder: (context, state) => _page(state, const PlayerScreen()),
        ),
        GoRoute(
          path: '/settings',
          pageBuilder: (context, state) => _page(state, const SettingsScreen()),
          routes: [
            GoRoute(
              path: 'account',
              pageBuilder: (context, state) => _page(state, const AccountSettingsScreen()),
            ),
            GoRoute(
              path: 'appearance',
              pageBuilder: (context, state) => _page(state, const AppearanceSettingsScreen()),
            ),
            GoRoute(
              path: 'player',
              pageBuilder: (context, state) => _page(state, const PlayerSettingsScreen()),
            ),
          ],
        ),
      ],
      redirect: (context, state) {
        final loggedIn = auth.valueOrNull?.isLoggedIn ?? false;
        final goingToLogin = state.fullPath == '/login';
        if (!loggedIn && !goingToLogin) return '/login';
        if (loggedIn && (goingToLogin || state.fullPath == '/')) return '/home';
        return null;
      },
    );

    
    
    const seed = Color(0xFF6750A4);

    return DynamicColorBuilder(
      builder: (lightDynamic, darkDynamic) {
        final useDynamic = settings.useDynamicColor;

        final lightScheme = ((useDynamic ? lightDynamic : null) ??
                ColorScheme.fromSeed(seedColor: seed, brightness: Brightness.light))
            .harmonized();
        final darkScheme = ((useDynamic ? darkDynamic : null) ??
                ColorScheme.fromSeed(seedColor: seed, brightness: Brightness.dark))
            .harmonized();

        return MaterialApp.router(
          title: 'Muufin',
          routerConfig: router,
          theme: buildExpressiveTheme(lightScheme),
          darkTheme: buildExpressiveTheme(darkScheme),
          themeMode: settings.themeMode,
        );
      },
    );
  }
}


class GoRouterRefreshStream extends ChangeNotifier {
  GoRouterRefreshStream(Stream<dynamic> stream) {
    _sub = stream.listen((_) => notifyListeners());
  }
  late final StreamSubscription<dynamic> _sub;

  @override
  void dispose() {
    _sub.cancel();
    super.dispose();
  }
}
