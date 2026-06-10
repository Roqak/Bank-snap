class AppConstants {
  // App Info
  static const String appName = 'BankSnap';
  static const String appVersion = '1.0.0';
  
  // NUBAN Validation
  static const int nubanLength = 10;
  static const String nubanRegex = r'^[0-9]{10}$';
  
  // Supported Bank Apps (package names)
  static const Map<String, String> bankApps = {
    'com.gtbank.main': 'GTBank',
    'com.accessbank.accessmobile': 'Access Bank',
    'com.zenith.bank': 'Zenith Bank',
    'ng.com.firstmobilebusiness.android': 'First Bank',
    'com.uba.mobile': 'UBA',
    'team.opay.pay': 'Opay',
    'com.kudabank.app': 'Kuda',
    'com.palmpay.app': 'PalmPay',
    'com.teamapt.monnify': 'Moniepoint',
    'com.sterlingbankmobileapp': 'Sterling Bank',
  };
  
  // SharedPreferences Keys
  static const String onboardingCompleted = 'onboarding_completed';
  static const String lastUsedBank = 'last_used_bank';
  static const String accessibilityEnabled = 'accessibility_enabled';
  
  // Hive Boxes
  static const String historyBox = 'capture_history';
  static const String settingsBox = 'app_settings';
  
  // Platform Channel
  static const String platformChannel = 'com.banksnap/accessibility';
  static const String methodLaunchBank = 'launchBankApp';
  static const String methodCheckAccessibility = 'checkAccessibilityService';
  static const String methodOpenAccessibilitySettings = 'openAccessibilitySettings';
  static const String methodStopAccessibility = 'stopAccessibilityService';
  
  // UI Constants
  static const double maxImageWidth = 1080.0;
  static const double maxImageHeight = 1920.0;
  static const int ocrTimeoutSeconds = 10;
  
  // Error Messages
  static const String errorNoCamera = 'Camera not available. Please use gallery import.';
  static const String errorOcrFailed = 'Could not read account number. Please try again or enter manually.';
  static const String errorNoBankApps = 'No supported bank apps detected. Please install a supported bank app.';
  static const String errorAccessibilityNotEnabled = 'Accessibility service is not enabled. Please enable it in settings.';
  static const String errorInvalidAccount = 'Invalid account number. Please enter a valid 10-digit NUBAN number.';
  static const String errorFillFailed = 'Could not auto-fill account number. Please enter manually.';
  
  // Success Messages
  static const String successOcr = 'Account number detected successfully!';
  static const String successFilled = 'Account number filled successfully!';
  
  // Onboarding
  static const int onboardingPageCount = 3;
  static const String onboardingTitle1 = 'Scan Account Numbers';
  static const String onboardingDesc1 = 'Take a photo of any document containing a bank account number. BankSnap will automatically detect and extract it.';
  static const String onboardingTitle2 = 'NUBAN Validation';
  static const String onboardingDesc2 = 'Every extracted number is validated against the Nigerian NUBAN format to ensure accuracy.';
  static const String onboardingTitle3 = 'Auto-fill Bank Apps';
  static const String onboardingDesc3 = 'Select your bank app and BankSnap will automatically fill the account number for you.';
}