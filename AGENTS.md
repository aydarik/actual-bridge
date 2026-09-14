# AGENTS.md

Welcome to **Actual Budget Bridge** (`actual-bridge`). This document provides architectural context, project structure, build/test commands, and development conventions to assist AI agents in navigating, understanding, and modifying the codebase efficiently and safely.

---

## 1. Project Overview

**Actual Budget Bridge** is an Android companion application designed to automatically capture financial transaction notifications from banking and payment apps (e.g., Google Wallet, bank apps) and sync them to a self-hosted [Actual Budget](https://actualbudget.org/) instance via [Actual Tap](https://github.com/MattFaz/actualtap).

### Key Features
- **Notification Listening**: Intercepts status bar notifications from banking apps via `NotificationListenerService`.
- **Regex Parsing Engine**: Matches notification package names, titles, and text to extract transaction fields (`amount`, `payee`, `account`, `type`, `notes`).
- **Review & Confirm**: Supports 1-tap notification quick actions (`Confirm & Send`, `Discard`), floating popup review dialogs (`ConfirmTransactionActivity`), or zero-touch auto-send.
- **Location Tagging**: Optionally attaches GPS coordinates (latitude/longitude) to transaction payloads.
- **Local History & Testing**: In-app test playground for parsing rules and local transaction history logs.

---

## 2. Tech Stack & Environment

- **Language**: Kotlin (JVM target 24)
- **Target SDK**: Android 36 (Extension / Minor API Level 1), **Min SDK**: 33, **Compile SDK**: 36
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) and Gradle Version Catalogs (`gradle/libs.versions.toml`)
- **Android Gradle Plugin (AGP)**: 9.1.1
- **UI Framework**: Android ViewBinding, AppCompat, Material Design Components (`com.google.android.material:material:1.12.0`)
- **Networking**: OkHttp 4.12.0 (`okhttp3`, `logging-interceptor`)
- **Serialization**: Gson 2.11.0
- **Concurrency**: Kotlin Coroutines (`kotlinx-coroutines-android:1.8.1`)
- **Location Services**: Google Play Services Location (`play-services-location:21.3.0`)
- **Testing Framework**: JUnit 4 (`4.13.2`), AndroidX JUnit (`1.3.0`), Espresso Core (`3.7.0`)

---

## 3. Project Directory Structure

```
ActualApp/
├── app/
│   ├── build.gradle.kts              # App-level build configuration, dependencies, SDK versions
│   ├── proguard-rules.pro            # ProGuard / R8 rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Manifest (permissions, services, receivers, activities)
│       │   ├── java/de/gumerbaev/actual/
│       │   │   ├── data/
│       │   │   │   └── AppPreferences.kt               # SharedPreferences wrapper (settings, rules, history)
│       │   │   ├── model/
│       │   │   │   ├── ActualTransaction.kt            # Transaction data model & JSON payload format
│       │   │   │   ├── ParsingRule.kt                  # Regex rule model & default preset rules
│       │   │   │   └── TransactionRecord.kt            # History log entry model & status enum
│       │   │   ├── network/
│       │   │   │   └── ActualApiClient.kt              # OkHttp client for Actual Tap API endpoints
│       │   │   ├── parser/
│       │   │   │   └── NotificationParser.kt           # Parsing engine (regex matching & group extraction)
│       │   │   ├── receiver/
│       │   │   │   └── NotificationActionReceiver.kt   # Receiver for 1-tap notification actions
│       │   │   ├── service/
│       │   │   │   └── ActualNotificationListenerService.kt # Notification listener service & dispatch logic
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt                 # Main settings, rule list, test dialog, history
│       │   │   │   ├── ConfirmTransactionActivity.kt   # Dialog-style review & edit screen
│       │   │   │   └── adapter/
│       │   │   │       ├── HistoryAdapter.kt           # RecyclerView adapter for transaction history
│       │   │   │       └── RulesAdapter.kt             # RecyclerView adapter for parsing rules
│       │   │   └── util/
│       │   │       └── LocationHelper.kt               # Location permission checking & retrieval
│       │   └── res/
│       │       ├── layout/                             # XML layout files (ViewBinding generated)
│       │       ├── values/                             # Strings, colors, themes, styles
│       │       └── xml/                                # Backup & data extraction rules
│       └── test/java/de/gumerbaev/actual/
│           ├── ExampleUnitTest.kt
│           └── parser/
│               └── NotificationParserTest.kt           # Unit tests for regex parsing logic
├── gradle/
│   ├── libs.versions.toml            # Centralized dependency catalog
│   └── wrapper/                      # Gradle wrapper files
├── build.gradle.kts                  # Root build script
├── gradle.properties                 # Gradle JVM arguments & settings
├── settings.gradle.kts               # Module and plugin repository definitions
└── README.md                         # End-user documentation
```

