import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/constants/app_constants.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/nuban_validator.dart';
import '../../domain/entities/bank_entities.dart';
import '../../services/accessibility_service.dart';
import '../providers/capture_provider.dart';
import 'accessibility_settings_screen.dart';

class BankPickerScreen extends ConsumerStatefulWidget {
  const BankPickerScreen({super.key});

  @override
  ConsumerState<BankPickerScreen> createState() => _BankPickerScreenState();
}

class _BankPickerScreenState extends ConsumerState<BankPickerScreen> {
  bool _isLoading = false;
  String? _selectedBankPackage;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadBankApps();
  }

  Future<void> _loadBankApps() async {
    final bankAppsNotifier = ref.read(bankAppsProvider.notifier);
    await bankAppsNotifier.loadInstalledApps();

    // If no apps detected, fallback to all supported apps
    final state = ref.read(bankAppsProvider);
    state.whenData((apps) {
      if (apps.isEmpty) {
        bankAppsNotifier.loadAllSupportedApps();
      }
    });
  }

  Future<void> _selectBank(BankApp bank) async {
    setState(() {
      _selectedBankPackage = bank.packageName;
      _errorMessage = null;
    });

    // Check if accessibility service is enabled
    final isEnabled = await AccessibilityService.isAccessibilityEnabled();
    if (!isEnabled) {
      if (mounted) {
        _showAccessibilityDialog();
      }
      return;
    }

    await _launchBankApp(bank);
  }

  Future<void> _launchBankApp(BankApp bank) async {
    final account = ref.read(selectedAccountProvider);
    if (account == null) return;

    setState(() {
      _isLoading = true;
    });

    try {
      await AccessibilityService.launchBankApp(
        accountNumber: account.accountNumber,
        packageName: bank.packageName,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Launching ${bank.name}...'),
            backgroundColor: AppTheme.successColor,
          ),
        );
        Navigator.pop(context); // Go back to result screen
        Navigator.pop(context); // Go back to home screen
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  void _showAccessibilityDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.accessibility, color: AppTheme.primaryColor),
            SizedBox(width: 8),
            Text('Enable Accessibility Service'),
          ],
        ),
        content: const Text(
          'BankSnap needs Accessibility Service permission to automatically fill account numbers in your bank app. '
          'This is required for the auto-fill feature to work.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(context);
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => const AccessibilitySettingsScreen(),
                ),
              );
            },
            child: const Text('Enable'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bankAppsState = ref.watch(bankAppsProvider);
    final account = ref.watch(selectedAccountProvider);
    final accountNumber = account?.accountNumber ?? '';
    final formattedNumber = NubanValidator.formatForDisplay(accountNumber);

    return Scaffold(
      backgroundColor: AppTheme.scaffoldBackground,
      appBar: AppBar(
        title: const Text('Select Bank App'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppTheme.defaultPadding),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Account number display
              Container(
                padding: const EdgeInsets.all(AppTheme.defaultPadding),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                  boxShadow: AppTheme.defaultShadow,
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(
                        Icons.account_balance_wallet,
                        color: AppTheme.primaryColor,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Account Number',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppTheme.textSecondary,
                            ),
                          ),
                          Text(
                            formattedNumber,
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.textPrimary,
                              letterSpacing: 1,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.edit, size: 20),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Bank apps grid
              Text(
                'Select Your Bank App',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                'BankSnap will launch the app and auto-fill the account number',
                style: TextStyle(
                  fontSize: 14,
                  color: AppTheme.textSecondary,
                ),
              ),
              const SizedBox(height: 16),

              // Loading or error state
              if (_isLoading)
                const Center(
                  child: Column(
                    children: [
                      CircularProgressIndicator(
                        valueColor: AlwaysStoppedAnimation<Color>(AppTheme.primaryColor),
                      ),
                      SizedBox(height: 16),
                      Text('Launching bank app...'),
                    ],
                  ),
                )
              else if (_errorMessage != null)
                Container(
                  padding: const EdgeInsets.all(AppTheme.defaultPadding),
                  decoration: BoxDecoration(
                    color: AppTheme.errorColor.withOpacity(0.05),
                    borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: AppTheme.errorColor,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: TextStyle(color: AppTheme.errorColor),
                        ),
                      ),
                    ],
                  ),
                )
              else
                Expanded(
                  child: bankAppsState.when(
                    data: (apps) => _buildBankGrid(apps),
                    loading: () => const Center(
                      child: CircularProgressIndicator(
                        valueColor: AlwaysStoppedAnimation<Color>(AppTheme.primaryColor),
                      ),
                    ),
                    error: (error, _) => Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.error_outline,
                            size: 48,
                            color: AppTheme.errorColor.withOpacity(0.5),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'Error loading bank apps',
                            style: TextStyle(color: AppTheme.textSecondary),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: _loadBankApps,
                            child: const Text('Retry'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBankGrid(List<BankApp> apps) {
    if (apps.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.account_balance_outlined,
              size: 64,
              color: AppTheme.textSecondary.withOpacity(0.5),
            ),
            const SizedBox(height: 16),
            Text(
              AppConstants.errorNoBankApps,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Supported apps: GTBank, Access Bank, Zenith, First Bank, UBA, Opay, Kuda, PalmPay, Moniepoint, Sterling',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppTheme.textHint,
                fontSize: 12,
              ),
            ),
          ],
        ),
      );
    }

    return GridView.builder(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 1.2,
        crossAxisSpacing: 12,
        mainAxisSpacing: 12,
      ),
      itemCount: apps.length,
      itemBuilder: (context, index) {
        final bank = apps[index];
        final isSelected = _selectedBankPackage == bank.packageName;

        return Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => _selectBank(bank),
            borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
            child: Container(
              decoration: BoxDecoration(
                color: isSelected
                    ? AppTheme.primaryColor.withOpacity(0.1)
                    : Colors.white,
                borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                boxShadow: AppTheme.defaultShadow,
                border: Border.all(
                  color: isSelected
                      ? AppTheme.primaryColor
                      : AppTheme.borderColor,
                  width: isSelected ? 2 : 1,
                ),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Bank icon placeholder
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppTheme.primaryColor.withOpacity(0.2)
                          : AppTheme.primaryColor.withOpacity(0.05),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(
                      Icons.account_balance,
                      color: isSelected
                          ? AppTheme.primaryColor
                          : AppTheme.primaryColor.withOpacity(0.5),
                      size: 28,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    bank.name,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: isSelected
                          ? AppTheme.primaryColor
                          : AppTheme.textPrimary,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  if (!bank.isInstalled)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        'Not installed',
                        style: TextStyle(
                          fontSize: 11,
                          color: AppTheme.warningColor,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
