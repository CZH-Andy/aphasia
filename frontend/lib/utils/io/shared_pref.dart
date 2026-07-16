import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract interface class SecureTokenStore {
  Future<void> write(String key, String value);

  Future<String?> read(String key);

  Future<void> delete(String key);
}

class FlutterSecureTokenStore implements SecureTokenStore {
  final FlutterSecureStorage _storage;

  FlutterSecureTokenStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              // macOS 的 Data Protection Keychain 需要开发证书签名。
              // Release 使用它；Debug/Profile 保持可无证书本地构建。
              mOptions: MacOsOptions(usesDataProtectionKeychain: kReleaseMode),
            );

  @override
  Future<void> write(String key, String value) {
    return _storage.write(key: key, value: value);
  }

  @override
  Future<String?> read(String key) {
    return _storage.read(key: key);
  }

  @override
  Future<void> delete(String key) {
    return _storage.delete(key: key);
  }
}

/// 历史名称保留给现有调用方。Token 已迁移到系统安全存储；
/// SharedPreferences 只用于读取并清理旧版本留下的明文值。
class WrappedSharedPref {
  static const String secureTokenKey = 'auth_token_v2';
  static const String legacyTokenKey = 'Token';

  static SecureTokenStore Function() secureStoreFactory =
      () => FlutterSecureTokenStore();

  final Future<SharedPreferences> _legacyPrefs =
      SharedPreferences.getInstance();
  final SecureTokenStore _secureStore;

  WrappedSharedPref._() : _secureStore = secureStoreFactory();

  static WrappedSharedPref? instance;

  factory WrappedSharedPref() {
    return instance ??= WrappedSharedPref._();
  }

  Future<void> saveToken(String token) async {
    if (token.isEmpty) {
      throw ArgumentError.value(token, 'token', 'Token不能为空');
    }

    await _secureStore.write(secureTokenKey, token);
    final prefs = await _legacyPrefs;
    await prefs.remove(legacyTokenKey);
  }

  Future<String?> retrieveToken() async {
    final secureToken = await _secureStore.read(secureTokenKey);
    final prefs = await _legacyPrefs;

    if (secureToken != null && secureToken.isNotEmpty) {
      await prefs.remove(legacyTokenKey);
      return secureToken;
    }

    final legacyToken = prefs.getString(legacyTokenKey);
    if (legacyToken == null || legacyToken.isEmpty) {
      if (legacyToken != null) {
        await prefs.remove(legacyTokenKey);
      }
      return null;
    }

    // 只有安全写入成功后才删除旧值，升级中断时不会丢失登录态。
    await _secureStore.write(secureTokenKey, legacyToken);
    await prefs.remove(legacyTokenKey);
    return legacyToken;
  }

  Future<void> deleteToken() async {
    Object? secureDeleteError;
    StackTrace? secureDeleteStackTrace;

    try {
      await _secureStore.delete(secureTokenKey);
    } catch (error, stackTrace) {
      secureDeleteError = error;
      secureDeleteStackTrace = stackTrace;
    }

    final prefs = await _legacyPrefs;
    await prefs.remove(legacyTokenKey);

    if (secureDeleteError != null) {
      Error.throwWithStackTrace(
          secureDeleteError, secureDeleteStackTrace ?? StackTrace.current);
    }
  }
}