---

## 4. Architecture & Core Workflows

### 4.1 Data Flow: Notification to Actual Budget
1. **Notification Interception**: An incoming push notification is detected by `ActualNotificationListenerService.onNotificationPosted()`.
2. **Rule Matching**: `NotificationParser.parse()` checks active `ParsingRule`s loaded from `AppPreferences`. It filters by `targetPackage`, validates `titleRegex` (if specified), and extracts fields using named capture groups (`amountGroup`, `payeeGroup`, `accountGroup`, `notesGroup`, `typeGroup`, `categoryGroup`).
3. **Location Enrichment**: If `prefs.attachLocation` is enabled and permissions are granted, `LocationHelper.getLastLocation()` adds `latitude` and `longitude`.
4. **Action Routing**:
   - **Auto-Send Enabled**: Immediately posts to `ActualApiClient.sendTransaction()`.
   - **Auto-Popup Enabled** (and overlay permission granted): Starts `ConfirmTransactionActivity` as a dialog over other apps.
   - **Default**: Posts an interactive Android system notification with quick actions (`Confirm & Send`, `Edit`, `Discard`).
5. **Persistence**: Transaction outcome (success, error, or pending) is recorded in `AppPreferences` history list.

### 4.2 Storage Model (`AppPreferences`)
- Storage uses Android `SharedPreferences` (`actual_prefs`).
- Parsing rules and transaction history are serialized as JSON strings using Gson.
- Default preset rules are seeded on first run via `ParsingRule.createDefaultRules()`.

### 4.3 Network API (`ActualApiClient`)
- Sends transactions via HTTP `POST` to the configured Actual Tap server URL.
- Auth header format: Supports `Bearer <token>` and `x-api-key: <token>`.
- Payload format matches Actual Tap specifications defined in `ActualTransaction.toJson()`.

---

## 5. Build, Test & Development Commands

> **Note**: Requires JDK 24 or newer. Ensure `JAVA_HOME` points to a compatible JDK installation.

### Run Unit Tests
```bash
./gradlew test
# or specifically:
./gradlew :app:testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run Lint & Code Checks
```bash
./gradlew lintDebug
```

### Clean Project
```bash
./gradlew clean
```

---

## 6. Development & Coding Conventions

- **ViewBinding**: Always access layout views via ViewBinding generated classes (`ActivityMainBinding`, `DialogEditRuleBinding`, etc.). Do not use `findViewById` or synthetic accessors.
- **Coroutines & Threading**:
  - Dispatch IO-bound tasks (network calls, storage serialization) on `Dispatchers.IO` or appropriate lifecycle-aware scopes (`lifecycleScope`, `serviceScope`).
  - Do not block the main thread in `NotificationListenerService` or broadcast receivers.
- **Regex Safety**:
  - Keep regex patterns resilient against regional currency formats (e.g., European comma decimals vs. US dot decimals; see `NotificationParser.parseAmount`).
  - Use named regex groups when adding new parsing capabilities.
  - When modifying `NotificationParser`, ensure existing parser tests pass and add unit test coverage in `NotificationParserTest.kt` for new cases.
- **Permissions Handling**:
  - Notification access requires `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` (user must grant access in system settings).
  - Floating overlays require `android.permission.SYSTEM_ALERT_WINDOW` (`Settings.canDrawOverlays`).
  - Location tagging requires runtime permission checks via `LocationHelper.hasLocationPermission()`.
- **Dependencies**: Manage all dependency versions in `gradle/libs.versions.toml`. Avoid hardcoding version numbers directly inside `app/build.gradle.kts`.
