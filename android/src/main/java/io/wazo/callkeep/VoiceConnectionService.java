/*
 * Copyright (c) 2016-2018 The CallKeep Authors (see the AUTHORS file)
 * SPDX-License-Identifier: ISC, MIT
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package io.wazo.callkeep;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.Voice;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.wazo.callkeep.RNCallKeepModule.ACTION_ANSWER_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_AUDIO_SESSION;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_DTMF_TONE;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_END_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_HOLD_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_MUTE_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_ONGOING_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_UNHOLD_CALL;
import static io.wazo.callkeep.RNCallKeepModule.ACTION_UNMUTE_CALL;
import static io.wazo.callkeep.RNCallKeepModule.EXTRA_CALLER_NAME;
import static io.wazo.callkeep.RNCallKeepModule.EXTRA_CALL_NUMBER;
import static io.wazo.callkeep.RNCallKeepModule.EXTRA_CALL_UUID;
import static io.wazo.callkeep.RNCallKeepModule.handle;

// @see https://github.com/kbagchiGWC/voice-quickstart-android/blob/9a2aff7fbe0d0a5ae9457b48e9ad408740dfb968/exampleConnectionService/src/main/java/com/twilio/voice/examples/connectionservice/VoiceConnectionService.java
@TargetApi(Build.VERSION_CODES.M)
public class VoiceConnectionService extends ConnectionService {
    private static VoiceConnection connection;
    private static Boolean isAvailable = false;
    private static String TAG = "VoiceConnectionService";
    private static Map<String, VoiceConnection> currentConnections = new HashMap<>();

    public static Connection getConnection() {
        return connection;
    }

    public VoiceConnectionService() {
        super();
        Log.e(TAG, "xxx");
    }

    public static void setAvailable(Boolean value) {
        isAvailable = value;
    }


    public static void deinitConnection() {
        connection = null;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Bundle extra = request.getExtras();
        Uri number = request.getAddress();
        String name = extra.getString(EXTRA_CALLER_NAME);
        Connection incomingCallConnection = createConnection(request);
        incomingCallConnection.setRinging();

        if (name != null && number != null) {
            incomingCallConnection.setAddress(number, TelecomManager.PRESENTATION_ALLOWED);
            incomingCallConnection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
        }

        return incomingCallConnection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        if (!this.canMakeOutgoingCall()) {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.LOCAL));
        }
        // TODO: Hold all other calls

        Bundle extras = request.getExtras();
        Connection outgoingCallConnection = null;
        String number = request.getAddress().getSchemeSpecificPart();
        String extrasNumber = extras.getString(EXTRA_CALL_NUMBER);

        if (extrasNumber != null && extrasNumber.equals(number)) {
            outgoingCallConnection = createConnection(request);
        } else {
            String uuid = UUID.randomUUID().toString();
            extras.putString(EXTRA_CALL_UUID, uuid);
            extras.putString(EXTRA_CALLER_NAME, null);
            extras.putString(EXTRA_CALL_NUMBER, number);
            outgoingCallConnection = createConnection(request);
        }
        outgoingCallConnection.setDialing();
        outgoingCallConnection.setAudioModeIsVoip(true);

        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        sendCallRequestToActivity(ACTION_ONGOING_CALL, extrasMap);
        sendCallRequestToActivity(ACTION_AUDIO_SESSION, null);

        return outgoingCallConnection;
    }

    private Boolean canMakeOutgoingCall() {
        return isAvailable;
    }

    private Connection createConnection(ConnectionRequest request) {

        Bundle extras = request.getExtras();
        HashMap<String, String> extrasMap = this.bundleToMap(extras);
        connection = new VoiceConnection(this, extrasMap);
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE | Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD);
        connection.setInitializing();
        connection.setExtras(extras);
        currentConnections.put(extras.getString(EXTRA_CALL_UUID), connection);

        // Get other connections for conferencing
        Map<String, VoiceConnection> otherConnections = new HashMap<>();
        for (Map.Entry<String, VoiceConnection> entry : currentConnections.entrySet()) {
            if(!(extras.getString(EXTRA_CALL_UUID).equals(entry.getKey()))) {
                otherConnections.put(entry.getKey(), entry.getValue());
            }
        }
        List<Connection> conferenceConnections = new ArrayList<Connection>(otherConnections.values());
        connection.setConferenceableConnections(conferenceConnections);

        connection.setInitialized();
        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        super.onConference(connection1, connection2);
        VoiceConnection voiceConnection1 = (VoiceConnection) connection1;
        VoiceConnection voiceConnection2 = (VoiceConnection) connection2;

        PhoneAccountHandle phoneAccountHandle = RNCallKeepModule.handle;

        VoiceConference voiceConference = new VoiceConference(handle);
        voiceConference.addConnection(voiceConnection1);
        voiceConference.addConnection(voiceConnection2);

        connection1.onUnhold();
        connection2.onUnhold();

        this.addConference(voiceConference);
    }

    /*
     * Send call request to the RNCallKeepModule
     */
    private void sendCallRequestToActivity(final String action, @Nullable final HashMap attributeMap) {
        final VoiceConnectionService instance = this;
        final Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(action);
                if (attributeMap != null) {
                    Bundle extras = new Bundle();
                    extras.putSerializable("attributeMap", attributeMap);
                    intent.putExtras(extras);
                }

                LocalBroadcastManager.getInstance(instance).sendBroadcast(intent);
            }
        });
    }

    private HashMap<String, String> bundleToMap(Bundle extras) {
        HashMap<String, String> extrasMap = new HashMap<>();
        Set<String> keySet = extras.keySet();
        Iterator<String> iterator = keySet.iterator();

        while(iterator.hasNext()) {
            String key = iterator.next();
            extrasMap.put(key, extras.getString(key));
        }
        return extrasMap;
    }
}
