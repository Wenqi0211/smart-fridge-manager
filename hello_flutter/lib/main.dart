// ignore_for_file: slash_for_doc_comments

import 'package:flutter/material.dart';

import 'app/main_shell.dart';
import 'screens/auth_screen.dart';
import 'screens/preferences_screen.dart';
import 'screens/welcome_screen.dart';
import 'services/fridge_api_service.dart';
import 'theme/smart_fridge_tokens.dart';

/** App entry point. */
void main() {
  runApp(const SmartFridgeApp());
}

/**
 * Root app widget.
 * <p>
 * Sets up the shared theme and named routes.
 *
 * Official references:
 * MaterialApp: https://api.flutter.dev/flutter/material/MaterialApp-class.html
 * ThemeData: https://api.flutter.dev/flutter/material/ThemeData-class.html
 */
class SmartFridgeApp extends StatefulWidget {
  const SmartFridgeApp({super.key});

  @override
  State<SmartFridgeApp> createState() => _SmartFridgeAppState();
}

class _SmartFridgeAppState extends State<SmartFridgeApp> {
  final _navigator = GlobalKey<NavigatorState>();

  @override
  void initState() {
    super.initState();
    FridgeApiService.instance.onSessionExpired = () {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _navigator.currentState?.pushNamedAndRemoveUntil(
            '/auth',
            (_) => false,
          );
        }
      });
    };
  }

  @override
  void dispose() {
    FridgeApiService.instance.onSessionExpired = null;
    super.dispose();
  }

  Widget _protected(Widget child) =>
      FridgeApiService.instance.isAuthenticated ? child : const AuthScreen();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigator,
      title: 'SmartFridge',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: SfColors.cream,
        colorScheme: ColorScheme.fromSeed(
          seedColor: SfColors.brown,
          brightness: Brightness.light,
          primary: SfColors.brown,
          surface: SfColors.cream,
        ),
      ),
      routes: {
        '/': (_) => const WelcomePage(),
        '/auth': (_) => const AuthScreen(),
        '/register': (_) => const AuthScreen(register: true),
        '/preference': (_) => _protected(const PreferencesScreen()),
        '/main': (_) => _protected(const MainShell()),
      },
    );
  }
}
