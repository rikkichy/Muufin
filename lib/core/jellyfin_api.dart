import 'dart:convert';

import 'package:dio/dio.dart';

import '../models/auth_state.dart';
import '../models/auth_result.dart';
import '../models/base_item.dart';
import '../models/query_result.dart';

class JellyfinApi {
  JellyfinApi({required this.auth})
      : _dio = Dio(
          BaseOptions(
            baseUrl: auth.baseUrl.endsWith('/') ? auth.baseUrl.substring(0, auth.baseUrl.length - 1) : auth.baseUrl,
            connectTimeout: const Duration(seconds: 10),
            receiveTimeout: const Duration(seconds: 30),
            headers: {
              'Accept': 'application/json',
            },
          ),
        ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          options.headers['Authorization'] = buildAuthorizationHeader(
            client: auth.clientName,
            device: auth.deviceName,
            deviceId: auth.deviceId,
            version: auth.appVersion,
            token: auth.accessToken.isEmpty ? null : auth.accessToken,
          );
          handler.next(options);
        },
      ),
    );
  }

  final AuthState auth;
  final Dio _dio;

  
  Dio get dio => _dio;

  static String buildAuthorizationHeader({
    required String client,
    required String device,
    required String deviceId,
    required String version,
    String? token,
  }) {
    
    
    final parts = <String>[
      'Client="$client"',
      'Device="$device"',
      'DeviceId="$deviceId"',
      'Version="$version"',
      if (token != null && token.isNotEmpty) 'Token="$token"',
    ];
    return 'MediaBrowser ' + parts.join(', ');
  }

  Future<AuthResult> authenticateByName({
    required String username,
    required String password,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/Users/AuthenticateByName',
      data: {'Username': username, 'Pw': password},
      options: Options(
        headers: {
          
          'Authorization': buildAuthorizationHeader(
            client: auth.clientName,
            device: auth.deviceName,
            deviceId: auth.deviceId,
            version: auth.appVersion,
            token: null,
          ),
        },
      ),
    );

    final data = res.data ?? const {};
    return AuthResult.fromJson(data);
  }

  
  
  
  Future<Map<String, dynamic>> getCurrentUser() async {
    final res = await _dio.get<Map<String, dynamic>>('/Users/Me');
    return res.data ?? const {};
  }

  Future<List<BaseItem>> getUserViews({
    required String userId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/UserViews',
      queryParameters: {'userId': userId},
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getArtists({
    required String userId,
    String? parentId,
    required int startIndex,
    required int limit,
    String? searchTerm,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Artists',
      queryParameters: {
        'userId': userId,
        if (parentId != null) 'parentId': parentId,
        'startIndex': startIndex,
        'limit': limit,
        if (searchTerm != null && searchTerm.isNotEmpty) 'searchTerm': searchTerm,
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getAlbums({
    required String userId,
    String? parentId,
    required int startIndex,
    required int limit,
    String? searchTerm,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        if (parentId != null) 'parentId': parentId,
        'includeItemTypes': 'MusicAlbum',
        'recursive': true,
        'startIndex': startIndex,
        'limit': limit,
        if (searchTerm != null && searchTerm.isNotEmpty) 'searchTerm': searchTerm,
        'sortBy': 'SortName',
        'sortOrder': 'Ascending',
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getAlbumTracks({
    required String userId,
    required String albumId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        'parentId': albumId,
        'includeItemTypes': 'Audio',
        'recursive': true,
        'sortBy': 'IndexNumber,SortName',
        'sortOrder': 'Ascending',
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getArtistAlbums({
    required String userId,
    required String artistId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        'includeItemTypes': 'MusicAlbum',
        'recursive': true,
        'artistIds': artistId,
        'sortBy': 'SortName',
        'sortOrder': 'Ascending',
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getPlaylists({
    required String userId,
    String? parentId,
    required int startIndex,
    required int limit,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        if (parentId != null) 'parentId': parentId,
        'includeItemTypes': 'Playlist',
        'recursive': true,
        'startIndex': startIndex,
        'limit': limit,
        'sortBy': 'SortName',
        'sortOrder': 'Ascending',
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  Future<List<BaseItem>> getPlaylistTracks({
    required String userId,
    required String playlistId,
  }) async {
    
    
    const pageSize = 1000;
    final out = <BaseItem>[];
    int start = 0;
    int? total;

    while (true) {
      final page = await getPlaylistTracksPage(
        userId: userId,
        playlistId: playlistId,
        startIndex: start,
        limit: pageSize,
      );
      total ??= page.totalRecordCount;
      out.addAll(page.items);

      start += page.items.length;
      if (page.items.isEmpty) break;
      if (total != null && start >= total) break;
    }

    return out;
  }

  Future<BaseItemQueryResult> getPlaylistTracksPage({
    required String userId,
    required String playlistId,
    required int startIndex,
    required int limit,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Playlists/$playlistId/Items',
      queryParameters: {
        'userId': userId,
        'startIndex': startIndex,
        'limit': limit,
        
        'enableUserData': false,
        'enableImages': true,
        'enableImageTypes': 'Primary',
        'imageTypeLimit': 1,
      },
    );
    return BaseItemQueryResult.fromJson(res.data ?? const {});
  }

  Future<List<BaseItem>> search({
    required String userId,
    required String term,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        'searchTerm': term,
        'recursive': true,
        'includeItemTypes': 'Audio,MusicAlbum,MusicArtist,Playlist',
        'limit': 50,
        'startIndex': 0,
        'enableImages': true,
              },
    );
    final qr = BaseItemQueryResult.fromJson(res.data ?? const {});
    return qr.items;
  }

  
  
  
  Future<BaseItem> getItem({
    required String userId,
    required String itemId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items/$itemId',
      queryParameters: {
        'userId': userId,
        'enableImages': true,
      },
    );
    return BaseItem.fromJson(res.data ?? const {});
  }

  
  Uri itemImageUri({
    required String itemId,
    String imageType = 'Primary',
    String? tag,
    int? maxWidth,
  }) {
    final qp = <String, dynamic>{};
    if (tag != null && tag.isNotEmpty) qp['tag'] = tag;
    if (maxWidth != null) qp['maxWidth'] = maxWidth;
    final uri = Uri.parse(_dio.options.baseUrl + '/Items/$itemId/Images/$imageType');
    return uri.replace(queryParameters: qp.isEmpty ? null : qp.map((k, v) => MapEntry(k, v.toString())));
  }

  
  Uri universalAudioUri({
    required String itemId,
    required String userId,
    required String deviceId,
    String container = 'mp3',
    String? audioCodec,
    int? maxStreamingBitrate,
    String? transcodingContainer,
    String? transcodingProtocol,
    int? maxAudioChannels,
    bool enableRedirection = true,
  }) {
    final uri = Uri.parse(_dio.options.baseUrl + '/Audio/$itemId/universal');
    final qp = <String, String>{
      'userId': userId,
      'deviceId': deviceId,
      'container': container,
      if (audioCodec != null) 'audioCodec': audioCodec,
      if (maxStreamingBitrate != null) 'maxStreamingBitrate': maxStreamingBitrate.toString(),
      if (transcodingContainer != null) 'transcodingContainer': transcodingContainer,
      if (transcodingProtocol != null) 'transcodingProtocol': transcodingProtocol,
      if (maxAudioChannels != null) 'maxAudioChannels': maxAudioChannels.toString(),
      'enableRedirection': enableRedirection ? 'true' : 'false',
    };
    return uri.replace(queryParameters: qp);
  }

  
  
  
  Uri audioStreamUri({
    required String itemId,
    String container = 'mp3',
    bool static = false,
  }) {
    final uri = Uri.parse(_dio.options.baseUrl + '/Audio/$itemId/stream.$container');
    final qp = <String, String>{
      if (static) 'static': 'true',
    };
    return uri.replace(queryParameters: qp.isEmpty ? null : qp);
  }

  Map<String, String> authHeaders() => {
        'Authorization': buildAuthorizationHeader(
          client: auth.clientName,
          device: auth.deviceName,
          deviceId: auth.deviceId,
          version: auth.appVersion,
          token: auth.accessToken.isEmpty ? null : auth.accessToken,
        ),
      };

  

  
  
  
  Future<Map<String, dynamic>?> getItemForPlaybackUi({
    required String userId,
    required String itemId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/Items',
      queryParameters: {
        'userId': userId,
        'ids': itemId,
        'fields': 'MediaStreams',
        'enableImages': false,
        'enableUserData': false,
        'startIndex': 0,
        'limit': 1,
      },
    );

    final items = res.data?['Items'];
    if (items is List && items.isNotEmpty && items.first is Map<String, dynamic>) {
      return (items.first as Map<String, dynamic>);
    }
    return null;
  }


  Uri originalDownloadUri({required String itemId}) {
    return Uri.parse(_dio.options.baseUrl + '/Items/$itemId/Download');
  }
}
