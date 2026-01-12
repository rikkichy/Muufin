import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class AuthStorage {
  const AuthStorage();

  static const _storage = FlutterSecureStorage();

  static const _kBaseUrl = 'baseUrl';
  static const _kUserId = 'userId';
  static const _kToken = 'accessToken';
  static const _kDeviceId = 'deviceId';
  static const _kDisableTls = 'disableTls';

  Future<void> save({
    required String baseUrl,
    required String userId,
    required String accessToken,
    required String deviceId,
    required bool disableTls,
  }) async {
    await _storage.write(key: _kBaseUrl, value: baseUrl);
    await _storage.write(key: _kUserId, value: userId);
    await _storage.write(key: _kToken, value: accessToken);
    await _storage.write(key: _kDeviceId, value: deviceId);
    await _storage.write(key: _kDisableTls, value: disableTls.toString());
  }

  Future<({String baseUrl, String userId, String accessToken, String deviceId, bool disableTls})?> read() async {
    final baseUrl = await _storage.read(key: _kBaseUrl);
    final userId = await _storage.read(key: _kUserId);
    final token = await _storage.read(key: _kToken);
    final deviceId = await _storage.read(key: _kDeviceId);
    final disableTlsStr = await _storage.read(key: _kDisableTls);

    if (baseUrl == null || userId == null || token == null || deviceId == null) return null;
    return (
      baseUrl: baseUrl,
      userId: userId,
      accessToken: token,
      deviceId: deviceId,
      disableTls: disableTlsStr == 'true',
    );
  }

  Future<void> clear() async {
    await _storage.delete(key: _kBaseUrl);
    await _storage.delete(key: _kUserId);
    await _storage.delete(key: _kToken);
    await _storage.delete(key: _kDeviceId);
    await _storage.delete(key: _kDisableTls);
  }
}
