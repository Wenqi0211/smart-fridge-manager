#!/usr/bin/env python3
"""Exercise a running local server over real HTTP. No mock controllers or fabricated metrics."""
import argparse
import datetime
import hashlib
from concurrent.futures import ThreadPoolExecutor
from threading import Lock
import json
from pathlib import Path
import secrets
import urllib.error
import urllib.request


def run(base):
    checks = []
    checks_lock = Lock()

    def call(name, method, path, expected=200, token=None, body=None, raw_body=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        data = raw_body.encode() if raw_body is not None else (None if body is None else json.dumps(body).encode())
        request = urllib.request.Request(base.rstrip('/') + path, method=method, headers=headers, data=data)
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                status, payload = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, payload = error.code, error.read()
        if status != expected:
            raise AssertionError(f'{name}: expected HTTP {expected}, received {status}')
        with checks_lock:
            checks.append({'name': name, 'status': status, 'passed': True})
        return json.loads(payload) if payload else None

    call('Missing bearer token rejected', 'GET', '/api/inventory', 401)
    call('Malformed bearer token rejected', 'GET', '/api/grocery/items', 401, token='invalid')
    credentials = {'username': 'smoke-' + secrets.token_hex(8), 'password': secrets.token_urlsafe(24)}
    alice = call('Alice registers', 'POST', '/api/auth/register', 201, body=credentials)['accessToken']
    bob = call('Bob registers', 'POST', '/api/auth/register', 201,
               body={'username': 'smoke-' + secrets.token_hex(8), 'password': secrets.token_urlsafe(24)})['accessToken']
    call('Alice adds inventory', 'POST', '/api/inventory', token=alice, body={'foodName': 'Large Eggs'})
    call('Alice saves preference', 'PUT', '/api/preference', token=alice, body={'healthGoal': 'MUSCLE_BUILDING'})
    assert len(call('Alice sees own inventory', 'GET', '/api/inventory', token=alice)) == 1
    assert call('Bob inventory remains empty', 'GET', '/api/inventory', token=bob) == []
    assert call('Bob preference remains unset', 'GET', '/api/preference', token=bob) is None
    first = call('Recipe adds two missing ingredients', 'POST', '/api/recommendations/rec_001/grocery', token=alice)
    assert first['addedCount'] == 2 and first['mergedCount'] == 0
    second = call('Second recipe plan merges both lines', 'POST', '/api/recommendations/rec_001/grocery', token=alice)
    assert second['addedCount'] == 0 and second['mergedCount'] == 2
    groceries = call('Alice sees two merged grocery lines', 'GET', '/api/grocery/items', token=alice)
    assert len(groceries) == 2
    assert {i['name']: i['quantity'] for i in groceries} == {'Unsalted Butter': 28, 'Whole Milk': 60}
    foreign = groceries[0]['id']
    call('Bob cannot alter Alice quantity', 'PATCH', f'/api/grocery/items/{foreign}/quantity?delta=1', 404, bob)
    call('Bob cannot collect Alice item', 'PATCH', f'/api/grocery/items/{foreign}/collected', 404, bob)
    call('Bob cannot delete Alice item', 'DELETE', f'/api/grocery/items/{foreign}', 404, bob)
    assert call('Bob sees no groceries', 'GET', '/api/grocery/items', token=bob) == []
    call('Bob checks out own state only', 'POST', '/api/grocery/checkout', token=bob)
    assert len(call('Alice grocery survives Bob checkout', 'GET', '/api/grocery/items', token=alice)) == 2
    login = call('Alice logs in again', 'POST', '/api/auth/token', body=credentials)['accessToken']
    assert len(call('New token retains same owner state', 'GET', '/api/inventory', token=login)) == 1
    call('Alice checks out', 'POST', '/api/grocery/checkout', token=login)
    assert call('Alice groceries cleared', 'GET', '/api/grocery/items', token=login) == []
    assert call('Alice inventory cleared', 'GET', '/api/inventory', token=login) == []
    assert call('Alice preference cleared', 'GET', '/api/preference', token=login) is None
    boundary = call('Boundary-test user registers', 'POST', '/api/auth/register', 201,
                    body={'username': 'bounds-' + secrets.token_hex(8), 'password': secrets.token_urlsafe(24)})['accessToken']
    for name, body in [
        ('Negative quantity rejected', {'foodName': 'Garlic', 'quantity': -1}),
        ('Negative price rejected', {'foodName': 'Garlic', 'price': -1}),
        ('Non-finite price rejected', {'foodName': 'Garlic', 'price': 'Infinity'}),
        ('Excessive price rejected', {'foodName': 'Garlic', 'price': 1000001}),
    ]:
        error = call(name, 'POST', '/api/grocery/items', 400, boundary, body)
        assert error['code'] == 'BAD_REQUEST' and error['message']
    for name, payload in [
        ('Malformed JSON rejected', '{'),
        ('Fractional quantity rejected', '{"foodName":"Garlic","quantity":1.5}'),
        ('Null numeric field rejected', '{"foodName":"Garlic","quantity":null}'),
    ]:
        error = call(name, 'POST', '/api/grocery/items', 400, boundary, raw_body=payload)
        assert error['code'] == 'INVALID_REQUEST' and 'trace' not in error
    assert call('Invalid requests leave groceries empty', 'GET', '/api/grocery/items', token=boundary) == []
    maximum = call('Maximum integer quantity accepted', 'POST', '/api/grocery/items', token=boundary,
                   body={'foodName': 'Whole Milk', 'quantity': 2147483647})
    maximum_id = maximum['id']
    for name, query in [('Missing delta rejected', ''), ('Invalid delta rejected', '?delta=nope')]:
        error = call(name, 'PATCH', f'/api/grocery/items/{maximum_id}/quantity{query}', 400, boundary)
        assert error['code'] == 'INVALID_REQUEST'
    error = call('Overflow rejected instead of resetting quantity', 'PATCH',
                 f'/api/grocery/items/{maximum_id}/quantity?delta=1', 409, boundary)
    assert error['code'] == 'QUANTITY_LIMIT_EXCEEDED'
    error = call('Recipe overflow rejected before any mutation', 'POST', '/api/recommendations/rec_001/grocery', 409, boundary)
    assert error['code'] == 'QUANTITY_LIMIT_EXCEEDED'
    lines = call('Failed quantity and recipe updates preserve original list', 'GET', '/api/grocery/items', token=boundary)
    assert len(lines) == 1 and lines[0]['quantity'] == 2147483647
    reduced = call('Largest negative delta floors quantity at zero', 'PATCH',
                   f'/api/grocery/items/{maximum_id}/quantity?delta=-2147483648', token=boundary)
    assert reduced['quantity'] == 0
    call('Boundary user checks out', 'POST', '/api/grocery/checkout', token=boundary)
    line = call('Concurrent-test grocery created', 'POST', '/api/grocery/items', token=boundary,
                body={'foodName': 'Garlic', 'quantity': 1})
    line_id = line['id']

    def concurrent_change(i):
        call(f'Concurrent quantity increment {i + 1}', 'PATCH',
             f'/api/grocery/items/{line_id}/quantity?delta=1', token=boundary)
        call(f'Concurrent collected toggle {i + 1}', 'PATCH',
             f'/api/grocery/items/{line_id}/collected', token=boundary)

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(concurrent_change, range(8)))
    concurrent = call('Concurrent requests retain exact quantity and toggle parity', 'GET', '/api/grocery/items', token=boundary)
    assert len(concurrent) == 1 and concurrent[0]['quantity'] == 9 and not concurrent[0]['collected']
    assert call('Concurrent owner mutations do not affect Bob', 'GET', '/api/grocery/items', token=bob) == []
    root = Path(__file__).resolve().parents[1]
    digest = hashlib.sha256()
    for path in sorted(list((root / 'src/main').rglob('*')) + list((root / 'src/test').rglob('*')) + [root / 'build.gradle']):
        if path.is_file():
            digest.update(str(path.relative_to(root)).encode())
            digest.update(path.read_bytes())
    return {'generatedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'baseUrl': base, 'transport': 'real HTTP to local Spring Boot server',
            'sourceAndTestSha256': digest.hexdigest(),
            'passed': len(checks), 'failed': 0, 'checks': checks,
            'limit': 'Functional smoke checks only; not a throughput or latency benchmark.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://127.0.0.1:18081')
    parser.add_argument('--output', default='reports/local-verification/http-smoke.json')
    args = parser.parse_args()
    summary = run(args.base_url)
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({k: summary[k] for k in ['transport', 'passed', 'failed']}, indent=2))
