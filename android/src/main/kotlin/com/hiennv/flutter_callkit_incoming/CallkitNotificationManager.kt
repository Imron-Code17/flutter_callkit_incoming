package com.hiennv.flutter_callkit_incoming

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.hiennv.flutter_callkit_incoming.widgets.CircleTransform
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import okhttp3.OkHttpClient
import android.view.ViewGroup.MarginLayoutParams
import java.util.concurrent.TimeUnit
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.util.Log


class CallkitNotificationManager(private val context: Context) {

    companion object {
        const val PERMISSION_NOTIFICATION_REQUEST_CODE = 6969

        const val EXTRA_TIME_START_CALL = "EXTRA_TIME_START_CALL"

        private const val NOTIFICATION_CHANNEL_ID_INCOMING = "callkit_incoming_channel_id"
        private const val NOTIFICATION_CHANNEL_ID_MISSED = "callkit_missed_channel_id"
    }

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var notificationViews: RemoteViews? = null
    private var notificationSmallViews: RemoteViews? = null
    private var notificationId: Int = 9696
    private var dataNotificationPermission: Map<String, Any> = HashMap()


    interface CallActionListener {
        fun onAcceptCall(data: Bundle)
        fun onDeclineCall(data: Bundle)
        fun onTimeoutCall(data: Bundle)
    }
    
    private var callActionListener: CallActionListener? = null
    
    // Setter untuk callback
    fun setCallActionListener(listener: CallActionListener) {
        this.callActionListener = listener
    }

    @SuppressLint("MissingPermission")
    private var targetLoadAvatarDefault = object : Target {

        override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
            notificationBuilder.setLargeIcon(bitmap)
            getNotificationManager().notify(notificationId, notificationBuilder.build())
        }

