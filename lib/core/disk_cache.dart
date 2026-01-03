import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';





class DiskCache {
  DiskCache({this.subdir = 'muufin_cache'});

  final String subdir;

  Future<Directory> _cacheDir() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory('${base.path}${Platform.pathSeparator}$subdir');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  Future<File> _fileForKey(String key) async {
    final dir = await _cacheDir();
    final name = '${_fnv1a64Hex(key)}.json.gz';
    return File('${dir.path}${Platform.pathSeparator}$name');
  }

  Future<Map<String, dynamic>?> readJson(String key) async {
    final f = await _fileForKey(key);
    if (!await f.exists()) return null;

    try {
      final bytes = await f.readAsBytes();
      if (bytes.isEmpty) return null;
      
      final dynamic json = () {
        try {
          final decoded = gzip.decode(bytes);
          final text = utf8.decode(decoded);
          return jsonDecode(text);
        } catch (_) {
          
          final text = utf8.decode(bytes);
          return jsonDecode(text);
        }
      }();
      if (json is Map<String, dynamic>) return json;
      if (json is Map) return json.cast<String, dynamic>();
      return null;
    } catch (_) {
      
      return null;
    }
  }

  Future<void> writeJson(String key, Map<String, dynamic> value) async {
    final f = await _fileForKey(key);
    final text = jsonEncode(value);
    final bytes = utf8.encode(text);
    final encoded = gzip.encode(bytes);

    
    
    
    
    final tmp = File('${f.path}.tmp');
    await tmp.writeAsBytes(encoded, flush: true);
    try {
      if (await f.exists()) {
        
        await f.delete();
      }
      await tmp.rename(f.path);
    } catch (_) {
      
      await f.writeAsBytes(await tmp.readAsBytes(), flush: true);
      try {
        await tmp.delete();
      } catch (_) {
        
      }
    }
  }

  Future<void> remove(String key) async {
    final f = await _fileForKey(key);
    if (await f.exists()) {
      try {
        await f.delete();
      } catch (_) {
        
      }
    }
  }

  
  
  
  Future<void> clear() async {
    final dir = await _cacheDir();
    if (!await dir.exists()) return;
    try {
      await dir.delete(recursive: true);
    } catch (_) {
      
    }
    try {
      await dir.create(recursive: true);
    } catch (_) {
      
    }
  }
}


String _fnv1a64Hex(String input) {
  const int fnvPrime = 0x100000001b3;
  const int offsetBasis = 0xcbf29ce484222325;
  int hash = offsetBasis;
  final bytes = utf8.encode(input);
  for (final b in bytes) {
    hash ^= b;
    hash = (hash * fnvPrime) & 0xFFFFFFFFFFFFFFFF;
  }
  return hash.toRadixString(16).padLeft(16, '0');
}
