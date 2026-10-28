# Flutter authentication and recipe workflow verification

The local Flutter 3.47.8 / Dart 3.13.7 run passed all 23 unit/widget tests and static analysis reported no issues. The recorded summary and source/test hash are in ../reports/frontend-verification/summary.json. Raw test and analysis logs are retained locally in that directory.

Implemented flows:

- Register or sign in through the Spring Boot auth endpoints, retain bearer tokens only in memory, and attach them to business API requests.
- Clear expired sessions on 401, discard protected navigation, and support explicit sign out. Late 401 responses from an old token cannot clear a new session.
- Load actual recipe detail and ingredient availability. Live mode does not substitute demo ingredients for missing or failed API results.
- Add missing recipe ingredients to the authenticated grocery list and display server-provided added/merged counts; prevent repeat submission after success and allow retries after known non-mutating HTTP rejections. Transport errors, server errors and unparseable success replies are treated as uncertain outcomes. The original Add action stays disabled; the user must inspect groceries and explicitly confirm the duplicate-quantity warning before adding another batch.
- Decode structured API error codes safely. A grocery quantity conflict explains that the quantity must be reduced before planning again, without showing a false success state. Malformed or unstructured error bodies do not crash code extraction.
- Simulate a server-side plan mutation followed by a lost response. The regression verifies that failure and Cancel do not send another request, and a second batch is sent only after explicit confirmation. A separate 503 regression checks the same uncertain state.
- Retain explicit demo mode without API calls; support narrow and desktop detail layouts without overflow.

Reproduce with an installed Flutter SDK:

```sh
flutter test --no-pub
flutter analyze --no-pub
```

These tests cover the Flutter boundary and widgets. Separate Java/Spring and real HTTP tests validate backend authorization and user isolation. No physical-device build, cloud deployment, recommendation accuracy or business impact is claimed.
