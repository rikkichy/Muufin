import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../core/auth_storage.dart';
import '../core/deps.dart';
import '../core/jellyfin_api.dart';
import 'auth_state.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import '../core/disk_cache.dart';
import '../player/audio_handler_provider.dart';

class AuthStateNotifier extends AsyncNotifier<AuthState> {
  final _streamController = StreamController<void>.broadcast();
  Stream<void> get stream => _streamController.stream;

  @override
  Future<AuthState> build() async {
    const clientName = 'Muufin';
    final deviceName = Platform.isWindows
        ? 'Windows'
        : Platform.isAndroid
            ? 'Android'
            : 'Unknown';
    const appVersion = '0.1.0';

    final storage = ref.read(authStorageProvider);
    final saved = await storage.read();

    if (saved == null) {
      final deviceId = const Uuid().v4();
      return AuthState(
        baseUrl: '',
        deviceId: deviceId,
        clientName: clientName,
        deviceName: deviceName,
        appVersion: appVersion,
        userId: '',
        accessToken: '',
      );
    }

    return AuthState(
      baseUrl: saved.baseUrl,
      deviceId: saved.deviceId,
      clientName: clientName,
      deviceName: deviceName,
      appVersion: appVersion,
      userId: saved.userId,
      accessToken: saved.accessToken,
    );
  }

  Future<void> login({
    required String baseUrl,
    required String username,
    required String password,
  }) async {
    
    
    final prev = state.value ?? await future;
    state = const AsyncLoading();
    final authApi = JellyfinApi(
      auth: AuthState(
        baseUrl: baseUrl,
        deviceId: prev.deviceId,
        clientName: prev.clientName,
        deviceName: prev.deviceName,
        appVersion: prev.appVersion,
        userId: '',
        accessToken: '',
      ),
    );

    try {
      final result = await authApi.authenticateByName(username: username, password: password);

      final next = AuthState(
        baseUrl: baseUrl,
        deviceId: prev.deviceId,
        clientName: prev.clientName,
        deviceName: prev.deviceName,
        appVersion: prev.appVersion,
        userId: result.userId,
        accessToken: result.accessToken,
      );

      await ref.read(authStorageProvider).save(
            baseUrl: next.baseUrl,
            userId: next.userId,
            accessToken: next.accessToken,
            deviceId: next.deviceId,
          );

      state = AsyncData(next);
      _streamController.add(null);
    } catch (e, st) {
      state = AsyncError(e, st);
      rethrow;
    }
  }

  Future<void> logout() async {

try {
  final handler = ref.read(audioHandlerProvider);
  await handler.stop();
  handler.queue.add(const []);
  handler.mediaItem.add(null);
} catch (_) {
  
}


try {
  await DiskCache().clear();
} catch (_) {
  
}
try {
  await DefaultCacheManager().emptyCache();
} catch (_) {
  
}


await ref.read(authStorageProvider).clear();

final prev = await future;
final fresh = AuthState(
  baseUrl: '',
  deviceId: prev.deviceId,
  clientName: prev.clientName,
  deviceName: prev.deviceName,
  appVersion: prev.appVersion,
  userId: '',
  accessToken: '',
);
state = AsyncData(fresh);
_streamController.add(null);}
}