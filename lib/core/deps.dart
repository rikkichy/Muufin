import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'auth_storage.dart';

final authStorageProvider = Provider<AuthStorage>((ref) => const AuthStorage());
