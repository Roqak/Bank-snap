import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/constants/app_constants.dart';

final onboardingCompletedProvider = FutureProvider<bool>((ref) async {
  final prefs = await SharedPreferences.getInstance();
  return prefs.getBool(AppConstants.onboardingCompleted) ?? false;
});

final onboardingStateProvider = StateNotifierProvider<OnboardingNotifier, int>((ref) {
  return OnboardingNotifier();
});

class OnboardingNotifier extends StateNotifier<int> {
  OnboardingNotifier() : super(0);
  
  void nextPage() {
    if (state < AppConstants.onboardingPageCount - 1) {
      state = state + 1;
    }
  }
  
  void previousPage() {
    if (state > 0) {
      state = state - 1;
    }
  }
  
  void setPage(int page) {
    if (page >= 0 && page < AppConstants.onboardingPageCount) {
      state = page;
    }
  }
  
  Future<void> completeOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(AppConstants.onboardingCompleted, true);
  }
}