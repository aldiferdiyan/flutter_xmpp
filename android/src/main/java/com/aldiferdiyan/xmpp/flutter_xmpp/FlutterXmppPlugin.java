package com.aldiferdiyan.xmpp.flutter_xmpp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class FlutterXmppPlugin extends FlutterActivity implements MethodCallHandler, EventChannel.StreamHandler {

    private static final String TAG = "flutter_xmpp";
    public static final Boolean DEBUG = false;


    private static final String CHANNEL = "flutter_xmpp/method";
    private static final String CHANNEL_STREAM = "flutter_xmpp/stream";

    private static Activity activity;

    private String jid_user = "";
    private String password = "";
    private String host = "";
    private Integer port = 0;
    private BroadcastReceiver mBroadcastReceiver = null;
    private String current_stat = "STOP";

    FlutterXmppPlugin(Activity activity) {
        this.activity = activity;
    }

    public static void registerWith(Registrar registrar) {

        //method channel
        final MethodChannel method_channel = new MethodChannel(registrar.messenger(), CHANNEL);
        method_channel.setMethodCallHandler(new FlutterXmppPlugin(registrar.activity()));

        //event channel
        final EventChannel event_channel = new EventChannel(registrar.messenger(), CHANNEL_STREAM);
        event_channel.setStreamHandler(new FlutterXmppPlugin(registrar.activity()));

    }


    // ****************************************
    // stream
    @Override
    public void onListen(Object auth, EventChannel.EventSink eventSink) {

        if (mBroadcastReceiver == null) {
            if (DEBUG) {
                Log.w(TAG, "adding listener");
            }
            mBroadcastReceiver = get_message(eventSink);
            IntentFilter filter = new IntentFilter();
            filter.addAction(FlutterXmppConnectionService.RECEIVE_MESSAGE);
            filter.addAction(FlutterXmppConnectionService.OUTGOING_MESSAGE);
            activity.registerReceiver(mBroadcastReceiver, filter);
        }

    }

    @Override
    public void onCancel(Object o) {
        if (mBroadcastReceiver != null) {
            if (DEBUG) {
                Log.w(TAG, "cancelling listener");
            }
            activity.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }


    private static BroadcastReceiver get_message(final EventChannel.EventSink events) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case FlutterXmppConnectionService.RECEIVE_MESSAGE:
                        String from = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_FROM_JID);
                        String body = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY);
                        String idIncoming = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS);

                        if (DEBUG) {
                            Log.d(TAG, "msg : " + from + " : " + body);
                        }
                        Map<String, Object> build = new HashMap<>();
                        build.put("type", "incoming");
                        build.put("id", idIncoming);
                        build.put("from", from);
                        build.put("body", body);
                        events.success(build);
                        break;
                    case FlutterXmppConnectionService.OUTGOING_MESSAGE:
                        String to = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_TO_JID);
                        String bodyTo = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY);
                        String idOutgoing = intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS);
                        if (DEBUG) {
                            Log.d(TAG, "msg : " + to + " : " + bodyTo);
                        }
                        Map<String, Object> buildTo = new HashMap<>();
                        buildTo.put("type", "outgoing");
                        buildTo.put("id", idOutgoing);
                        buildTo.put("to", to);
                        buildTo.put("body", bodyTo);
                        events.success(buildTo);
                        break;

                }
            }
        };
    }


    // ****************************************
    // method call
    @Override
    public void onMethodCall(MethodCall call, Result result) {

        // send_message
        if (call.method.equals("login")) {

            if (!call.hasArgument("user_jid") || !call.hasArgument("password") || !call.hasArgument("host")) {
                result.error("MISSING", "Missing auth.", null);
            }
            this.jid_user = call.argument("user_jid").toString();
            this.password = call.argument("password").toString();
            this.host = call.argument("host").toString();
            if (call.hasArgument("port")) {
                this.port = Integer.parseInt(call.argument("port").toString());
            }
            login();

            result.success("SUCCESS");

        } else if (call.method.equals("logout")) {

            logout();

            result.success("SUCCESS");

        } else if (call.method.equals("send_message")) {
            if (!call.hasArgument("to_jid") || !call.hasArgument("body") || !call.hasArgument("id")) {
                result.error("MISSING", "Missing argument to_jid / body / id chat.", null);
            }
            String to_jid = call.argument("to_jid");
            String body = call.argument("body");
            String id = call.argument("id");
            send_message(body, to_jid, id);

            result.success("SUCCESS");

        } else if (call.method.equals("send_group_message")) {
            if (!call.hasArgument("to_jid") || !call.hasArgument("body") || !call.hasArgument("id")) {
                result.error("MISSING", "Missing argument to_jid / body / id chat.", null);
            }
            String to_jid = call.argument("to_jid");
            String body = call.argument("body");
            String id = call.argument("id");
            send_group_message(body, to_jid, id);

            result.success("SUCCESS");

        } else if (call.method.equals("read_message")) {
            if (!call.hasArgument("to_jid") || !call.hasArgument("id")) {
                result.error("MISSING", "Missing argument to_jid / body / id chat.", null);
            }
            String to_jid = call.argument("to_jid");
            String id = call.argument("id");
            read_message(to_jid, id);

            result.success("SUCCESS");

        } else if (call.method.equals("current_state")) {
            String state = "UNKNOWN";
            switch (FlutterXmppConnectionService.getState()) {
                case CONNECTED:
                    state = "CONNECTED";
                    break;
                case AUTHENTICATED:
                    state = "AUTHENTICATED";
                    break;
                case CONNECTING:
                    state = "CONNECTING";
                    break;
                case DISCONNECTING:
                    state = "DISCONNECTING";
                    break;
                case DISCONNECTED:
                    state = "DISCONNECTED";
                    break;
            }

            if (DEBUG) {
                Log.d(TAG, state);
            }
            result.success(state);

        } else {
            result.notImplemented();
        }
    }

    // login
    private void login() {
        if (FlutterXmppConnectionService.getState().equals(FlutterXmppConnection.ConnectionState.DISCONNECTED)) {
            Intent i = new Intent(activity, FlutterXmppConnectionService.class);
            i.putExtra("jid_user", jid_user);
            i.putExtra("password", password);
            i.putExtra("host", host);
            i.putExtra("port", port);
            activity.startService(i);
        }
    }

    private void logout() {
        if (FlutterXmppConnectionService.getState().equals(FlutterXmppConnection.ConnectionState.CONNECTED)) {
            Intent i1 = new Intent(activity, FlutterXmppConnectionService.class);
            activity.stopService(i1);
        }
    }

    // send message to JID
    private void send_group_message(String msg, String jid_user, String id) {
        Log.d(TAG, "Current Status : " + FlutterXmppConnectionService.getState().toString());
        if (FlutterXmppConnectionService.getState().equals(FlutterXmppConnection.ConnectionState.CONNECTED)) {
            if (DEBUG) {
                Log.d(TAG, "ngirim pesan ke : " + jid_user);
            }
            Intent intent = new Intent(FlutterXmppConnectionService.GROUP_SEND_MESSAGE);
            intent.putExtra(FlutterXmppConnectionService.GROUP_MESSAGE_BODY, msg);
            intent.putExtra(FlutterXmppConnectionService.GROUP_TO, jid_user);
            intent.putExtra(FlutterXmppConnectionService.GROUP_MESSAGE_PARAMS, id);

            activity.sendBroadcast(intent);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Tidak terhubung ke server");
            }
        }
    }

    // send message to JID
    private void send_message(String msg, String jid_user, String id) {
        Log.d(TAG, "Current Status : " + FlutterXmppConnectionService.getState().toString());
        if (FlutterXmppConnectionService.getState().equals(FlutterXmppConnection.ConnectionState.CONNECTED)) {
            if (DEBUG) {
                Log.d(TAG, "ngirim pesan ke : " + jid_user);
            }
            Intent intent = new Intent(FlutterXmppConnectionService.SEND_MESSAGE);
            intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY, msg);
            intent.putExtra(FlutterXmppConnectionService.BUNDLE_TO, jid_user);
            intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS, id);

            activity.sendBroadcast(intent);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Tidak terhubung ke server");
            }
        }
    }

    // send message to JID
    private void read_message( String jid_user, String id) {
        Log.d(TAG, "Current Status : " + FlutterXmppConnectionService.getState().toString());
        if (FlutterXmppConnectionService.getState().equals(FlutterXmppConnection.ConnectionState.CONNECTED)) {
            if (DEBUG) {
                Log.d(TAG, "read pesan dari : " + jid_user);
            }
            Intent intent = new Intent(FlutterXmppConnectionService.READ_MESSAGE);
            intent.putExtra(FlutterXmppConnectionService.BUNDLE_TO, jid_user);
            intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS, id);

            activity.sendBroadcast(intent);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Tidak terhubung ke server");
            }
        }
    }


    private void set_presence() {

    }

    private void get_presence() {

    }


}
