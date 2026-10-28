# SmartFridge local verification

Generated UTC: 2026-10-28T05:56:59.024404+00:00

All compiled Java main-source classes; no class exclusions; Flutter is separate.

JUnit: 197 tests; 0 failures; 0 errors; 0 skipped.

| Scope | Line coverage | Branch coverage |
| --- | --- | --- |
| All production Java | 996/1266 (78.67%) | 303/460 (65.87%) |
| api | 487/512 (95.12%) | 165/210 (78.57%) |
| controller | 50/51 (98.04%) | 0/0 (not applicable) |
| loader | 76/103 (73.79%) | 34/58 (58.62%) |
| model | 125/126 (99.21%) | 5/6 (83.33%) |
| service | 215/241 (89.21%) | 74/94 (78.72%) |
| ui | 43/233 (18.45%) | 25/92 (27.17%) |

The denominator includes main entrypoints and legacy CLI classes. Coverage is not a correctness guarantee.

## Verified behaviors

- BCrypt registration/login and HS256 bearer-token signature, issuer, audience, type, subject and expiry checks.
- Two users retain separate inventory, preferences, recommendation state and grocery lists; foreign IDs return 404.
- One user checkout does not clear another user; missing/forged/expired/invalid tokens are rejected.
- Twelve recipe-add requests on four worker threads merge into three lines with no lost quantity updates.
- Malformed JSON, fractional/null numeric inputs, negative quantities and invalid prices return structured errors without mutation.
- Quantity overflow returns 409 without resetting stock or partially applying a recipe plan; negative deltas still floor at zero.
- Concurrent increments/toggles retain exact quantity and parity; competing increments at the limit yield one success and one conflict.
- Concurrent recipe planning and checkout retain either an empty list or complete ingredient quantities for whole recipe plans.
- Recipe scoring checks exact 60/20/15/5-point components, time boundaries and all three health goals.
- Expired/depleted inventory is excluded; usable batches nearest expiry determine urgency; optional ingredients do not reduce coverage.

## Limits

- Local functional verification; not production traffic or a cloud deployment.
- No recipe quality/accuracy improvement or business impact was measured.
- Accounts, inventory, preferences, recommendations and groceries are process-local memory.
- Grocery quantities retain the original simplified unit model; no cross-unit conversion or inventory consumption.

## Reproduce

```sh
./gradlew clean test jacocoTestReport
python3 tools/summarize_verification.py
```

Base commit: `7c97b4b33aa2bcac82b72d90a0c23a14af512005`
Source/test SHA256: `ad21a487ac96a6d515cf82a86fa96f048c64ffeca3d0ea00a62ecc5b9cd53e2a`

## Real HTTP smoke checks

60 checks passed; 0 failed against a running local Spring Boot server.
Evidence: `reports/local-verification/http-smoke.json`. This is functional verification, not a throughput/latency benchmark.
