import 'dart:async';

import 'package:flutter/services.dart';

class FlutterXmpp {
  static const MethodChannel _channel = MethodChannel('flutter_xmpp/method');
  static const EventChannel _eventChannel = EventChannel('flutter_xmpp/stream');

  static StreamSubscription streamGetMsg;

  dynamic auth;

  FlutterXmpp(dynamic params){
    this.auth = params;
  }

  Future<void> login() async {
      await _channel.invokeMethod('login',this.auth);
  }

  Future<void> logout() async {
    await _channel.invokeMethod('logout');
  }

  Future<String> sendMessage(String toJid,String body,String Id) async {
    var params = {
      "to_jid": toJid,
      "body":body,
      "id":Id
    };
    String status = await _channel.invokeMethod('send_message',params);
    return status;
  }

  Future<String> sendGroupMessage(String toJid,String body,String Id) async {
    var params = {
      "to_jid": toJid,
      "body":body,
      "id":Id
    };
    String status = await _channel.invokeMethod('send_group_message',params);
    return status;
  }

  Future<String> readMessage(String toJid,String Id) async {
    var params = {
      "to_jid": toJid,
      "id":Id
    };
    String status = await _channel.invokeMethod('read_message',params);
    return status;
  }

  Future<String> currentState() async {
    String state = await _channel.invokeMethod('current_state');
    return state;
  }

  Future<void> start(_onEvent,_onError) async {
    streamGetMsg = _eventChannel.receiveBroadcastStream().listen(_onEvent, onError: _onError);
  }

  Future<void> stop() async {
    streamGetMsg.cancel();
  }









}
