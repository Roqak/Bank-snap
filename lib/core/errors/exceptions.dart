class AppException implements Exception {
  final String message;
  final String? code;
  final dynamic details;
  
  AppException(this.message, {this.code, this.details});
  
  @override
  String toString() => 'AppException: $message (code: $code, details: $details)';
}

class CaptureException extends AppException {
  CaptureException(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}

class OcrException extends AppException {
  OcrException(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}

class ValidationException extends AppException {
  ValidationException(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}

class BankAppException extends AppException {
  BankAppException(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}

class AccessibilityException extends AppException {
  AccessibilityException(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}