package com.banksnap.banksnap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BanksnapAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BankSnapAccessibility"
        private const val MAX_RETRY_ATTEMPTS = 30
        private const val RETRY_DELAY_MS = 300L
        private const val FIELD_FILL_TIMEOUT_MS = 15000L

        private val BANK_APP_CONFIGS = mapOf(
            "com.gtbank.main" to BankConfig(
                packageName = "com.gtbank.main",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to", "transfer to", "send to", "credit"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm", "verify", "ok", "done"),
                transferMenuTexts = listOf("transfer", "send money", "quick transfer", "send", "payments", "send funds")
            ),
            "com.accessbank.accessmobile" to BankConfig(
                packageName = "com.accessbank.accessmobile",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to", "transfer to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm", "verify"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "com.zenith.bank" to BankConfig(
                packageName = "com.zenith.bank",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "ng.com.firstmobilebusiness.android" to BankConfig(
                packageName = "ng.com.firstmobilebusiness.android",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "com.uba.mobile" to BankConfig(
                packageName = "com.uba.mobile",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "team.opay.pay" to BankConfig(
                packageName = "team.opay.pay",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to", "opay number", "phone number", "send to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm", "verify", "done"),
                transferMenuTexts = listOf("transfer", "send money", "to opay", "send")
            ),
            "com.kudabank.app" to BankConfig(
                packageName = "com.kudabank.app",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to", "kuda tag"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "com.palmpay.app" to BankConfig(
                packageName = "com.palmpay.app",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to", "phone number"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "com.teamapt.monnify" to BankConfig(
                packageName = "com.teamapt.monnify",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            ),
            "com.sterlingbankmobileapp" to BankConfig(
                packageName = "com.sterlingbankmobileapp",
                accountFieldHints = listOf("account number", "beneficiary", "account", "recipient", "destination", "to"),
                confirmButtonTexts = listOf("next", "continue", "proceed", "transfer", "send", "confirm"),
                transferMenuTexts = listOf("transfer", "send money", "send")
            )
        )

        var isServiceRunning = false
            private set

        private var pendingAccountNumber: String? = null
        private var pendingPackageName: String? = null
        private var operationCallback: ((Boolean) -> Unit)? = null

        fun setPendingOperation(accountNumber: String, packageName: String, callback: (Boolean) -> Unit) {
            Log.d(TAG, "Setting pending operation: account=$accountNumber, package=$packageName")
            pendingAccountNumber = accountNumber
            pendingPackageName = packageName
            operationCallback = callback
        }

        fun clearPendingOperation() {
            Log.d(TAG, "Clearing pending operation")
            pendingAccountNumber = null
            pendingPackageName = null
            operationCallback = null
        }
    }

    data class BankConfig(
        val packageName: String,
        val accountFieldHints: List<String>,
        val confirmButtonTexts: List<String>,
        val transferMenuTexts: List<String>
    )

    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "Accessibility Service Connected")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val config = BANK_APP_CONFIGS[packageName] ?: return

        // Check if we have a pending operation for this app
        if (pendingPackageName != packageName) return

        val accountNumber = pendingAccountNumber ?: return

        Log.d(TAG, "Event: ${event.eventType}, Package: $packageName")

        // Cancel any pending timeout
        timeoutRunnable?.let { handler.removeCallbacks(it) }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                rootInActiveWindow?.let { rootNode ->
                    handleWindowChange(rootNode, accountNumber, config)
                }
            }
        }
    }

    private fun handleWindowChange(rootNode: AccessibilityNodeInfo, accountNumber: String, config: BankConfig) {
        Log.d(TAG, "Handling window change, retry: $retryCount")

        // First, try to find the account input field
        val accountField = findAccountInputField(rootNode, config)

        if (accountField != null) {
            Log.d(TAG, "Found account field, attempting to fill")
            val filled = fillAccountNumber(accountField, accountNumber)

            if (filled) {
                Log.d(TAG, "Successfully filled account number!")
                showToast("Account number filled successfully!")

                // Try to click confirm/next button
                findAndClickConfirmButton(rootNode, config)

                operationCallback?.invoke(true)
                clearPendingOperation()
                retryCount = 0
                return
            } else {
                Log.w(TAG, "Found field but failed to fill, will retry")
            }
        } else {
            Log.d(TAG, "Account field not found yet, retrying...")

            // Try to navigate to transfer screen if we're on the main menu
            val transferButton = findTransferButton(rootNode, config)
            if (transferButton != null && retryCount < 5) {
                Log.d(TAG, "Found transfer button, clicking it")
                transferButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // Schedule retry if we haven't exceeded max attempts
        if (retryCount < MAX_RETRY_ATTEMPTS && pendingAccountNumber != null) {
            retryCount++
            handler.postDelayed({
                rootInActiveWindow?.let {
                    handleWindowChange(it, accountNumber, config)
                }
            }, RETRY_DELAY_MS)
        } else if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts reached, giving up")
            showToast("Could not auto-fill. Please enter manually.")
            operationCallback?.invoke(false)
            clearPendingOperation()
            retryCount = 0
        }

        // Set timeout
        timeoutRunnable = Runnable {
            if (pendingAccountNumber != null) {
                Log.e(TAG, "Operation timed out")
                showToast("Auto-fill timed out. Please enter manually.")
                operationCallback?.invoke(false)
                clearPendingOperation()
                retryCount = 0
            }
        }
        handler.postDelayed(timeoutRunnable!!, FIELD_FILL_TIMEOUT_MS)
    }

    private fun findAccountInputField(rootNode: AccessibilityNodeInfo, config: BankConfig): AccessibilityNodeInfo? {
        val allEditTexts = mutableListOf<AccessibilityNodeInfo>()
        collectAllEditTexts(rootNode, allEditTexts)

        Log.d(TAG, "Found ${allEditTexts.size} EditText fields")

        // Strategy 1: Find by hint/content description matching account-related text
        for (editText in allEditTexts) {
            val hint = editText.hintText?.toString()?.lowercase() ?: ""
            val contentDesc = editText.contentDescription?.toString()?.lowercase() ?: ""
            val nodeText = editText.text?.toString()?.lowercase() ?: ""
            val label = getLabelForNode(editText)?.lowercase() ?: ""

            for (hintText in config.accountFieldHints) {
                if (hint.contains(hintText) ||
                    contentDesc.contains(hintText) ||
                    nodeText.contains(hintText) ||
                    label.contains(hintText)) {
                    Log.d(TAG, "Found field by hint: $hintText (hint=$hint, desc=$contentDesc, label=$label)")
                    return editText
                }
            }
        }

        // Strategy 2: Find EditText near text saying "Account Number" or similar labels
        for (editText in allEditTexts) {
            val label = getLabelForNode(editText)?.lowercase() ?: ""
            for (hintText in config.accountFieldHints) {
                if (label.contains(hintText)) {
                    Log.d(TAG, "Found field by nearby label: $label")
                    return editText
                }
            }
        }

        // Strategy 3: If we only have one empty EditText on the screen, it's likely the account field
        val emptyEditTexts = allEditTexts.filter { it.text.isNullOrEmpty() && !it.isFocused }
        if (emptyEditTexts.size == 1) {
            Log.d(TAG, "Only one empty EditText found, assuming it's the account field")
            return emptyEditTexts.first()
        }

        // Strategy 4: Find focused or focusable EditText
        val focusedEditText = allEditTexts.firstOrNull { it.isFocused }
        if (focusedEditText != null) {
            Log.d(TAG, "Found focused EditText")
            return focusedEditText
        }

        // Strategy 5: Return the first empty EditText as last resort
        val firstEmpty = allEditTexts.firstOrNull { it.text.isNullOrEmpty() }
        if (firstEmpty != null) {
            Log.d(TAG, "Using first empty EditText as fallback")
            return firstEmpty
        }

        // Strategy 6: Return the first EditText at all
        return allEditTexts.firstOrNull()
    }

    private fun fillAccountNumber(node: AccessibilityNodeInfo, accountNumber: String): Boolean {
        Log.d(TAG, "Filling account number: $accountNumber")

        // Strategy A: Use ACTION_SET_TEXT (most reliable when supported)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, accountNumber)
        }
        val setTextResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "ACTION_SET_TEXT result: $setTextResult")

        if (setTextResult) {
            // Verify it was actually set
            val currentText = node.text?.toString() ?: ""
            if (currentText == accountNumber) {
                return true
            }
        }

        // Strategy B: Focus the field and type character by character
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Clear existing text first
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

        // Type character by character with small delays
        for (char in accountNumber) {
            val charArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, char.toString())
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, charArgs)
        }

        // Verify
        val finalText = node.text?.toString() ?: ""
        return finalText == accountNumber || finalText.contains(accountNumber)
    }

    private fun findAndClickConfirmButton(rootNode: AccessibilityNodeInfo, config: BankConfig) {
        val allClickables = mutableListOf<AccessibilityNodeInfo>()
        collectAllClickables(rootNode, allClickables)

        Log.d(TAG, "Found ${allClickables.size} clickable elements")

        for (button in allClickables) {
            val text = button.text?.toString()?.lowercase() ?: ""
            val contentDesc = button.contentDescription?.toString()?.lowercase() ?: ""

            for (confirmText in config.confirmButtonTexts) {
                if (text.contains(confirmText) || contentDesc.contains(confirmText)) {
                    Log.d(TAG, "Clicking confirm button: '$text' / '$contentDesc'")
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }
    }

    private fun findTransferButton(rootNode: AccessibilityNodeInfo, config: BankConfig): AccessibilityNodeInfo? {
        val allClickables = mutableListOf<AccessibilityNodeInfo>()
        collectAllClickables(rootNode, allClickables)

        for (button in allClickables) {
            val text = button.text?.toString()?.lowercase() ?: ""
            val contentDesc = button.contentDescription?.toString()?.lowercase() ?: ""

            for (transferText in config.transferMenuTexts) {
                if (text == transferText || contentDesc == transferText ||
                    text.contains(transferText) || contentDesc.contains(transferText)) {
                    Log.d(TAG, "Found transfer button: '$text'")
                    return button
                }
            }
        }
        return null
    }

    private fun getLabelForNode(node: AccessibilityNodeInfo): String? {
        // Try to find a label text view that is visually near this node
        val labelNode = node.labeledBy
        return labelNode?.text?.toString()
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

    private fun collectAllClickables(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && (node.className?.toString()?.contains("Button") == true ||
                    node.className?.toString()?.contains("TextView") == true ||
                    node.className?.toString()?.contains("View") == true)) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAllClickables(child, result)
            }
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        clearPendingOperation()
        retryCount = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        clearPendingOperation()
        retryCount = 0
        Log.d(TAG, "Service destroyed")
    }
}
