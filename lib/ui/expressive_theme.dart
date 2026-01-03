import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';











ThemeData buildExpressiveTheme(ColorScheme scheme) {
  const rSmall = 16.0;
  const rMed = 22.0;
  const rLarge = 30.0;

  final text = Typography.material2021(platform: defaultTargetPlatform).englishLike;
  final roundedLarge = RoundedRectangleBorder(borderRadius: BorderRadius.circular(rLarge));
  final roundedMed = RoundedRectangleBorder(borderRadius: BorderRadius.circular(rMed));
  final roundedSmall = RoundedRectangleBorder(borderRadius: BorderRadius.circular(rSmall));

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: scheme.surface,
    typography: Typography.material2021(platform: defaultTargetPlatform),
    textTheme: text.copyWith(
      
      headlineSmall: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
      titleLarge: text.titleLarge?.copyWith(fontWeight: FontWeight.w700),
      titleMedium: text.titleMedium?.copyWith(fontWeight: FontWeight.w600),
    ),
    visualDensity: VisualDensity.adaptivePlatformDensity,
    splashFactory: InkSparkle.splashFactory,

    dividerTheme: DividerThemeData(
      thickness: 1,
      space: 24,
      color: scheme.outlineVariant,
    ),

    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      showCloseIcon: true,
      shape: roundedLarge,
      elevation: 1,
    ),

    bottomSheetTheme: BottomSheetThemeData(
      showDragHandle: true,
      clipBehavior: Clip.antiAlias,
      shape: roundedLarge,
    ),

    
    cardTheme: CardThemeData(
      clipBehavior: Clip.antiAlias,
      shape: roundedLarge,
      margin: EdgeInsets.zero,
    ),
    listTileTheme: ListTileThemeData(
      shape: roundedMed,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
    ),
    chipTheme: ChipThemeData(
      shape: roundedSmall,
      labelStyle: TextStyle(color: scheme.onSurface, fontWeight: FontWeight.w600),
      side: BorderSide(color: scheme.outlineVariant),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
    ),
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(rMed)),
      filled: true,
      fillColor: scheme.surfaceVariant,
    ),
    searchBarTheme: SearchBarThemeData(
      elevation: const WidgetStatePropertyAll(0),
      shape: WidgetStatePropertyAll(roundedLarge),
      padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 14)),
    ),

    appBarTheme: AppBarTheme(
      centerTitle: false,
      backgroundColor: scheme.surface,
      foregroundColor: scheme.onSurface,
      scrolledUnderElevation: 0,
    ),

    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: scheme.surface,
      indicatorColor: scheme.secondaryContainer,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          color: selected ? scheme.onSurface : scheme.onSurfaceVariant,
          fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
        );
      }),
    ),

    
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        shape: roundedLarge,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    iconButtonTheme: IconButtonThemeData(
      style: IconButton.styleFrom(
        shape: roundedSmall,
      ),
    ),

    sliderTheme: SliderThemeData(
      trackHeight: 6,
      overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
    ),

    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.surfaceVariant,
    ),

    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: scheme.surface,
      indicatorColor: scheme.secondaryContainer,
      labelType: NavigationRailLabelType.all,
      selectedIconTheme: IconThemeData(color: scheme.onSecondaryContainer),
      selectedLabelTextStyle: TextStyle(color: scheme.onSurface, fontWeight: FontWeight.w700),
    ),
  );
}
