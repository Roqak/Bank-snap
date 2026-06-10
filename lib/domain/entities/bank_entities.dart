import 'package:equatable/equatable.dart';

class BankApp extends Equatable {
  final String packageName;
  final String name;
  final String? icon;
  final bool isInstalled;
  
  const BankApp({
    required this.packageName,
    required this.name,
    this.icon,
    this.isInstalled = false,
  });
  
  BankApp copyWith({
    String? packageName,
    String? name,
    String? icon,
    bool? isInstalled,
  }) {
    return BankApp(
      packageName: packageName ?? this.packageName,
      name: name ?? this.name,
      icon: icon ?? this.icon,
      isInstalled: isInstalled ?? this.isInstalled,
    );
  }
  
  @override
  List<Object?> get props => [packageName, name, icon, isInstalled];
}

class ExtractedAccount extends Equatable {
  final String accountNumber;
  final String? rawText;
  final double? confidence;
  final bool isValid;
  final List<String>? boundingBox;
  
  const ExtractedAccount({
    required this.accountNumber,
    this.rawText,
    this.confidence,
    this.isValid = false,
    this.boundingBox,
  });
  
  ExtractedAccount copyWith({
    String? accountNumber,
    String? rawText,
    double? confidence,
    bool? isValid,
    List<String>? boundingBox,
  }) {
    return ExtractedAccount(
      accountNumber: accountNumber ?? this.accountNumber,
      rawText: rawText ?? this.rawText,
      confidence: confidence ?? this.confidence,
      isValid: isValid ?? this.isValid,
      boundingBox: boundingBox ?? this.boundingBox,
    );
  }
  
  @override
  List<Object?> get props => [accountNumber, rawText, confidence, isValid, boundingBox];
}

class CaptureHistory extends Equatable {
  final String id;
  final String accountNumber;
  final String? bankAppName;
  final DateTime capturedAt;
  final String? imagePath;
  
  const CaptureHistory({
    required this.id,
    required this.accountNumber,
    this.bankAppName,
    required this.capturedAt,
    this.imagePath,
  });
  
  @override
  List<Object?> get props => [id, accountNumber, bankAppName, capturedAt, imagePath];
}