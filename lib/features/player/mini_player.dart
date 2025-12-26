import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../player/playback_controller.dart';
import '../../providers.dart';
import '../shared/item_art.dart';

class MiniPlayer extends ConsumerWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playbackStateProvider);

    return playerState.when(
      data: (s) {
        final media = s.mediaItem;
        if (media == null) return const SizedBox.shrink();

        final nowItemAsync = ref.watch(itemProvider(media.id));

        final controller = ref.read(playbackControllerProvider);

        return SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
            child: Card.filled(
              child: InkWell(
                borderRadius: BorderRadius.circular(24),
                onTap: () => context.push('/player'),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      child: Row(
                        children: [
                          Hero(
                            tag: 'nowplaying_art',
                            child: SizedBox(
                              width: 44,
                              height: 44,
                              child: nowItemAsync.when(
                                data: (it) {
                                  if (it == null) {
                                    return const Icon(Icons.music_note_rounded);
                                  }
                                  return ItemArt(
                                    item: it,
                                    width: 44,
                                    height: 44,
                                    placeholderIcon: Icons.music_note_rounded,
                                  );
                                },
                                loading: () => const Icon(Icons.music_note_rounded),
                                error: (_, __) => const Icon(Icons.music_note_rounded),
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(media.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                                if ((media.artist ?? '').isNotEmpty)
                                  Text(
                                    media.artist!,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                              ],
                            ),
                          ),
                          IconButton(
                            tooltip: 'Next',
                            onPressed: controller.skipToNext,
                            icon: const Icon(Icons.skip_next_rounded),
                          ),
                          IconButton(
                            tooltip: s.playing ? 'Pause' : 'Play',
                            onPressed: controller.togglePlayPause,
                            icon: Icon(s.playing ? Icons.pause_rounded : Icons.play_arrow_rounded),
                          ),
                        ],
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
                      child: StreamBuilder<Duration>(
                        stream: controller.positionStream,
                        builder: (context, snap) {
                          final pos = snap.data ?? Duration.zero;
                          final dur = s.duration ?? media.duration ?? Duration.zero;
                          final v = dur.inMilliseconds <= 0
                              ? 0.0
                              : (pos.inMilliseconds / dur.inMilliseconds).clamp(0.0, 1.0);
                          return LinearProgressIndicator(value: v);
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}
