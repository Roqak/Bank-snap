import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/constants/app_constants.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/nuban_validator.dart';
import '../../domain/entities/bank_entities.dart';
import '../providers/capture_provider.dart';
import 'bank_picker_screen.dart';

class ResultScreen extends ConsumerStatefulWidget {
  final File image;

  const ResultScreen({super.key, required this.image});

  @override
  ConsumerState<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends ConsumerState<ResultScreen> {
  final TextEditingController _accountController = TextEditingController();
  bool _isProcessing = true;
  List<ExtractedAccount> _accounts = [];
  int _selectedIndex = 0;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _processImage();
  }

  Future<void> _processImage() async {
    try {
      final ocrService = ref.read(ocrServiceProvider);
      final accounts = await ocrService.processImage(widget.image);

      if (mounted) {
        setState(() {
          _accounts = accounts;
          _isProcessing = false;
          if (accounts.isNotEmpty) {
            _accountController.text = accounts.first.accountNumber;
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isProcessing = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  @override
  void dispose() {
    _accountController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isValid = NubanValidator.isValidFormat(_accountController.text);

    return Scaffold(
      backgroundColor: AppTheme.scaffoldBackground,
      appBar: AppBar(
        title: const Text('Account Number'),
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
              // Image preview
              ClipRRect(
                borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                child: Container(
                  height: 200,
                  width: double.infinity,
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                  ),
                  child: Image.file(
                    widget.image,
                    fit: BoxFit.cover,
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Processing or Results
              if (_isProcessing)
                const Center(
                  child: Column(
                    children: [
                      CircularProgressIndicator(
                        valueColor: AlwaysStoppedAnimation<Color>(AppTheme.primaryColor),
                      ),
                      SizedBox(height: 16),
                      Text('Processing image...'),
                    ],
                  ),
                )
              else if (_errorMessage.isNotEmpty)
                _buildErrorWidget()
              else ...[
                // Account number label
                Text(
                  'Detected Account Number',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),

                // Account number input
                TextField(
                  controller: _accountController,
                  keyboardType: TextInputType.number,
                  maxLength: 10,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: Colors.white,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                      borderSide: BorderSide(
                        color: isValid ? AppTheme.successColor : AppTheme.borderColor,
                      ),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                      borderSide: BorderSide(
                        color: isValid ? AppTheme.successColor : AppTheme.borderColor,
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                      borderSide: const BorderSide(
                        color: AppTheme.primaryColor,
                        width: 2,
                      ),
                    ),
                    prefixIcon: Icon(
                      isValid ? Icons.check_circle : Icons.edit,
                      color: isValid ? AppTheme.successColor : AppTheme.textHint,
                    ),
                    suffixText: '${_accountController.text.length}/10',
                    counterText: '',
                    hintText: 'Enter account number',
                  ),
                  onChanged: (value) {
                    setState(() {});
                  },
                ),

                // Validation indicator
                if (isValid)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Icon(
                          Icons.verified,
                          color: AppTheme.successColor,
                          size: 16,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Valid NUBAN format',
                          style: TextStyle(
                            color: AppTheme.successColor,
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  )
                else if (_accountController.text.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Icon(
                          Icons.error_outline,
                          color: AppTheme.warningColor,
                          size: 16,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Enter a valid 10-digit account number',
                          style: TextStyle(
                            color: AppTheme.warningColor,
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                  ),

                // Multiple detected numbers
                if (_accounts.length > 1) ...[
                  const SizedBox(height: 24),
                  Text(
                    'Other Detected Numbers',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: _accounts.asMap().entries.map((entry) {
                      final index = entry.key;
                      final account = entry.value;
                      return ChoiceChip(
                        label: Text(account.accountNumber),
                        selected: _selectedIndex == index,
                        onSelected: (selected) {
                          if (selected) {
                            setState(() {
                              _selectedIndex = index;
                              _accountController.text = account.accountNumber;
                            });
                          }
                        },
                        selectedColor: AppTheme.primaryColor.withOpacity(0.1),
                        checkmarkColor: AppTheme.primaryColor,
                      );
                    }).toList(),
                  ),
                ],
              ],

              const Spacer(),

              // Action buttons
              if (!_isProcessing && _errorMessage.isEmpty)
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: ElevatedButton(
                    onPressed: isValid
                        ? () => _proceedToBankSelection()
                        : null,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.primaryColor,
                      foregroundColor: Colors.white,
                      disabledBackgroundColor: AppTheme.primaryColor.withOpacity(0.3),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
                      ),
                    ),
                    child: const Text(
                      'Select Bank App',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
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

  Widget _buildErrorWidget() {
    return Container(
      padding: const EdgeInsets.all(AppTheme.largePadding),
      decoration: BoxDecoration(
        color: AppTheme.errorColor.withOpacity(0.05),
        borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
        border: Border.all(
          color: AppTheme.errorColor.withOpacity(0.2),
        ),
      ),
      child: Column(
        children: [
          Icon(
            Icons.error_outline,
            color: AppTheme.errorColor,
            size: 48,
          ),
          const SizedBox(height: 16),
          Text(
            AppConstants.errorOcrFailed,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: AppTheme.errorColor,
              fontSize: 16,
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _accountController,
            keyboardType: TextInputType.number,
            maxLength: 10,
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppTheme.defaultRadius),
              ),
              hintText: 'Enter account number manually',
            ),
            onChanged: (value) => setState(() {}),
          ),
        ],
      ),
    );
  }

  void _proceedToBankSelection() {
    if (_accountController.text.isEmpty) return;

    final account = ExtractedAccount(
      accountNumber: _accountController.text,
      isValid: NubanValidator.isValidFormat(_accountController.text),
    );

    ref.read(selectedAccountProvider.notifier).state = account;

    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const BankPickerScreen(),
      ),
    );
  }
}
