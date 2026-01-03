class AuthResult {
  const AuthResult({
    required this.userId,
    required this.accessToken,
    required this.serverId,
  });

  final String userId;
  final String accessToken;
  final String serverId;

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    final user = (json['User'] as Map<String, dynamic>?) ?? const {};
    return AuthResult(
      userId: (user['Id'] as String?) ?? '',
      accessToken: (json['AccessToken'] as String?) ?? '',
      serverId: (json['ServerId'] as String?) ?? '',
    );
  }
}
