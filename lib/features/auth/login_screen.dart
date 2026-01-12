import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../providers.dart';
import '../../core/jellyfin_api.dart';
import '../../models/auth_state.dart';
import '../../models/auth_result.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _serverCtrl = TextEditingController(text: 'https://');
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _hide = true;
  bool _disableTls = false;

  @override
  void dispose() {
    _serverCtrl.dispose();
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final ok = _formKey.currentState?.validate() ?? false;
    if (!ok) return;

    final baseUrl = _serverCtrl.text.trim().replaceAll(RegExp(r'/+$'), '');
    final username = _userCtrl.text.trim();
    final password = _passCtrl.text;

    try {
      await ref.read(authStateProvider.notifier).login(
            baseUrl: baseUrl,
            username: username,
            password: password,
            disableTls: _disableTls,
          );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Login failed: $e')),
      );
    }
  }

  Future<void> _startQuickConnect() async {
    final baseUrl = _serverCtrl.text.trim().replaceAll(RegExp(r'/+$'), '');
    if (baseUrl.isEmpty || !baseUrl.startsWith('http')) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a valid Server URL first')),
      );
      return;
    }

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => _QuickConnectDialog(
        baseUrl: baseUrl,
        disableTls: _disableTls,
        ref: ref,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authAsync = ref.watch(authStateProvider);
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      appBar: AppBar(
        title: const Text('Muufin'),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 16),
          child: Card.filled(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Sign in to your Jellyfin server',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _serverCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Server URL',
                        prefixIcon: Icon(Icons.cloud),
                        hintText: 'https://your.jellyfin.server',
                      ),
                      validator: (v) {
                        final s = (v ?? '').trim();
                        if (s.isEmpty) return 'Required';
                        if (!s.startsWith('http')) return 'Use http(s)://';
                        return null;
                      },
                    ),
                    const SizedBox(height: 8),
                    SwitchListTile(
                      title: const Text('Disable TLS Verification'),
                      subtitle: const Text('For self-signed certificates'),
                      value: _disableTls,
                      onChanged: (v) => setState(() => _disableTls = v),
                      contentPadding: EdgeInsets.zero,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _userCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Username',
                        prefixIcon: Icon(Icons.person),
                      ),
                      validator: (v) => (v == null || v.trim().isEmpty) ? 'Required' : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _passCtrl,
                      decoration: InputDecoration(
                        labelText: 'Password',
                        prefixIcon: const Icon(Icons.lock),
                        suffixIcon: IconButton(
                          icon: Icon(_hide ? Icons.visibility : Icons.visibility_off),
                          onPressed: () => setState(() => _hide = !_hide),
                        ),
                      ),
                      obscureText: _hide,
                      validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
                    ),
                    const SizedBox(height: 18),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: authAsync.isLoading ? null : _submit,
                        child: authAsync.isLoading
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('Sign in'),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: TextButton.icon(
                        onPressed: authAsync.isLoading ? null : _startQuickConnect,
                        icon: const Icon(Icons.qr_code),
                        label: const Text('Quick Connect'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _QuickConnectDialog extends StatefulWidget {
  final String baseUrl;
  final bool disableTls;
  final WidgetRef ref;

  const _QuickConnectDialog({
    required this.baseUrl,
    required this.disableTls,
    required this.ref,
  });

  @override
  State<_QuickConnectDialog> createState() => _QuickConnectDialogState();
}

class _QuickConnectDialogState extends State<_QuickConnectDialog> {
  String? _code;
  String? _secret;
  String? _error;
  bool _authorized = false;
  bool _disposed = false;

  @override
  void initState() {
    super.initState();
    _initiate();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  Future<void> _initiate() async {
    try {
      final prev = await widget.ref.read(authStateProvider.future);

      final api = JellyfinApi(
        auth: AuthState(
          baseUrl: widget.baseUrl,
          deviceId: prev.deviceId,
          clientName: prev.clientName,
          deviceName: prev.deviceName,
          appVersion: prev.appVersion,
          userId: '',
          accessToken: '',
          disableTls: widget.disableTls,
        ),
      );

      final res = await api.initiateQuickConnect();
      if (_disposed) return;

      setState(() {
        _code = res['Code'];
        _secret = res['Secret'];
      });

      _poll(api);
    } catch (e) {
      if (_disposed) return;
      setState(() => _error = e.toString());
    }
  }

  Future<void> _poll(JellyfinApi api) async {
    while (!_authorized && !_disposed && _secret != null) {
      await Future.delayed(const Duration(seconds: 2));
      if (_disposed) break;

      try {
        final res = await api.checkQuickConnect(_secret!);
        final authorized = res['Authenticated'] == true;

        if (authorized) {
           _authorized = true;

           final userId = res['Id'] ?? res['UserId'];
           final token = res['AccessToken'];

           if (userId != null && token != null) {
             final authResult = AuthResult(
               userId: userId,
               accessToken: token,
               serverId: '',
             );

             if (mounted) {
               await widget.ref.read(authStateProvider.notifier).loginWithResult(
                 baseUrl: widget.baseUrl,
                 result: authResult,
                 disableTls: widget.disableTls,
               );
               Navigator.of(context).pop();
             }
           } else {
             setState(() => _error = "Authorized but token missing.");
           }
           break;
        }
      } catch (e) {
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Quick Connect'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_error != null)
            Text('Error: $_error', style: const TextStyle(color: Colors.red))
          else if (_code == null)
            const CircularProgressIndicator()
          else ...[
            Text('Enter this code on another device:', style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 16),
            Text(
              _code!,
              style: Theme.of(context).textTheme.displayMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    letterSpacing: 4,
                  ),
            ),
            const SizedBox(height: 16),
            const CircularProgressIndicator(),
            const SizedBox(height: 8),
            const Text('Waiting for authorization...'),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
      ],
    );
  }
}
