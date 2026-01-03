import 'package:intl/intl.dart';

class BaseItem {
  const BaseItem({
    required this.id,
    required this.name,
    required this.type,
    required this.container,
    required this.collectionType,
    required this.album,
    required this.albumId,
    required this.albumArtist,
    required this.artists,
    required this.imageTags,
    required this.parentId,
    required this.indexNumber,
    required this.runTimeTicks,
  });

  final String id;
  final String name;
  final String type;
  final String? container; 
  final String? collectionType;

  final String? album;
  final String? albumId;
  final String? albumArtist;
  final List<String> artists;

  final Map<String, String> imageTags; 

  final String? parentId;
  final int? indexNumber;
  final int? runTimeTicks; 

  Duration? get duration {
    final ticks = runTimeTicks;
    if (ticks == null) return null;
    
    final seconds = ticks ~/ 10000000;
    return Duration(seconds: seconds);
  }

  String durationLabel() {
    final d = duration;
    if (d == null) return '';
    final mm = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final ss = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '${d.inHours > 0 ? '${d.inHours}:' : ''}$mm:$ss';
  }

  factory BaseItem.fromJson(Map<String, dynamic> json) {
    final imageTags = <String, String>{};
    final rawTags = json['ImageTags'];
    if (rawTags is Map) {
      for (final e in rawTags.entries) {
        if (e.key is String && e.value is String) {
          imageTags[e.key as String] = e.value as String;
        }
      }
    }

    final artists = <String>[];
    final rawArtists = json['Artists'];
    if (rawArtists is List) {
      for (final a in rawArtists) {
        if (a is String) artists.add(a);
      }
    }

    return BaseItem(
      id: (json['Id'] as String?) ?? '',
      name: (json['Name'] as String?) ?? '',
      type: (json['Type'] as String?) ?? '',
      container: json['Container'] as String?,
      collectionType: json['CollectionType'] as String?,
      album: json['Album'] as String?,
      albumId: json['AlbumId'] as String?,
      albumArtist: json['AlbumArtist'] as String?,
      artists: artists,
      imageTags: imageTags,
      parentId: json['ParentId'] as String?,
      indexNumber: json['IndexNumber'] as int?,
      runTimeTicks: json['RunTimeTicks'] as int?,
    );
  }

  String subtitle() {
    if (type == 'Audio') {
      if (artists.isNotEmpty) return artists.join(', ');
      if (albumArtist != null && albumArtist!.isNotEmpty) return albumArtist!;
      if (album != null && album!.isNotEmpty) return album!;
    }
    if (type == 'MusicAlbum') {
      if (albumArtist != null && albumArtist!.isNotEmpty) return albumArtist!;
    }
    if (type == 'MusicArtist') {
      return 'Artist';
    }
    if (type == 'Playlist') {
      return 'Playlist';
    }
    return '';
  }

  String titleWithTrackNumber() {
    final n = indexNumber;
    if (n == null) return name;
    final fmt = NumberFormat('00');
    return '${fmt.format(n)}. $name';
  }

  
  
  
  
  Map<String, dynamic> toJson() {
    return {
      'Id': id,
      'Name': name,
      'Type': type,
      if (container != null) 'Container': container,
      if (collectionType != null) 'CollectionType': collectionType,
      if (album != null) 'Album': album,
      if (albumId != null) 'AlbumId': albumId,
      if (albumArtist != null) 'AlbumArtist': albumArtist,
      if (artists.isNotEmpty) 'Artists': artists,
      if (imageTags.isNotEmpty) 'ImageTags': imageTags,
      if (parentId != null) 'ParentId': parentId,
      if (indexNumber != null) 'IndexNumber': indexNumber,
      if (runTimeTicks != null) 'RunTimeTicks': runTimeTicks,
    };
  }
}
