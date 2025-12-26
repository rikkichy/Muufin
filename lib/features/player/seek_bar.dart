import 'package:flutter/material.dart';

/// A stable seek bar that:
/// - throttles position updates (prevents "shaking" on some platforms)
/// - ignores stream updates while the user is dragging
/// - disables seeking when duration is unknown/zero
class SeekBar extends StatefulWidget {
  const SeekBar({
    super.key,
    required this.positionStream,
    required this.duration,
    required this.onSeek,
    this.throttle = const Duration(milliseconds: 200),
  });

  final Stream<Duration> positionStream;
  final Duration duration;
  final ValueChanged<Duration> onSeek;
  final Duration throttle;

  @override
  State<SeekBar> createState() => _SeekBarState();
}

class _SeekBarState extends State<SeekBar> {
  bool _dragging = false;
  double _dragMs = 0;

  // If the user just performed a seek, accept the next position jump immediately.
  DateTime? _lastUserSeekAt;

  // Filter small backwards jitter (common at the start of playback on some backends).
  double _lastStableMs = 0;
  bool _hasLastStable = false;
  static const double _jitterToleranceMs = 150.0;
  static const double _acceptBackwardJumpMs = 1500.0;

  // Some backends briefly report position=0 (or a large backwards jump) during
  // buffering/track transitions. If we accept that single sample, the thumb
  // "teleports". We only accept large backwards jumps after they persist.
  int _suspiciousBackwards = 0;
  static const int _suspiciousThreshold = 3;

  static const double _nearZeroMs = 800.0;
  static const double _nearZeroAfterMs = 5000.0;

  late Stream<Duration> _position;

  void _resetStability() {
    _hasLastStable = false;
    _lastStableMs = 0;
    _suspiciousBackwards = 0;
  }

  @override
  void initState() {
    super.initState();
    _position = _throttled(widget.positionStream);
  }

  @override
  void didUpdateWidget(covariant SeekBar oldWidget) {
    super.didUpdateWidget(oldWidget);

    // Rebuild the cached stream only when its inputs actually change.
    if (!identical(oldWidget.positionStream, widget.positionStream) ||
        oldWidget.throttle != widget.throttle) {
      _position = _throttled(widget.positionStream);
    }

    // When a new track loads (or duration becomes known), reset stability so we
    // don't carry over position heuristics.
    if (oldWidget.duration != widget.duration) {
      _resetStability();
      _dragging = false;
    }
  }

  Stream<Duration> _throttled(Stream<Duration> s) {
    final stepMs = widget.throttle.inMilliseconds.clamp(16, 2000);
    return s
        .map((d) => Duration(milliseconds: (d.inMilliseconds ~/ stepMs) * stepMs))
        .distinct((a, b) => a.inMilliseconds == b.inMilliseconds);
  }

  @override
  Widget build(BuildContext context) {
    final dur = widget.duration;
    final hasDuration = dur.inMilliseconds > 0;
    final maxMs = hasDuration ? dur.inMilliseconds.toDouble() : 1.0;

    return StreamBuilder<Duration>(
      stream: _position,
      initialData: Duration.zero,
      builder: (context, snap) {
        final rawPos = snap.data ?? Duration.zero;
        var posMs = rawPos.inMilliseconds.toDouble().clamp(0.0, maxMs);

        final now = DateTime.now();
        final userSeeking = _lastUserSeekAt != null &&
            now.difference(_lastUserSeekAt!).inMilliseconds < 1200;

        if (!_dragging) {
          if (!_hasLastStable) {
            _hasLastStable = true;
            _lastStableMs = posMs;
          } else {
            if (posMs < _lastStableMs - _jitterToleranceMs) {
              final delta = _lastStableMs - posMs;
              final looksLikeTransientZero =
                  posMs <= _nearZeroMs && _lastStableMs >= _nearZeroAfterMs;
              final suspicious = delta >= _acceptBackwardJumpMs || looksLikeTransientZero;

              if (userSeeking) {
                // User initiated: accept immediately.
                _lastStableMs = posMs;
                _suspiciousBackwards = 0;
              } else if (!suspicious) {
                // Ignore small backwards jitter.
                posMs = _lastStableMs;
                _suspiciousBackwards = 0;
              } else {
                // Large backwards jump: only accept if it persists.
                _suspiciousBackwards++;
                if (_suspiciousBackwards >= _suspiciousThreshold) {
                  _lastStableMs = posMs;
                  _suspiciousBackwards = 0;
                } else {
                  posMs = _lastStableMs;
                }
              }
            } else {
              _lastStableMs = posMs;
              _suspiciousBackwards = 0;
            }
            posMs = _lastStableMs;
          }
        }

        final shownMs = _dragging ? _dragMs.clamp(0.0, maxMs) : posMs;

        return Column(
          children: [
            Slider(
              value: hasDuration ? shownMs : 0.0,
              max: maxMs,
              onChangeStart: hasDuration
                  ? (v) => setState(() {
                        _dragging = true;
                        _dragMs = v;
                      })
                  : null,
              onChanged: hasDuration
                  ? (v) => setState(() {
                        _dragging = true;
                        _dragMs = v;
                      })
                  : null,
              onChangeEnd: hasDuration
                  ? (v) {
                      setState(() => _dragging = false);
                      _lastUserSeekAt = DateTime.now();
                      widget.onSeek(Duration(milliseconds: v.toInt()));
                    }
                  : null,
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(_fmt(Duration(milliseconds: shownMs.toInt()))),
                Text(hasDuration ? _fmt(dur) : '--:--'),
              ],
            ),
          ],
        );
      },
    );
  }

  String _fmt(Duration d) {
    final mm = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final ss = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '${d.inHours > 0 ? '${d.inHours}:' : ''}$mm:$ss';
  }
}
