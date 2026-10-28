import 'package:flutter/material.dart';

import '../services/fridge_api_service.dart';
import '../theme/smart_fridge_tokens.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key, this.register = false});

  final bool register;

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _form = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  late bool _register = widget.register;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = FridgeApiService.instance;
      if (_register) {
        await api.register(_username.text.trim(), _password.text);
      } else {
        await api.signIn(_username.text.trim(), _password.text);
      }
      final preference = await api.fetchPreference();
      if (!mounted || !api.isAuthenticated) return;
      Navigator.of(context).pushNamedAndRemoveUntil(
        preference == null ? '/preference' : '/main',
        (_) => false,
      );
    } on FridgeApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = switch (error.statusCode) {
          409 => 'That username is already in use. Please sign in.',
          401 => 'The username or password is incorrect.',
          400 => 'Please check your username and password.',
          _ => 'Could not sign in. Please try again.',
        };
      });
    } catch (_) {
      if (!mounted) return;
      setState(
        () =>
            _error = 'Could not connect. Check your connection and try again.',
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SfColors.cream,
      appBar: AppBar(title: const Text('SmartFridge')),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Form(
              key: _form,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    _register ? 'Create your account' : 'Welcome back',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 20),
                  TextFormField(
                    controller: _username,
                    enabled: !_busy,
                    autofillHints: const [AutofillHints.username],
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(labelText: 'Username'),
                    validator: (value) => value == null || value.trim().isEmpty
                        ? 'Enter your username.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _password,
                    enabled: !_busy,
                    obscureText: true,
                    autocorrect: false,
                    enableSuggestions: false,
                    autofillHints: [
                      _register
                          ? AutofillHints.newPassword
                          : AutofillHints.password,
                    ],
                    decoration: InputDecoration(
                      labelText: 'Password',
                      helperText: _register
                          ? 'Use at least 12 characters.'
                          : null,
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Enter your password.';
                      }
                      if (_register && value.length < 12) {
                        return 'Use at least 12 characters.';
                      }
                      return null;
                    },
                    onFieldSubmitted: (_) => _submit(),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      _error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: Text(
                      _busy
                          ? 'Please wait…'
                          : (_register ? 'Create account' : 'Sign in'),
                    ),
                  ),
                  TextButton(
                    onPressed: _busy
                        ? null
                        : () => setState(() {
                            _register = !_register;
                            _error = null;
                            _password.clear();
                          }),
                    child: Text(
                      _register
                          ? 'Already have an account? Sign in'
                          : 'Create an account',
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
