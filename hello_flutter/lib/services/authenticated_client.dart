import 'package:http/http.dart' as http;

/// Keeps the access token in memory and attaches it only to business requests.
class AuthenticatedClient extends http.BaseClient {
  AuthenticatedClient(this._inner);

  final http.Client _inner;
  String? _token;
  void Function()? onSessionExpired;

  bool get isAuthenticated => _token != null;

  void establishSession(String token) {
    _token = token;
  }

  void signOut() {
    _token = null;
  }

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final publicAuth = request.url.path.startsWith('/api/auth/');
    final sentToken = publicAuth ? null : _token;
    if (sentToken != null) {
      request.headers['Authorization'] = 'Bearer $sentToken';
    }
    final response = await _inner.send(request);
    // A late response from an old session must not sign out a newly signed-in user.
    if (response.statusCode == 401 &&
        !publicAuth &&
        sentToken != null &&
        _token == sentToken) {
      signOut();
      onSessionExpired?.call();
    }
    return response;
  }

  @override
  void close() => _inner.close();
}
