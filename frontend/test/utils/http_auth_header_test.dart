import 'package:aphasia_recovery/settings.dart';
import 'package:aphasia_recovery/utils/http/http_manager.dart';
import 'package:aphasia_recovery/utils/io/shared_pref.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart';
import 'package:mockito/mockito.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late HttpClientManager manager;
  late Client client;

  setUp(() {
    SharedPreferences.setMockInitialValues({'Token': 'saved-jwt'});
    WrappedSharedPref.instance = null;
    manager = HttpClientManager();
    manager.testClient = null;
    manager.enableTestMode();
    client = manager.testClient!;
  });

  test('自动认证使用 Authorization Bearer，不发送旧 Token header', () async {
    when(client.get(
      Uri.parse('${HttpConstants.backendBaseUrl}/api/test'),
      headers: argThat(
        allOf(
          containsPair('Authorization', 'Bearer saved-jwt'),
          isNot(contains('Token')),
        ),
        named: 'headers',
      ),
    )).thenAnswer((_) async => Response('{"ok":true}', 200));

    final response = await manager.get(
      url: '${HttpConstants.backendBaseUrl}/api/test',
    ) as Map<String, dynamic>;

    expect(response['ok'], true);
  });

  test('构造认证 header 时不修改调用方传入的 Map', () async {
    final original = <String, String>{
      'Token': 'caller-legacy-token',
      'X-Request-Id': 'request-1',
    };

    final resolved = await manager.setTokenToHeaders(original);

    expect(original['Token'], 'caller-legacy-token');
    expect(resolved['Token'], isNull);
    expect(resolved['Authorization'], 'Bearer saved-jwt');
    expect(resolved['X-Request-Id'], 'request-1');
  });

  test('业务响应中的旧 Token header 不再覆盖本地会话', () async {
    when(client.get(
      Uri.parse('${HttpConstants.backendBaseUrl}/api/test'),
      headers: anyNamed('headers'),
    )).thenAnswer((_) async => Response(
          '{"ok":true}',
          200,
          headers: {'Token': 'unexpected-rotated-token'},
        ));

    await manager.get(url: '${HttpConstants.backendBaseUrl}/api/test');

    expect(await WrappedSharedPref().retrieveToken(), 'saved-jwt');
  });
}
