# BankSnap

**AI-Powered Account Number Capture & Bank App Automation**

[![Flutter Version](https://img.shields.io/badge/Flutter-3.27.0-blue.svg)](https://flutter.dev)
[![Dart Version](https://img.shields.io/badge/Dart-3.6.0-blue.svg)](https://dart.dev)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)

BankSnap is a Flutter mobile application that streamlines the process of entering bank account numbers during transfers. Instead of manually typing long account numbers, users snap a photo of any document containing an account number. The app uses on-device OCR to extract the number, validates it against the Nigerian NUBAN format, then lets the user pick a bank app already installed on their device. An Accessibility Service then launches the selected bank app and automatically inputs the account number into the transfer field.

---

## Table of Contents

- [Features](#features)
- [Demo](#demo)
- [Installation](#installation)
- [Usage](#usage)
- [Architecture](#architecture)
- [Supported Banks](#supported-banks)
- [Permissions](#permissions)
- [Privacy](#privacy)
- [Screenshots](#screenshots)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Core Features
- **Camera Capture** - Take photos of documents containing account numbers
- **Gallery Import** - Import images from device storage
- **On-Device OCR** - Uses Google ML Kit Text Recognition (fully offline)
- **NUBAN Validation** - Validates extracted numbers against the 10-digit Nigerian NUBAN format
- **OCR Error Correction** - Automatically corrects common OCR misreadings (O→0, I→1, S→5, etc.)
- **Manual Editing** - Always allows manual correction before submitting to any bank app
- **Installed Bank Detection** - Automatically detects supported Nigerian bank apps
- **Bank App Picker** - Beautiful grid UI to select your bank app
- **Accessibility Auto-Fill** - Android Accessibility Service fills the account number field

### Security & Privacy
- **Fully On-Device** - All OCR processing happens locally, no images or data sent to any server
- **No Analytics** - No analytics SDKs that transmit financial data
- **Scoped Access** - Accessibility Service only interacts with declared bank app packages
- **Optional History** - No account number history stored by default

### UI/UX
- **Onboarding Flow** - Step-by-step setup guide for permissions
- **Modern Design** - Clean, trust-first design with Material 3
- **Error Recovery** - Clear error messages with retry options
- **Dark Mode Ready** - Architecture supports system theme switching

---

## Demo

```
Primary Flow — Snap & Fill:
1. Open BankSnap → Tap "Scan Account Number"
2. Capture photo or import from gallery
3. ML Kit processes image → highlights detected number
4. Editable field shows extracted number → user can correct
5. Tap "Select Bank App" → grid of installed apps appears
6. Select bank → Accessibility Service launches app and fills field
7. User reviews pre-filled number and completes transfer
```

---

## Installation

### Prerequisites
- Flutter SDK >= 3.27.0
- Dart >= 3.6.0
- Android SDK (API 26 - API 36)
- Java JDK 21
- Gradle 8.10+

### Setup

```bash
# Clone the repository
git clone https://github.com/Roqak/Bank-snap.git
cd Bank-snap

# Get dependencies
flutter pub get

# Run the app
flutter run
```

### Build Release APK

```bash
# Build release APK
flutter build apk --release

# Or build split APKs per ABI
flutter build apk --split-per-abi
```

The release APK will be located at:
```
build/app/outputs/flutter-apk/app-release.apk
```

---

## Usage

### First-Time Setup
1. Launch BankSnap
2. Complete the 3-screen onboarding (Scan, Validate, Auto-fill)
3. Grant Camera permission when prompted
4. Enable Accessibility Service in Android Settings
5. Return to the app — you're ready to go!

### Scanning an Account Number
1. From the home screen, tap **"Scan with Camera"** or **"Import from Gallery"**
2. Frame the document and capture (or select an image)
3. Review the detected account number in the editable field
4. Correct any errors if needed
5. Tap **"Select Bank App"**
6. Choose your bank from the grid of installed apps
7. The bank app will launch with the account number pre-filled

---

## Architecture

### Technology Stack
| Component | Technology |
|-----------|-----------|
| Framework | Flutter (Dart) |
| State Management | flutter_riverpod |
| Camera | camera plugin |
| OCR | google_mlkit_text_recognition |
| Image Picker | image_picker |
| Local Storage | shared_preferences, Hive |
| Accessibility | Native Kotlin AccessibilityService |
| Bridge | Flutter MethodChannel |

### Project Structure
```
lib/
├── core/
│   ├── constants/       # App constants, bank mappings
│   ├── errors/          # Custom exceptions
│   ├── theme/           # AppTheme (colors, typography, spacing)
│   └── utils/           # NUBAN validation, OCR correction
├── data/
│   └── repositories/    # Data layer
├── domain/
│   └── entities/        # BankApp, ExtractedAccount, CaptureHistory
├── presentation/
│   ├── providers/       # Riverpod state management
│   ├── screens/         # UI screens (7 screens)
│   └── widgets/         # Reusable UI components
└── services/
    ├── ocr_service.dart        # ML Kit OCR processing
    └── accessibility_service.dart  # Flutter-Native bridge
```

### Native Android (Kotlin)
- **MainActivity.kt** - Flutter-Native MethodChannel bridge
- **BanksnapAccessibilityService.kt** - Android AccessibilityService for auto-fill
- **AndroidManifest.xml** - Permissions (Camera, Storage, Accessibility, QUERY_ALL_PACKAGES)

---

## Supported Banks

BankSnap supports the following Nigerian bank apps at launch:

| Bank | Package Name |
|------|-------------|
| GTBank | `com.gtbank.main` |
| Access Bank | `com.accessbank.accessmobile` |
| Zenith Bank | `com.zenith.bank` |
| First Bank | `ng.com.firstmobilebusiness.android` |
| UBA | `com.uba.mobile` |
| Opay | `team.opay.pay` |
| Kuda | `com.kudabank.app` |
| PalmPay | `com.palmpay.app` |
| Moniepoint | `com.teamapt.monnify` |
| Sterling Bank | `com.sterlingbankmobileapp` |

> Note: The target bank app must be installed on the device for auto-fill to work.

---

## Permissions

BankSnap requires the following Android permissions:

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Live capture of documents |
| `READ_EXTERNAL_STORAGE` | Gallery import (API ≤ 32) |
| `READ_MEDIA_IMAGES` | Gallery import (API ≥ 33) |
| `QUERY_ALL_PACKAGES` | Detect installed bank apps |
| `BIND_ACCESSIBILITY_SERVICE` | Auto-fill bank app fields |

---

## Privacy

BankSnap is built with privacy-first principles:

- **On-Device Processing** — All OCR happens locally using Google ML Kit. No images or account numbers are ever sent to any server.
- **No Cloud Dependency** — The app works completely offline.
- **No Analytics** — No third-party analytics SDKs that could transmit financial data.
- **Scoped Accessibility** — The Accessibility Service only monitors and interacts with the declared bank app packages.
- **Revocable** — Users can disable the Accessibility Service at any time from Android Settings.
- **No Default History** — Account numbers are not stored by default. History/logging is planned for Phase 2 as an opt-in feature.

---

## Screenshots

> Coming soon — UI screenshots of the onboarding, capture, result, and bank picker screens.

---

## Roadmap

### Phase 1 — MVP (Months 1-3)
- ✅ Core snap-to-fill flow for top 10 Nigerian banks
- ✅ NUBAN OCR extraction and validation
- ✅ Bank app picker with installed app detection
- ✅ Onboarding and permission setup flow
- ✅ Basic error handling and manual fallback

### Phase 2 — Enhanced (Months 4-6)
- [ ] Support for 10 additional bank apps
- [ ] Recent banks shortcut and usage history
- [ ] Multiple account number detection from one image
- [ ] Dark mode and improved theming
- [ ] Haptic and audio confirmation feedback
- [ ] OTA bank mapping config updates

### Phase 3 — Expansion (Months 7-12)
- [ ] iOS support (Screen Automation / Shortcuts)
- [ ] Batch processing: multiple transfers from one document
- [ ] Business account features: saved payees, CSV import
- [ ] Optional end-to-end encrypted cloud sync

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Acknowledgments

- [Flutter](https://flutter.dev) — UI framework
- [Google ML Kit](https://developers.google.com/ml-kit) — On-device OCR
- [Riverpod](https://riverpod.dev) — State management
- Central Bank of Nigeria (CBN) — NUBAN standard

---

## Disclaimer

BankSnap is designed to assist with account number entry. Users should always verify the pre-filled account number before completing any transfer. The developers are not responsible for misdirected transfers resulting from incorrect OCR or user error.

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

<p align="center">Built with ❤️ for the Nigerian fintech community</p>
