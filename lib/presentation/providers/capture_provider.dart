import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/errors/exceptions.dart';
import '../../domain/entities/bank_entities.dart';
import '../../services/ocr_service.dart';
import '../../services/accessibility_service.dart';

// OCR Service Provider
final ocrServiceProvider = Provider<OcrService>((ref) {
  final service = OcrService();
  ref.onDispose(() => service.dispose());
  return service;
});

// Capture Image State
final captureImageProvider = StateNotifierProvider<CaptureNotifier, AsyncValue<File?>>((ref) {
  return CaptureNotifier(ref.read(ocrServiceProvider));
});

class CaptureNotifier extends StateNotifier<AsyncValue<File?>> {
  final OcrService _ocrService;
  
  CaptureNotifier(this._ocrService) : super(const AsyncValue.data(null));
  
  Future<void> captureFromCamera() async {
    state = const AsyncValue.loading();
    try {
      final image = await _ocrService.captureImage();
      state = AsyncValue.data(image);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> pickFromGallery() async {
    state = const AsyncValue.loading();
    try {
      final image = await _ocrService.pickImageFromGallery();
      state = AsyncValue.data(image);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  void clear() {
    state = const AsyncValue.data(null);
  }
}

// OCR Processing State
final ocrResultProvider = StateNotifierProvider<OcrResultNotifier, AsyncValue<List<ExtractedAccount>>>((ref) {
  return OcrResultNotifier(ref.read(ocrServiceProvider));
});

class OcrResultNotifier extends StateNotifier<AsyncValue<List<ExtractedAccount>>> {
  final OcrService _ocrService;
  
  OcrResultNotifier(this._ocrService) : super(const AsyncValue.data([]));
  
  Future<void> processImage(File imageFile) async {
    state = const AsyncValue.loading();
    try {
      final accounts = await _ocrService.processImage(imageFile);
      state = AsyncValue.data(accounts);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  void clear() {
    state = const AsyncValue.data([]);
  }
}

// Selected Account Provider
final selectedAccountProvider = StateProvider<ExtractedAccount?>((ref) => null);

// Manual Account Number Provider
final manualAccountProvider = StateProvider<String>((ref) => '');

// Bank Apps Provider
final bankAppsProvider = StateNotifierProvider<BankAppsNotifier, AsyncValue<List<BankApp>>>((ref) {
  return BankAppsNotifier();
});

class BankAppsNotifier extends StateNotifier<AsyncValue<List<BankApp>>> {
  BankAppsNotifier() : super(const AsyncValue.loading());
  
  Future<void> loadInstalledApps() async {
    try {
      final installedApps = await AccessibilityService.getInstalledBankApps();
      
      final List<BankApp> apps = installedApps.map((app) => BankApp(
        packageName: app['packageName'] as String,
        name: app['name'] as String,
        isInstalled: true,
      )).toList();
      
      state = AsyncValue.data(apps);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  // Fallback: use all supported bank apps (user might need to install them)
  void loadAllSupportedApps() {
    final supportedApps = <BankApp>[
      const BankApp(packageName: 'com.gtbank.main', name: 'GTBank'),
      const BankApp(packageName: 'com.accessbank.accessmobile', name: 'Access Bank'),
      const BankApp(packageName: 'com.zenith.bank', name: 'Zenith Bank'),
      const BankApp(packageName: 'ng.com.firstmobilebusiness.android', name: 'First Bank'),
      const BankApp(packageName: 'com.uba.mobile', name: 'UBA'),
      const BankApp(packageName: 'team.opay.pay', name: 'Opay'),
      const BankApp(packageName: 'com.kudabank.app', name: 'Kuda'),
      const BankApp(packageName: 'com.palmpay.app', name: 'PalmPay'),
      const BankApp(packageName: 'com.teamapt.monnify', name: 'Moniepoint'),
      const BankApp(packageName: 'com.sterlingbankmobileapp', name: 'Sterling Bank'),
    ];
    state = AsyncValue.data(supportedApps);
  }
}

// Selected Bank App Provider
final selectedBankAppProvider = StateProvider<BankApp?>((ref) => null);

// Accessibility Service Status
final accessibilityStatusProvider = FutureProvider<bool>((ref) async {
  return await AccessibilityService.isAccessibilityEnabled();
});

// Auto-fill Operation State
final autoFillProvider = StateNotifierProvider<AutoFillNotifier, AsyncValue<void>>((ref) {
  return AutoFillNotifier();
});

class AutoFillNotifier extends StateNotifier<AsyncValue<void>> {
  AutoFillNotifier() : super(const AsyncValue.data(null));
  
  Future<void> autoFillAccount({
    required String accountNumber,
    required String packageName,
  }) async {
    state = const AsyncValue.loading();
    try {
      await AccessibilityService.launchBankApp(
        accountNumber: accountNumber,
        packageName: packageName,
      );
      state = const AsyncValue.data(null);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}