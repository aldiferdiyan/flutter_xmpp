import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_xmpp/flutter_xmpp.dart';

void main() {
  const MethodChannel channel = MethodChannel('flutter_xmpp');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await FlutterXmpp.platformVersion, '42');
  });
}
