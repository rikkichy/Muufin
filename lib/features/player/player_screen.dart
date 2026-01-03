import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:audio_service/audio_service.dart';
import 'package:go_router/go_router.dart';

import '../../player/playback_controller.dart';
import '../../providers.dart';
import '../shared/item_art.dart';
import 'seek_bar.dart';

class PlayerScreen extends ConsumerStatefulWidget {
  const PlayerScreen({super.key});

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> {
  static const _dismissOverscrollThreshold = 72.0;
  double _pulldown = 0.0;

  void _resetPulldown() {
    if (_pulldown == 0.0) return;
    setState(() => _pulldown = 0.0);
  }

  void _close(BuildContext context) {
    
    if (GoRouter.of(context).canPop()) {
      context.pop();
      return;
    }
    Navigator.of(context).maybePop();
  }

  @override
  Widget build(BuildContext context) {
    final stateAsync = ref.watch(playbackStateProvider);
    final isWindows = defaultTargetPlatform == TargetPlatform.windows;

    return Scaffold(
      body: stateAsync.when(
        data: (s) {
          final media = s.mediaItem;
          if (media == null) {
            return const Center(child: Text('Nothing playing'));
          }

          final cs = Theme.of(context).colorScheme;
          final controller = ref.read(playbackControllerProvider);
          final handler = ref.read(audioHandlerProvider);
          final nowItemAsync = ref.watch(itemProvider(media.id));

          final shuffleOn = s.shuffleMode != AudioServiceShuffleMode.none;
          final repeatMode = s.repeatMode;
          final repeatIcon = switch (repeatMode) {
            AudioServiceRepeatMode.one => Icons.repeat_one_rounded,
            _ => Icons.repeat_rounded,
          };
          final repeatTip = switch (repeatMode) {
            AudioServiceRepeatMode.none => 'Repeat off',
            AudioServiceRepeatMode.one => 'Repeat one',
            AudioServiceRepeatMode.all => 'Repeat all',
            _ => 'Repeat',
          };

          void openQueue() {
            showModalBottomSheet<void>(
              context: context,
              showDragHandle: true,
              isScrollControlled: true,
              builder: (ctx) {
                return SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                    child: StreamBuilder<List<MediaItem>>(
                      stream: handler.queue,
                      initialData: handler.queue.value,
                      builder: (context, qSnap) {
                        final q = qSnap.data ?? const <MediaItem>[];
                        return StreamBuilder<PlaybackState>(
                          stream: handler.playbackState,
                          initialData: handler.playbackState.value,
                          builder: (context, sSnap) {
                            final idx = sSnap.data?.queueIndex;
                            return ConstrainedBox(
                              constraints: BoxConstraints(
                                maxHeight: MediaQuery.of(context).size.height * 0.8,
                              ),
                              child: Column(
                                children: [
                                  Align(
                                    alignment: Alignment.centerLeft,
                                    child: Padding(
                                      padding: const EdgeInsets.fromLTRB(6, 0, 6, 12),
                                      child: Text('Up next', style: Theme.of(context).textTheme.titleLarge),
                                    ),
                                  ),
                                  Expanded(
                                    child: ListView.separated(
                                      itemCount: q.length,
                                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                                      itemBuilder: (context, i) {
                                        final it = q[i];
                                        final selected = idx == i;
                                        return Card.filled(
                                          child: ListTile(
                                            leading: SizedBox(
                                              width: 34,
                                              child: Center(
                                                child: Text(
                                                  (i + 1).toString().padLeft(2, '0'),
                                                  style: Theme.of(context)
                                                      .textTheme
                                                      .labelLarge
                                                      ?.copyWith(fontWeight: FontWeight.w800),
                                                ),
                                              ),
                                            ),
                                            title: Text(
                                              it.title,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                            subtitle: (it.artist ?? '').isEmpty
                                                ? null
                                                : Text(
                                                    it.artist!,
                                                    maxLines: 1,
                                                    overflow: TextOverflow.ellipsis,
                                                  ),
                                            trailing: selected
                                                ? Icon(Icons.graphic_eq_rounded, color: cs.primary)
                                                : null,
                                            onTap: () async {
                                              await controller.skipToQueueItem(i);
                                              if (ctx.mounted) Navigator.of(ctx).pop();
                                            },
                                          ),
                                        );
                                      },
                                    ),
                                  ),
                                ],
                              ),
                            );
                          },
                        );
                      },
                    ),
                  ),
                );
              },
            );
          }

          return ColoredBox(
            
            color: cs.surface,
            child: NotificationListener<ScrollNotification>(
              onNotification: (n) {
                
                
                if (n is OverscrollNotification) {
                  if (n.overscroll < 0) {
                    setState(() => _pulldown += (-n.overscroll));
                  } else {
                    _resetPulldown();
                  }
                } else if (n is ScrollUpdateNotification) {
                  
                  if (n.metrics.pixels > 0) _resetPulldown();
                } else if (n is ScrollEndNotification) {
                  if (_pulldown >= _dismissOverscrollThreshold) {
                    _resetPulldown();
                    _close(context);
                  } else {
                    _resetPulldown();
                  }
                }
                return false;
              },
              child: CustomScrollView(
                slivers: [
                  SliverAppBar(
                    floating: true,
                    snap: true,
                    leading: IconButton(
                      tooltip: 'Close',
                      onPressed: () => _close(context),
                      icon: const Icon(Icons.keyboard_arrow_down_rounded),
                    ),
                    title: const SizedBox.shrink(),
                    centerTitle: true,
                    actions: [
                      IconButton(
                        tooltip: 'Queue',
                        onPressed: openQueue,
                        icon: const Icon(Icons.queue_music_rounded),
                      ),
                    ],
                  ),

                  
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 6, bottom: 10),
                      child: Center(
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 120),
                          width: 42,
                          height: 5,
                          decoration: BoxDecoration(
                            color: cs.outlineVariant,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          transform: Matrix4.translationValues(
                            0,
                            (_pulldown > 0 ? (_pulldown / 12).clamp(0, 8) : 0).toDouble(),
                            0,
                          ),
                        ),
                      ),
                    ),
                  ),

                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                      child: Column(
                        children: [
                      Center(
                        child: Hero(
                          tag: 'nowplaying_art',
                          child: nowItemAsync.when(
                            data: (it) {
                              if (it == null) {
                                return Container(
                                  width: 220,
                                  height: 220,
                                  decoration: BoxDecoration(
                                    color: cs.surfaceVariant,
                                    borderRadius: BorderRadius.circular(30),
                                  ),
                                  child: Icon(Icons.album_rounded, size: 140, color: cs.primary),
                                );
                              }
                              return ItemArt(
                                item: it,
                                width: 220,
                                height: 220,
                                borderRadius: BorderRadius.circular(30),
                                placeholderIcon: Icons.album_rounded,
                              );
                            },
                            loading: () => Container(
                              width: 220,
                              height: 220,
                              decoration: BoxDecoration(
                                color: cs.surfaceVariant,
                                borderRadius: BorderRadius.circular(30),
                              ),
                              child: Icon(Icons.album_rounded, size: 140, color: cs.primary),
                            ),
                            error: (_, __) => Container(
                              width: 220,
                              height: 220,
                              decoration: BoxDecoration(
                                color: cs.surfaceVariant,
                                borderRadius: BorderRadius.circular(30),
                              ),
                              child: Icon(Icons.album_rounded, size: 140, color: cs.primary),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        media.title,
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if ((media.artist ?? '').isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Text(media.artist!, style: Theme.of(context).textTheme.titleMedium),
                      ],
                      const SizedBox(height: 8),
                      _StreamInfoPill(itemId: media.id),
                      const SizedBox(height: 16),

                      
                      Card.filled(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                          child: SeekBar(
                            key: ValueKey('seekbar_${media.id}'),
                            positionStream: controller.positionStream,
                            duration: s.duration ?? media.duration ?? Duration.zero,
                            onSeek: controller.seek,
                          ),
                        ),
                      ),

                      
                      if (isWindows) ...[
                        const SizedBox(height: 12),
                        Card.filled(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: StreamBuilder<double>(
                              stream: handler.volumeStream,
                              initialData: handler.volume,
                              builder: (context, snap) {
                                final v = (snap.data ?? 1.0).clamp(0.0, 1.0) as double;
                                final icon = v == 0
                                    ? Icons.volume_off
                                    : (v < 0.5 ? Icons.volume_down : Icons.volume_up);
                                return Row(
                                  children: [
                                    Icon(icon),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Slider(
                                        value: v,
                                        onChanged: (nv) => handler.setVolume(nv),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    SizedBox(
                                      width: 48,
                                      child: Text(
                                        '${(v * 100).round()}%',
                                        textAlign: TextAlign.end,
                                      ),
                                    ),
                                  ],
                                );
                              },
                            ),
                          ),
                        ),
                      ],

                      const SizedBox(height: 12),

                      
                      Card.filled(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              IconButton(
                                tooltip: shuffleOn ? 'Shuffle on' : 'Shuffle',
                                onPressed: controller.toggleShuffle,
                                icon: Icon(
                                  Icons.shuffle_rounded,
                                  color: shuffleOn ? cs.primary : null,
                                ),
                              ),
                              const SizedBox(width: 6),
                              IconButton(
                                iconSize: 36,
                                tooltip: 'Previous',
                                onPressed: () => controller.skipToPrevious(),
                                icon: const Icon(Icons.skip_previous_rounded),
                              ),
                              const SizedBox(width: 12),
                              FilledButton.tonal(
                                onPressed: () => controller.togglePlayPause(),
                                style: FilledButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 16),
                                ),
                                child: Icon(s.playing ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 28),
                              ),
                              const SizedBox(width: 12),
                              IconButton(
                                iconSize: 36,
                                tooltip: 'Next',
                                onPressed: () => controller.skipToNext(),
                                icon: const Icon(Icons.skip_next_rounded),
                              ),
                              const SizedBox(width: 6),
                              IconButton(
                                tooltip: repeatTip,
                                onPressed: controller.cycleRepeatMode,
                                icon: Icon(
                                  repeatIcon,
                                  color: repeatMode == AudioServiceRepeatMode.none ? null : cs.primary,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
                ],
              ),
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, st) => Center(child: Text('Error: $e')),
      ),
    );
  }
}


class _StreamInfoPill extends ConsumerWidget {
  const _StreamInfoPill({required this.itemId});

  final String itemId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(appSettingsProvider).valueOrNull;
    if (settings != null && !settings.showStreamInfo) {
      return const SizedBox.shrink();
    }

    final asyncInfo = ref.watch(streamInfoProvider(itemId));
    final cs = Theme.of(context).colorScheme;

    return asyncInfo.when(
      data: (info) {
        final label = info?.label;
        if (label == null || label.isEmpty) return const SizedBox.shrink();

        return Align(
          alignment: Alignment.center,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: cs.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(999),
            ),
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelLarge,
            ),
          ),
        );
      },
      loading: () => const SizedBox(height: 24),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}
