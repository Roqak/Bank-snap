package com.banksnap.banksnap

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingBubbleService : android.app.Service() {

    companion object {
        private const val TAG = "BankSnapBubble"
        private const val SCAN_INTERVAL_MS = 500L
        private const val MAX_SCAN_ATTEMPTS = 60
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "banksnap_bubble"

        var isRunning = false
            private set

        var currentAccountNumber: String? = null
        var currentBankPackage: String? = null
        var currentBankName: String? = null

        fun startService(context: android.content.Context, accountNumber: String, bankPackage: String, bankName: String) {
            currentAccountNumber = accountNumber
            currentBankPackage = bankPackage
            currentBankName = bankName
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: android.content.Context) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = "STOP"
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var scanCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isExpanded = false
    private var currentScreenState = ScreenState.UNKNOWN
    private var lastFilledField: String? = null

    private enum class ScreenState {
        UNKNOWN, LOGIN, DASHBOARD, TRANSFER_MENU, ACCOUNT_ENTRY,
        AMOUNT_ENTRY, CONFIRMATION, PIN_ENTRY, SUCCESS, ERROR
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingBubbleService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                if (!isRunning) {
                    startForegroundServiceWithNotification()
                    showBubble()
                    startSmartMonitoring()
                }
            }
            "STOP" -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "BankSnap Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps BankSnap active while you transfer"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BankSnap Assistant")
                .setContentText("Helping with ${currentBankName ?: "bank transfer"}")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}")
            // Continue without foreground — the overlay will still work
        }
    }

    private fun showBubble() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            bubbleView = inflater.inflate(R.layout.floating_bubble, null)

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 150
            }

            windowManager?.addView(bubbleView, params)
            isRunning = true

            setupBubbleInteractions()
            updateBubbleUI()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bubble: ${e.message}")
            showToast("Could not show bubble. Please allow 'Display over other apps' permission.")
            stopSelf()
        }
    }

    private fun setupBubbleInteractions() {
        bubbleView?.let { view ->
            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(bubbleView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (dx * dx + dy * dy < 100) {
                            toggleExpandedView()
                        }
                        true
                    }
                    else -> false
                }
            }

            view.findViewById<ImageView>(R.id.btn_close)?.setOnClickListener {
                stopSelf()
            }

            view.findViewById<Button>(R.id.btn_quick_1000)?.setOnClickListener {
                attemptSmartFill("1000", "amount")
            }
            view.findViewById<Button>(R.id.btn_quick_2000)?.setOnClickListener {
                attemptSmartFill("2000", "amount")
            }
            view.findViewById<Button>(R.id.btn_quick_5000)?.setOnClickListener {
                attemptSmartFill("5000", "amount")
            }
            view.findViewById<Button>(R.id.btn_quick_10000)?.setOnClickListener {
                attemptSmartFill("10000", "amount")
            }
            view.findViewById<Button>(R.id.btn_other)?.setOnClickListener {
                showToast("Please enter amount manually in the bank app")
            }
            view.findViewById<Button>(R.id.btn_done)?.setOnClickListener {
                showToast("Please enter your PIN manually. BankSnap cannot access PIN fields for security.")
                updateBubbleStatus("PIN required - enter manually", ScreenState.PIN_ENTRY)
            }
            view.findViewById<Button>(R.id.btn_recheck)?.setOnClickListener {
                scanCount = 0
                showToast("Rechecking screen...")
            }
        }
    }

    private fun toggleExpandedView() {
        bubbleView?.let { view ->
            val expandedView = view.findViewById<LinearLayout>(R.id.expanded_view)
            isExpanded = expandedView.visibility != View.VISIBLE
            expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun updateBubbleUI() {
        bubbleView?.let { view ->
            view.findViewById<TextView>(R.id.bubble_bank_name)?.text = currentBankName ?: "Bank App"
            view.findViewById<TextView>(R.id.bubble_account)?.text = currentAccountNumber ?: ""
            updateBubbleStatus("Analyzing screen...", ScreenState.UNKNOWN)
        }
    }

    private fun updateBubbleStatus(message: String, state: ScreenState) {
        bubbleView?.let { view ->
            val statusText = view.findViewById<TextView>(R.id.bubble_status)
            val progressBar = view.findViewById<ProgressBar>(R.id.bubble_progress)
            val statusIcon = view.findViewById<ImageView>(R.id.status_icon)

            statusText?.text = message
            progressBar?.visibility = if (state == ScreenState.UNKNOWN) View.VISIBLE else View.GONE
            statusIcon?.visibility = if (state == ScreenState.UNKNOWN) View.GONE else View.VISIBLE

            val iconRes = when (state) {
                ScreenState.UNKNOWN -> android.R.drawable.ic_menu_search
                ScreenState.LOGIN -> android.R.drawable.ic_lock_idle_lock
                ScreenState.DASHBOARD -> android.R.drawable.ic_menu_search
                ScreenState.TRANSFER_MENU -> android.R.drawable.ic_menu_send
                ScreenState.ACCOUNT_ENTRY -> android.R.drawable.ic_menu_edit
                ScreenState.AMOUNT_ENTRY -> android.R.drawable.ic_menu_agenda
                ScreenState.CONFIRMATION -> android.R.drawable.ic_menu_info_details
                ScreenState.PIN_ENTRY -> android.R.drawable.ic_lock_idle_lock
                ScreenState.SUCCESS -> android.R.drawable.ic_menu_send
                ScreenState.ERROR -> android.R.drawable.ic_dialog_alert
            }
            statusIcon?.setImageResource(iconRes)

            val amountButtons = view.findViewById<LinearLayout>(R.id.amount_buttons_container)
            val actionButtons = view.findViewById<LinearLayout>(R.id.action_buttons_container)
            val btnDone = view.findViewById<Button>(R.id.btn_done)
            val btnOther = view.findViewById<Button>(R.id.btn_other)

            when (state) {
                ScreenState.ACCOUNT_ENTRY -> {
                    amountButtons?.visibility = View.GONE
                    actionButtons?.visibility = View.VISIBLE
                    btnDone?.visibility = View.GONE
                    btnOther?.visibility = View.GONE
                }
                ScreenState.AMOUNT_ENTRY -> {
                    amountButtons?.visibility = View.VISIBLE
                    actionButtons?.visibility = View.VISIBLE
                    btnDone?.visibility = View.GONE
                    btnOther?.visibility = View.VISIBLE
                }
                ScreenState.PIN_ENTRY -> {
                    amountButtons?.visibility = View.GONE
                    actionButtons?.visibility = View.VISIBLE
                    btnDone?.visibility = View.VISIBLE
                    btnOther?.visibility = View.GONE
                }
                else -> {
                    amountButtons?.visibility = View.GONE
                    actionButtons?.visibility = View.GONE
                }
            }
        }
    }

    private fun startSmartMonitoring() {
        val accountNumber = currentAccountNumber ?: return
        val packageName = currentBankPackage ?: return

        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                if (scanCount >= MAX_SCAN_ATTEMPTS) {
                    updateBubbleStatus("Scan limit reached. Tap Recheck.", ScreenState.ERROR)
                    return
                }

                try {
                    val rootNode = BanksnapAccessibilityService.instance?.rootInActiveWindow
                    if (rootNode != null) {
                        val currentPackage = rootNode.packageName?.toString()
                        if (currentPackage == packageName) {
                            analyzeScreenAndAct(rootNode, accountNumber)
                        } else {
                            updateBubbleStatus("Waiting for ${currentBankName ?: "bank"}...", ScreenState.UNKNOWN)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during screen scan: ${e.message}")
                }

                scanCount++
                if (isRunning) {
                    handler.postDelayed(this, SCAN_INTERVAL_MS)
                }
            }
        })
    }

    private fun analyzeScreenAndAct(rootNode: AccessibilityNodeInfo, accountNumber: String) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        val allTexts = allNodes.mapNotNull { it.text?.toString()?.lowercase() }
        val allHints = allNodes.mapNotNull { it.hintText?.toString()?.lowercase() }
        val allDescs = allNodes.mapNotNull { it.contentDescription?.toString()?.lowercase() }

        val newState = determineScreenState(allTexts, allHints, allDescs, allNodes)

        if (newState != currentScreenState) {
            currentScreenState = newState
            Log.d(TAG, "Screen state: $newState")
        }

        when (currentScreenState) {
            ScreenState.LOGIN -> {
                updateBubbleStatus("Please log in to ${currentBankName ?: "bank"} first", ScreenState.LOGIN)
            }
            ScreenState.DASHBOARD -> {
                updateBubbleStatus("Tap 'Transfer' or I'll try to navigate", ScreenState.DASHBOARD)
                val transferBtn = findTransferButton(rootNode)
                if (transferBtn != null && scanCount < 5) {
                    transferBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    showToast("Navigating to transfer...")
                }
            }
            ScreenState.TRANSFER_MENU -> {
                updateBubbleStatus("Select transfer type", ScreenState.TRANSFER_MENU)
            }
            ScreenState.ACCOUNT_ENTRY -> {
                updateBubbleStatus("Filling account number...", ScreenState.ACCOUNT_ENTRY)
                val accountField = findAccountInputField(allNodes)
                if (accountField != null && lastFilledField != "account") {
                    val filled = fillFieldSmart(accountField, accountNumber)
                    if (filled) {
                        lastFilledField = "account"
                        updateBubbleStatus("Account filled! Enter amount ↓", ScreenState.AMOUNT_ENTRY)
                        vibrateSuccess()
                        if (!isExpanded) toggleExpandedView()
                    }
                }
            }
            ScreenState.AMOUNT_ENTRY -> {
                updateBubbleStatus("Account confirmed. Tap amount below", ScreenState.AMOUNT_ENTRY)
            }
            ScreenState.CONFIRMATION -> {
                updateBubbleStatus("Review and confirm transfer", ScreenState.CONFIRMATION)
            }
            ScreenState.PIN_ENTRY -> {
                updateBubbleStatus("PIN required - BankSnap cannot fill this", ScreenState.PIN_ENTRY)
                if (!isExpanded) toggleExpandedView()
            }
            ScreenState.SUCCESS -> {
                updateBubbleStatus("Transfer complete!", ScreenState.SUCCESS)
                showToast("Transfer completed!")
            }
            ScreenState.ERROR -> {
                updateBubbleStatus("Error detected. Check bank app.", ScreenState.ERROR)
            }
            ScreenState.UNKNOWN -> {
                updateBubbleStatus("Analyzing... (${scanCount}/${MAX_SCAN_ATTEMPTS})", ScreenState.UNKNOWN)
            }
        }
    }

    private fun determineScreenState(
        texts: List<String>,
        hints: List<String>,
        descs: List<String>,
        nodes: List<AccessibilityNodeInfo>
    ): ScreenState {
        val allContent = texts + hints + descs

        if (allContent.any { it.contains("pin") || it.contains("otp") || it.contains("token") || it.contains("password") }) {
            return ScreenState.PIN_ENTRY
        }

        if (allContent.any { it.contains("successful") || it.contains("completed") || it.contains("done") || it.contains("sent") }) {
            return ScreenState.SUCCESS
        }

        if (allContent.any { it.contains("error") || it.contains("failed") || it.contains("invalid") || it.contains("unsuccessful") }) {
            return ScreenState.ERROR
        }

        if (allContent.any { it.contains("confirm") || it.contains("review") || it.contains("verify") || it.contains("summary") }) {
            return ScreenState.CONFIRMATION
        }

        val accountField = findAccountInputField(nodes)
        val amountField = findAmountInputField(nodes)
        if (amountField != null) {
            val amountText = amountField.text?.toString() ?: ""
            val accountText = accountField?.text?.toString() ?: ""
            if (accountText.isNotEmpty() && amountText.isEmpty()) {
                return ScreenState.AMOUNT_ENTRY
            }
        }

        val hasAccountField = nodes.any { node ->
            val hint = node.hintText?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            hint.contains("account") || desc.contains("account") ||
                    hint.contains("beneficiary") || desc.contains("beneficiary")
        }
        val editTexts = nodes.filter { it.className?.toString()?.contains("EditText") == true }
        if (hasAccountField || (editTexts.isNotEmpty() && allContent.any { it.contains("account") })) {
            return ScreenState.ACCOUNT_ENTRY
        }

        if (allContent.any { it.contains("transfer") || it.contains("send money") || it.contains("send funds") || it.contains("quick transfer") }) {
            return ScreenState.TRANSFER_MENU
        }

        if (allContent.any { it.contains("login") || it.contains("sign in") || it.contains("welcome") || it.contains("password") } ||
            nodes.any { it.className?.toString()?.contains("Password") == true }) {
            return ScreenState.LOGIN
        }

        if (allContent.any { it.contains("balance") || it.contains("home") || it.contains("dashboard") || it.contains("welcome back") }) {
            return ScreenState.DASHBOARD
        }

        return ScreenState.UNKNOWN
    }

    private fun attemptSmartFill(value: String, fieldType: String) {
        val rootNode = BanksnapAccessibilityService.instance?.rootInActiveWindow ?: return

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        val targetField = when (fieldType) {
            "amount" -> findAmountInputField(allNodes)
            "account" -> findAccountInputField(allNodes)
            else -> null
        }

        if (targetField != null) {
            val filled = fillFieldSmart(targetField, value)
            if (filled) {
                vibrateSuccess()
                showToast("₦$value entered!")
                updateBubbleStatus("₦$value entered. Review and confirm.", ScreenState.CONFIRMATION)
            } else {
                showToast("Could not fill. Tap the field first, then try again.")
            }
        } else {
            showToast("$fieldType field not found. Navigate to correct screen.")
        }
    }

    private fun fillFieldSmart(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (result) {
            val current = node.text?.toString() ?: ""
            if (current == text || current.contains(text)) return true
        }

        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

        for (char in text) {
            val charArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, char.toString())
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, charArgs)
        }

        val finalText = node.text?.toString() ?: ""
        return finalText == text || finalText.contains(text)
    }

    private fun findAccountInputField(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val hints = listOf("account", "beneficiary", "recipient", "destination", "send to", "transfer to", "credit")

        for (node in nodes) {
            val hint = node.hintText?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            for (h in hints) {
                if (hint.contains(h) || desc.contains(h) || text.contains(h)) {
                    if (node.className?.toString()?.contains("EditText") == true) return node
                }
            }
        }

        val editTexts = nodes.filter { it.className?.toString()?.contains("EditText") == true }
        return editTexts.firstOrNull { it.text.isNullOrEmpty() } ?: editTexts.firstOrNull()
    }

    private fun findAmountInputField(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val amountHints = listOf("amount", "sum", "value", "how much", "enter amount")

        for (node in nodes) {
            val hint = node.hintText?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            for (h in amountHints) {
                if (hint.contains(h) || desc.contains(h)) {
                    if (node.className?.toString()?.contains("EditText") == true) return node
                }
            }
        }

        val editTexts = nodes.filter { it.className?.toString()?.contains("EditText") == true }
        val nonAccountFields = editTexts.filter { node ->
            val hint = node.hintText?.toString()?.lowercase() ?: ""
            !hint.contains("account") && !hint.contains("beneficiary")
        }
        return nonAccountFields.firstOrNull { it.text.isNullOrEmpty() } ?: nonAccountFields.firstOrNull()
    }

    private fun findTransferButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        val transferTexts = listOf("transfer", "send money", "send", "quick transfer", "to bank", "new transfer")

        for (node in allNodes) {
            if (!node.isClickable) continue
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            for (t in transferTexts) {
                if (text == t || desc == t || text.contains(t) || desc.contains(t)) {
                    return node
                }
            }
        }
        return null
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAllNodes(child, result)
            }
        }
    }

    private fun vibrateSuccess() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentAccountNumber = null
        currentBankPackage = null
        currentBankName = null
        lastFilledField = null
        try {
            if (bubbleView != null) {
                windowManager?.removeView(bubbleView)
                bubbleView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing bubble: ${e.message}")
        }
        Log.d(TAG, "FloatingBubbleService destroyed")
    }
}
