import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hello_flutter/services/authenticated_client.dart';
import 'package:hello_flutter/services/fridge_api_service.dart';

void main() {
  test(
    'registration establishes a session used by subsequent API requests',
    () async {
      final requests = <http.Request>[];
      final service = FridgeApiService(
        apiBase: 'http://localhost:8080',
        client: MockClient((request) async {
          requests.add(request);
          if (request.url.path == '/api/auth/register') {
            expect(jsonDecode(request.body), {
              'username': 'alice',
              'password': 'long-password-123',
            });
            expect(request.headers['Authorization'], isNull);
            return http.Response(
              '{"accessToken":"test-token","tokenType":"Bearer","expiresIn":1800}',
              201,
            );
          }
          return http.Response('[]', 200);
        }),
      );
      addTearDown(service.close);
      await service.register('alice', 'long-password-123');
      expect(service.isAuthenticated, isTrue);
      expect(await service.fetchInventory(), isEmpty);
      expect(requests.last.headers['Authorization'], 'Bearer test-token');
    },
  );

  test('invalid credentials do not create an authenticated session', () async {
    final service = FridgeApiService(
      apiBase: 'http://localhost:8080',
      client: MockClient((_) async => http.Response('{}', 401)),
    );
    addTearDown(service.close);
    await expectLater(
      service.signIn('alice', 'wrong-password'),
      throwsA(
        isA<FridgeApiException>().having((e) => e.statusCode, 'status', 401),
      ),
    );
    expect(service.isAuthenticated, isFalse);
  });

  test(
    'malformed authentication response cannot establish a session',
    () async {
      final service = FridgeApiService(
        apiBase: 'http://localhost:8080',
        client: MockClient(
          (_) async =>
              http.Response('{"accessToken":"","tokenType":"Bearer"}', 200),
        ),
      );
      addTearDown(service.close);
      await expectLater(
        service.signIn('alice', 'password'),
        throwsA(isA<FridgeApiException>()),
      );
      expect(service.isAuthenticated, isFalse);
    },
  );

  test('authorization is attached for all business request methods', () async {
    final client = AuthenticatedClient(
      MockClient((request) async {
        expect(request.headers['Authorization'], 'Bearer session-token');
        return http.Response('{}', 200);
      }),
    )..establishSession('session-token');
    addTearDown(client.close);
    for (final method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']) {
      final response = await client.send(
        http.Request(method, Uri.parse('http://localhost:8080/api/inventory')),
      );
      await response.stream.drain<void>();
    }
  });

  test('401 clears the session and notifies the app once', () async {
    var expired = 0;
    final client = AuthenticatedClient(
      MockClient((_) async => http.Response('{}', 401)),
    )..establishSession('expired-token');
    client.onSessionExpired = () => expired++;
    addTearDown(client.close);
    await client.get(Uri.parse('http://localhost:8080/api/inventory'));
    await client.get(Uri.parse('http://localhost:8080/api/inventory'));
    expect(client.isAuthenticated, isFalse);
    expect(expired, 1);
  });

  test('late 401 from an old session does not clear the new session', () async {
    final oldResponse = Completer<http.Response>();
    final started = Completer<void>();
    var expired = 0;
    final client = AuthenticatedClient(
      MockClient((request) async {
        if (request.headers['Authorization'] == 'Bearer old-token') {
          started.complete();
          return oldResponse.future;
        }
        expect(request.headers['Authorization'], 'Bearer new-token');
        return http.Response('{}', 200);
      }),
    )..establishSession('old-token');
    client.onSessionExpired = () => expired++;
    addTearDown(client.close);
    final oldRequest = client.get(
      Uri.parse('http://localhost:8080/api/inventory'),
    );
    await started.future;
    client.establishSession('new-token');
    oldResponse.complete(http.Response('{}', 401));
    await oldRequest;
    await client.get(Uri.parse('http://localhost:8080/api/inventory'));
    expect(client.isAuthenticated, isTrue);
    expect(expired, 0);
  });

  test('sign out removes the token from future requests', () async {
    final client = AuthenticatedClient(
      MockClient((request) async {
        expect(request.headers['Authorization'], isNull);
        return http.Response('{}', 200);
      }),
    )..establishSession('old-token');
    addTearDown(client.close);
    client.signOut();
    await client.get(Uri.parse('http://localhost:8080/api/inventory'));
    expect(client.isAuthenticated, isFalse);
  });

  test('authentication requests never send a previous access token', () async {
    final client = AuthenticatedClient(
      MockClient((request) async {
        expect(request.headers['Authorization'], isNull);
        return http.Response('{}', 401);
      }),
    )..establishSession('previous-token');
    addTearDown(client.close);
    await client.post(Uri.parse('http://localhost:8080/api/auth/token'));
    expect(client.isAuthenticated, isTrue);
  });
  test('structured errors retain codes and malformed bodies stay safe', () {
    expect(
      FridgeApiException('rejected', '{"code":"BAD_REQUEST"}').code,
      'BAD_REQUEST',
    );
    for (final body in [
      '',
      '<html>unavailable</html>',
      'null',
      '[]',
      '{"code":42}',
    ]) {
      expect(FridgeApiException('rejected', body).code, isNull);
    }
  });
}