        override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        }
    }

    @SuppressLint("MissingPermission")
    private var targetLoadAvatarCustomize = object : Target {
        override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
            notificationViews?.setImageViewBitmap(R.id.ivAvatar, bitmap)
            notificationViews?.setViewVisibility(R.id.ivAvatar, View.VISIBLE)
            notificationSmallViews?.setImageViewBitmap(R.id.ivAvatar, bitmap)
            notificationSmallViews?.setViewVisibility(R.id.ivAvatar, View.VISIBLE)
            getNotificationManager().notify(notificationId, notificationBuilder.build())
        }

        override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        }
    }


    @SuppressLint("MissingPermission")
    fun showIncomingNotification(data: Bundle) {
        data.putLong(EXTRA_TIME_START_CALL, System.currentTimeMillis())

        notificationId =
            data.getString(CallkitConstants.EXTRA_CALLKIT_ID, "callkit_incoming").hashCode()
        createNotificationChanel(
            data.getString(
                CallkitConstants.EXTRA_CALLKIT_INCOMING_CALL_NOTIFICATION_CHANNEL_NAME,
                "Incoming Call"
            ),
            data.getString(
                CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_NOTIFICATION_CHANNEL_NAME,
                "Missed Call"
            ),
        )

        notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_INCOMING)
        notificationBuilder.setAutoCancel(false)
        notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID_INCOMING)
        notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_CALL)
            notificationBuilder.priority = NotificationCompat.PRIORITY_MAX
        }
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setWhen(0)
        notificationBuilder.setTimeoutAfter(
            data.getLong(
                CallkitConstants.EXTRA_CALLKIT_DURATION,
                0L
            )
        )
        notificationBuilder.setOnlyAlertOnce(true)
        notificationBuilder.setSound(null)
        notificationBuilder.setFullScreenIntent(
            getActivityPendingIntent(notificationId, data), true
        )
        notificationBuilder.setContentIntent(getActivityPendingIntent(notificationId, data))
        notificationBuilder.setDeleteIntent(getTimeOutPendingIntent(notificationId, data))
        val typeCall = data.getInt(CallkitConstants.EXTRA_CALLKIT_TYPE, -1)
        var smallIcon = context.applicationInfo.icon
        if (typeCall > 0) {
            smallIcon = R.drawable.ic_video
        } else {
            if (smallIcon >= 0) {
                smallIcon = R.drawable.ic_follow_up
            }
        }
        notificationBuilder.setSmallIcon(smallIcon)
        val actionColor = data.getString(CallkitConstants.EXTRA_CALLKIT_ACTION_COLOR, "#4CAF50")
        try {
            notificationBuilder.color = Color.parseColor(actionColor)
        } catch (_: Exception) {
        }
        notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID_INCOMING)
        notificationBuilder.priority = NotificationCompat.PRIORITY_MAX
        val isCustomNotification =
            data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_CUSTOM_NOTIFICATION, false)
        val isCustomSmallExNotification =
            data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_CUSTOM_SMALL_EX_NOTIFICATION, false)
        if (isCustomNotification) {
    notificationViews =
        RemoteViews(context.packageName, R.layout.layout_custom_notification)
    initNotificationViews(notificationViews!!, data)

    if ((Build.MANUFACTURER.equals(
            "Samsung",
            ignoreCase = true
        ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) || isCustomSmallExNotification
    ) {
        notificationSmallViews =
            RemoteViews(context.packageName, R.layout.layout_custom_small_ex_notification)
        initNotificationViews(notificationSmallViews!!, data)
    } else {
        notificationSmallViews =
            RemoteViews(context.packageName, R.layout.layout_custom_small_notification)
        initNotificationViews(notificationSmallViews!!, data)
    }

    notificationBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
    notificationBuilder.setCustomContentView(notificationSmallViews)
    notificationBuilder.setCustomBigContentView(notificationViews)
    notificationBuilder.setCustomHeadsUpContentView(notificationSmallViews)
} else {
    notificationBuilder.setContentText(
        data.getString(
            CallkitConstants.EXTRA_CALLKIT_HANDLE,
            ""
        )
    )
    
    // === BAGIAN YANG DIMODIFIKASI UNTUK AVATAR PLACEHOLDER ===
    val avatarUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, "")
    val subtitle = data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "Unknown")
    
    if (avatarUrl != null && avatarUrl.isNotEmpty()) {
        val headers =
            data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as HashMap<String, Any?>
        getPicassoInstance(context, headers).load(avatarUrl)
            .into(targetLoadAvatarDefault)
    } else {
        // Buat avatar placeholder dengan initial
        val placeholderBitmap = createAvatarPlaceholder(subtitle)
        notificationBuilder.setLargeIcon(placeholderBitmap)
    }
    // === AKHIR MODIFIKASI ===
    
    val title = data.getString(CallkitConstants.EXTRA_CALLKIT_TITLE, "")
    val timer = data.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, "")
    val senderMessage = data.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, "")
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val person = Person.Builder()
            .setName(subtitle)
            .setImportant(
                data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_IMPORTANT, false)
            )
            .setBot(data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_BOT, false))
            .build()
        notificationBuilder.setStyle(
            NotificationCompat.CallStyle.forIncomingCall(
                person,
                getDeclinePendingIntent(notificationId, data),
                getAcceptPendingIntent(notificationId, data),
            )
                .setIsVideo(typeCall > 0)
        )
    } else {
        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentTitle(subtitle)
        notificationBuilder.setContentTitle(timer)
        notificationBuilder.setContentTitle(senderMessage)
        val textDecline = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_DECLINE, "")
        val declineAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_decline,
            if (TextUtils.isEmpty(textDecline)) context.getString(R.string.text_decline) else textDecline,
            getDeclinePendingIntent(notificationId, data)
        ).build()
        notificationBuilder.addAction(declineAction)
        val textFollowUp = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_FOLLOW_UP, "")
        val followUpAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_follow_up,
            if (TextUtils.isEmpty(textFollowUp)) context.getString(R.string.text_follow_up) else textFollowUp,
            getAcceptPendingIntent(notificationId, data)
        ).build()
        notificationBuilder.addAction(followUpAction)
        val fcmData = data.getString(CallkitConstants.EXTRA_CALLKIT_FCM_DATA, "")
    }
}
        val notification = notificationBuilder.build()
        notification.flags = Notification.FLAG_INSISTENT
        getNotificationManager().notify(notificationId, notification)
    }

    private fun initNotificationViews(remoteViews: RemoteViews, data: Bundle) {
        remoteViews.setTextViewText(
            R.id.tvTitle,
            data.getString(CallkitConstants.EXTRA_CALLKIT_TITLE, "")
        )
        remoteViews.setTextViewText(
            R.id.tvSubtitle,
            data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "")
        )
        remoteViews.setTextViewText(
            R.id.tvTimer,
            data.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, "")
        )
        remoteViews.setTextViewText(
            R.id.tvSenderMessage,
            data.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, "")
        )
        remoteViews.setOnClickPendingIntent(
            R.id.llDecline,
            getDeclinePendingIntent(notificationId, data)
        )
        val textDecline = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_DECLINE, "")
        remoteViews.setTextViewText(
            R.id.tvDecline,
            if (TextUtils.isEmpty(textDecline)) context.getString(R.string.text_decline) else textDecline
        )
        remoteViews.setOnClickPendingIntent(
            R.id.llFollowUp,
            getAcceptPendingIntent(notificationId, data)
        )
        val textFollowUp = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_FOLLOW_UP, "")
        remoteViews.setTextViewText(
            R.id.tvFollowUp,
            if (TextUtils.isEmpty(textFollowUp)) context.getString(R.string.text_follow_up) else textFollowUp
        )
        // === BAGIAN YANG DIPERBAIKI UNTUK AVATAR PLACEHOLDER ===
    val avatarUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, "")
    val subtitle = data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "Unknown")
    
    if (avatarUrl != null && avatarUrl.isNotEmpty()) {
        val headers =
            data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as HashMap<String, Any?>
        getPicassoInstance(context, headers).load(avatarUrl)
            .transform(CircleTransform())
            .into(targetLoadAvatarCustomize)
    } else {
        // Set placeholder avatar dengan initial untuk custom notification
        val placeholderBitmap = createAvatarPlaceholder(subtitle)
        remoteViews.setImageViewBitmap(R.id.ivAvatar, placeholderBitmap)
        remoteViews.setViewVisibility(R.id.ivAvatar, View.VISIBLE)
    }
    // === AKHIR PERBAIKAN ===
    }

    @SuppressLint("MissingPermission")
    fun showMissCallNotification(data: Bundle) {
        val missedNotificationId = data.getInt(
            CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_ID,
            data.getString(CallkitConstants.EXTRA_CALLKIT_ID, "callkit_incoming").hashCode() + 1
        )
        createNotificationChanel(
            data.getString(
                CallkitConstants.EXTRA_CALLKIT_INCOMING_CALL_NOTIFICATION_CHANNEL_NAME,
                "Incoming Call"
            ),
            data.getString(
                CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_NOTIFICATION_CHANNEL_NAME,
                "Missed Call"
            ),
        )
        val missedCallSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val typeCall = data.getInt(CallkitConstants.EXTRA_CALLKIT_TYPE, -1)
        var smallIcon = context.applicationInfo.icon
        if (typeCall > 0) {
            smallIcon = R.drawable.ic_video_missed
        } else {
            if (smallIcon >= 0) {
                smallIcon = R.drawable.ic_call_missed
            }
        }
        notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_MISSED)
        notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID_MISSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                notificationBuilder.setCategory(Notification.CATEGORY_MISSED_CALL)
            }
        }
        val textMissedCall = data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SUBTITLE, "")
        val texttimer = data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_TIMER, "")
        val textSenderMessage = data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_SENDERMESSAGE, "")
        
        notificationBuilder.setSubText(if (TextUtils.isEmpty(textMissedCall)) context.getString(R.string.text_missed_call) else textMissedCall)
        notificationBuilder.setSmallIcon(smallIcon)
        val isCustomNotification =
            data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_CUSTOM_NOTIFICATION, false)
        val count = data.getInt(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_COUNT, 1)
        if (count > 1) {
            notificationBuilder.setNumber(count)
        }
        if (isCustomNotification) {
            notificationViews =
                RemoteViews(context.packageName, R.layout.layout_custom_miss_notification)
            notificationViews?.setTextViewText(
                R.id.tvTitle,
                data.getString(CallkitConstants.EXTRA_CALLKIT_TITLE, "")
            )
            notificationViews?.setTextViewText(
                R.id.tvSubtitle,
                data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "")
            )
            notificationViews?.setTextViewText(
                R.id.tvTimer,
                data.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, "")
            )
            notificationViews?.setTextViewText(
                R.id.tvSenderMessage,
                data.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, "")
            )
            notificationViews?.setOnClickPendingIntent(
                R.id.llCallback,
                getCallbackPendingIntent(notificationId, data)
            )
            val isShowCallback = data.getBoolean(
                CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_SHOW,
                true
            )
            notificationViews?.setViewVisibility(
                R.id.llCallback,
                if (isShowCallback) View.VISIBLE else View.GONE
            )
            val textCallback =
                data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_TEXT, "")
            notificationViews?.setTextViewText(
                R.id.tvCallback,
                if (TextUtils.isEmpty(textCallback)) context.getString(R.string.text_call_back) else textCallback
            )

            val avatarUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, "")
            if (avatarUrl != null && avatarUrl.isNotEmpty()) {
                val headers =
                    data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as HashMap<String, Any?>

                getPicassoInstance(context, headers).load(avatarUrl)
                    .transform(CircleTransform()).into(targetLoadAvatarCustomize)
            }
            notificationBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            notificationBuilder.setCustomContentView(notificationViews)
            notificationBuilder.setCustomBigContentView(notificationViews)
        } else {
            notificationBuilder.setContentTitle(
                data.getString(
                    CallkitConstants.EXTRA_CALLKIT_SUBTITLE,
                    ""
                )
            )
            notificationBuilder.setContentText(
                data.getString(
                    CallkitConstants.EXTRA_CALLKIT_HANDLE,
                    ""
                )
            )
            val avatarUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, "")
            if (avatarUrl != null && avatarUrl.isNotEmpty()) {
                val headers =
                    data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as HashMap<String, Any?>

                getPicassoInstance(context, headers).load(avatarUrl)
                    .into(targetLoadAvatarDefault)
            }
            val isShowCallback = data.getBoolean(
                CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_SHOW,
                true
            )
            if (isShowCallback) {
                val textCallback =
                    data.getString(CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_CALLBACK_TEXT, "")
                val callbackAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
                    R.drawable.ic_follow_up,
                    if (TextUtils.isEmpty(textCallback)) context.getString(R.string.text_call_back) else textCallback,
                    getCallbackPendingIntent(notificationId, data)
                ).build()
                notificationBuilder.addAction(callbackAction)
            }
        }
        notificationBuilder.priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            Notification.PRIORITY_HIGH
        }
        notificationBuilder.setSound(missedCallSound)
        notificationBuilder.setContentIntent(getAppPendingIntent(notificationId, data))
        val actionColor = data.getString(CallkitConstants.EXTRA_CALLKIT_ACTION_COLOR, "#4CAF50")
        try {
            notificationBuilder.color = Color.parseColor(actionColor)
        } catch (_: Exception) {
        }
        val notification = notificationBuilder.build()
        getNotificationManager().notify(missedNotificationId, notification)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                getNotificationManager().notify(missedNotificationId, notification)
            } catch (_: Exception) {
            }
        }, 1000)
    }

