import 'dart:async';

import 'package:aphasia_recovery/utils/io/shared_pref.dart';

import 'support/fake_secure_token_store.dart';

Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  WrappedSharedPref.secureStoreFactory = () => FakeSecureTokenStore();
  await testMain();
}
