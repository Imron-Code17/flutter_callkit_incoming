package com.hiennv.flutter_callkit_incoming

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hiennv.flutter_callkit_incoming.widgets.RippleRelativeLayout
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import kotlin.math.abs
import okhttp3.OkHttpClient
import com.squareup.picasso.OkHttp3Downloader
import android.view.ViewGroup.MarginLayoutParams
import java.util.concurrent.TimeUnit
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class CallkitIncomingActivity : Activity() {

    companion object {
        private const val TAG = "CallkitIncomingActivity"
        private const val ACTION_ENDED_CALL_INCOMING =
                "com.hiennv.flutter_callkit_incoming.ACTION_ENDED_CALL_INCOMING"
         private const val ACTION_OPEN_APP = "OPEN_APP"

        fun getIntent(context: Context, data: Bundle) = Intent(CallkitConstants.ACTION_CALL_INCOMING).apply {
            action = "${context.packageName}.${CallkitConstants.ACTION_CALL_INCOMING}"
            putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or
                     Intent.FLAG_ACTIVITY_SINGLE_TOP or
                     Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        fun getIntentEnded(context: Context, isAccepted: Boolean): Intent {
            val intent = Intent("${context.packageName}.${ACTION_ENDED_CALL_INCOMING}")
            intent.putExtra("ACCEPTED", isAccepted)
            return intent
        }
    }

    inner class EndedCallkitIncomingBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "EndedCallkitIncomingBroadcastReceiver received")
            if (!isFinishing && !isCallHandled) {
                val isAccepted = intent.getBooleanExtra("ACCEPTED", false)
                isCallHandled = true
                stopCountdownTimer()
                if (isAccepted) {
                    finishDelayed()
                } else {
                    finishTask()
                }
            }
        }
    }

    private var endedCallkitIncomingBroadcastReceiver = EndedCallkitIncomingBroadcastReceiver()

    private lateinit var ivBackground: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvSenderMessage: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var ivAvatar: CircleImageView
    private lateinit var llAction: LinearLayout
    private lateinit var ivFollowUpCall: ImageView
    private lateinit var tvFollowUp: TextView
    private lateinit var ivDeclineCall: ImageView
    private lateinit var tvDecline: TextView

    // HTTP Client
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Countdown timer variables
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null
    private var remainingTimeInMillis: Long = 0
    private var isCountdownActive = false

    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null

    // Call handling variables
    private var isCallHandled = false
    private var callData: Bundle? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate started")
        
        // CRITICAL: Cancel all notifications immediately to prevent duplicate display
        cancelAllNotifications()
        
        // Set screen orientation
        requestedOrientation = if (!Utils.isTablet(this@CallkitIncomingActivity)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        
        // Configure window for lockscreen display
        configureWindowForLockscreen()
        
        // Set transparent status and navigation
        transparentStatusAndNavigation()
        
        // Set content view
        setContentView(R.layout.activity_callkit_incoming)
        
        // Initialize views
        initView()
        
        // Process incoming data
        incomingData(intent)
        
        // Register broadcast receiver
        registerEndedCallReceiver()
        
        Log.d(TAG, "onCreate completed")
    }

    private fun cancelAllNotifications() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            Log.d(TAG, "All notifications cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notifications", e)
        }
    }

    private fun configureWindowForLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // For Android 8.1 and above
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
        } else {
            // For older versions
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }

    private fun registerEndedCallReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    endedCallkitIncomingBroadcastReceiver,
                    IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}"),
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                registerReceiver(
                    endedCallkitIncomingBroadcastReceiver,
                    IntentFilter("${packageName}.${ACTION_ENDED_CALL_INCOMING}")
                )
            }
            Log.d(TAG, "Broadcast receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering broadcast receiver", e)
        }
    }

    private fun wakeLockRequest(duration: Long) {
        try {
            val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.FULL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Callkit:PowerManager"
            )
            wakeLock?.acquire(duration)
            Log.d(TAG, "Wake lock acquired for ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun transparentStatusAndNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or 
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, 
                true
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setWindowFlag(
                (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                 WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION), 
                false
            )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win: Window = window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }

    private fun incomingData(intent: Intent) {
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
        if (data == null) {
            Log.e(TAG, "No incoming data found")
            finish()
            return
        }

        // Store data for later use
        callData = data

        // Configure lockscreen display
        val isShowFullLockedScreen = data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_SHOW_FULL_LOCKED_SCREEN, true)
        if (isShowFullLockedScreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
            Log.d(TAG, "Configured for lockscreen display")
        }

        // Set text colors
        val textColor = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_COLOR, "#ffffff")
        val textColorBlack = data.getString(CallkitConstants.EXTRA_CALLKIT_SENDER_TEXT_COLOR, "#000000")

        // Set text content
        tvTitle.text = data.getString(CallkitConstants.EXTRA_CALLKIT_TITLE, "")
        tvSubtitle.text = data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "")
        tvSenderMessage.text = data.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, "")

        // Setup countdown timer
        setupTimerFromData(data)

        // Apply text colors
        applyTextColors(textColor)

        // Configure logo
        val isShowLogo = data.getBoolean(CallkitConstants.EXTRA_CALLKIT_IS_SHOW_LOGO, false)
        ivLogo.visibility = if (isShowLogo) View.VISIBLE else View.INVISIBLE

        // Load avatar
        loadAvatar(data)

        // Configure call type
        val callType = data.getInt(CallkitConstants.EXTRA_CALLKIT_TYPE, 0)
        if (callType > 0) {
            ivFollowUpCall.setImageResource(R.drawable.ic_video)
        }

        // Get duration and setup wake lock
        val duration = data.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 0L)
        if (duration > 0) {
            wakeLockRequest(duration)
        }

        // Configure action buttons text
        configureActionButtons(data, textColor)

        // Configure background
        configureBackground(data)
    }

    private fun setupTimerFromData(data: Bundle) {
        val duration = data.getLong(CallkitConstants.EXTRA_CALLKIT_DURATION, 0L)
        
        if (duration > 0) {
            // Calculate remaining time based on start time
            val currentSystemTime = System.currentTimeMillis()
            val timeStartCall = data.getLong(CallkitNotificationManager.EXTRA_TIME_START_CALL, currentSystemTime)
            remainingTimeInMillis = duration - abs(currentSystemTime - timeStartCall)
            
            if (remainingTimeInMillis > 0) {
                setupCountdownTimer()
                Log.d(TAG, "Countdown timer started with ${remainingTimeInMillis}ms remaining")
            } else {
                // Time expired, auto-decline immediately
                Log.d(TAG, "Timer already expired, auto-declining")
                autoDeclineCall()
            }
        } else {
            // No duration, show static timer text
            tvTimer.text = data.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, "")
        }
    }

    private fun setupCountdownTimer() {
        isCountdownActive = true
        countdownHandler = Handler(Looper.getMainLooper())
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingTimeInMillis <= 0) {
                    // Timer finished
                    tvTimer.text = "00:00"
                    isCountdownActive = false
                    Log.d(TAG, "Countdown timer finished")
                    
                    // Auto-decline if no action taken
                    if (!isCallHandled && !isFinishing) {
                        Log.d(TAG, "Auto-declining call due to timeout")
                        autoDeclineCall()
                    }
                    return
                }
                
                // Update timer display
                val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTimeInMillis)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTimeInMillis) % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)
                tvTimer.text = timeString
                
                // Decrease remaining time
                remainingTimeInMillis -= 1000
                
                // Schedule next update
                if (isCountdownActive && !isFinishing) {
                    countdownHandler?.postDelayed(this, 1000)
                }
            }
        }
        
        // Start the countdown
        countdownHandler?.post(countdownRunnable!!)
    }

    private fun autoDeclineCall() {
        if (isCallHandled) return // Prevent multiple calls
        
        Log.d(TAG, "Auto-declining call - no user action within duration")
        isCallHandled = true
        stopCountdownTimer()
        
        // Call decline API
        callDeclineAPI(callData)
        
        // Finish activity
        finishTask()
    }

    private fun stopCountdownTimer() {
        isCountdownActive = false
        countdownHandler?.removeCallbacks(countdownRunnable!!)
        countdownHandler = null
        countdownRunnable = null
        Log.d(TAG, "Countdown timer stopped")
    }

    private fun applyTextColors(textColor: String?) {
        try {
            val color = Color.parseColor(textColor ?: "#ffffff")
            tvTitle.setTextColor(color)
            tvSubtitle.setTextColor(color)
            tvTimer.setTextColor(color)
            tvFollowUp.setTextColor(color)
            tvDecline.setTextColor(color)
        } catch (error: Exception) {
            Log.e(TAG, "Error applying text colors", error)
        }
    }

    private fun loadAvatar(data: Bundle) {
        val avatarUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_AVATAR, "")
        val subtitle = data.getString(CallkitConstants.EXTRA_CALLKIT_SUBTITLE, "")
        
        ivAvatar.visibility = View.VISIBLE
        
        if (!avatarUrl.isNullOrEmpty()) {
            // Load avatar from URL
            val headers = data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as? HashMap<String, Any?> ?: hashMapOf()
            
            try {
                getPicassoInstance(this@CallkitIncomingActivity, headers)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(ivAvatar)
                Log.d(TAG, "Avatar loaded: $avatarUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading avatar", e)
                // Fallback to initials if loading fails
                setAvatarWithInitials(subtitle)
            }
        } else {
            // Show initials when avatar URL is empty
            setAvatarWithInitials(subtitle)
        }
    }

    private fun setAvatarWithInitials(subtitle: String?) {
        if (subtitle.isNullOrEmpty()) {
            // Set default background if no subtitle
            ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            return
        }
        
        try {
            // Extract initials from subtitle
            val initials = getInitials(subtitle)
            
            // Create circular drawable with initials
            val drawable = createInitialsDrawable(initials)
            ivAvatar.setImageDrawable(drawable)
            
            Log.d(TAG, "Avatar set with initials: $initials")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating initials avatar", e)
            ivAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun getInitials(name: String): String {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return "?"
        
        val words = trimmedName.split("\\s+".toRegex())
        return when {
            words.size >= 2 -> {
                // Take first letter of first and second word
                "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
            }
            words.size == 1 && words[0].length >= 2 -> {
                // Take first two letters if only one word with at least 2 characters
                "${words[0][0].uppercaseChar()}${words[0][1].uppercaseChar()}"
            }
            else -> {
                // Take first letter only
                words[0].first().uppercaseChar().toString()
            }
        }
    }

    private fun createInitialsDrawable(initials: String): android.graphics.drawable.Drawable {
        val size = 200 // Size in pixels
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background color (light gray)
        val backgroundPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E0E0E0") // Light gray
            isAntiAlias = true
        }
        
        // Draw circular background
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, backgroundPaint)
        
        // Text properties
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#424242") // Dark gray text
            textSize = size * 0.4f // 40% of avatar size
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Calculate text position to center it
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        val textY = radius + (textBounds.height() / 2f)
        
        // Draw text
        canvas.drawText(initials, radius, textY, textPaint)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun configureActionButtons(data: Bundle, textColor: String?) {
        val textFollowUp = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_FOLLOW_UP, "")
        tvFollowUp.text = if (TextUtils.isEmpty(textFollowUp)) getString(R.string.text_follow_up) else textFollowUp
        
        val textDecline = data.getString(CallkitConstants.EXTRA_CALLKIT_TEXT_DECLINE, "")
        tvDecline.text = if (TextUtils.isEmpty(textDecline)) getString(R.string.text_decline) else textDecline
    }

    private fun configureBackground(data: Bundle) {
        // Set background color
        val backgroundColor = data.getString(CallkitConstants.EXTRA_CALLKIT_BACKGROUND_COLOR, "#0955fa")
        try {
            ivBackground.setBackgroundColor(Color.parseColor(backgroundColor))
        } catch (error: Exception) {
            Log.e(TAG, "Error setting background color", error)
        }

        // Load background image if available
        var backgroundUrl = data.getString(CallkitConstants.EXTRA_CALLKIT_BACKGROUND_URL, "")
        if (!backgroundUrl.isNullOrEmpty()) {
            if (!backgroundUrl.startsWith("http://", true) && !backgroundUrl.startsWith("https://", true)) {
                backgroundUrl = "file:///android_asset/flutter_assets/$backgroundUrl"
            }
            
            val headers = data.getSerializable(CallkitConstants.EXTRA_CALLKIT_HEADERS) as? HashMap<String, Any?> ?: hashMapOf()
            
            try {
                getPicassoInstance(this@CallkitIncomingActivity, headers)
                    .load(backgroundUrl)
                    .placeholder(R.drawable.transparent_image)
                    .error(R.drawable.transparent_image)
                    .into(ivBackground)
                Log.d(TAG, "Background image loaded: $backgroundUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading background image", e)
            }
        }
    }

    private fun initView() {
        val data = intent.extras?.getBundle(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
        
        // Initialize views
        ivBackground = findViewById(R.id.ivBackground)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvTimer = findViewById(R.id.tvTimer)
        tvSenderMessage = findViewById(R.id.tvSenderMessage)
        ivLogo = findViewById(R.id.ivLogo)
        ivAvatar = findViewById(R.id.ivAvatar)
        llAction = findViewById(R.id.llAction)
        ivFollowUpCall = findViewById(R.id.ivFollowUpCall)
        tvFollowUp = findViewById(R.id.tvFollowUp)
        ivDeclineCall = findViewById(R.id.ivDeclineCall)
        tvDecline = findViewById(R.id.tvDecline)

        // Adjust layout for navigation bar
        val params = llAction.layoutParams as MarginLayoutParams
        params.setMargins(0, 0, 0, Utils.getNavigationBarHeight(this@CallkitIncomingActivity))
        llAction.layoutParams = params

        // Configure layout visibility
        val texttimer = data?.getString(CallkitConstants.EXTRA_CALLKIT_TIMER, "")
        val textSenderMessage = data?.getString(CallkitConstants.EXTRA_CALLKIT_SENDERMESSAGE, "")

        val layoutParent = findViewById<LinearLayout>(R.id.layoutParent)
        if (TextUtils.isEmpty(texttimer) && TextUtils.isEmpty(textSenderMessage)) {
            layoutParent.visibility = View.INVISIBLE
        } else {
            layoutParent.visibility = View.VISIBLE
        }

        // Set click listeners
        ivFollowUpCall.setOnClickListener {
            Log.d(TAG, "Follow up call clicked")
            onAcceptClick()
        }
        
        ivDeclineCall.setOnClickListener {
            Log.d(TAG, "Decline call clicked")
            onDeclineClick()
        }
    }

    private fun animateAcceptCall() {
        val shakeAnimation = AnimationUtils.loadAnimation(this@CallkitIncomingActivity, R.anim.shake_anim)
        ivFollowUpCall.animation = shakeAnimation
    }

    private fun onAcceptClick() {
        if (isCallHandled) return // Prevent multiple actions
        
        Log.d(TAG, "Call accepted")
        isCallHandled = true
        stopCountdownTimer()
        
        // Call API for follow up
        callFollowUpAPI(callData)
        openAppWithRoute()
        
        dismissKeyguard()
        finishTask()
    }

    private fun onDeclineClick() {
        if (isCallHandled) return // Prevent multiple actions
        
        Log.d(TAG, "Call declined")
        isCallHandled = true
        stopCountdownTimer()
        
        // Call API for decline
        callDeclineAPI(callData)
        
        finishTask()
    }

    private fun openAppWithRoute() {
    try {
        // Ambil data routing dari bundle
        val targetRoute = callData?.getString(CallkitConstants.EXTRA_CALLKIT_TARGET_ROUTE, "")
        
        // Buat intent untuk membuka aplikasi utama
        val packageManager = applicationContext.packageManager
        val packageName = applicationContext.packageName
        
        // Ambil launch intent dari aplikasi utama
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        
        if (launchIntent != null) {
            // Set flags untuk membuka aplikasi
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                               Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                               Intent.FLAG_ACTIVITY_SINGLE_TOP
            
            // Tambahkan extra data untuk routing
            launchIntent.putExtra("FROM_CALLKIT", true)
            launchIntent.putExtra("ACTION", ACTION_OPEN_APP)
            
            if (!targetRoute.isNullOrEmpty()) {
                launchIntent.putExtra("TARGET_ROUTE", targetRoute)
            }
            

            
            // Start aplikasi
            startActivity(launchIntent)
            Log.d(TAG, "App opened with route: $targetRoute")
            
        } else {
            Log.e(TAG, "Could not get launch intent for package: $packageName")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error opening app with route", e)
    }
}

    private fun callFollowUpAPI(data: Bundle?) {
        try {
            val fcmDataString = data?.getString(CallkitConstants.EXTRA_CALLKIT_FCM_DATA, "")
            if (fcmDataString.isNullOrEmpty()) {
                Log.e(TAG, "FCM data is empty for follow up")
                return
            }

            val fcmData = JSONObject(fcmDataString)
            val messageId = fcmData.optInt("message_id", 0)
            val salesId = fcmData.optInt("sales_id", 0)

            if (messageId == 0 || salesId == 0) {
                Log.e(TAG, "Invalid message_id or sales_id for follow up")
                return
            }

            val requestBody = JSONObject().apply {
                put("message_id", messageId)
                put("sales_id", salesId)
            }

           Log.e(TAG, "MS ID: $messageId - S ID: $salesId")

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, requestBody.toString())
            val urlFollowUp = data.getString(CallkitConstants.EXTRA_CALLKIT_URL_FOLLOW_UP, "")
            Log.e(TAG, "URL FOLLOW UP: $urlFollowUp")

            val request = Request.Builder()
                .url(urlFollowUp)
                .addHeader("accept", "application/json")
                .post(body)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Follow up API call failed", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Follow up API call successful: ${response.body()?.string()}")
                        } else {
                            Log.e(TAG, "Follow up API call failed with code: ${response.code()}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error calling follow up API", e)
        }
    }

    private fun callDeclineAPI(data: Bundle?) {
        try {
            val fcmDataString = data?.getString(CallkitConstants.EXTRA_CALLKIT_FCM_DATA, "")
            if (fcmDataString.isNullOrEmpty()) {
                Log.e(TAG, "FCM data is empty for decline")
                return
            }

            val fcmData = JSONObject(fcmDataString)
            val messageId = fcmData.optInt("message_id", 0)
            val salesId = fcmData.optInt("sales_id", 0)

            if (messageId == 0 || salesId == 0) {
                Log.e(TAG, "Invalid message_id or sales_id for decline")
                return
            }

            val requestBody = JSONObject().apply {
                put("message_id", messageId)
                put("sales_id", salesId)
            }

           Log.e(TAG, "MS ID: $messageId - S ID: $salesId")

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, requestBody.toString())

            val urlDecline = data?.getString(CallkitConstants.EXTRA_CALLKIT_URL_DECLINE, "")
            Log.e(TAG, "URL DECLINE: $urlDecline")

            val request = Request.Builder()
                .url(urlDecline)
                .post(body)
                .addHeader("accept", "application/json")
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Decline API call failed", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Decline API call successful: ${response.body()?.string()}")
                        } else {
                            Log.e(TAG, "Decline API call failed with code: ${response.code()}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error calling decline API", e)
        }
    }

    private fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun finishDelayed() {
        stopCountdownTimer()
        Handler(Looper.getMainLooper()).postDelayed({
            finishTask()
        }, 1000)
    }

    private fun finishTask() {
        Log.d(TAG, "Finishing task")
        stopCountdownTimer()
        releaseWakeLock()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
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

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        stopCountdownTimer()
        releaseWakeLock()
        
        try {
            unregisterReceiver(endedCallkitIncomingBroadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Prevent back button from closing the activity
        Log.d(TAG, "Back button pressed - ignored")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity paused")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity resumed")
        
        // Ensure notifications are cancelled when activity resumes
        cancelAllNotifications()
    }
}