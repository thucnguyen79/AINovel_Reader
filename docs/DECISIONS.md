# Architecture Decision Records (ngắn gọn)

Mỗi mục: bối cảnh, quyết định, hệ quả. Mới nhất ở trên.

## ADR-003 (2026-10-21): AGP 9.4.1 + Gradle 9.6.0, compileSdk 37, Kotlin tích hợp sẵn
- Bối cảnh: AndroidX mới nhất (core 1.19, lifecycle 2.11, navigation 2.10, Compose 1.12) yêu cầu compileSdk 37 và AGP >= 9.1; AGP 9.4 yêu cầu Gradle 9.6. Từ API 37 gói platform có số phụ (`platforms;android-37.0`). AGP 9 bật built-in Kotlin mặc định.
- Quyết định: AGP 9.4.1, Gradle wrapper 9.6.0, compileSdk = targetSdk = 37, minSdk 26. Không apply `org.jetbrains.kotlin.android`; cấu hình Kotlin qua `compileOptions` và `tasks.withType<KotlinJvmCompile>`. KGP 2.4.21 và KSP 2.3.12 nằm trong classpath của build-logic để ghi đè KGP mà AGP bundle. DSL AGP 9: các block `compileOptions`, `lint`, `defaultConfig`, `buildFeatures` chỉ có trên `ApplicationExtension`/`LibraryExtension`, không có trên `CommonExtension`, nên convention plugin có 2 overload.
- Hệ quả: nâng AGP phải nâng Gradle tương ứng (xem bảng trong release notes). Không dùng toolchain JDK cố định vì máy chỉ có JDK 21.

## ADR-002 (2026-10-21): Không backend, không tài khoản, chỉ công cụ miễn phí
- Bối cảnh: yêu cầu người dùng, xem `docs/PLAN.md` mục 0.
- Quyết định: TTS = Android TextToSpeech + sherpa-onnx/Piper; dịch = convert engine offline + ML Kit; LLM chỉ BYOK. File rule nguồn host tĩnh trên GitHub.
- Hệ quả: không có module billing/auth/sync; mọi dữ liệu nằm trong app-specific storage.

## ADR-001 (2026-10-21): Android native, Kotlin + Jetpack Compose, đa module
- Bối cảnh: phát nền audio, MediaSession, TTS hệ thống, WebView là API Android sâu.
- Quyết định: native thay vì Flutter/KMP; MVVM + Clean nhẹ; convention plugins trong `build-logic`.
- Hệ quả: chỉ Android; nếu cần iOS sau này, `domain`, `tts/api` đã thuần Kotlin nên có thể chuyển sang KMP.
