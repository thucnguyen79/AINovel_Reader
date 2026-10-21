# AINovel Reader – hướng dẫn cho Claude Code

Ứng dụng Android đọc và nghe truyện (TTS) miễn phí, offline, không tài khoản, không backend.
Kế hoạch đầy đủ: `docs/PLAN.md`. Mọi quyết định kiến trúc mới ghi vào `docs/DECISIONS.md`.

## Ràng buộc cứng (không vi phạm)
- Không nhúng API key, không thêm dependency hay dịch vụ trả phí, không analytics/Firebase.
- Dịch và TTS chỉ dùng công cụ miễn phí: Android TextToSpeech, sherpa-onnx/Piper, convert engine offline, ML Kit on-device. LLM chỉ ở dạng BYOK do người dùng tự nhập key.
- Dữ liệu người dùng nằm trong bộ nhớ riêng của app. Không xin quyền đọc toàn bộ bộ nhớ.
- Chuỗi hiển thị cho người dùng đặt trong `res/values/strings.xml` (tiếng Việt), không hard-code trong Kotlin.
- Không commit keystore, `local.properties`, file build.

## Môi trường
- JDK 21: `D:\Tools\jdk-21.0.11+10` (biến `JAVA_HOME` đã trỏ sẵn). Android SDK: `D:\Android\Sdk` (platform 34–37, build-tools 36.0.0). adb: `D:\Android\Sdk\platform-tools\adb.exe`.
- Gradle wrapper 9.6.0, AGP 9.4.1 (Kotlin tích hợp sẵn, KHÔNG apply `org.jetbrains.kotlin.android`), Kotlin 2.4.21, KSP 2.3.12.
- Không có Android Studio trên máy. Mọi thao tác chạy từ terminal.

## Lệnh thường dùng (chạy tại thư mục gốc, PowerShell)
```
.\gradlew.bat :app:assembleDebug            # build APK debug
.\gradlew.bat testDebugUnitTest             # unit test tất cả module
.\gradlew.bat :data:source:testDebugUnitTest  # unit test 1 module
.\gradlew.bat lintDebug                     # lint
D:\Android\Sdk\platform-tools\adb.exe devices
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\Android\Sdk\platform-tools\adb.exe shell am start -n vn.esstar.ainovel.debug/vn.esstar.ainovel.MainActivity
D:\Android\Sdk\platform-tools\adb.exe exec-out screencap -p > shot.png
D:\Android\Sdk\platform-tools\adb.exe logcat -d -s AINovel:V AndroidRuntime:E
```
Build lần đầu hoặc sau khi đổi dependency có thể mất vài phút: chạy nền và chờ thông báo.

## Thiết bị test
- OPPO CPH2357 (Android 14, 1080x2412, DPR 3). Màn hình tự khoá sau 30 giây và shell không được đổi cài đặt (OPPO chặn `settings put`, `svc power`); trước mỗi chuỗi thao tác adb hãy kiểm tra `dumpsys window | grep isKeyguardShowing` và nhờ người dùng mở khoá nếu cần. Nên bật "Giữ màn hình luôn bật khi sạc" trong Tuỳ chọn nhà phát triển.
- Toạ độ `input tap` tính theo pixel thật (1080x2412); ảnh chụp đọc bằng Read hiển thị thu nhỏ, nhân 1,21.
- `uiautomator dump` ghi ra `/sdcard/window_dump.xml`.
- Kiểm tra phát nền: `adb shell dumpsys media_session | grep state=` (trạng thái, position, speed, custom actions) và `adb shell dumpsys notification --noredact | grep ainovel`. Toạ độ player: 5 nút ở y≈1565 (x 168 / 333 / 542 / 748 / 916), nút tai nghe trong Reader ở (1000, 203).
- Gỡ lỗi WebView: bản debug đã bật `setWebContentsDebuggingEnabled`; `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` rồi dùng script CDP (xem lịch sử phiên 21/10/2026) để chạy JS trong trang.

## Cấu trúc module (Gradle path -> package `vn.esstar.ainovel.<path>`)
- `app` – Application, MainActivity, điều hướng 4 tab + mini-player.
- `core/common` (dispatchers, AppResult), `core/designsystem` (theme, component), `core/network` (OkHttp, DoH, Jsoup, WebView fetcher), `core/database` (Room), `core/datastore` (settings), `core/storage` (bố cục file).
- `domain` – model và use case thuần Kotlin (JVM, không Android).
- `data/source` (rule engine nguồn truyện), `data/library`, `data/ebook`, `data/translate` (convert engine, ML Kit, LlmProvider BYOK).
- `tts/api` (JVM: TtsEngine, TextSegmenter, TextNormalizer, AudioCache), `tts/system` (Android TextToSpeech), sau này `tts/neural`, `tts/online`.
- `feature/*` – màn hình Compose + ViewModel: home, browser, reader, player, library, translate, settings.
- `service/playback` (Media3 MediaSessionService), `service/download` (WorkManager).

Quy tắc phụ thuộc: `feature` -> `domain`, `core/*`, `data/*` (qua interface trong domain); `data` -> `core`, `domain`; `domain` không phụ thuộc gì ngoài Kotlin/coroutines/serialization. `app` là nơi duy nhất ghép tất cả.

## Convention plugins (`build-logic/convention`)
- `ainovel.android.application`, `ainovel.android.library`, `ainovel.android.library.compose`, `ainovel.android.feature` (compose + hilt + nav + viewmodel), `ainovel.android.hilt`, `ainovel.android.room`, `ainovel.jvm.library`.
- Namespace tự sinh từ Gradle path. Version nằm trong `gradle/libs.versions.toml`, không ghi version trong module.
- Module mới: thêm `include(":x:y")` vào `settings.gradle.kts`, tạo `x/y/build.gradle.kts` áp plugin phù hợp.

## Quy ước code
- Kotlin, Compose, MVVM: `XxxScreen` (stateless, nhận state + callback) + `XxxViewModel` (Hilt, `StateFlow<UiState>`) + `XxxNavigation.kt` (route `@Serializable`, `navigateToXxx`, `NavGraphBuilder.xxxScreen`).
- Coroutines: inject dispatcher qua `@Dispatcher(AppDispatcher.IO)`; không dùng `Dispatchers.IO` trực tiếp ngoài module common.
- Log bằng Timber. Không `println`/`Log.d`.
- Tên file/lớp tiếng Anh; chuỗi UI tiếng Việt trong resources; comment ngắn gọn, ưu tiên tiếng Anh trong code.
- Mỗi rule nguồn truyện (`data/source`) đi kèm HTML mẫu trong `src/test/resources/fixtures/<sourceId>/` và unit test parse không cần mạng.
- Parser, convert engine, TextSegmenter, TextNormalizer bắt buộc có unit test. UI placeholder không cần test.

## Quy trình làm việc
1. Mỗi tính năng là một lát dọc nhỏ (UI + domain + data + test), hoàn thành và build xanh trước khi sang việc khác.
2. Trước khi commit: `.\gradlew.bat testDebugUnitTest :app:assembleDebug` phải xanh.
3. Commit message tiếng Anh, dạng `feat(scope): ...`, `fix(scope): ...`, `chore: ...`, `docs: ...`.
4. Không cài APK hay chạy lệnh adb khi không có thiết bị (`adb devices` trống) – báo lại để người dùng cắm máy.
5. Khi gặp API Android/AGP mới chưa chắc, đọc tài liệu chính thức trước khi đoán; ghi điều học được vào `docs/DECISIONS.md` nếu ảnh hưởng toàn dự án.
