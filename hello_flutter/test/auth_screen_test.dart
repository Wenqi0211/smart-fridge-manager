import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hello_flutter/screens/auth_screen.dart';

void main() {
  testWidgets('registration validates the password before sending a request', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: AuthScreen(register: true)),
    );
    await tester.enterText(find.byType(TextFormField).at(0), 'alice');
    await tester.enterText(find.byType(TextFormField).at(1), 'short');
    await tester.tap(find.widgetWithText(FilledButton, 'Create account'));
    await tester.pump();
    expect(
      tester
          .state<FormFieldState<String>>(find.byType(TextFormField).at(1))
          .errorText,
      'Use at least 12 characters.',
    );
  });

  testWidgets('sign in validates required fields', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: AuthScreen()));
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pump();
    expect(find.text('Enter your username.'), findsOneWidget);
    expect(find.text('Enter your password.'), findsOneWidget);
  });

  testWidgets('switching modes clears the password', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: AuthScreen()));
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'private-password',
    );
    await tester.tap(find.widgetWithText(TextButton, 'Create an account'));
    await tester.pump();
    final password = tester.widget<TextFormField>(
      find.byType(TextFormField).at(1),
    );
    expect(password.controller!.text, isEmpty);
    expect(find.text('Create your account'), findsOneWidget);
  });
}
