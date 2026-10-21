# Architecture Decision Records (ngắn gọn)

Mỗi mục: bối cảnh, quyết định, hệ quả. Mới nhất ở trên.

## ADR-010 (2026-10-22): Test rule nguồn bằng dữ liệu ghi sẵn (`expect.json`) thay vì test Kotlin viết tay cho từng site
- Bối cảnh: thêm 17 site một lúc; test viết tay cho mỗi rule (`TruyenfullRuleTest`…) tốn công và dễ bỏ sót trường.
- Quyết định: `RuleProbe` chạy rule trên fixture và làm phẳng mọi giá trị thành `key=value`; `RuleDumpTest` in ra để người viết rule soát; `tools/sources/record_expect.py` ghi thành `fixtures/<id>/expect.json`; `SiteFixturesTest` so sánh lại toàn bộ và bắt buộc mọi rule trong assets phải có thư mục fixture. Engine thêm `toc.pageSize/totalChapters`, `toc.jsonItems/jsonTitle/jsonLink`, `chapter.nextPage` thay vì viết code riêng cho site.
- Hệ quả: thêm site = rule JSON + HTML mẫu + một lệnh ghi; đổi parser làm lệch kết quả sẽ lộ ở tất cả site. Fixture lớn phải cắt bớt danh sách chương (teamtruyen, phithang) để repo không phình. Site mục lục nạp JS/POST vẫn chưa hỗ trợ (ghi trong `docs/SOURCE_RULES.md`).

## ADR-009 (2026-10-21): Phát hành qua GitHub Releases, ký bằng keystore cục bộ, sao lưu ZIP do người dùng giữ
- Bối cảnh: app không có backend, cần cách phân phối và cập nhật không tốn phí, và cách người dùng giữ dữ liệu khi đổi máy.
- Quyết định: (1) APK ký bằng keystore trong `keystore/` + `keystore.properties` (git-ignore); CI nhận keystore qua secrets base64; không có keystore thì release ký debug chỉ để thử. (2) Split ABI arm64/armv7 + universal, R8 bật; rule keep cho sherpa-onnx JNI, pdfbox, serializer. (3) Kiểm tra bản mới bằng GitHub REST API `releases/latest` khi người dùng bấm, không tự động, không gửi thông tin máy. (4) Sao lưu là một file ZIP (JSON + text ebook + bìa) người dùng tự chọn nơi lưu qua SAF; khôi phục gộp, không ghi đè truyện đang có. (5) Nhật ký ghi file trong app, chỉ rời máy khi người dùng chia sẻ.
- Hệ quả: tên tag phải là `vX.Y.Z` và asset APK phải chứa `arm64` để app chọn đúng file. Mất keystore là mất khả năng cập nhật cho người dùng cũ, cần sao lưu keystore riêng. Backup không chứa chương web nên khôi phục cần mạng để đọc lại.

## ADR-007 (2026-10-21): Giọng neural bằng sherpa-onnx AAR tải từ GitHub, model Piper tải theo yêu cầu
- Bối cảnh: sherpa-onnx không có bản ổn định trên Maven Central vào thời điểm này; bản master đã bỏ `dataDir` (espeak-ng) và yêu cầu phonemize bên ngoài, trong khi model Piper tiếng Việt cần espeak-ng. AAR chính thức ~50 MB (4 ABI) không nên commit vào git.
- Quyết định: dùng AAR `sherpa-onnx-1.13.8.aar` (còn hỗ trợ `dataDir`) qua repo Maven cục bộ `third_party/m2` (chỉ commit POM; `tools/sherpa/fetch_sherpa_aar.py` tải AAR, git-ignore). App chỉ đóng gói `arm64-v8a` và `armeabi-v7a`. Model tải từ release `tts-models` dưới dạng tar.bz2 (giải nén bằng commons-compress), lưu `files/models/tts/<id>/`, đánh dấu `.ready`. `CompositeTtsEngine` thay binding `TtsEngine` và định tuyến theo voice id `neural:<model>`; giọng hệ thống vẫn là mặc định. Piper bỏ qua chữ số nên engine neural đọc số bằng `VietnameseNumberSpeller`.
- Hệ quả: người mới clone phải chạy script tải AAR trước khi build; nâng phiên bản sherpa-onnx phải kiểm tra lại API Kotlin (đặc biệt `dataDir`). APK debug tăng ~30 MB; bản phát hành nên dùng App Bundle để tách ABI.

## ADR-008 (2026-10-21): Ebook lưu như sách thường với nội dung chương có sẵn
- Bối cảnh: Reader, player, dịch và tải offline đều làm việc trên `books`/`chapters` + `ChapterStore`; ebook cần dùng lại toàn bộ.
- Quyết định: ebook là một `BookEntity` với `source_id = "ebook"`, URL giả `ebook://<uuid>`, chương `ebook://<uuid>/<i>` có `content_path` ngay khi nhập; bảng `ebooks` chỉ giữ metadata file. Parser thuần JVM (java.util.zip + Jsoup cho EPUB, pdfbox-android cho PDF) để unit test được bằng fixture tạo trong test. Không giữ file gốc sau khi nhập.
- Hệ quả: `SourceRepository` không bao giờ được gọi cho ebook (nội dung luôn có sẵn); xoá ebook = xoá sách. MOBI/AZW3 chưa hỗ trợ.

