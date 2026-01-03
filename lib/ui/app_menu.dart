import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';




class AppMenuButton extends StatelessWidget {
  const AppMenuButton({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Settings',
      icon: const Icon(Icons.settings_rounded),
      onPressed: () {
        
        context.push('/settings');
      },
    );
  }
}
