---
name: meowcode-app-build-env
description: Android SDK/NDK paths and Java version for building meowcode-app on this machine
metadata:
  node_type: memory
  type: reference
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T04:24:04.259Z
---

On this machine the Android SDK and NDK live at **`/run/media/xingguangcuican/数据/NDK/`**
(the path contains CJK characters):

- platforms: android-34, android-35, android-37.0 (android-36 must be installed for
  compileSdk 36 — the ABK standard uses 36)
- build-tools: 34/35/36/37
- ndk: `26.1.10909125`, `28.2.13676358`
- cmdline-tools/latest (`sdkmanager`), platform-tools (`adb`)
- licenses present under `licenses/`

Java: use `/usr/lib/jvm/java-17-openjdk` as `JAVA_HOME` for Gradle/AGP.

**Gotcha:** `local.properties` is read as ISO-8859-1 by `java.util.Properties`, so the
CJK `数据` path does not survive a UTF-8 written local.properties reliably. Prefer the
environment variable (`ANDROID_HOME=/run/media/xingguangcuican/数据/NDK ./gradlew ...`);
AGP honours `ANDROID_HOME`/`ANDROID_SDK_ROOT`.

Gradle wrapper is copied from rebuild_abk_design (Gradle 8.13, sha256 pinned); AGP
8.11.1, Kotlin/Compose 2.2.21, Compose BOM 2025.11.01.

**Why:** the machine's default Java is newer and the SDK path is non-ASCII.
**How to apply:** always pass both env vars in build commands; never commit
`local.properties`, SDK copies or signing keys.
