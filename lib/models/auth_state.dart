class AuthState {
  const AuthState({
    required this.baseUrl,
    required this.deviceId,
    required this.clientName,
    required this.deviceName,
    required this.appVersion,
    required this.userId,
    required this.accessToken,
  });

  final String baseUrl;
  final String deviceId;
  final String clientName;
  final String deviceName;
  final String appVersion;
  final String userId;
  final String accessToken;

  bool get isLoggedIn => accessToken.isNotEmpty && userId.isNotEmpty;
}
