package com.hiennv.flutter_callkit_incoming

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import okhttp3.OkHttpClient
import android.view.ViewGroup.MarginLayoutParams
import java.util.concurrent.TimeUnit
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class CallkitIncomingBroadcastReceiver : BroadcastReceiver() {
         // HTTP Client
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()


    companion object {
        private const val TAG = "CallkitIncomingReceiver"
        var silenceEvents = false

        fun getIntent(context: Context, action: String, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                this.action = "${context.packageName}.${action}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentIncoming(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_INCOMING}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentStart(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_START}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentAccept(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_FOLLOW_UP}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentDecline(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_DECLINE}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }
        

        fun getIntentEnded(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_ENDED}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentTimeout(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_TIMEOUT}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentCallback(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_CALLBACK}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentHeldByCell(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_HELD}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }

        fun getIntentUnHeldByCell(context: Context, data: Bundle?) =
            Intent(context, CallkitIncomingBroadcastReceiver::class.java).apply {
                action = "${context.packageName}.${CallkitConstants.ACTION_CALL_UNHELD}"
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }
    }


    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        
        val callkitNotificationManager = CallkitNotificationManager(context)
        val action = intent.action ?: return
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA) ?: return
        when (action) {
            "${context.packageName}.${CallkitConstants.ACTION_CALL_INCOMING}" -> {
                try {
                    callkitNotificationManager.showIncomingNotification(data)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_INCOMING, data)
                    addCall(context, Data.fromBundle(data))
                    if (callkitNotificationManager.incomingChannelEnabled()) {
                        val soundPlayerServiceIntent =
                                Intent(context, CallkitSoundPlayerService::class.java)
                        soundPlayerServiceIntent.putExtras(data)
                        context.startService(soundPlayerServiceIntent)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_START}" -> {
                try {
                    sendEventFlutter(CallkitConstants.ACTION_CALL_START, data)
                    addCall(context, Data.fromBundle(data), true)
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_FOLLOW_UP}" -> {
                Log.d("CallActions", "#>> Follow up call clicked")
                callFollowUpAPI(data)
                try {
                    stopSoundService(context)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_FOLLOW_UP, data)
                    context.stopService(Intent(context, CallkitSoundPlayerService::class.java))
                    callkitNotificationManager.clearIncomingNotification(data, true)
                    addCall(context, Data.fromBundle(data), true)
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_DECLINE}" -> {
                Log.d("CallActions", "#>> Decline call clicked")
                callDeclineAPI(data)
                try {
                    stopSoundService(context)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_DECLINE, data)
                    context.stopService(Intent(context, CallkitSoundPlayerService::class.java))
                    callkitNotificationManager.clearIncomingNotification(data, false)
                    removeCall(context, Data.fromBundle(data))
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_ENDED}" -> {
                try {
                    stopSoundService(context)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_ENDED, data)
                    context.stopService(Intent(context, CallkitSoundPlayerService::class.java))
                    callkitNotificationManager.clearIncomingNotification(data, false)
                    removeCall(context, Data.fromBundle(data))
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_TIMEOUT}" -> {
                try {
                    callDeclineAPI(data)
                    stopSoundService(context)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_TIMEOUT, data)
                    context.stopService(Intent(context, CallkitSoundPlayerService::class.java))
                    if (data.getBoolean(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SHOW, true)) {
                        callkitNotificationManager.showMissCallNotification(data)
                    }
                    removeCall(context, Data.fromBundle(data))
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }

            "${context.packageName}.${CallkitConstants.ACTION_CALL_CALLBACK}" -> {
                try {
                    stopSoundService(context)
                    callkitNotificationManager.clearMissCallNotification(data)
                    sendEventFlutter(CallkitConstants.ACTION_CALL_CALLBACK, data)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        val closeNotificationPanel = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        context.sendBroadcast(closeNotificationPanel)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, null, error)
                }
            }
        }
    }

    private fun sendEventFlutter(event: String, data: Bundle) {
        if (silenceEvents) return

        val android = mapOf(
                "isCustomNotification" to data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_CUSTOM_NOTIFICATION, false),
                "isCustomSmallExNotification" to data.getBoolean(
                        CallkitConstants.EXTRA_CALLKIT_IS_CUSTOM_SMALL_EX_NOTIFICATION,
                        false
                ),
                "ringtonePath" to data.getString(CallkitConstants.EXTRA_CALLKIT_RINGTONE_PATH, ""),
                "backgroundColor" to data.getString(CallkitConstants.EXTRA_CALLKIT_BACKGROUND_COLOR, ""),
                "backgroundUrl" to data.getString(CallkitConstants.EXTRA_CALLKIT_BACKGROUND_URL, ""),
                "actionColor" to data.getString(CallkitConstants.EXTRA_CALLKIT_ACTION_COLOR, ""),
                "textColor" to data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_COLOR, ""),
                "incomingCallNotificationChannelName" to data.getString(
                        CallkitConstants.EXTRA_CALLKIT_INCOMING_CALL_NOTIFICATION_CHANNEL_NAME,
                        ""
                ),
                "missedCallNotificationChannelName" to data.getString(
                        CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_NOTIFICATION_CHANNEL_NAME,
                        ""
                ),
                "isImportant" to data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_IMPORTANT, false),
                "isBot" to data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_BOT, false),
        )
        val notification = mapOf(
                "id" to data.getInt(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_ID),
                "showNotification" to data.getBoolean(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SHOW),
                "count" to data.getInt(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_COUNT),
                "title" to data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_TITLE),
                "subtitle" to data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SUBTITLE),
                "timer" to data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_TIMER),
                "senderMessage" to data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SENDERMESSAGE),
                "callbackText" to data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_TEXT),
                "isShowCallback" to data.getBoolean(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_SHOW),
        )
        val forwardData = mapOf(
                "id" to data.getString(CallkitConstants.EXTRA_CALLKIT_ID, ""),
                "title" to data.getString(CallkitConstants.EXTRA_CALLKIT_TITLE, ""),
                "subtitle" to data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, ""),
                "timer" to data.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, ""),
                "senderMessage" to data.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, ""),
                "avatar" to data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, ""),
                "number" to data.getString(CallkitConstants.EXTRA_CALLKIT_HANDLE, ""),
                "type" to data.getInt(CallkitConstants.EXTRA_CALLKIT_TYPE, 0),
                "duration" to data.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 0L),
                "textFollowUp" to data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_FOLLOW_UP, ""),
                "textDecline" to data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_DECLINE, ""),
                "extra" to data.getSerializable(CallkitConstants.EXTRA_CALLKIT_EXTRA)!!,
                "missedCallNotification" to notification,
                "android" to android
        )
        FlutterCallkitIncomingPlugin.sendEvent(event, forwardData)
    }

    private fun stopSoundService(context: Context) {
    try {
        val stopIntent = Intent(context, CallkitSoundPlayerService::class.java)
        context.stopService(stopIntent)
        Log.d(TAG, "Sound service stopped")
    } catch (error: Exception) {
        Log.e(TAG, "Error stopping sound service", error)
    }
}

