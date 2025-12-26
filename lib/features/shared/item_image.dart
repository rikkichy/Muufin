import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/base_item.dart';
import '../../providers.dart';

class ItemImage extends ConsumerWidget {
  const ItemImage({super.key, required this.item, required this.size});

  final BaseItem item;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider).valueOrNull;
    if (auth == null || !auth.isLoggedIn) return _placeholder();

    final api = ref.watch(jellyfinApiProvider);
    final tag = item.imageTags['Primary'];
    final uri = api.itemImageUri(itemId: item.id, tag: tag, maxWidth: size.toInt() * 2);

    return ClipRRect(
      borderRadius: BorderRadius.circular(22),
      child: CachedNetworkImage(
        imageUrl: uri.toString(),
        httpHeaders: api.authHeaders(),
        width: size,
        height: size,
        fit: BoxFit.cover,
        placeholder: (_, __) => _placeholder(),
        errorWidget: (_, __, ___) => _placeholder(),
      ),
    );
  }

  Widget _placeholder() {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: Colors.black12,
        borderRadius: BorderRadius.circular(18),
      ),
      child: const Icon(Icons.music_note),
    );
  }
}
