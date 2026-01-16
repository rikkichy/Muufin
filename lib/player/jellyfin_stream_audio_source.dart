import 'dart:async';

import 'package:dio/dio.dart';
import 'package:just_audio/just_audio.dart';

import '../core/jellyfin_api.dart';

class JellyfinStreamAudioSource extends StreamAudioSource {
  JellyfinStreamAudioSource({
    required this.api,
    required this.itemId,
    this.container = 'mp3',
    dynamic tag,
  }) : super(tag: tag);

  final JellyfinApi api;
  final String itemId;
  final String container;

  @override
  Future<StreamAudioResponse> request([int? start, int? end]) async {
    final uri = api.audioStreamUri(itemId: itemId, container: container, static: false);

    final headers = <String, dynamic>{
      ...api.authHeaders(),
      'Accept': '*/*',
    };

    if (start != null && (start > 0 || end != null)) {
      final endInclusive = (end != null) ? (end - 1) : null;
      headers['Range'] = endInclusive != null ? 'bytes=$start-$endInclusive' : 'bytes=$start-';
    }

    final cancelToken = CancelToken();

    try {
      final res = await api.dio.get<ResponseBody>(
        uri.toString(),
        cancelToken: cancelToken,
        options: Options(
          responseType: ResponseType.stream,
          headers: headers,
          followRedirects: true,
          validateStatus: (s) => s != null && s >= 200 && s < 400,
        ),
      );

      final contentRange = res.headers.value('content-range');
      int? sourceLength;
      int? contentLength;
      int offset = start ?? 0;

      if (contentRange != null) {
        final m = RegExp(r'^bytes\s+(\d+)-(\d+)/(\d+|\*)$', caseSensitive: false).firstMatch(contentRange);
        if (m != null) {
          offset = int.tryParse(m.group(1) ?? '') ?? offset;
          final endByte = int.tryParse(m.group(2) ?? '');
          final total = m.group(3);
          if (endByte != null) {
            contentLength = (endByte - offset) + 1;
          }
          if (total != null && total != '*') {
            sourceLength = int.tryParse(total);
          }
        }
      }

      sourceLength ??= int.tryParse(res.headers.value('content-length') ?? '');
      contentLength ??= int.tryParse(res.headers.value('content-length') ?? '');

      final contentType = res.headers.value('content-type') ?? _defaultContentType(container);

      final controller = StreamController<List<int>>();

      final sub = res.data!.stream.listen(
        (data) => controller.add(data),
        onError: (e) => controller.addError(e),
        onDone: () => controller.close(),
      );

      controller.onCancel = () {
        sub.cancel();
        if (!cancelToken.isCancelled) {
          cancelToken.cancel();
        }
      };

      return StreamAudioResponse(
        sourceLength: sourceLength,
        contentLength: contentLength,
        offset: offset,
        stream: controller.stream,
        contentType: contentType,
      );
    } catch (e) {
      if (!cancelToken.isCancelled) cancelToken.cancel();
      rethrow;
    }
  }

  static String _defaultContentType(String container) {
    switch (container.toLowerCase()) {
      case 'mp3':
      case 'mpeg':
        return 'audio/mpeg';
      case 'aac':
        return 'audio/aac';
      case 'm4a':
      case 'mp4':
        return 'audio/mp4';
      case 'flac':
        return 'audio/flac';
      case 'wav':
        return 'audio/wav';
      default:
        return 'application/octet-stream';
    }
  }
}
