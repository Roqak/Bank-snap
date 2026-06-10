package com.banksnap.banksnap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

class BanksnapAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "BankSnapAccessibility"
        private const val CHANNEL_NAME = "com.banksnap/accessibility"
        
        // Supported bank app package names and their account input field identifiers
        private val BANK_APP_CONFIGS = mapOf(
            "com.gtbank.main" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "enter account number"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.gtbank.main"
            ),
            "com.accessbank.accessmobile" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.accessbank.accessmobile"
            ),
            "com.zenith.bank" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.zenith.bank"
            ),
            "ng.com.firstmobilebusiness.android" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "ng.com.firstmobilebusiness.android"
            ),
            "com.uba.mobile" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.uba.mobile"
            ),
            "team.opay.pay" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber", "recipient_account"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account", "recipient"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue", "verify"),
                packageName = "team.opay.pay"
            ),
            "com.kudabank.app" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.kudabank.app"
            ),
            "com.palmpay.app" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.palmpay.app"
            ),
            "com.teamapt.monnify" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.teamapt.monnify"
            ),
            "com.sterlingbankmobileapp" to BankConfig(
                accountFieldIds = listOf("account_number", "edit_text", "accountNumber"),
                accountFieldDescriptions = listOf("account number", "account", "beneficiary account"),
                confirmButtonIds = listOf("confirm", "next", "proceed", "continue"),
                packageName = "com.sterlingbankmobileapp"
            )
        )
        
        var isServiceRunning = false
            private set
        
        private var pendingAccountNumber: String? = null
        private var pendingPackageName: String? = null
        private var operationCallback: ((Boolean) -> Unit)? = null
        
        fun setPendingOperation(accountNumber: String, packageName: String, callback: (Boolean) -> Unit) {
            pendingAccountNumber = accountNumber
            pendingPackageName = packageName
            operationCallback = callback
        }
        
        fun clearPendingOperation() {
            pendingAccountNumber = null
            pendingPackageName = null
            operationCallback = null
        }
    }
    
    data class BankConfig(
        val accountFieldIds: List<String>,
        val accountFieldDescriptions: List<String>,
        val confirmButtonIds: List<String>,
        val packageName: String
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "Accessibility Service Connected")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Check if this is a supported bank app
        if (!BANK_APP_CONFIGS.containsKey(packageName)) return
        
        // Check if we have a pending operation for this app
        if (pendingPackageName != packageName) return
        
        val accountNumber = pendingAccountNumber ?: return
        val config = BANK_APP_CONFIGS[packageName] ?: return
        
        // Process the event
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                rootInActiveWindow?.let { rootNode ->
                    try {
                        fillAccountNumber(rootNode, accountNumber, config)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error filling account number: ${e.message}")
                        operationCallback?.invoke(false)
                        clearPendingOperation()
                    }
                }
            }
        }
    }
    
    private fun fillAccountNumber(rootNode: AccessibilityNodeInfo, accountNumber: String, config: BankConfig) {
        // Find the account number input field
        val accountField = findAccountField(rootNode, config)
        
        if (accountField != null) {
            // Set the text
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, accountNumber)
            }
            
            val success = accountField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            if (success) {
                Log.d(TAG, "Successfully filled account number: $accountNumber")
                
                // Find and click the confirm/next button if available
                val confirmButton = findConfirmButton(rootNode, config)
                confirmButton?.let { button ->
                    Handler(Looper.getMainLooper()).postDelayed({
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }, 500)
                }
                
                operationCallback?.invoke(true)
                clearPendingOperation()
            } else {
                Log.e(TAG, "Failed to set text on account field")
                operationCallback?.invoke(false)
                clearPendingOperation()
            }
        } else {
            // Field not found yet, might need to wait for the screen to fully load
            // The callback will be called when the field is found or timeout occurs
        }
    }
    
    private fun findAccountField(rootNode: AccessibilityNodeInfo, config: BankConfig): AccessibilityNodeInfo? {
        // Try to find by resource ID
        for (id in config.accountFieldIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("${config.packageName}:id/$id")
            if (nodes.isNotEmpty()) {
                return nodes.first()
            }
        }
        
        // Try to find by content description
        val field = findNodeByContentDescription(rootNode, config.accountFieldDescriptions)
        if (field != null) return field
        
        // Try to find by class name (EditText) and hints
        return findNodeByClassAndHint(rootNode, "android.widget.EditText", "account")
    }
    
    private fun findConfirmButton(rootNode: AccessibilityNodeInfo, config: BankConfig): AccessibilityNodeInfo? {
        for (id in config.confirmButtonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("${config.packageName}:id/$id")
            if (nodes.isNotEmpty()) {
                return nodes.first()
            }
        }
        
        return findNodeByText(rootNode, config.confirmButtonIds)
    }
    
    private fun findNodeByContentDescription(
        rootNode: AccessibilityNodeInfo, 
        descriptions: List<String>
    ): AccessibilityNodeInfo? {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            
            val contentDesc = child.contentDescription?.toString()?.toLowerCase() ?: ""
            if (descriptions.any { contentDesc.contains(it) }) {
                return child
            }
            
            val found = findNodeByContentDescription(child, descriptions)
            if (found != null) return found
        }
        return null
    }
    
    private fun findNodeByClassAndHint(
        rootNode: AccessibilityNodeInfo,
        className: String,
        hintText: String
    ): AccessibilityNodeInfo? {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            
            if (child.className?.toString() == className) {
                val hint = child.hintText?.toString()?.toLowerCase() ?: ""
                val text = child.text?.toString()?.toLowerCase() ?: ""
                if (hint.contains(hintText) || text.contains(hintText)) {
                    return child
                }
            }
            
            val found = findNodeByClassAndHint(child, className, hintText)
            if (found != null) return found
        }
        return null
    }
    
    private fun findNodeByText(rootNode: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            
            val nodeText = child.text?.toString()?.toLowerCase() ?: ""
            if (texts.any { nodeText.contains(it) }) {
                return child
            }
            
            val found = findNodeByText(child, texts)
            if (found != null) return found
        }
        return null
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
        clearPendingOperation()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        clearPendingOperation()
        Log.d(TAG, "Accessibility Service Destroyed")
    }
    
    // Check if the service is enabled
    fun isServiceEnabled(): Boolean {
        return isServiceRunning
    }
}