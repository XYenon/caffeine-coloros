# Caffeine for ColorOS & OxygenOS

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%20--%2016-green.svg)](https://developer.android.com)
[![libxposed](https://img.shields.io/badge/libxposed-API%20102-orange.svg)](https://github.com/LibXposed)

A modern libxposed module bringing the classic **LineageOS Caffeine** Quick Settings tile to **OxygenOS & ColorOS** (OnePlus, OPPO, and Realme devices), built for **libxposed API 102**.

---

## ✨ Features

- **1:1 LineageOS Interaction**:
  - **Multi-tier Cycle**: `Off` → `5 min` → `10 min` → `30 min` → `1 hour` → `Infinity (∞)` → `Off`.
  - **Live Dynamic Countdown**: Displays a real-time per-second countdown directly on the Quick Settings tile title (e.g., `Caffeine (4:59)` / `Caffeine (59:59)`).
  - **Auto-Turn Off on Screen Lock**: Automatically releases the wake lock when the screen turns off or device is locked, avoiding unintended battery drain.
  - **Long-Press to Open App**: Long-pressing the tile smoothly collapses the notification shade and launches the configuration application.
- **Deep SystemUI Integration**:
  - **Native In-Process WakeLock**: Acquires `PowerManager.FULL_WAKE_LOCK` directly inside the `com.android.systemui` system process. Completely immune to OxygenOS/ColorOS battery saver and process freezing (`OplusAppFreezer`).
  - **Zero-Latency Response**: Intercepts `QSTileImpl` and `CustomTile` click/state events locally inside SystemUI, avoiding cross-process IPC lag.
  - **Modern Hook Bridge**: Uses the libxposed API 102 interceptor API while completely isolating system tiles (Wi-Fi, Bluetooth, Torch) from interference.
  - **Verified Module Status**: Uses `XposedService` for framework/scope status and a correlated SystemUI handshake to verify that the hook is actually running.
  - **Standalone Fallback**: Includes a standard Android `TileService` and foreground service fallback for operation even without Xposed injection.

---

## 📱 Compatibility

- **OxygenOS 11 – 16** (Android 11 through Android 16)
- **ColorOS 12 – 16**
- **RealmeUI 3.0 – 6.0**
- Tested on OnePlus 13 (Android 16 / OxygenOS 16.1.0) with Vector Framework 2.2.

---

## 🚀 Installation & Setup

1. **Download / Install APK**:
   - Download the latest APK from the [Releases](https://github.com/xyenon/caffeine-coloros/releases) page or build from source.
   - Install the APK:
     ```bash
     adb install -r app-debug.apk
     ```

2. **Enable in a libxposed-compatible manager**:
   - Open **Vector Manager** or another compatible manager.
   - Enable the **Caffeine (咖啡因)** module.
   - Select only **System UI** (`com.android.systemui`) as the scope.
   - Soft reboot SystemUI or reboot the device.

3. **Add the Quick Settings Tile**:
   - Pull down the Quick Settings notification shade and tap the **Edit (Pencil)** icon.
   - Drag the **Caffeine** tile into your active tiles list.
   - Tap the tile to start cycling durations!

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 37+)
- Gradle 8.x / 9.x

### Build Commands
```bash
# Clone the repository
git clone https://github.com/xyenon/caffeine-coloros.git
cd caffeine-coloros

# Build debug APK
./gradlew assembleDebug

# Build unsigned release APK
./gradlew assembleRelease
```
Compiled APKs will be located at `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

---

## 📂 Project Architecture

```
caffeine/
├── app/
│   ├── src/main/
│   │   ├── kotlin/bid/xyenon/caffeine/coloros/
│   │   │   ├── CaffeineApplication.kt      # XposedService lifecycle and scope status
│   │   │   ├── core/
│   │   │   │   ├── CaffeineEngine.kt       # State machine, WakeLock manager, and ticker
│   │   │   │   ├── CaffeineConfig.kt       # Duration sequences, action constants
│   │   │   │   └── TimeFormatter.kt        # Human-readable time formatting
│   │   │   ├── hook/
│   │   │   │   ├── HookBridge.kt           # LibXposed interceptor bridge
│   │   │   │   ├── LibXposedEntry.kt       # Modern LibXposed API 102 entry point
│   │   │   │   ├── SystemUIHook.kt         # OxygenOS / ColorOS SystemUI tile hook
│   │   │   │   └── DexHelper.kt            # Reflection and DexKit lookup utilities
│   │   │   ├── provider/
│   │   │   │   └── SettingsProvider.kt     # IPC content provider
│   │   │   ├── service/
│   │   │   │   ├── CaffeineTileService.kt  # Android TileService implementation
│   │   │   │   └── CaffeineForegroundService.kt # Fallback wake lock foreground service
│   │   │   └── ui/
│   │   │       └── MainActivity.kt         # Companion configuration UI
│   │   ├── res/                            # Vector drawables, themes, localized strings
│   │   ├── resources/META-INF/xposed/      # LibXposed metadata (module.prop, scope.list)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/build.yml             # GitHub Actions CI workflow
├── LICENSE                                 # GNU General Public License v3.0
└── settings.gradle.kts
```

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.
