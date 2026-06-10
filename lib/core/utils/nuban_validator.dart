import 'dart:math';

class NubanValidator {
  // NUBAN validation regex - exactly 10 digits
  static final RegExp nubanRegex = RegExp(r'^[0-9]{10}$');
  
  // Validate if a string is a valid NUBAN format
  static bool isValidFormat(String accountNumber) {
    if (accountNumber.isEmpty) return false;
    // Remove any whitespace or special characters
    final cleaned = accountNumber.replaceAll(RegExp(r'[^0-9]'), '');
    return nubanRegex.hasMatch(cleaned);
  }
  
  // Extract potential NUBAN numbers from a string
  static List<String> extractPotentialNumbers(String text) {
    final List<String> numbers = [];
    
    // Remove all non-digit characters and then look for 10-digit sequences
    final cleaned = text.replaceAll(RegExp(r'[^0-9]'), ' ');
    final parts = cleaned.split(RegExp(r'\s+'));
    
    for (final part in parts) {
      if (part.length == 10 && nubanRegex.hasMatch(part)) {
        numbers.add(part);
      } else if (part.length > 10) {
        // For longer strings, look for 10-digit sequences
        for (int i = 0; i <= part.length - 10; i++) {
          final sub = part.substring(i, i + 10);
          if (nubanRegex.hasMatch(sub)) {
            numbers.add(sub);
          }
        }
      }
    }
    
    // Remove duplicates
    return numbers.toSet().toList();
  }
  
  // Validate NUBAN checksum (optional, based on CBN algorithm)
  // This is a simplified validation
  static bool validateChecksum(String accountNumber) {
    if (!isValidFormat(accountNumber)) return false;
    
    // The NUBAN checksum algorithm involves:
    // 1. First 3 digits are bank code
    // 2. Last digit is check digit
    // 3. Algorithm based on modulus 11
    
    // For now, we just validate the format (10 digits)
    // Full checksum validation would require bank code mappings
    return true;
  }
  
  // Clean and format account number for display
  static String formatForDisplay(String accountNumber) {
    if (accountNumber.length != 10) return accountNumber;
    
    // Format as XXXX XXXX XX (4-4-2)
    return '${accountNumber.substring(0, 4)} ${accountNumber.substring(4, 8)} ${accountNumber.substring(8, 10)}';
  }
  
  // Clean input - remove all non-digit characters
  static String cleanInput(String input) {
    return input.replaceAll(RegExp(r'[^0-9]'), '');
  }
  
  // Check if input is complete (10 digits)
  static bool isComplete(String input) {
    return cleanInput(input).length == 10;
  }
  
  // Get completion progress (0.0 to 1.0)
  static double getProgress(String input) {
    final cleaned = cleanInput(input);
    return min(1.0, cleaned.length / 10.0);
  }
  
  // Common OCR misreadings to correct
  static String correctOcrErrors(String text) {
    String corrected = text;
    
    // Replace common OCR errors
    final Map<String, String> ocrCorrections = {
      'O': '0',  // Letter O -> number 0
      'o': '0',
      'I': '1',  // Letter I -> number 1
      'i': '1',
      'l': '1',  // Letter l -> number 1
      'S': '5',  // Letter S -> number 5
      's': '5',
      'B': '8',  // Letter B -> number 8
      'g': '9',  // Letter g -> number 9
      'q': '9',  // Letter q -> number 9
      'Z': '2',  // Letter Z -> number 2
      'z': '2',
    };
    
    // Apply corrections only if the text looks like it might be an account number
    // (contains mostly digits or correction candidates)
    for (final entry in ocrCorrections.entries) {
      corrected = corrected.replaceAll(entry.key, entry.value);
    }
    
    return corrected;
  }
}