## ADR-006 (2026-10-21): Luật nhân trên token, glossary theo bộ, ML Kit là chế độ tuỳ chọn
- Bối cảnh: convert thuần khớp dài nhất cho ra trật tự Hán ("X của Y" ngược), không cho người đọc sửa tên riêng, và một số người muốn câu tự nhiên hơn dù sai tên.
- Quyết định: (1) engine trả `ConvertedToken` (nguồn, đích, nghĩa khác); luật nhân kiểu QuickTranslator (`{0}的{1}={1} của {0}`) chạy trên chuỗi token sau khi tra từ điển, nhiều lượt, luật dài nhất thắng, kết quả luật có thể làm placeholder cho luật khác; không áp luật trong chế độ Hán Việt. (2) Glossary lưu trong Room (`glossary`, `book_id` null = toàn cục), được ghép vào engine theo từng lần convert và thắng mọi từ điển; Reader hiện sheet chọn nghĩa khi chạm từ. (3) ML Kit Translation (on-device, miễn phí, không key) là chế độ thứ tư; model chỉ tải khi người dùng bấm, có nút xoá; không có model thì tự quay về Convert. ML Kit không có ánh xạ từ nên không hỗ trợ chạm từ / glossary.
- Hệ quả: thêm dependency `com.google.mlkit:translate` (~1 MB mã, model ~60 MB tải riêng). Đổi engine phải cập nhật `ConvertEngineTest`. `GetReadableChapterUseCase` trả thêm `tokens` nên Reader và TTS vẫn dùng cùng văn bản.

## ADR-005 (2026-10-21): Fetch mode `webview` cho site render bằng JS hoặc có WAF
- Bối cảnh: zongheng (mục lục render bằng Nuxt), ixdzs8 (trang chương có bước xác minh trình duyệt bằng JS), 17k (WAF Aliyun) không lấy được bằng OkHttp. Giải mã API/JS riêng từng site dễ hỏng khi site đổi.
- Quyết định: thêm `WebViewHtmlFetcher` dùng WebView ẩn của hệ thống (miễn phí, không thêm dependency) với cơ chế chờ selector `waitFor`; `RoutingHtmlFetcher` chọn HTTP hay WebView theo `FetchOptions`. Rule khai báo `mode`/`waitFor` ở mức `fetch` hoặc ghi đè theo từng phần để chỉ dùng WebView ở trang bắt buộc. Cookie WebView được đồng bộ sang OkHttp để các request HTTP sau có thể qua WAF. Fixture test là DOM đã render lấy từ trình duyệt thật.
- Hệ quả: tải chương qua WebView chậm (2–5 giây/trang) và tuần tự; tải offline các site này sẽ lâu. Chưa xử lý CAPTCHA. WebView cần main thread nên fetcher dùng `AppDispatcher.Main`.

## ADR-004 (2026-10-21): Dịch Trung → Việt bằng convert engine từ điển, định dạng QuickTranslator
- Bối cảnh: dịch máy offline chất lượng cao (LLM) không miễn phí hoặc quá nặng cho điện thoại; cộng đồng đọc truyện convert đã có sẵn từ điển Names/VietPhrase dạng `Hán=Việt` (QuickTranslator) nhưng file lớn (VietPhrase ~1 triệu dòng, >50 MB) không thể nạp thành `HashMap` trên máy yếu.
- Quyết định: app không kèm VietPhrase (bản quyền/kích thước), người dùng tự nhập file; app chỉ kèm từ điển Hán Việt từng chữ (`assets/dicts/hanviet.txt`, ~18.000 chữ) sinh bằng `tools/hanviet/gen_hanviet.py` từ: Hán Việt tự điển Thiều Chửu (tác giả mất 1954, đã hết hạn bảo hộ), danh sách phiên âm của cộng đồng dịch (bổ sung chữ giản thể), Unihan `kVietnamese` (giấy phép Unicode) và ánh xạ giản thể → phồn thể `kTraditionalVariant`; `hanviet_overrides.txt` sửa các chữ thường gặp. Chỉ dùng Unihan không đủ: thiếu ~12% chữ giản thể trong truyện mạng và âm đầu tiên đôi khi là âm Nôm (第 = đậy). Đổi file này phải tăng `BUILTIN_HANVIET_VERSION` trong `DictionaryManager` để máy đã cài build lại chỉ mục. Từ điển nhập xong được build thành file nhị phân sắp xếp (`IndexedDictionary`, magic `AIDC`) và tra qua mmap + tìm nhị phân, khớp dài nhất từ trái sang. Thứ tự ưu tiên: glossary bộ truyện > Names > VietPhrase > Hán Việt từng chữ. Reader và TTS dùng chung `GetReadableChapterUseCase` để văn bản nhìn và nghe trùng nhau. ML Kit zh→vi để ở mức P1, tải model theo yêu cầu.
- Hệ quả: chất lượng convert phụ thuộc từ điển người dùng; đổi định dạng `.dict` phải tăng version trong header và build lại. Cần mode `webview` ở fetcher trước khi thêm zongheng/ixdzs8/17k.

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
