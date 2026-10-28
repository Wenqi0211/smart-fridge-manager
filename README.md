[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/W5CdCei3)

# SmartFridge

A Flutter food-management app backed by a layered Java/Spring Boot API. Users track inventory, choose a health goal, inspect ranked recipes and add missing ingredients to a grocery list.

This repository originated as a CS 5004 team project. The original coursework architecture, classroom link and AI-use disclosure are retained. October 2026 extensions add authentication, isolation between users, live recipe planning, safer quantity updates and reproducible verification. The application remains a local demonstration with process-local storage.

## What works

- **Authenticated user workflows:** register/sign in, use short-lived HS256 bearer tokens, sign out and return to authentication when a session expires.
- **Inventory-aware recipe ranking:** required-ingredient coverage, usable stock nearest expiry, health-goal alignment and cooking convenience contribute to a score out of 100. Expired or depleted food does not count as available.
- **Recipe-to-grocery planning:** canonical food aliases merge into a single line; optional ingredients are excluded. A complete recipe plan is checked before any quantity changes, so an overflow cannot leave a partially updated list.
- **Consistent failure behavior:** invalid numeric inputs return structured errors, excessive increments leave quantities unchanged, and valid decrements stop at zero. Flutter explains quantity conflicts and requires explicit confirmation before another batch when a reply is lost or a server error leaves the outcome uncertain.
- **Independent user state:** inventory, preferences, recommendations and groceries belong to the verified token subject. Concurrent requests for one user are serialized; another user's item IDs return 404.

## Run locally

Use JDK 17 and a Flutter SDK. Java source/target compatibility remains 11. The Gradle wrapper is included; no database or Docker is required.

Start the HTTP API from the repository root:

```sh
./gradlew bootRun --args='--server.port=18081'
```

The default port is 8080 when no override is supplied. Port 18081 keeps this demo independent of another local service on 8080.

Start the official frontend from `hello_flutter`:

```sh
flutter pub get
flutter run --dart-define=SMARTFRIDGE_API=http://127.0.0.1:18081
```

For an Android emulator, use `http://10.0.2.2:18081`. For a physical device, supply the development machine's reachable address. Create an account with a password of at least 12 characters, select a goal, open a recipe and choose **Add missing ingredients**. The grocery screen supports quantity changes and checkout; checkout clears the current user's inventory, preferences, recommendations and grocery list. Settings provides **Sign out**.

The original interactive console remains available through `./gradlew run`.

## API overview

Business endpoints require `Authorization: Bearer <accessToken>`. Authentication endpoints and catalog GET requests are public.

| Method and path | Behavior |
| --- | --- |
| `POST /api/auth/register` | Create an account; return 201 and an access token |
| `POST /api/auth/token` | Sign in; return a new token for the same user state |
| `GET /api/catalog/suggestions?q=milk` | Search canonical food names and aliases |
| `GET /api/inventory`, `POST /api/inventory` | List inventory or add a food name |
| `GET /api/preference`, `PUT /api/preference` | Read or save a health goal |
| `GET /api/recommendations`, `GET /api/recommendations/{id}` | Rank recipes or read ingredient availability |
| `POST /api/recommendations/{id}/grocery` | Add or merge missing required ingredients |
| `GET /api/grocery/items`, `POST /api/grocery/items` | Read or manually add grocery rows |
| `PATCH /api/grocery/items/{id}/quantity?delta=1` | Increase/decrease a row's quantity |
| `PATCH /api/grocery/items/{id}/collected` | Toggle whether a row contributes to totals |
| `DELETE /api/grocery/items/{id}` | Remove an owned row |
| `GET /api/grocery/totals`, `POST /api/grocery/checkout` | Read totals or reset this user's current session data |

Known controller rejections return JSON such as:

```json
{"code":"QUANTITY_LIMIT_EXCEEDED","message":"Grocery quantity cannot exceed 2147483647"}
```

A manual grocery quantity must be a non-negative 32-bit integer; the default is 1. Price defaults to 0 and must be finite, non-negative and at most 1,000,000 per unit. Fractional quantities, explicit null numeric fields, malformed JSON and invalid/missing deltas return 400. An increment or whole recipe plan that exceeds the quantity limit returns 409 without applying that operation. Repeating a successful recipe-add request plans another batch and adds quantities; it is not an idempotent retry API. If the Flutter app cannot confirm the outcome, it disables the original Add action, asks the user to inspect groceries, and requires a warning confirmation before another batch. See [VERIFICATION.md](VERIFICATION.md) for the full contract.

## Architecture

```text
hello_flutter → FridgeApiService → api/web/*ApiController
             → controller/* → service/* → model/*
```

Spring routing and request errors live in `api/web`. Internal `controller` classes coordinate workflows, `service` classes manage business behavior and user state, and `model` classes describe food and recipes. The `ui` package is the legacy console driver. The official frontend is `hello_flutter`.

## Verification

```sh
./gradlew clean test jacocoTestReport
# Start the API in another terminal, then:
python3 tools/http_smoke.py --base-url http://127.0.0.1:18081
python3 tools/summarize_verification.py
```

From `hello_flutter`:

```sh
flutter test --no-pub
flutter analyze --no-pub
```

The recorded local run passed **197 Java tests**, **60 real HTTP checks** and **23 Flutter unit/widget tests**; Flutter static analysis reported no issues. The [Java report](reports/local-verification/summary.md) includes covered/total line and branch counts for every compiled production class, including console code and entrypoints. The [frontend report](hello_flutter/VERIFICATION.md) describes its separate scope. The Java CI workflow reruns tests and retains JUnit/JaCoCo XML; the recorded HTTP and Flutter evidence comes from local runs.

## Limitations

Accounts and all food data are held in one process and disappear on restart. When `SMARTFRIDGE_JWT_SECRET` is unset, the server also generates a new random signing key at startup; set a base64 secret containing at least 32 random bytes for a stable local key. Tokens expire after 30 minutes; there is no refresh-token, recovery or durable account store. Development CORS is open for `/api/**`.

Grocery planning retains the original simplified quantity model: it does not consume inventory, convert units or calculate partial quantity deficits. Prices use the inherited `double` model and are not a financial ledger. Shared durable storage and a different concurrency strategy would be required for multiple server instances. No production deployment, traffic benchmark, recommendation-quality improvement or business impact is claimed.

## Coursework attribution and original disclosure

The following disclosure is retained verbatim from the original project:

## LLM Disclosure
Use chatGPT and cursor to fix frontend
Because I don't build component, so I find a lot of example and open source on github and flutter official document
I use chatgpt and search by myself to find some component, and I put them to cursor, describe the detail make it try to understand my figma frame
I use chatGPT give me some file name and variable name, because give them a name will spend me a lot of time
I use chatGPT generate some JavaDoc, and help me write comment, because my grammar is terrible
I use chatGPT to write restAPI, because I don't know how to write it

## References

Official Flutter documentation: [StatefulWidget](https://api.flutter.dev/flutter/widgets/StatefulWidget-class.html), [State](https://api.flutter.dev/flutter/widgets/State-class.html), [LayoutBuilder](https://api.flutter.dev/flutter/widgets/LayoutBuilder-class.html), [Scaffold](https://api.flutter.dev/flutter/material/Scaffold-class.html), [Drawer](https://api.flutter.dev/flutter/material/Drawer-class.html) and [SafeArea](https://api.flutter.dev/flutter/widgets/SafeArea-class.html).

Original open-source references: [Flutter samples](https://github.com/flutter/samples) and [Material 3 demo](https://github.com/flutter/samples/tree/main/material_3_demo).
