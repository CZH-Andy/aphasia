import 'package:aphasia_recovery/utils/io/shared_pref.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/fake_secure_token_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeSecureTokenStore secureStore;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    secureStore = FakeSecureTokenStore();
    WrappedSharedPref.secureStoreFactory = () => secureStore;
    WrappedSharedPref.instance = null;
  });

  test('优先读取安全存储并清理残留的旧 Token', () async {
    secureStore.values[WrappedSharedPref.secureTokenKey] = 'secure-token';
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    WrappedSharedPref.instance = null;

    final token = await WrappedSharedPref().retrieveToken();
    final prefs = await SharedPreferences.getInstance();

    expect(token, 'secure-token');
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), isNull);
  });

  test('首次读取会把旧 SharedPreferences Token 无损迁移到安全存储', () async {
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    WrappedSharedPref.instance = null;

    final token = await WrappedSharedPref().retrieveToken();
    final prefs = await SharedPreferences.getInstance();

    expect(token, 'legacy-token');
    expect(
        secureStore.values[WrappedSharedPref.secureTokenKey], 'legacy-token');
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), isNull);
  });

  test('安全写入失败时保留旧 Token，避免升级过程中丢失登录态', () async {
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    secureStore.writeError = StateError('secure storage unavailable');
    WrappedSharedPref.instance = null;

    await expectLater(
      WrappedSharedPref().retrieveToken(),
      throwsA(isA<StateError>()),
    );

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), 'legacy-token');
  });

  test('保存 Token 只写安全存储并删除旧值', () async {
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    WrappedSharedPref.instance = null;

    await WrappedSharedPref().saveToken('new-token');
    final prefs = await SharedPreferences.getInstance();

    expect(secureStore.values[WrappedSharedPref.secureTokenKey], 'new-token');
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), isNull);
  });

  test('登出同时清理安全存储和旧 SharedPreferences', () async {
    secureStore.values[WrappedSharedPref.secureTokenKey] = 'secure-token';
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    WrappedSharedPref.instance = null;

    await WrappedSharedPref().deleteToken();
    final prefs = await SharedPreferences.getInstance();

    expect(secureStore.values[WrappedSharedPref.secureTokenKey], isNull);
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), isNull);
  });

  test('安全存储删除失败时仍会清理旧 SharedPreferences 并向上抛错', () async {
    secureStore.values[WrappedSharedPref.secureTokenKey] = 'secure-token';
    secureStore.deleteError = StateError('delete failed');
    SharedPreferences.setMockInitialValues({
      WrappedSharedPref.legacyTokenKey: 'legacy-token',
    });
    WrappedSharedPref.instance = null;

    await expectLater(
      WrappedSharedPref().deleteToken(),
      throwsA(isA<StateError>()),
    );

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(WrappedSharedPref.legacyTokenKey), isNull);
    expect(
        secureStore.values[WrappedSharedPref.secureTokenKey], 'secure-token');
  });
}
