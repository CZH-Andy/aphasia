import 'dart:convert';

import 'package:aphasia_recovery/exceptions/http_exceptions.dart';
import 'package:aphasia_recovery/utils/common_widget_function.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

void main() {
  testWidgets("409 result conflict shows recoverable message without throwing",
      (tester) async {
    late BuildContext context;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Builder(builder: (builderContext) {
          context = builderContext;
          return const SizedBox();
        }),
      ),
    ));

    final request =
        http.Request("POST", Uri.parse("http://localhost/api/examRecord"));
    final response = http.Response.bytes(
      utf8.encode('{"code":409,"message":"作答记录已在其他请求中更新，请刷新后重试"}'),
      409,
      request: request,
      headers: {"content-type": "application/json; charset=utf-8"},
    );

    requestResultErrorHandler(
      context,
      error: HttpRequestException(message: response.body, response: response),
    );
    await tester.pump();

    expect(find.text("作答记录已在其他请求中更新，请刷新后重试"), findsOneWidget);
  });
}