private fun callFollowUpAPI(data: Bundle?) {
    try {
        val fcmDataString = data?.getString(CallkitConstants.EXTRA_CALLKIT_FCM_DATA, "")
        if (fcmDataString.isNullOrEmpty()) {
            Log.e("CallkitNotificationManager", "FCM data is empty for follow up")
            return
        }
        val fcmData = JSONObject(fcmDataString)
        val messageId = fcmData.optInt("message_id", 0)
        val salesId = fcmData.optInt("sales_id", 0)
        if (messageId == 0 || salesId == 0) {
            Log.e("CallkitNotificationManager", "Invalid message_id or sales_id for follow up")
            return
        }
        val requestBody = JSONObject().apply {
            put("message_id", messageId)
            put("sales_id", salesId)
        }
        Log.e("CallkitNotificationManager", "MS ID: $messageId - S ID: $salesId")
        val mediaType = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(mediaType, requestBody.toString())
        val urlFollowUp = data.getString(CallkitConstants.EXTRA_CALLKIT_URL_FOLLOW_UP, "")
        Log.e("CallkitNotificationManager", "URL FOLLOW UP: $urlFollowUp")
        val request = Request.Builder()
            .url(urlFollowUp)
            .addHeader("accept", "application/json")
            .post(body)
            .build()
            
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CallkitNotificationManager", "Follow up API call failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.d("CallkitNotificationManager", "Follow up API call successful: ${response.body()?.string()}")
                    } else {
                        Log.e("CallkitNotificationManager", "Follow up API call failed with code: ${response.code()}")
                    }
                }
            }
        })
    } catch (e: Exception) {
        Log.e("CallkitNotificationManager", "Error calling follow up API", e)
    }
}

    private fun callDeclineAPI(data: Bundle?) {
        try {
            val fcmDataString = data?.getString(CallkitConstants.EXTRA_CALLKIT_FCM_DATA, "")
            if (fcmDataString.isNullOrEmpty()) {
                Log.e("CallkitNotificationManager","FCM data is empty for decline")
                return
            }

            val fcmData = JSONObject(fcmDataString)
            val messageId = fcmData.optInt("message_id", 0)
            val salesId = fcmData.optInt("sales_id", 0)

            if (messageId == 0 || salesId == 0) {
                Log.e("CallkitNotificationManager","Invalid message_id or sales_id for decline")
                return
            }

            val requestBody = JSONObject().apply {
                put("message_id", messageId)
                put("sales_id", salesId)
            }

           Log.e("CallkitNotificationManager","MS ID: $messageId - S ID: $salesId")

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, requestBody.toString())

            val urlDecline = data?.getString(CallkitConstants.EXTRA_CALLKIT_URL_DECLINE, "")
            Log.e("CallkitNotificationManager","URL DECLINE: $urlDecline")

            val request = Request.Builder()
                .url(urlDecline)
                .post(body)
                .addHeader("accept", "application/json")
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("CallkitNotificationManager","Decline API call failed", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            Log.d("CallkitNotificationManager","Decline API call successful: ${response.body()?.string()}")
                        } else {
                            Log.e("CallkitNotificationManager","Decline API call failed with code: ${response.code()}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("CallkitNotificationManager","Error calling decline API", e)
        }
    }
}