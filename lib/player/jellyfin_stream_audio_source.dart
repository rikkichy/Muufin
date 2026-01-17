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
    final uri = api.audioStreamUri(itemId: itemId, container: container, static: true);

    final headers = <String, dynamic>{
      ...api.authHeaders(),
      'Accept': '*/*',
      'Accept-Encoding': 'identity',
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

      int responseStart = 0;
      int? sourceLength;

      final contentRange = res.headers.value('content-range');
      final contentLengthHeader = int.tryParse(res.headers.value('content-length') ?? '');

      if (res.statusCode == 206 && contentRange != null) {
        final m = RegExp(r'^bytes\s+(\d+)-(\d+)/(\d+|\*)$', caseSensitive: false).firstMatch(contentRange);
        if (m != null) {
          responseStart = int.tryParse(m.group(1) ?? '') ?? 0;
          final totalStr = m.group(3);
          if (totalStr != null && totalStr != '*') {
            sourceLength = int.tryParse(totalStr);
          }
        }
      } else if (res.statusCode == 200) {
        responseStart = 0;
        sourceLength = contentLengthHeader;
      }

      sourceLength ??= contentLengthHeader;

      final requestedStart = start ?? 0;

      bool isFirstChunk = true;
      int skippedSoFar = 0;

      int bytesToSkip = 0;
      if (requestedStart > responseStart) {
        bytesToSkip = requestedStart - responseStart;
      }

      final transformer = StreamTransformer<List<int>, List<int>>.fromHandlers(
        handleData: (data, sink) {
          if (isFirstChunk) {
            isFirstChunk = false;

            if (requestedStart > 0 && data.length >= 4) {
               if (data[0] == 0x66 && data[1] == 0x4C && data[2] == 0x61 && data[3] == 0x43) {
                  bytesToSkip = requestedStart;
               }
            }
          }

          if (bytesToSkip > 0) {
            if (skippedSoFar >= bytesToSkip) {
              sink.add(data);
            } else {
              final remaining = bytesToSkip - skippedSoFar;
              if (data.length <= remaining) {
                skippedSoFar += data.length;
              } else {
                sink.add(data.sublist(remaining));
                skippedSoFar += data.length;
              }
            }
          } else {
            sink.add(data);
          }
        },
      );

      final stream = res.data!.stream.transform(transformer);

      final reportedOffset = requestedStart;

      int? reportedContentLength;
      if (sourceLength != null) {
        reportedContentLength = sourceLength - reportedOffset;
      } else if (contentLengthHeader != null) {
         reportedContentLength = contentLengthHeader - (requestedStart - responseStart);
      }

      final contentType = res.headers.value('content-type') ?? _defaultContentType(container);

      final controller = StreamController<List<int>>();

      final sub = stream.listen(
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
        contentLength: reportedContentLength,
        offset: reportedOffset,
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
