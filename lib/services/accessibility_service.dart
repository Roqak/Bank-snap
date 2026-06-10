import 'dart:async';
import 'package:flutter/services.dart';
import '../core/constants/app_constants.dart';
import '../core/errors/exceptions.dart';

class AccessibilityService {
  static const MethodChannel _channel = MethodChannel(AppConstants.platformChannel);
  
  // Launch a bank app and auto-fill the account number
  static Future<bool> launchBankApp({
    required String accountNumber,
    required String packageName,
  }) async {
    try {
      final result = await _channel.invokeMethod(AppConstants.methodLaunchBank, {
        'accountNumber': accountNumber,
        'packageName': packageName,
      });
      
      return result == true;
    } on PlatformException catch (e) {
      throw AccessibilityException(
        e.message ?? 'Failed to launch bank app',
        code: e.code,
        details: e.details,
      );
    } catch (e) {
      throw AccessibilityException('Failed to launch bank app: $e');
    }
  }
  
  // Check if accessibility service is enabled
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final result = await _channel.invokeMethod(AppConstants.methodCheckAccessibility);
      return result == true;
    } on PlatformException catch (e) {
      throw AccessibilityException(
        e.message ?? 'Failed to check accessibility status',
        code: e.code,
      );
    } catch (e) {
      return false;
    }
  }
  
  // Open accessibility settings
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod(AppConstants.methodOpenAccessibilitySettings);
    } on PlatformException catch (e) {
      throw AccessibilityException(
        e.message ?? 'Failed to open accessibility settings',
        code: e.code,
      );
    } catch (e) {
      throw AccessibilityException('Failed to open accessibility settings: $e');
    }
  }
  
  // Stop accessibility service operation
  static Future<void> stopAccessibilityService() async {
    try {
      await _channel.invokeMethod(AppConstants.methodStopAccessibility);
    } catch (e) {
      // Ignore errors on stop
    }
  }
  
  // Get installed bank apps
  static Future<List<Map<String, dynamic>>> getInstalledBankApps() async {
    try {
      final result = await _channel.invokeMethod('getInstalledBankApps');
      if (result is List) {
        return result.map((item) => Map<String, dynamic>.from(item)).toList();
      }
      return [];
    } catch (e) {
      return [];
    }
  }
}