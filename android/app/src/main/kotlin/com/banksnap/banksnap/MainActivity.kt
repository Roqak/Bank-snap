package com.banksnap.banksnap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.banksnap/accessibility"
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "launchBankApp" -> {
                    val accountNumber = call.argument<String>("accountNumber")
                    val packageName = call.argument<String>("packageName")
                    
                    if (accountNumber == null || packageName == null) {
                        result.error("INVALID_ARGUMENTS", "Account number and package name are required", null)
                        return@setMethodCallHandler
                    }
                    
                    launchBankApp(accountNumber, packageName, result)
                }
                "checkAccessibilityService" -> {
                    val isEnabled = isAccessibilityServiceEnabled()
                    result.success(isEnabled)
                }
                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }
                "stopAccessibilityService" -> {
                    BanksnapAccessibilityService.clearPendingOperation()
                    result.success(true)
                }
                "getInstalledBankApps" -> {
                    val installedApps = getInstalledBankApps()
                    result.success(installedApps)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    
    private fun launchBankApp(accountNumber: String, packageName: String, result: MethodChannel.Result) {
        try {
            // Check if the bank app is installed
            val packageManager = packageManager
            try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                result.error("APP_NOT_INSTALLED", "Bank app is not installed", null)
                return
            }
            
            // Check if accessibility service is enabled
            if (!isAccessibilityServiceEnabled()) {
                result.error("ACCESSIBILITY_NOT_ENABLED", "Accessibility service is not enabled", null)
                return
            }
            
            // Set up the pending operation for the accessibility service
            BanksnapAccessibilityService.setPendingOperation(accountNumber, packageName) { success ->
                runOnUiThread {
                    if (success) {
                        result.success(true)
                    } else {
                        result.error("FILL_FAILED", "Failed to auto-fill account number", null)
                    }
                }
            }
            
            // Launch the bank app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                
                // Set a timeout for the operation
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (BanksnapAccessibilityService.isServiceRunning) {
                        // Check if operation is still pending
                        // If still pending after timeout, consider it failed
                        result.error("TIMEOUT", "Operation timed out", null)
                    }
                }, 15000) // 15 second timeout
                
            } else {
                result.error("LAUNCH_FAILED", "Could not launch bank app", null)
            }
            
        } catch (e: Exception) {
            result.error("LAUNCH_ERROR", "Error launching bank app: ${e.message}", null)
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        for (service in enabledServices) {
            val serviceInfo = service.resolveInfo.serviceInfo
            if (serviceInfo.packageName == packageName && 
                serviceInfo.name == "com.banksnap.banksnap.BanksnapAccessibilityService") {
                return true
            }
        }
        
        return BanksnapAccessibilityService.isServiceRunning
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    private fun getInstalledBankApps(): List<Map<String, String>> {
        val bankApps = listOf(
            mapOf("packageName" to "com.gtbank.main", "name" to "GTBank"),
            mapOf("packageName" to "com.accessbank.accessmobile", "name" to "Access Bank"),
            mapOf("packageName" to "com.zenith.bank", "name" to "Zenith Bank"),
            mapOf("packageName" to "ng.com.firstmobilebusiness.android", "name" to "First Bank"),
            mapOf("packageName" to "com.uba.mobile", "name" to "UBA"),
            mapOf("packageName" to "team.opay.pay", "name" to "Opay"),
            mapOf("packageName" to "com.kudabank.app", "name" to "Kuda"),
            mapOf("packageName" to "com.palmpay.app", "name" to "PalmPay"),
            mapOf("packageName" to "com.teamapt.monnify", "name" to "Moniepoint"),
            mapOf("packageName" to "com.sterlingbankmobileapp", "name" to "Sterling Bank")
        )
        
        val installedApps = mutableListOf<Map<String, String>>()
        
        for (app in bankApps) {
            val packageName = app["packageName"] ?: continue
            try {
                packageManager.getPackageInfo(packageName, 0)
                installedApps.add(app)
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed, skip
            }
        }
        
        return installedApps
    }
}