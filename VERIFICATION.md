# SmartFridge local enhancement and verification

This local checkout preserves the original Flutter → HTTP API → internal controller → service → model architecture. Authentication and user isolation were added during the October 2026 enhancement; they were absent from the original checkout. Existing coursework attribution and the original README disclosure are retained.

## Run

Use JDK 17 (the source/target remains Java 11) and the checked-in Gradle wrapper:

```sh
./gradlew bootRun
```

The default port is 8080. Override with `--args='--server.port=18081'` if another local project uses that port. No database or Docker is needed for this process-local version.

Without `SMARTFRIDGE_JWT_SECRET`, the app generates a new random 32-byte signing key on startup. This keeps a local demo runnable without a hard-coded shared secret; previously issued tokens stop working after restart. For a stable key, set `SMARTFRIDGE_JWT_SECRET` to base64-encoded random bytes (at least 32 decoded bytes). The app rejects shorter keys. Do not commit this value.

## Authentication contract

- `POST /api/auth/register` accepts `{"username":"alice","password":"a-long-password"}` and returns 201.
- `POST /api/auth/token` accepts the same fields and returns 200 for valid credentials.
- Both return `{"accessToken":"...","tokenType":"Bearer","expiresIn":1800}`.
- Usernames are normalized by trimming and lowercasing; allowed characters are letters/digits/`_.@-`, length 3–64. Passwords must contain at least 12 characters and at most 72 UTF-8 bytes (the BCrypt input limit).
- Business API calls require `Authorization: Bearer <accessToken>`. Catalog GET requests remain public. No authentication cookies are accepted.
- Duplicate registration returns 409. Invalid credentials or JWT return 401. Invalid registration returns 400. Other users' grocery IDs return 404.

JWTs use a fixed HS256 algorithm allowlist and validate signature, issuer, audience, JWT type, subject, issue/expiry claims and expiry time. Passwords are stored as BCrypt hashes, never in JWT claims. Tokens last 30 minutes; Spring's standard timestamp validator includes clock skew tolerance. There is no refresh-token or account recovery flow.

## State isolation

The verified JWT subject selects each user's inventory, preferences, recommendation cache and grocery service instances. Clients cannot select an owner by passing a username, query parameter or body field. The existing facades remain internal classes without web routing annotations. Per-user HTTP operations are serialized to keep the original mutable list workflow and recipe grocery merges consistent under concurrent requests; different users have independent locks.

This is single-process memory, not durable multi-instance storage. Accounts and food data disappear when the process restarts. Horizontal scaling would require shared durable repositories and a revised concurrency strategy. This work has not been deployed to a cloud or tested under production traffic.

## Recipe behavior

The existing maximum score is 100: required-ingredient coverage up to 60, urgency up to 20, health-goal alignment up to 15, and cooking convenience up to 5. Tests check those components and boundary values. Expired/depleted batches no longer count as usable ingredients; where a food has several batches, the nearest usable expiry determines urgency. The recipe detail response uses the same availability rule. Missing optional ingredients do not reduce required-ingredient coverage.

Recipe-to-grocery planning adds missing required ingredient names and aggregates canonical duplicates, including aliases within the same recipe. Optional ingredients are excluded even when they share a canonical name with a required ingredient. All planned quantity changes are preflighted before mutation. If any addition would exceed the integer limit, the entire operation returns 409 and leaves the grocery list unchanged. The inherited quantity model is simplified: it does not consume inventory, convert grams/ml/count units, or plan partial quantity deficits. Calling the recipe add endpoint twice is treated as planning two servings/batches, so quantities add; it is not an idempotent retry API. A transport error or server error can occur after a plan has been applied. Flutter treats that outcome as unconfirmed, disables the original Add action and requires explicit confirmation of a duplicate-quantity warning before sending another batch. Known non-mutating 400/401/403/404/409 rejections remain recoverable without that warning.

## Request validation and error contract

Known controller rejections use `{"code":"...","message":"..."}` without exception names or stack traces. Missing/invalid bearer tokens are handled separately by Spring Security with HTTP 401 and the standard bearer challenge.

- Manual grocery `quantity` defaults to 1 and must be an integer from 0 through 2,147,483,647. `price` defaults to 0 and must be finite, non-negative and at most 1,000,000 per unit. The price cap keeps extreme input out of the inherited floating-point totals model; it does not add monetary precision or unit conversion.
- Negative quantity/price, non-finite price, excessive price, blank food names and unsupported health goals return 400 (`BAD_REQUEST`). Invalid requests do not mutate user data.
- Malformed/missing JSON, fractional quantities, explicit null primitive fields, and missing/invalid/out-of-range `delta` parameters return 400 (`INVALID_REQUEST`).
- `delta` can be negative. Decrements still floor the resulting quantity at zero, including `-2147483648`; positive overflow returns 409 (`QUANTITY_LIMIT_EXCEEDED`) without changing the stored row.
- Recipe planning applies the same limit to the complete plan before adding or updating any row. A conflict on a later ingredient leaves earlier ingredients unchanged. Flutter displays a recovery message for this code.
- Missing owned grocery rows and recipes return 404 (`NOT_FOUND`). A foreign grocery ID returns the same response as an unknown ID.

Full Spring tests exercise concurrent quantity increments and collected toggles, competing increments at the integer limit, and recipe planning interleaved with checkout. They verify exact quantities/parity or a complete recipe plan, and separate ownership. These checks cover the existing per-user HTTP serialization; direct legacy service instances do not acquire these HTTP locks.

## Verify and inspect real coverage

```sh
./gradlew clean test jacocoTestReport
# With the local API running on 18081:
python3 tools/http_smoke.py
python3 tools/summarize_verification.py
```

For the actual HTTP transport, start the API on port 18081, then run `python3 tools/http_smoke.py`. The recorded local run passed 60 HTTP checks for registration/login, inventory-to-recipe-to-grocery planning, cross-user 404s, owner isolation, checkout, structured invalid-input responses, overflow atomicity and concurrent quantity/toggle updates. Those checks are functional tests, not a traffic benchmark.

The JaCoCo report includes **all compiled production Java classes**, including legacy CLI code and main entrypoints; no classes are excluded. The summary records line and branch coverage with covered/total denominators, test failures/errors/skips, the base Git commit and a fingerprint of current source/tests. The current run contains 197 Java tests with no failures, errors or skips. Coverage describes the executed tests and does not prove correctness or user benefit. The real HTTP report must match the source/test fingerprint before the summarizer accepts it; these recorded fingerprints identify inputs rather than attesting to a deployed binary. Flutter checks are separate and are not included in the Java coverage denominator.

Artifacts:

- `build/test-results/test/TEST-*.xml`: actual JUnit results.
- `build/reports/jacoco/test/jacocoTestReport.xml`: machine-readable coverage.
- `build/reports/jacoco/test/html/index.html`: line-by-line uncovered code.
- `reports/local-verification/summary.json` and `summary.md`: reproducible evidence for resume wording.
- `reports/local-verification/http-smoke.json`: results from the running local API over real HTTP.
- `reports/local-verification/jacoco-class-summary.xml`: raw JaCoCo counters by class, extracted by the summarizer without the larger HTML report.

## Continuous integration

`.github/workflows/java-verification.yml` runs the complete Java test suite and all-production-class JaCoCo task on pushes and pull requests with JDK 17. It retains JUnit XML and JaCoCo XML as artifacts. HTTP transport checks and Flutter unit/widget/static checks are separately recorded local verification, not claimed CI results.
