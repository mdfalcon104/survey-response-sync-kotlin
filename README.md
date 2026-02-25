# Survey Response Sync Engine

A robust offline-first sync engine for Android, designed for field agents collecting agricultural survey data in rural areas with unreliable connectivity.

## Features

- **Offline Storage**: Survey responses persist locally using Room database
- **Partial Failure Handling**: Track succeeded, failed, and pending responses independently
- **Network Degradation Detection**: Stop sync early when network becomes unstable
- **Concurrent Sync Prevention**: Only one sync operation runs at a time
- **Unified Error Model**: Consistent error handling with retry classification
- **Storage Management**: Automatic cleanup of synced responses and media files

## Project Structure

```
app/src/main/java/com/survey/sync/
├── core/                    # Shared models (SyncError, SyncResult, SyncStatus)
├── domain/                  # Business logic and interfaces
│   ├── SyncEngine.kt        # Main sync orchestrator
│   ├── SurveyResponse.kt    # Domain entity
│   ├── SurveyRepository.kt  # Repository interface
│   ├── SurveyApi.kt         # API interface
│   └── StorageManager.kt    # File operations interface
└── data/                    # Implementations
    ├── SurveyDatabase.kt    # Room database
    ├── SurveyResponseDao.kt # Data access object
    └── SurveyRepositoryImpl.kt
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Gradle 8.5+
- Android SDK (API 34)

## Setup

### Option 1: Android Studio (Recommended)

1. Open Android Studio
2. Select "Open" and navigate to the project root directory
3. Android Studio will prompt to install missing SDK components if needed
4. Wait for Gradle sync to complete
5. Build and run tests from the IDE:
   - Right-click on `app/src/test` → "Run Tests"
   - Or use the green play button next to test classes

### Option 2: Command Line

Ensure Android SDK is installed and configured:

```bash
# Set ANDROID_HOME (adjust path as needed)
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Or create local.properties in project root
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

## Build

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Clean and rebuild
./gradlew clean build
```

## Run Tests

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.survey.sync.SyncEngineTest"

# Run with coverage report
./gradlew testDebugUnitTest
```

### Instrumented Tests (requires emulator or device)

```bash
# Run Android instrumented tests
./gradlew connectedAndroidTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.survey.sync.SurveyDatabaseTest
```

## Test Results

All **38 unit tests** pass successfully:

```
BUILD SUCCESSFUL

Test Summary:
├── SyncEngineTest .............. 24 tests ✓
├── SyncErrorTest ............... 11 tests ✓
└── StorageCleanupManagerTest ...  3 tests ✓
```

### Test Coverage by Scenario

| Scenario | Test Cases |
|----------|------------|
| **Scenario 1: Offline Storage** | Room persistence, JSON answers, media file paths, status tracking |
| **Scenario 2: Partial Failure** | First N succeed, mark SYNCED; failure marks FAILED_RETRYABLE; remaining stay PENDING |
| **Scenario 3: Network Degradation** | Consecutive failure detection, early stop, clear stop reason |
| **Scenario 4: Concurrent Sync** | Mutex prevents duplicate syncs, second caller reuses running job |
| **Scenario 5: Error Handling** | NoInternet, Timeout, ServerError (4xx/5xx), Serialization, Unknown |

### Test Files

| Test File | Coverage |
|-----------|----------|
| `SyncEngineTest.kt` | All sync scenarios, retry logic, media deletion |
| `SyncErrorTest.kt` | Error classification (retryable vs non-retryable) |
| `StorageCleanupManagerTest.kt` | Storage cleanup logic |

## Usage Example

```kotlin
// Initialize dependencies
val database = SurveyDatabase.getInstance(context)
val repository = SurveyRepositoryImpl(database.surveyResponseDao())
val api = YourSurveyApiImpl() // Implement SurveyApi interface
val storageManager = FileStorageManager()

// Create sync engine
val syncEngine = SyncEngine(
    repository = repository,
    api = api,
    storageManager = storageManager
)

// Execute sync
val result = syncEngine.sync()

// Handle result
println("Succeeded: ${result.succeededIds.size}")
println("Failed: ${result.failedIds.size}")
println("Pending: ${result.pendingIds.size}")

if (result.stopReason != null) {
    println("Stopped early: ${result.stopReason}")
}
```

## Configuration

```kotlin
val config = SyncConfig(
    maxRetryCount = 5,              // Max retries before marking permanent failure
    consecutiveFailureThreshold = 3, // Failures before stopping sync
    initialBackoffMs = 1000L,        // Initial retry delay
    maxBackoffMs = 60000L            // Maximum retry delay
)

val syncEngine = SyncEngine(
    repository = repository,
    api = api,
    storageManager = storageManager,
    config = config
)
```

## Design Decisions

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture decisions, alternatives considered, and answers to technical questions about:

- Media compression strategy
- Network detection edge cases
- Remote troubleshooting support
- GPS boundary capture challenges

## Device Constraints

This implementation is optimized for low-end devices:

- **RAM**: 1-2 GB (sequential processing, no parallel uploads)
- **Storage**: 16-32 GB (automatic cleanup, media deletion after sync)
- **Battery**: 8-12 hour field usage (early stop on network issues)
- **Android**: 8.0+ (minSdk 26)

## License

This project is provided as a technical assessment submission.
