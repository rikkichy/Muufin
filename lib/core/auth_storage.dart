import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class AuthStorage {
  const AuthStorage();

  static const _storage = FlutterSecureStorage();

  static const _kBaseUrl = 'baseUrl';
  static const _kUserId = 'userId';
  static const _kToken = 'accessToken';
  static const _kDeviceId = 'deviceId';

  Future<void> save({
    required String baseUrl,
    required String userId,
    required String accessToken,
    required String deviceId,
  }) async {
    await _storage.write(key: _kBaseUrl, value: baseUrl);
    await _storage.write(key: _kUserId, value: userId);
    await _storage.write(key: _kToken, value: accessToken);
    await _storage.write(key: _kDeviceId, value: deviceId);
  }

  Future<({String baseUrl, String userId, String accessToken, String deviceId})?> read() async {
    final baseUrl = await _storage.read(key: _kBaseUrl);
    final userId = await _storage.read(key: _kUserId);
    final token = await _storage.read(key: _kToken);
    final deviceId = await _storage.read(key: _kDeviceId);

    if (baseUrl == null || userId == null || token == null || deviceId == null) return null;
    return (baseUrl: baseUrl, userId: userId, accessToken: token, deviceId: deviceId);
  }

  Future<void> clear() async {
    await _storage.delete(key: _kBaseUrl);
    await _storage.delete(key: _kUserId);
    await _storage.delete(key: _kToken);
    await _storage.delete(key: _kDeviceId);
  }
}
