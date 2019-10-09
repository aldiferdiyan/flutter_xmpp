package com.aldiferdiyan.xmpp.flutter_xmpp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.chat2.OutgoingChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FlutterXmppConnection implements ConnectionListener {

    private static final String TAG ="flutter_xmpp";

    private Context mApplicationContext;
    private String mUsername;
    private String mPassword;
    private String mServiceName;
    private String mResource;
    private String mHost;
    private Integer mPort;
    private XMPPTCPConnection mConnection;
    private BroadcastReceiver uiThreadMessageReceiver;//Receives messages from the ui thread.


    public static enum ConnectionState{
        CONNECTED ,AUTHENTICATED, CONNECTING ,DISCONNECTING ,DISCONNECTED;
    }

    public static enum LoggedInState{
        LOGGED_IN , LOGGED_OUT;
    }

    public FlutterXmppConnection(Context context,String jid_user,String password,String host,Integer port){
        if(FlutterXmppPlugin.DEBUG) {
            Log.d(TAG, "Connection Constructor called.");
        }
        mApplicationContext = context.getApplicationContext();
        String jid = jid_user;
        mPassword = password;
        mPort = port;
        mHost = host;
        if(jid != null) {
            String[] jid_list = jid.split("@");
            mUsername = jid_list[0];
            if(jid_list[1].contains("/")) {
                String[] domain_resource = jid_list[1].split("/");
                mServiceName = domain_resource[0];
                mResource = domain_resource[1];
            }else{
                mServiceName = jid_list[1];
                mResource  = "Android";
            }
        }else{
            mUsername ="";
            mServiceName="";
            mResource = "";
        }
    }


    public static boolean validIP (String ip) {
        try {
            if ( ip == null || ip.isEmpty() ) {
                return false;
            }

            String[] parts = ip.split( "\\." );
            if ( parts.length != 4 ) {
                return false;
            }

            for ( String s : parts ) {
                int i = Integer.parseInt( s );
                if ( (i < 0) || (i > 255) ) {
                    return false;
                }
            }
            if ( ip.endsWith(".") ) {
                return false;
            }

            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }


    public void connect() throws IOException, XMPPException, SmackException
    {
        XMPPTCPConnectionConfiguration.Builder conf = XMPPTCPConnectionConfiguration.builder();

        conf.setXmppDomain(mServiceName);
        if(validIP(mHost)) {
            InetAddress addr = InetAddress.getByName(mHost);
            conf.setHostAddress(addr);
        }else{
            conf.setHost(mHost);
        }
        if(mPort != 0) {
            conf.setPort(mPort);
        }
//        conf.setPort(0);

        conf.setUsernameAndPassword(mUsername, mPassword);
        conf.setResource(mResource);
        conf.setKeystoreType(null);
        conf.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        conf.setCompressionEnabled(true);

        if(FlutterXmppPlugin.DEBUG) {
            Log.d(TAG, "Username : " + mUsername);
            Log.d(TAG, "Password : " + mPassword);
            Log.d(TAG, "Server : " + mServiceName);
            Log.d(TAG, "Port : " + mPort.toString());

        }


        //Set up the ui thread broadcast message receiver.

        mConnection = new XMPPTCPConnection(conf.build());
        mConnection.addConnectionListener(this);
        try {
            if(FlutterXmppPlugin.DEBUG) {
                Log.d(TAG, "Calling connect() ");
            }
            mConnection.connect();
            mConnection.login(mUsername,mPassword);
            if(FlutterXmppPlugin.DEBUG) {
                Log.d(TAG, " login() Called ");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        setupUiThreadBroadCastMessageReceiver();

         ChatManager.getInstanceFor(mConnection).addIncomingListener(new IncomingChatMessageListener() {
            @Override
            public void newIncomingMessage(EntityBareJid messageFrom, Message message, Chat chat) {
                if(FlutterXmppPlugin.DEBUG) {

                    Log.d(TAG, "INCOMING :" + message.toString());

                    ///ADDED
                    Log.d(TAG, "message.getBody() :" + message.getBody());
                    Log.d(TAG, "message.getFrom() :" + message.getFrom());
                }
                DelayInformation inf = null;
                inf = (DelayInformation)message.getExtension(DelayInformation.ELEMENT,DelayInformation.NAMESPACE);
                if (inf != null){
                    Date date = inf.getStamp();
                    Log.d(TAG,"date: "+date);
                }

                String from = message.getFrom().toString();
                String contactJid="";
                if (from.contains("/")){
                    contactJid = from.split("/")[0];
                    if(FlutterXmppPlugin.DEBUG) {
                        Log.d(TAG, "The real jid is :" + contactJid);
                        Log.d(TAG, "The message is from :" + from);
                    }
                }else {
                    contactJid = from;
                }

                String id = message.getBody("id");

                //Bundle up the intent and send the broadcast.
                Intent intent = new Intent(FlutterXmppConnectionService.RECEIVE_MESSAGE);
                intent.setPackage(mApplicationContext.getPackageName());
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_FROM_JID,contactJid);
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY,message.getBody());
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS,id);

                mApplicationContext.sendBroadcast(intent);
                if(FlutterXmppPlugin.DEBUG) {
                    Log.d(TAG, "Received message from :" + contactJid + " broadcast sent.");
                }
                ///ADDED

            }
        });

        ChatManager.getInstanceFor(mConnection).addOutgoingListener(new OutgoingChatMessageListener() {
            @Override
            public void newOutgoingMessage(EntityBareJid to, Message message, Chat chat) {
                if(FlutterXmppPlugin.DEBUG) {
                    Log.d(TAG, "OUTGOING :" + message.toString());
                    ///ADDED
                    Log.d(TAG, "message.getBody() :" + message.getBody());
                    Log.d(TAG, "message.getTo() :" + message.getTo());

                }

                DelayInformation inf = null;
                inf = (DelayInformation)message.getExtension(DelayInformation.ELEMENT,DelayInformation.NAMESPACE);
                if (inf != null){
                    Date date = inf.getStamp();
                    Log.d(TAG,"date: "+date);
                }

                String from = message.getTo().toString();
                String contactJid="";
                if (from.contains("/")){
                    contactJid = from.split("/")[0];
                    if(FlutterXmppPlugin.DEBUG) {
                        Log.d(TAG, "The real jid is :" + contactJid);
                        Log.d(TAG, "The message is from :" + from);
                    }
                }else {
                    contactJid = from;
                }


                String id = message.getBody("id");


                //Bundle up the intent and send the broadcast.
                Intent intent = new Intent(FlutterXmppConnectionService.OUTGOING_MESSAGE);
                intent.setPackage(mApplicationContext.getPackageName());
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_TO_JID,contactJid);
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY,message.getBody());
                intent.putExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS,id);
                mApplicationContext.sendBroadcast(intent);
                if(FlutterXmppPlugin.DEBUG) {
                    Log.d(TAG, "Outgoing message from :" + contactJid + " broadcast sent.");
                }
            }
        });


        ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(mConnection);
        reconnectionManager.setEnabledPerDefault(true);
        reconnectionManager.enableAutomaticReconnection();

    }


    private void setupUiThreadBroadCastMessageReceiver()
    {
        Log.d(TAG,"setupUiThreadBroadCastMessageReceiver");

        uiThreadMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                //Check if the Intents purpose is to send the message.
                String action = intent.getAction();
                Log.d(TAG,"broadcast " + action);

                if( action.equals(FlutterXmppConnectionService.SEND_MESSAGE)) {
                    //Send the message.
                    sendMessage(intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_BODY),
                            intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_TO),
                            intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS));

                }else  if( action.equals(FlutterXmppConnectionService.READ_MESSAGE)) {
                    //Send the message.
                    sendRead(intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_TO),
                            intent.getStringExtra(FlutterXmppConnectionService.BUNDLE_MESSAGE_PARAMS));

                }else  if( action.equals(FlutterXmppConnectionService.GROUP_SEND_MESSAGE)) {
                    //Send group message.
                    sendGroupMessage(intent.getStringExtra(FlutterXmppConnectionService.GROUP_MESSAGE_BODY),
                            intent.getStringExtra(FlutterXmppConnectionService.GROUP_TO),
                            intent.getStringExtra(FlutterXmppConnectionService.GROUP_MESSAGE_PARAMS));
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(FlutterXmppConnectionService.SEND_MESSAGE);
        filter.addAction(FlutterXmppConnectionService.READ_MESSAGE);
        mApplicationContext.registerReceiver(uiThreadMessageReceiver,filter);

    }

    private void sendRead (String toJid, String id)
    {
        Log.d(TAG,"Sending message to :"+ toJid);
        EntityBareJid jid = null;
        ChatManager chatManager = ChatManager.getInstanceFor(mConnection);
        try {
            jid = JidCreate.entityBareFrom(toJid);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        Chat chat = chatManager.chatWith(jid);
        try {
            Message message = new Message(jid,Message.Type.normal);
            message.setBody("icm_send_message");
            message.addBody("id", id);
            chat.send(message);

        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    private void sendGroupMessage (String body , String toRoom, String id)
    {
//        Log.d(TAG,"Sending group message to :"+ toRoom);
//        EntityBareJid jid = null;
//        ChatManager chatManager = ChatManager.getInstanceFor(mConnection);
//
//         try {
//             MultiUserChat muc = chatManager.("test2@conference.cca");
//
//             Message message = new Message(toRoom, Message.Type.groupchat());
//            message.setBody(body);
//            message.addBody("id", id);
//            message.setType(Message.Type.groupchat);
//            message.setTo(toRoom);
//            MultiUserChat.se(message);
//
//        } catch (SmackException.NotConnectedException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }



    private void sendMessage (String body , String toJid, String id)
    {
        Log.d(TAG,"Sending message to :"+ toJid);
        EntityBareJid jid = null;
        ChatManager chatManager = ChatManager.getInstanceFor(mConnection);
        try {
            jid = JidCreate.entityBareFrom(toJid);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        Chat chat = chatManager.chatWith(jid);
        try {
            Message message = new Message(jid, Message.Type.chat);
            message.setBody(body);
            message.addBody("id", id);

//            try {
//                final JSONArray parameters = new JSONArray(custom_parameter);
//                for(int cs = 0;cs < parameters.length();cs++) {
//                    final JSONObject param = parameters.getJSONObject(cs);
//                    String key = param.getString("key");
//                    String value = param.getString("value");
//                    message.addBody(key, value);
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }

            chat.send(message);

        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void disconnect() {
        if(FlutterXmppPlugin.DEBUG) {
            Log.d(TAG, "Disconnecting from serser " + mServiceName);
        }
        if (mConnection != null){
            mConnection.disconnect();
            mConnection = null;
        }

        // Unregister the message broadcast receiver.
        if( uiThreadMessageReceiver != null) {
            mApplicationContext.unregisterReceiver(uiThreadMessageReceiver);
            uiThreadMessageReceiver = null;
        }
    }


    @Override
    public void connected(XMPPConnection connection) {
        FlutterXmppConnectionService.sConnectionState=ConnectionState.CONNECTED;
        Log.d(TAG,"Connected Successfully");

    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        FlutterXmppConnectionService.sConnectionState=ConnectionState.CONNECTED;
        Log.d(TAG,"Authenticated Successfully");
//        showContactListActivityWhenAuthenticated();
    }


    @Override
    public void connectionClosed() {
        FlutterXmppConnectionService.sConnectionState=ConnectionState.DISCONNECTED;
        Log.d(TAG,"Connectionclosed()");

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        FlutterXmppConnectionService.sConnectionState=ConnectionState.DISCONNECTED;
        Log.d(TAG,"ConnectionClosedOnError, error "+ e.toString());

    }

    @Override
    public void reconnectingIn(int seconds) {
        FlutterXmppConnectionService.sConnectionState = ConnectionState.CONNECTING;
        Log.d(TAG,"ReconnectingIn() ");

    }

    @Override
    public void reconnectionSuccessful() {
        FlutterXmppConnectionService.sConnectionState = ConnectionState.CONNECTED;
        Log.d(TAG,"ReconnectionSuccessful()");

    }

    @Override
    public void reconnectionFailed(Exception e) {
        FlutterXmppConnectionService.sConnectionState = ConnectionState.DISCONNECTED;
        Log.d(TAG,"ReconnectionFailed()");

    }



}


