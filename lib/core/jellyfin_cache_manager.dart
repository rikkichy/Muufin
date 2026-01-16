import 'dart:io';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

class JellyfinCacheManager extends CacheManager {
  static const key = 'jellyfinCache';

  static final JellyfinCacheManager _instance = JellyfinCacheManager._();
  factory JellyfinCacheManager() => _instance;

  JellyfinCacheManager._() : super(
    Config(
      key,
      fileService: HttpFileService(
        httpClient: HttpClient()
          ..badCertificateCallback = (X509Certificate cert, String host, int port) => true,
      ),
    ),
  );
}