private fun createAvatarPlaceholder(name: String, size: Int = 200): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Background circle dengan warna gray cerah
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#E0E0E0") // Gray cerah
    }
    
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    
    // Text untuk initial
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#757575") // Dark gray untuk text
        textSize = size * 0.4f // 40% dari ukuran avatar
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    // Ambil initial dari nama
    val initial = getInitials(name)
    
    // Hitung posisi text di tengah
    val textBounds = android.graphics.Rect()
    textPaint.getTextBounds(initial, 0, initial.length, textBounds)
    val textY = radius + textBounds.height() / 2f
    
    canvas.drawText(initial, radius, textY, textPaint)
    
    return bitmap
}

// 2. Tambahkan fungsi untuk mengambil initial dari nama
private fun getInitials(name: String): String {
    val words = name.trim().split("\\s+".toRegex())
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> "${words[0].take(1)}${words[1].take(1)}".uppercase()
    }
}

    fun clearIncomingNotification(data: Bundle, isAccepted: Boolean) {
        context.sendBroadcast(CallkitIncomingActivity.getIntentEnded(context, isAccepted))
        notificationId =
            data.getString(CallkitConstants.EXTRA_CALLKIT_ID, "callkit_incoming").hashCode()
        getNotificationManager().cancel(notificationId)
    }

    fun clearMissCallNotification(data: Bundle) {
        val missedNotificationId = data.getInt(
            CallkitConstants.EXTRA_CALLKIT_MISSED_CALL_ID,
            data.getString(CallkitConstants.EXTRA_CALLKIT_ID, "callkit_incoming").hashCode() + 1
        )
        getNotificationManager().cancel(missedNotificationId)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                getNotificationManager().cancel(missedNotificationId)
            } catch (_: Exception) {
            }
        }, 1000)
    }

    fun incomingChannelEnabled(): Boolean = getNotificationManager().run {
        val channel = getNotificationChannel(NOTIFICATION_CHANNEL_ID_INCOMING)

        return areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        channel != null &&
                        channel.importance > NotificationManagerCompat.IMPORTANCE_NONE) ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    }

    private fun createNotificationChanel(
        incomingCallChannelName: String,
        missedCallChannelName: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getNotificationManager().apply {
                var channelCall = getNotificationChannel(NOTIFICATION_CHANNEL_ID_INCOMING)
                if (channelCall != null) {
                    channelCall.setSound(null, null)
                } else {
                    channelCall = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID_INCOMING,
                        incomingCallChannelName,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = ""
                        vibrationPattern =
                            longArrayOf(0, 1000, 500, 1000, 500)
                        lightColor = Color.RED
                        enableLights(true)
                        enableVibration(true)
                        setSound(null, null)
                    }
                }
                channelCall.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                channelCall.importance = NotificationManager.IMPORTANCE_HIGH

                createNotificationChannel(channelCall)

                val channelMissedCall = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID_MISSED,
                    missedCallChannelName,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = ""
                    vibrationPattern = longArrayOf(0, 1000)
                    lightColor = Color.RED
                    enableLights(true)
                    enableVibration(true)
                }
                channelMissedCall.importance = NotificationManager.IMPORTANCE_DEFAULT
                createNotificationChannel(channelMissedCall)
            }
        }
    }

    private fun getAcceptPendingIntent(id: Int, data: Bundle): PendingIntent {
        val intentTransparent = TransparentActivity.getIntent(
            context,
            CallkitConstants.ACTION_CALL_FOLLOW_UP,
            data
        )
        return PendingIntent.getActivity(context, id, intentTransparent, getFlagPendingIntent())
    }

    private fun getDeclinePendingIntent(id: Int, data: Bundle): PendingIntent {
        val declineIntent = CallkitIncomingBroadcastReceiver.getIntentDecline(context, data)
        return PendingIntent.getBroadcast(context, id, declineIntent, getFlagPendingIntent())
    }

    private fun getTimeOutPendingIntent(id: Int, data: Bundle): PendingIntent {
        val timeOutIntent = CallkitIncomingBroadcastReceiver.getIntentTimeout(context, data)
        return PendingIntent.getBroadcast(context, id, timeOutIntent, getFlagPendingIntent())
    }

    private fun getCallbackPendingIntent(id: Int, data: Bundle): PendingIntent {
        val intentTransparent = TransparentActivity.getIntent(
            context,
            CallkitConstants.ACTION_CALL_CALLBACK,
            data
        )
        return PendingIntent.getActivity(context, id, intentTransparent, getFlagPendingIntent())
    }

    private fun getActivityPendingIntent(id: Int, data: Bundle): PendingIntent {
        val intent = CallkitIncomingActivity.getIntent(context, data)
        return PendingIntent.getActivity(context, id, intent, getFlagPendingIntent())
    }

    private fun getAppPendingIntent(id: Int, data: Bundle): PendingIntent {
        val intent: Intent? = AppUtils.getAppIntent(context, data = data)
        return PendingIntent.getActivity(context, id, intent, getFlagPendingIntent())
    }

    private fun getFlagPendingIntent(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }


    private fun getNotificationManager(): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }


    private fun getPicassoInstance(context: Context, headers: HashMap<String, Any?>): Picasso {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val newRequestBuilder: okhttp3.Request.Builder = chain.request().newBuilder()
                for ((key, value) in headers) {
                    newRequestBuilder.addHeader(key, value.toString())
                }
                chain.proceed(newRequestBuilder.build())
            }
            .build()
        return Picasso.Builder(context)
            .downloader(OkHttp3Downloader(client))
            .build()
    }


    fun requestNotificationPermission(activity: Activity?, map: Map<String, Any>) {
        this.dataNotificationPermission = map
        if (Build.VERSION.SDK_INT > 32) {
            activity?.let {
                ActivityCompat.requestPermissions(
                    it,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_NOTIFICATION_REQUEST_CODE
                )
            }
        }
    }

    fun requestFullIntentPermission(activity: Activity?) {
        if (Build.VERSION.SDK_INT > 33) {
           val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data =  Uri.fromParts("package", activity?.packageName, null)
            }
            activity?.startActivity(intent)
        }
    }

    fun onRequestPermissionsResult(activity: Activity?, requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_NOTIFICATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] === PackageManager.PERMISSION_GRANTED
                ) {
                    // allow
                } else {
                    //deny
                    activity?.let {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(
                                it,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        ) {
                            //showDialogPermissionRationale()
                            if (this.dataNotificationPermission["rationaleMessagePermission"] != null) {
                                showDialogMessage(
                                    it,
                                    this.dataNotificationPermission["rationaleMessagePermission"] as String
                                ) { dialog, _ ->
                                    dialog?.dismiss()
                                    requestNotificationPermission(
                                        activity,
                                        this.dataNotificationPermission
                                    )
                                }
                            } else {
                                requestNotificationPermission(
                                    activity,
                                    this.dataNotificationPermission
                                )
                            }
                        } else {
                            //Open Setting
                            if (this.dataNotificationPermission["postNotificationMessageRequired"] != null) {
                                showDialogMessage(
                                    it,
                                    this.dataNotificationPermission["postNotificationMessageRequired"] as String
                                ) { dialog, _ ->
                                    dialog?.dismiss()
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", it.packageName, null)
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    it.startActivity(intent)
                                }
                            } else {
                                showDialogMessage(
                                    it,
                                    it.resources.getString(R.string.text_post_notification_message_required)
                                ) { dialog, _ ->
                                    dialog?.dismiss()
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", it.packageName, null)
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    it.startActivity(intent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDialogMessage(
        activity: Activity?,
        message: String,
        okListener: DialogInterface.OnClickListener
    ) {
        activity?.let {
            AlertDialog.Builder(it, R.style.DialogTheme)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, okListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show()
        }
    }

    

}


