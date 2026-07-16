import 'dart:convert';

import 'package:aphasia_recovery/settings.dart';
import 'package:aphasia_recovery/utils/http/http_manager.dart';
import 'package:aphasia_recovery/utils/io/shared_pref.dart';
import 'package:aphasia_recovery/widgets/ui/patient/home.dart';
import 'package:aphasia_recovery/widgets/ui/register.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart';
import 'package:mockito/mockito.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../TestBase.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    WrappedSharedPref.instance = null;
    HttpClientManager().testClient = null;
    TestBase.commonSetUp();
  });

  testWidgets('公开注册只显示患者说明并固定提交 role=1', (tester) async {
    final client = HttpClientManager().testClient!;
    when(client.post(
      Uri.parse('${HttpConstants.backendBaseUrl}/api/register'),
      body: anyNamed('body'),
      headers: anyNamed('headers'),
    )).thenAnswer((_) async => Response(
          jsonEncode({
            'identity': 'patient@example.com',
            'uid': 'patient-1',
            'token': 'patient-token',
            'role': 1,
          }),
          200,
        ));

    await TestBase.testWithFullGlobalStates(
        tester, const RegisterPage(commonStyles: null), () async {
      await tester.pumpAndSettle();

      expect(find.textContaining('公开注册仅创建患者账号'), findsOneWidget);
      expect(find.text('医疗人员'), findsNothing);

      await tester.enterText(
          find.widgetWithText(TextFormField, '手机号/邮箱'), 'patient@example.com');
      await tester.enterText(
          find.widgetWithText(TextFormField, '密码'), 'abc1234');
      await tester.enterText(
          find.widgetWithText(TextFormField, '确认密码'), 'abc1234');

      await tester.tap(find.widgetWithText(ElevatedButton, '立即注册'));
      await tester.pumpAndSettle();

      final verification = verify(client.post(
        Uri.parse('${HttpConstants.backendBaseUrl}/api/register'),
        body: captureAnyNamed('body'),
        headers: anyNamed('headers'),
      ));
      final payload = jsonDecode(verification.captured.single as String)
          as Map<String, dynamic>;
      expect(payload['role'], 1);
      expect(find.byType(HomePage), findsOneWidget);
    });
  });
}
