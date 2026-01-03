class CurrentUser {
  const CurrentUser({required this.id, required this.name});

  final String id;
  final String name;

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      id: (json['Id'] as String?) ?? '',
      name: (json['Name'] as String?) ?? '',
    );
  }
}
