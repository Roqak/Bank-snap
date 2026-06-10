package com.banksnap.banksnap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingBubbleService : android.app.Service() {

    companion object {
        private const val TAG = "BankSnapBubble"
        private const val MAX_RETRY_ATTEMPTS = 40
        private const val RETRY_DELAY_MS = 250L
        private const val FIELD_FILL_TIMEOUT_MS = 20000L

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
            context.startService(intent)
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
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingBubbleService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                if (!isRunning) {
                    showBubble()
                    startMonitoringBankApp()
                }
            }
            "STOP" -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.floating_bubble, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager?.addView(bubbleView, params)
        isRunning = true

        setupBubbleInteractions()
        updateBubbleUI()
    }

    private fun setupBubbleInteractions() {
        bubbleView?.let { view ->
            // Make draggable
            view.setOnTouchListener { v, event ->
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
                        // Check if it was a click (small movement)
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
        }
    }

    private fun toggleExpandedView() {
        bubbleView?.let { view ->
            val expandedView = view.findViewById<LinearLayout>(R.id.expanded_view)
            val isVisible = expandedView.visibility == View.VISIBLE
            expandedView.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
    }

    private fun updateBubbleUI() {
        bubbleView?.let { view ->
            val bankNameText = view.findViewById<TextView>(R.id.bubble_bank_name)
            val accountText = view.findViewById<TextView>(R.id.bubble_account)
            val statusText = view.findViewById<TextView>(R.id.bubble_status)

            bankNameText?.text = currentBankName ?: "Bank App"
            accountText?.text = currentAccountNumber ?: ""
            statusText?.text = "Waiting for transfer screen..."

            // Setup quick action buttons
            view.findViewById<Button>(R.id.btn_1000)?.setOnClickListener {
                attemptFillAmount("1000")
            }
            view.findViewById<Button>(R.id.btn_2000)?.setOnClickListener {
                attemptFillAmount("2000")
            }
            view.findViewById<Button>(R.id.btn_5000)?.setOnClickListener {
                attemptFillAmount("5000")
            }
            view.findViewById<Button>(R.id.btn_10000)?.setOnClickListener {
                attemptFillAmount("10000")
            }
            view.findViewById<Button>(R.id.btn_custom)?.setOnClickListener {
                showCustomAmountDialog()
            }
            view.findViewById<Button>(R.id.btn_done)?.setOnClickListener {
                showToast("Please enter your PIN manually. BankSnap cannot access PIN fields for security.")
            }
            view.findViewById<ImageView>(R.id.btn_close)?.setOnClickListener {
                stopSelf()
            }
        }
    }

    private fun startMonitoringBankApp() {
        val packageName = currentBankPackage ?: return
        val accountNumber = currentAccountNumber ?: return

        // Start periodic scanning of the bank app UI
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                // Use AccessibilityService to get root node
                val rootNode = BanksnapAccessibilityService.instance?.rootInActiveWindow
                if (rootNode != null) {
                    val currentPackage = rootNode.packageName?.toString()
                    if (currentPackage == packageName) {
                        handleBankAppScreen(rootNode, accountNumber)
                    }
                }

                if (isRunning) {
                    handler.postDelayed(this, RETRY_DELAY_MS)
                }
            }
        })
    }

    private fun handleBankAppScreen(rootNode: AccessibilityNodeInfo, accountNumber: String) {
        bubbleView?.let { view ->
            val statusText = view.findViewById<TextView>(R.id.bubble_status)

            // Try to find and fill account number
            val accountField = findAccountInputField(rootNode)
            if (accountField != null) {
                val filled = fillField(accountField, accountNumber)
                if (filled) {
                    statusText?.text = "Account filled! Enter amount below"
                    view.findViewById<LinearLayout>(R.id.amount_buttons)?.visibility = View.VISIBLE
                    return
                }
            }

            // Check if we're on amount entry screen
            val amountField = findAmountInputField(rootNode)
            if (amountField != null) {
                statusText?.text = "Account confirmed. Enter amount or use quick buttons"
                view.findViewById<LinearLayout>(R.id.amount_buttons)?.visibility = View.VISIBLE
            }

            // Check if PIN field is present
            val pinField = findPinInputField(rootNode)
            if (pinField != null) {
                statusText?.text = "PIN required. BankSnap cannot fill this. Please enter manually."
                view.findViewById<LinearLayout>(R.id.amount_buttons)?.visibility = View.GONE
                view.findViewById<Button>(R.id.btn_done)?.visibility = View.VISIBLE
            }
        }
    }

    private fun attemptFillAmount(amount: String) {
        val rootNode = BanksnapAccessibilityService.instance?.rootInActiveWindow ?: return
        val amountField = findAmountInputField(rootNode)

        if (amountField != null) {
            val filled = fillField(amountField, amount)
            if (filled) {
                showToast("Amount ₦$amount entered!")
                bubbleView?.let { view ->
                    view.findViewById<TextView>(R.id.bubble_status)?.text = "Amount ₦$amount entered. Review and confirm transfer."
                }
            } else {
                showToast("Could not fill amount. Please enter manually.")
            }
        } else {
            showToast("Amount field not found. Please tap the field first.")
        }
    }

    private fun showCustomAmountDialog() {
        // For simplicity, show a toast suggesting manual entry
        showToast("Custom amount: Please enter the amount manually in the bank app, then tap 'Done'")
    }

    private fun findAccountInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
        collectAllEditTexts(rootNode, allEditTexts)

        val hints = listOf("account", "beneficiary", "recipient", "destination", "send to", "transfer to", "credit")

        // Strategy 1: By hint/content description
        for (editText in allEditTexts) {
            val hint = editText.hintText?.toString()?.lowercase() ?: ""
            val desc = editText.contentDescription?.toString()?.lowercase() ?: ""
            val text = editText.text?.toString()?.lowercase() ?: ""

            for (h in hints) {
                if (hint.contains(h) || desc.contains(h) || text.contains(h)) {
                    return editText
                }
            }
        }

        // Strategy 2: Only empty EditText
        val emptyEditTexts = allEditTexts.filter { it.text.isNullOrEmpty() }
        if (emptyEditTexts.size == 1) return emptyEditTexts.first()

        // Strategy 3: First empty or first overall
        return emptyEditTexts.firstOrNull() ?: allEditTexts.firstOrNull()
    }

    private fun findAmountInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
        collectAllEditTexts(rootNode, allEditTexts)

        val amountHints = listOf("amount", "sum", "value", "₦", "ngn", "naira")

        for (editText in allEditTexts) {
            val hint = editText.hintText?.toString()?.lowercase() ?: ""
            val desc = editText.contentDescription?.toString()?.lowercase() ?: ""

            for (h in amountHints) {
                if (hint.contains(h) || desc.contains(h)) {
                    return editText
                }
            }
        }

        // If account is already filled, the next empty field is likely amount
        val emptyFields = allEditTexts.filter { it.text.isNullOrEmpty() }
        return emptyFields.firstOrNull()
    }

    private fun findPinInputField(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
        collectAllEditTexts(rootNode, allEditTexts)

        val pinHints = listOf("pin", "password", "otp", "token", "secure", "authorize")

        for (editText in allEditTexts) {
            val hint = editText.hintText?.toString()?.lowercase() ?: ""
            val desc = editText.contentDescription?.toString()?.lowercase() ?: ""

            for (h in pinHints) {
                if (hint.contains(h) || desc.contains(h)) {
                    return editText
                }
            }
        }
        return null
    }

    private fun fillField(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (result) {
            val current = node.text?.toString() ?: ""
            return current == text || current.contains(text)
        }

        // Fallback: focus and type char by char
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        for (char in text) {
            val charArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, char.toString())
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, charArgs)
        }

        val finalText = node.text?.toString() ?: ""
        return finalText == text || finalText.contains(text)
    }

    private fun collectAllEditTexts(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString()?.contains("EditText") == true ||
            node.className?.toString()?.contains("TextInput") == true) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAllEditTexts(child, result)
            }
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
        if (bubbleView != null) {
            windowManager?.removeView(bubbleView)
            bubbleView = null
        }
        Log.d(TAG, "FloatingBubbleService destroyed")
    }
}
