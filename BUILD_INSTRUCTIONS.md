# Practical Build & Installation Guide — LocalDev Studio X

This guide explains how to compile `LocalDev-Studio-X-Android-Source.zip` into an installable `.apk` whether you have a desktop computer with Android Studio or **only an Android phone**.

## 1. Why the Sandbox Delivered the Complete Buildable Source ZIP (Level 2 Delivery)

During automated environment detection in the Argon 4 execution container:
- **Available**: Linux `x86_64` kernel, OpenJDK `17.0.20.1` (`java`, `jdk.compiler/javac`, `jdk.jartool/jar`), GCC `12.2.0`, Python `3.11`, and native `libsqlite3.so.0` (`3.40.1`).
- **Unavailable**: Android SDK (`platforms/android-35/android.jar`), Android Build Tools (`aapt2`, `d8`, `apksigner`), Android NDK, Kotlin compiler (`kotlinc`), Gradle distribution archive, and outbound internet access.

Because compiling an Android `.apk` requires `aapt2`, `d8`, `android.jar`, and the Kotlin Compose compiler plugin, **Level 2 (Complete Buildable Android Studio Project ZIP + Live Verification of Core Java 17 Engines)** is the highest genuine delivery level supported without fabricating binaries.

## 2. Building if You Only Have an Android Phone (No PC Required)

Do **not** rename `LocalDev-Studio-X-Android-Source.zip` to `.apk` — a source ZIP is not a compiled Android package and Android Package Installer will reject it. Instead, use one of these two real phone-only workflows:

### Workflow A: GitHub Actions Cloud APK Build (Recommended for Phone-Only Users)
1. Extract `LocalDev-Studio-X-Android-Source.zip` on your phone using your built-in Files app.
2. Create a free repository on GitHub from your mobile browser and upload the project files.
3. Add a `.github/workflows/android-build.yml` workflow using `actions/setup-java@v4` (JDK 17) and `gradle/actions/setup-gradle@v3` running `gradle assembleDebug`.
4. GitHub's cloud runner automatically downloads the Android SDK, compiles the Kotlin Compose UI, signs `app-debug.apk`, and provides it under the **Artifacts** tab for direct download and installation on your phone.

### Workflow B: On-Device Android IDE (AndroidIDE / Termux ARM64)
1. Install **AndroidIDE** (available for ARM64 Android devices) which bundles OpenJDK 17, the Android SDK (`android-35`), `aapt2`, and Gradle for ARM64.
2. Open the extracted `LocalDev-Studio-X` folder in AndroidIDE.
3. Tap **Build -> Assemble Debug** (or run `gradle assembleDebug` in the integrated terminal).
4. Once finished, install the generated APK from:
   `app/build/outputs/apk/debug/app-debug.apk`

## 3. Building on Desktop (Android Studio Ladybug 2024.2+ or Command-Line Gradle)

### Prerequisites
- **JDK 17** (bundled with Android Studio Ladybug / Koala).
- **Android SDK Platform 35** (`compileSdk = 35`, `minSdk = 26`).

### Step-by-Step Build Commands
1. Extract `LocalDev-Studio-X-Android-Source.zip`:
   ```bash
   unzip LocalDev-Studio-X-Android-Source.zip -d LocalDev-Studio-X
   cd LocalDev-Studio-X
   ```
2. Open the folder in **Android Studio** (which automatically generates/syncs the Gradle wrapper JAR) or run with a local Gradle 8.9+ installation:
   ```bash
   gradle assembleDebug
   ```
3. Run the unit and host integration verification suites:
   ```bash
   gradle testDebugUnitTest
   ```
4. Locate the verified debug APK at:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```
5. Install onto a connected Android phone via ADB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## 4. Bundling the Native PHP CGI Binary (`libphp_cgi.so`) for Android ABIs

Android 10+ (API 29+) enforces W^X (`SELinux app_data_file:file execute_no_trans` restrictions) blocking execution of binaries downloaded into writable home directories. To execute genuine PHP on Android 10–15:
1. Place a pie-compiled Android `php-cgi` binary (compiled with `--enable-cgi --with-pdo-sqlite --enable-mbstring --enable-session`) named **`libphp_cgi.so`** inside:
   - `app/src/main/jniLibs/arm64-v8a/libphp_cgi.so`
   - `app/src/main/jniLibs/armeabi-v7a/libphp_cgi.so`
   - `app/src/main/jniLibs/x86_64/libphp_cgi.so`
2. Because `app/build.gradle.kts` sets `jniLibs.useLegacyPackaging = true` and `AndroidManifest.xml` sets `android:extractNativeLibs="true"`, Android Package Manager extracts `libphp_cgi.so` into `context.applicationInfo.nativeLibraryDir` with executable (`r-x`) SELinux permissions.
3. `PhpRuntimeEngine` automatically locates `libphp_cgi.so` in `nativeLibraryDir`, passes `-c files/runtime_config/php.ini`, and executes `.php` scripts over CGI/1.1.
