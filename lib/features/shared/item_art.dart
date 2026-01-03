import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/base_item.dart';
import '../../providers.dart';


class ItemArt extends ConsumerWidget {
  const ItemArt({
    super.key,
    required this.item,
    this.borderRadius,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.placeholderIcon = Icons.music_note_rounded,
  });

  final BaseItem item;
  final BorderRadius? borderRadius;
  final BoxFit fit;
  final double? width;
  final double? height;
  final IconData placeholderIcon;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider).valueOrNull;
    if (auth == null || !auth.isLoggedIn) return _placeholder(context);

    final api = ref.watch(jellyfinApiProvider);
    final tag = item.imageTags['Primary'];

    return LayoutBuilder(
      builder: (context, constraints) {
        
        final w = width ?? (constraints.hasBoundedWidth ? constraints.maxWidth : 320);
        final requested = w.isFinite ? w.toInt() * 2 : 640;

        final uri = api.itemImageUri(
          itemId: item.id,
          tag: tag,
          maxWidth: requested.clamp(256, 2048),
        );

        final clip = borderRadius ?? BorderRadius.circular(28);
        return ClipRRect(
          borderRadius: clip,
          child: CachedNetworkImage(
            imageUrl: uri.toString(),
            httpHeaders: api.authHeaders(),
            width: width,
            height: height,
            fit: fit,
            placeholder: (_, __) => _placeholder(context),
            errorWidget: (_, __, ___) => _placeholder(context),
          ),
        );
      },
    );
  }

  Widget _placeholder(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: width,
      height: height,
      color: cs.surfaceVariant,
      alignment: Alignment.center,
      child: Icon(placeholderIcon, color: cs.onSurfaceVariant),
    );
  }
}
