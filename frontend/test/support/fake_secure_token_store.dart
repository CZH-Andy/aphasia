import 'package:aphasia_recovery/utils/io/shared_pref.dart';

class FakeSecureTokenStore implements SecureTokenStore {
  final Map<String, String> values;
  Object? readError;
  Object? writeError;
  Object? deleteError;

  FakeSecureTokenStore({Map<String, String>? initialValues})
      : values = {...?initialValues};

  @override
  Future<void> write(String key, String value) async {
    if (writeError != null) {
      throw writeError!;
    }
    values[key] = value;
  }

  @override
  Future<String?> read(String key) async {
    if (readError != null) {
      throw readError!;
    }
    return values[key];
  }

  @override
  Future<void> delete(String key) async {
    if (deleteError != null) {
      throw deleteError!;
    }
    values.remove(key);
  }
}
