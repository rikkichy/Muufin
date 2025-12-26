import 'base_item.dart';

class BaseItemQueryResult {
  const BaseItemQueryResult({
    required this.items,
    required this.totalRecordCount,
  });

  final List<BaseItem> items;
  final int? totalRecordCount;

  factory BaseItemQueryResult.fromJson(Map<String, dynamic> json) {
    final items = <BaseItem>[];
    final raw = json['Items'];
    if (raw is List) {
      for (final v in raw) {
        if (v is Map<String, dynamic>) items.add(BaseItem.fromJson(v));
        if (v is Map && v is! Map<String, dynamic>) {
          items.add(BaseItem.fromJson(v.cast<String, dynamic>()));
        }
      }
    }
    return BaseItemQueryResult(
      items: items,
      totalRecordCount: json['TotalRecordCount'] as int?,
    );
  }
}
