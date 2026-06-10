import 'dart:async';
import 'dart:io';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import 'package:image/image.dart' as img;
import '../core/errors/exceptions.dart';
import '../core/utils/nuban_validator.dart';
import '../domain/entities/bank_entities.dart';

class OcrService {
  final TextRecognizer _textRecognizer = TextRecognizer(script: TextRecognitionScript.latin);
  final ImagePicker _imagePicker = ImagePicker();
  
  // Capture image from camera
  Future<File> captureImage() async {
    try {
      final XFile? image = await _imagePicker.pickImage(
        source: ImageSource.camera,
        maxWidth: 1080,
        maxHeight: 1920,
        imageQuality: 85,
      );
      
      if (image == null) {
        throw CaptureException('No image captured');
      }
      
      return File(image.path);
    } catch (e) {
      throw CaptureException('Camera capture failed: $e');
    }
  }
  
  // Pick image from gallery
  Future<File> pickImageFromGallery() async {
    try {
      final XFile? image = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        maxWidth: 1080,
        maxHeight: 1920,
        imageQuality: 85,
      );
      
      if (image == null) {
        throw CaptureException('No image selected');
      }
      
      return File(image.path);
    } catch (e) {
      throw CaptureException('Gallery selection failed: $e');
    }
  }
  
  // Process image and extract account numbers
  Future<List<ExtractedAccount>> processImage(File imageFile) async {
    try {
      // Preprocess image for better OCR
      final processedImage = await _preprocessImage(imageFile);
      
      // Create InputImage from file
      final inputImage = InputImage.fromFile(processedImage);
      
      // Perform OCR
      final RecognizedText recognizedText = await _textRecognizer.processImage(inputImage);
      
      // Extract all text
      final String fullText = recognizedText.text;
      
      if (fullText.isEmpty) {
        throw OcrException('No text detected in image');
      }
      
      // Extract potential account numbers
      final List<String> potentialNumbers = NubanValidator.extractPotentialNumbers(fullText);
      
      if (potentialNumbers.isEmpty) {
        throw OcrException('No valid account number found');
      }
      
      // Create ExtractedAccount objects
      final List<ExtractedAccount> accounts = potentialNumbers.map((number) {
        // Try to find the text block containing this number for confidence/bounding box
        final matchingBlock = recognizedText.blocks.firstWhere(
          (block) => block.text.contains(number),
          orElse: () => recognizedText.blocks.first,
        );
        
        return ExtractedAccount(
          accountNumber: number,
          rawText: matchingBlock.text,
          isValid: NubanValidator.isValidFormat(number),
          boundingBox: [
            matchingBlock.boundingBox.left.toString(),
            matchingBlock.boundingBox.top.toString(),
            matchingBlock.boundingBox.right.toString(),
            matchingBlock.boundingBox.bottom.toString(),
          ],
        );
      }).toList();
      
      return accounts;
      
    } catch (e) {
      if (e is OcrException) rethrow;
      throw OcrException('OCR processing failed: $e');
    }
  }
  
  // Preprocess image for better OCR results
  Future<File> _preprocessImage(File imageFile) async {
    try {
      // Read image
      final bytes = await imageFile.readAsBytes();
      final originalImage = img.decodeImage(bytes);
      
      if (originalImage == null) {
        return imageFile; // Return original if can't decode
      }
      
      // Convert to grayscale for better OCR
      final grayscale = img.grayscale(originalImage);
      
      // Increase contrast
      final contrasted = img.adjustColor(grayscale, contrast: 1.2);
      
      // Save processed image to temp directory
      final tempDir = await getTemporaryDirectory();
      final processedPath = path.join(tempDir.path, 'processed_${DateTime.now().millisecondsSinceEpoch}.jpg');
      final processedFile = File(processedPath);
      await processedFile.writeAsBytes(img.encodeJpg(contrasted, quality: 85));
      
      return processedFile;
      
    } catch (e) {
      // If preprocessing fails, return original
      return imageFile;
    }
  }
  
  // Quick scan - returns the first valid account number found
  Future<ExtractedAccount?> quickScan(File imageFile) async {
    final accounts = await processImage(imageFile);
    if (accounts.isEmpty) return null;
    
    // Return first valid account, or first account if none are valid
    return accounts.firstWhere(
      (acc) => acc.isValid,
      orElse: () => accounts.first,
    );
  }
  
  // Dispose resources
  void dispose() {
    _textRecognizer.close();
  }
}