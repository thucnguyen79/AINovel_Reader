# AINovel Reader – Kế hoạch triển khai (bản 2)

> Ứng dụng Android **miễn phí, không tài khoản, không backend**: đọc truyện và nghe truyện audio từ các website được hỗ trợ, tải về máy để đọc/nghe offline, nhập ebook, dịch truyện Trung → Việt. Toàn bộ dịch và TTS dùng công cụ **miễn phí**, chạy chủ yếu trên thiết bị.
>
> Phát triển bằng **Claude Desktop (Code)**. Lập: 21/10/2026, chỉnh sửa lần 2 cùng ngày. Tham khảo app mẫu (phiên bản 4.0.0) qua 14 ảnh chụp màn hình.

---

## 0. Ràng buộc thiết kế

| Ràng buộc | Hệ quả thiết kế |
|---|---|
| Miễn phí, không thanh toán, không tài khoản | Bỏ Play Billing, VIP, đăng nhập, đồng bộ đa thiết bị. Không có "gói" nào; mọi tính năng mở cho mọi người. |
| Không backend | Không proxy API, không server. Dữ liệu chỉ nằm trên máy. File rule nguồn truyện host tĩnh trên GitHub (miễn phí). |
| Dịch và TTS chỉ dùng công cụ miễn phí | TTS: Google TTS hệ thống + giọng neural on-device (sherpa-onnx/Piper). Dịch: bộ máy convert Hán-Việt offline (VietPhrase) + ML Kit on-device; tuỳ chọn LLM gói miễn phí bằng key của chính người dùng. |
| Lưu offline trên máy | Nội dung chương, audio cache, ebook, từ điển đều nằm trong bộ nhớ riêng của app (app-specific storage), không cần quyền truy cập bộ nhớ toàn cục. |
| Phát triển bằng Claude Code | Mọi thao tác build, test, cài đặt, chụp màn hình phải chạy được từ dòng lệnh (Gradle wrapper + adb). Có `CLAUDE.md` mô tả quy ước dự án. |

---

## 1. Phân tích app tham khảo

| Tab | Chức năng quan sát được |
|---|---|
| **Trang chủ** | Hẹn giờ tắt; banner bật Google DNS; ô dán link truyện + tìm kiếm; danh sách "Website hỗ trợ" (hơn 100 site Việt); 3 nút nhanh: Giọng / Tải truyện / Đọc truyện |
| **Tủ sách** | Lịch sử (truyện, chương cuối, thời điểm nghe), Tải truyện (danh sách đã tải), EBook (EPUB/PDF/TXT/ZIP/MOBI/AZW3, link Google Drive) |
| **Dịch truyện** | Dán link chương tiếng Trung (52shuku, 10sto, 256wx, ixdzs8, 17k, zongheng, ranwenapp, tycqxs, jjwxc) → bản dịch tiếng Việt; "Truyện đã dịch" |
| **Cài đặt** | Tốc độ, cao độ, ngôn ngữ truyện, giọng đọc (Google / AI), âm lượng, nhạc nền, hẹn giờ, ngắt dấu phẩy, chế độ phát ổn định, xoá cache, khoá màn hình, gửi log lỗi |
| **Trình duyệt trong app** | WebView mở site nguồn, thanh nổi "Tải chương này / Nghe chương này" + mini-player |
| **Mini-player** | Tiến độ %, tên truyện, chương, 5 nút: chương trước / lùi / play / tiến / chương sau |

Bỏ qua từ app mẫu: VIP, Facebook, Discord.

---

## 2. Danh sách tính năng

**P0** = MVP bắt buộc, **P1** = bản 1.0, **P2** = mở rộng.

### 2.1 Nguồn truyện & khám phá
- **P0** Dán link truyện / link chương → nhận diện site, lấy thông tin truyện và danh sách chương.
- **P0** Nguồn truyện dạng **rule JSON**, ship kèm app và cập nhật từ GitHub (xem 4.3).
- **P0** Tìm kiếm theo tên truyện trên một hoặc nhiều nguồn.
- **P0** Trình duyệt trong app (WebView) với thanh "Đọc / Nghe / Tải chương này" cho site chưa có rule.
- **P1** Trích xuất nội dung tổng quát (readability) cho site lạ.
- **P1** Danh sách website hỗ trợ có lọc theo ngôn ngữ / thể loại.
- **P1** DNS-over-HTTPS (Google / Cloudflare) bật trong app.
- **P1** Người dùng tự nhập / sửa rule, có màn hình Source Tester.
- **P2** Vượt Cloudflare challenge bằng WebView ẩn, tái dùng cookie cho OkHttp.

### 2.2 Đọc truyện
- **P0** Trình đọc: cuộn dọc / lật trang, cỡ chữ, font, giãn dòng, lề, theme (sáng / tối / sepia / OLED), độ sáng.
- **P0** Danh sách chương, nhảy chương, tự lưu vị trí.
- **P0** Chương trước / sau, tải trước chương kế.
- **P1** Highlight câu đang được đọc, chạm câu để nghe từ đó.
- **P1** Bookmark, tìm trong chương, điều khiển bằng nút âm lượng, giữ màn hình sáng.
- **P2** Làm sạch nội dung theo rule (bỏ quảng cáo, dòng rác lặp).

### 2.3 Nghe truyện (audio)
- **P0** **Giọng hệ thống**: Google Speech Services (miễn phí, có giọng vi-VN offline) và mọi engine TTS khác cài trên máy; chọn giọng, tốc độ, cao độ.
- **P0** Phát nền (MediaSessionService): lock screen, tai nghe, thông báo.
- **P0** Mini-player toàn app + player đầy đủ; tiến độ %, 5 nút điều khiển.
- **P0** Tự chuyển chương, tiếp tục đúng câu sau khi thoát app.
- **P0** Hẹn giờ tắt (15/30/60 phút, hết chương).
- **P1** **Giọng neural on-device**: sherpa-onnx + model Piper tiếng Việt (Apache-2.0 / MIT, miễn phí, offline, tải model theo yêu cầu ~60 MB). Cache audio theo câu.
- **P1** Nhạc nền trộn với giọng đọc, âm lượng riêng.
- **P1** "Ngắt dấu phẩy", "chế độ phát ổn định" (buffer trước nhiều câu).
- **P1** Chuẩn hoá văn bản offline: số, ngày tháng, viết tắt, bảng tên Hán Việt.
- **P2** Giọng online miễn phí **tuỳ chọn** (Edge Read Aloud, giọng vi-VN HoaiMy / NamMinh): chất lượng rất tốt nhưng là API không chính thức, có thể ngừng bất kỳ lúc nào, nên chỉ là tuỳ chọn tắt mặc định, có cảnh báo.
- **P2** Xuất chương thành MP3 / OGG.

### 2.4 Tải truyện & offline
- **P0** Tải cả bộ hoặc khoảng chương bằng WorkManager; thông báo tiến độ; tạm dừng / tiếp tục / huỷ.
- **P0** Đọc và nghe offline hoàn toàn (giọng hệ thống offline hoặc giọng neural on-device).
- **P1** Tải trước audio N chương kế tiếp (tuỳ chọn, khi sạc / Wi-Fi).
- **P1** Quản lý dung lượng: dung lượng từng truyện, xoá chương, xoá audio cache.
- **P2** Kiểm tra chương mới theo lịch, thông báo khi có chương.

### 2.5 Tủ sách
- **P0** Lịch sử đọc / nghe; danh sách đã tải; yêu thích.
- **P1** Nhập ebook: EPUB, TXT (tách chương theo regex), ZIP chứa TXT, PDF (trích text).
- **P1** Nhập từ URL trực tiếp / link chia sẻ Google Drive (tải file công khai, không cần đăng nhập).
- **P1** Sao lưu / khôi phục tủ sách ra file ZIP (metadata + tiến độ + glossary), người dùng tự lưu đâu tuỳ ý.
- **P2** MOBI / AZW3.

### 2.6 Dịch truyện (miễn phí)
- **P0** **Dịch convert offline** kiểu VietPhrase: từ điển Names + VietPhrase + Hán Việt, thuật toán ghép cụm dài nhất (longest-match), xử lý xưng hô. Kết quả tức thì, không cần mạng. Đây là cách phần lớn người đọc truyện Trung ở Việt Nam quen dùng.
- **P0** Dán link chương tiếng Trung → lấy nội dung (rule JSON cho site Trung) → convert → lưu "Truyện đã dịch".
- **P1** **ML Kit On-device Translation** (Google, miễn phí, offline, có cặp zh → vi) làm chế độ "dịch máy" thay thế hoặc song song với convert.
- **P1** Từ điển tên riêng (Names) theo bộ truyện, người dùng sửa; nhập / xuất file từ điển cộng đồng (định dạng `.txt` tab-separated quen thuộc).
- **P1** Dịch nối tiếp cả bộ chạy nền.
- **P1** Hiển thị song song: Trung / Hán Việt / Việt khi chạm vào câu.
- **P2** **LLM tuỳ chọn, key của người dùng (BYOK)**: người dùng tự dán API key gói miễn phí (ví dụ Gemini API free tier, Groq, OpenRouter free models) để "trau chuốt" bản convert với glossary. App không gánh chi phí, không có key nhúng. Tắt mặc định.
- **P2** Tóm tắt chương bằng LLM BYOK.

### 2.7 Vận hành (không backend)
- **P0** Không quyền truy cập mạng ngoài việc tải nội dung; không analytics.
- **P1** Ghi log lỗi ra file trong app, nút "Chia sẻ log" (share sheet) thay cho "gửi log lỗi".
- **P1** Khoá màn hình bằng PIN / sinh trắc (BiometricPrompt).
- **P1** Kiểm tra bản mới qua GitHub Releases (tuỳ chọn), link tải APK.

---

## 3. Nền tảng & stack

### 3.1 Quyết định chính

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Nền tảng | **Android native, Kotlin**, minSdk 26, targetSdk mới nhất | Phát nền, MediaSession, TTS hệ thống, WebView, WorkManager là API Android sâu. |
| UI | Jetpack Compose + Material 3 | |
| Kiến trúc | MVVM + Clean nhẹ, đa module | Tách source engine, player, dịch để làm song song và test độc lập. |
| DI / async | Hilt; Coroutines + Flow | |
| CSDL | Room (+ FTS4 tìm kiếm) | Metadata; nội dung chương lưu file. |
| Cài đặt | DataStore | |
| Mạng | OkHttp + DoH interceptor; Jsoup; kotlinx.serialization | |
| JS / Cloudflare | WebView + JavascriptInterface | Fallback và trình duyệt trong app. |
| Audio | **Media3 ExoPlayer + MediaSessionService** | Một pipeline phát cho mọi loại giọng. |
| TTS hệ thống | `TextToSpeech.synthesizeToFile` theo câu | Google TTS miễn phí, offline khi đã tải giọng vi-VN. |
| TTS neural on-device | **sherpa-onnx** (AAR Android) + model Piper `vi_VN` (ví dụ `vits-piper-vi_VN-vais1000-medium`) | Miễn phí, offline, giấy phép mở. Tải model từ GitHub Releases của sherpa-onnx khi người dùng bật. |
| TTS online miễn phí (tuỳ chọn, P2) | Edge Read Aloud (không chính thức) | Chất lượng cao, rủi ro ngừng hoạt động; cô lập trong một module riêng để gỡ dễ. |
| Dịch offline | **Convert engine tự viết** (VietPhrase / Names / Hán Việt, longest-match) | Miễn phí, tức thì, chuẩn "truyện convert". Từ điển: dùng bộ cộng đồng mã mở + cho người dùng nhập. |
| Dịch máy offline | **ML Kit Translation** (zh ↔ vi) | Miễn phí, model ~30 MB tải một lần. |
| LLM (tuỳ chọn, P2) | BYOK qua HTTP trực tiếp từ app: Gemini free tier / Groq / OpenRouter; thiết kế interface `LlmProvider` để thêm Claude API nếu người dùng muốn trả tiền | Không key nhúng, không chi phí vận hành. |
| Tải nền | WorkManager | |
| Ebook | EPUB: Readium Kotlin Toolkit (Apache-2.0) hoặc epublib; PDF: PdfRenderer + pdfbox-android; TXT / ZIP: tự viết | |
| Ảnh | Coil | |
| Điều hướng | Navigation Compose type-safe | |
| Log lỗi | Timber + file logger + chia sẻ file; không Firebase | Không dịch vụ bên ngoài. |
| Phân phối | GitHub Releases (APK ký) + Google Play nếu được duyệt | Play có rủi ro chính sách với trình đọc web; giữ APK là kênh chính. |
| Hosting rule nguồn | GitHub repo công khai `ainovel-sources`, raw URL + version | Miễn phí, có lịch sử, nhận PR cộng đồng. |

### 3.2 Phương án đã loại
- **Backend proxy / Cloudflare Workers**: không cần vì không có API trả phí và không có VIP.
- **Google Cloud TTS, Azure, ElevenLabs, OpenAI TTS, Claude API nhúng**: trả phí, loại. Chỉ để lại cửa BYOK do người dùng tự chọn.
- **Firebase Crashlytics / Analytics**: miễn phí nhưng gửi dữ liệu ra ngoài; thay bằng log cục bộ.
- **Flutter / KMP**: chỉ cân nhắc khi cần iOS.
- **Google Translate web endpoint không chính thức**: vi phạm điều khoản, dễ bị chặn, loại.

---

## 4. Kiến trúc

### 4.1 Cấu trúc module

```
AINovel_Reader/
├── CLAUDE.md                 # Quy ước dự án cho Claude Code (lệnh build/test, cấu trúc, style)
├── docs/                     # PLAN.md, ARCHITECTURE.md, SOURCE_RULES.md, DECISIONS.md
├── app/                      # Application, Navigation, DI root, theme
├── core/
│   ├── common/               # Result, dispatchers, logger (file), extensions
│   ├── designsystem/         # Theme M3, components (card, chip, player bar)
│   ├── network/              # OkHttp, DoH, cookie jar, WebView fetcher
│   ├── database/             # Room entities, DAO, migrations
│   ├── datastore/            # Settings
│   └── storage/              # Bố cục file: chapters/, audio-cache/, models/, dicts/
├── domain/                   # Models, use cases, repository interfaces
├── data/
│   ├── source/               # Rule engine: parser, fetcher, registry, updater (GitHub)
│   ├── library/              # Book / chapter / history / download repositories
│   ├── ebook/                # EPUB / TXT / ZIP / PDF importers
│   └── translate/            # Convert engine (VietPhrase), ML Kit adapter, LlmProvider (BYOK)
├── tts/
│   ├── api/                  # TtsEngine interface, TextSegmenter, TextNormalizer, AudioCache
│   ├── system/               # Android TextToSpeech
│   ├── neural/               # sherpa-onnx + model manager (tải / xoá model)
│   └── online/               # (P2) Edge Read Aloud, cô lập, có thể gỡ
├── feature/
│   ├── home/  browser/  reader/  player/  library/  translate/  settings/
├── service/
│   ├── playback/             # MediaSessionService, PlaybackQueue
│   └── download/             # WorkManager workers
└── tools/                    # Script dev: tải HTML mẫu, chạy Source Tester CLI
```

### 4.2 Luồng dữ liệu

```
URL / tìm kiếm → SourceRegistry.resolve(host) → Rule JSON
    → Fetcher (OkHttp | WebView) → Parser (Jsoup / regex / JSONPath)
    → Book + Chapters → Room + file chương (txt đã làm sạch)

Chapter → TextSegmenter → TextNormalizer
    → TtsEngine (System | Neural | Online).synthesize(sentence) → AudioCache (file)
    → PlaybackQueue → ExoPlayer (MediaSessionService) → notification / lock screen
    → PositionTracker (bookId, chapterId, sentenceIndex) → Room

Link chương Trung → Rule JSON (site Trung) → Chinese text
    → ConvertEngine (Names → VietPhrase → Hán Việt)  [tức thì, offline]
    → (tuỳ chọn) MlKitTranslator  hoặc  LlmProvider BYOK + glossary
    → file bản dịch + bản gốc → Room (translations)
```

### 4.3 Source engine (rule JSON)

```json
{
  "id": "truyenfull",
  "name": "truyenfull.live",
  "hosts": ["truyenfull.live", "truyenfull.vn"],
  "lang": "vi",
  "fetch": { "mode": "http", "headers": { "User-Agent": "Mozilla/5.0 ..." } },
  "search": { "url": "https://truyenfull.live/tim-kiem/?tukhoa={q}",
              "list": ".list-truyen .row", "name": ".truyen-title a@text",
              "url": ".truyen-title a@href", "cover": "img@src" },
  "book":   { "name": "h3.title@text", "author": "a[itemprop=author]@text",
              "cover": ".book img@src", "desc": ".desc-text@html" },
  "toc":    { "list": ".list-chapter li a", "name": "@text", "url": "@href",
              "next": ".pagination .next a@href" },
  "chapter": { "content": "#chapter-c", "remove": ["script", ".ads"],
               "next": "#next_chap@href" }
}
```

- Selector: CSS (Jsoup) + hậu tố `@text / @html / @attr`; tiền tố `regex:` và `json:`.
- `fetch.mode`: `http`, `webview`, `webview-cf`.
- Bộ rule mặc định nằm trong `assets/sources/`; app kiểm tra `https://raw.githubusercontent.com/<org>/ainovel-sources/main/sources.json` (có `version` + `sha256`) mỗi 24 giờ, người dùng có thể tắt.
- Unit test: mỗi rule kèm 2–3 file HTML mẫu trong `testFixtures/`, test chạy không cần mạng.
- Source Tester: màn hình debug + CLI (`tools/source-tester`) để Claude Code chạy kiểm tra rule từ terminal.

### 4.4 Convert engine (dịch Trung → Việt offline)

- Dữ liệu: `Names.txt` (tên riêng, ưu tiên cao nhất), `VietPhrase.txt` (cụm từ → nghĩa, nhiều nghĩa cách nhau `/`), `ChinesePhienAmWords.txt` (Hán Việt từng chữ), `LuatNhan.txt` (luật nhân: mẫu `{0}的{1}` …). Định dạng `trung=việt` mỗi dòng, giống hệ sinh thái QuickTranslator để người dùng dùng lại từ điển sẵn có.
- Thuật toán: xây trie từ từ điển; duyệt câu theo longest-match với thứ tự ưu tiên Names → VietPhrase → Hán Việt; áp luật nhân; hậu xử lý dấu câu, viết hoa đầu câu, khoảng trắng.
- Hiệu năng: từ điển ~1 triệu dòng nạp vào trie tốn vài trăm MB nếu ngây thơ; dùng **double-array trie** hoặc **sorted array + binary search** nén, nạp lười theo lần đầu dùng, lưu dạng nhị phân sau lần nạp đầu.
- Từ điển người dùng theo bộ truyện (`glossary` trong Room) ghi đè `Names`.
- Nguồn từ điển: bộ cộng đồng mã mở trên GitHub (kiểm tra giấy phép trước khi đóng gói); nếu không rõ giấy phép thì **không ship kèm**, chỉ cho người dùng nhập file.

### 4.5 Pipeline audio

1. `TextSegmenter`: tách câu theo `. ! ? …` và xuống dòng; tuỳ chọn tách tại `,`; giới hạn ~200 ký tự.
2. `TextNormalizer`: số → chữ, "Ch.12" → "chương mười hai", ký tự lặp, bảng tên Hán Việt, bỏ emoji / ký tự rác.
3. `TtsEngine.synthesize(text, voice, speed, pitch): File` với 3 cài đặt: `SystemTtsEngine`, `NeuralTtsEngine` (sherpa-onnx), `OnlineTtsEngine` (P2).
4. `AudioCache`: key = hash(text + engine + voice + params); LRU theo dung lượng (mặc định 500 MB), ghim với chương đã tải.
5. `PlaybackQueue`: buffer 3–5 câu (chế độ ổn định: 10–15); nối chương; nhạc nền qua ExoPlayer thứ hai.
6. `MediaSessionService`: custom commands (câu trước / sau, chương trước / sau), hẹn giờ, audio focus, ducking; tuân thủ foreground service type `mediaPlayback` của Android 14+.

### 4.6 Mô hình dữ liệu (Room, rút gọn)

- `books(id, sourceId, url, title, author, cover, description, status, lang, isFavorite, createdAt)`
- `chapters(id, bookId, index, title, url, contentPath, downloadedAt, wordCount)`
- `reading_progress(bookId, chapterId, sentenceIndex, scrollOffset, percent, updatedAt)`
- `history(bookId, lastChapterId, lastAt, mode)`
- `downloads(id, bookId, fromIndex, toIndex, state, progress, error)`
- `ebooks(id, bookId, format, filePath, parsedAt)`
- `translations(id, bookId, chapterId, sourceUrl, engine, contentPath, sourcePath, createdAt)`
- `glossary(bookId, term, translation, note)`
- `audio_cache(key, path, bytes, lastUsedAt, pinned)`
- `tts_models(id, name, sizeBytes, path, state)`

---

## 5. Quy trình phát triển bằng Claude Desktop (Code)

### 5.1 Môi trường cần cài trên máy Windows
- JDK 17 (Temurin), Android SDK (qua Android Studio hoặc `cmdline-tools`), đặt `ANDROID_HOME`, thêm `platform-tools` vào `PATH`.
- Android Studio vẫn nên cài để quản lý SDK, tạo emulator và debug khi cần; nhưng vòng lặp hằng ngày chạy từ terminal.
- Một điện thoại Android bật USB debugging (hoặc wireless adb) để test TTS và phát nền thật; emulator không có Google TTS đầy đủ.
- Git.

### 5.2 Vòng lặp làm việc
```bash
.\gradlew.bat :app:assembleDebug
```
```bash
.\gradlew.bat testDebugUnitTest
```
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
```bash
adb exec-out screencap -p > shot.png
```
- Claude Code build, chạy test, cài APK, chụp màn hình và đọc `adb logcat` để tự kiểm tra; người dùng chỉ cần xác nhận trên máy thật ở các mốc.
- Mỗi tính năng là một **lát dọc nhỏ** (UI + domain + data + test), một nhánh, một commit có ý nghĩa.
- Parser rule, convert engine, TextSegmenter, TextNormalizer phải có unit test với dữ liệu mẫu, vì đây là nơi Claude Code tự xác minh được mà không cần nhìn UI.

### 5.3 `CLAUDE.md` (tạo ở giai đoạn 0)
Nội dung: lệnh build / test / cài đặt; sơ đồ module và quy tắc phụ thuộc; style Kotlin (ktlint); quy ước đặt tên; cách thêm rule nguồn mới (file + fixture + test); những gì cấm (không nhúng key, không thêm dependency trả phí, không gọi dịch vụ ngoài danh sách cho phép); checklist trước khi commit.

### 5.4 Tài liệu sống trong `docs/`
- `PLAN.md` (file này), `ARCHITECTURE.md`, `SOURCE_RULES.md` (đặc tả rule JSON), `TRANSLATE_ENGINE.md`, `DECISIONS.md` (ADR ngắn mỗi quyết định).

---

## 6. Lộ trình

Ước lượng cho một người phát triển cùng Claude Code. Thời gian đã rút ngắn so với bản 1 vì bỏ backend, thanh toán, tài khoản.

| Giai đoạn | Thời gian | Nội dung | Kết quả |
|---|---|---|---|
| **0. Khởi tạo** | 1 tuần | Project đa module, Version Catalog, Hilt, Room, DataStore, Navigation, theme dark M3, `CLAUDE.md`, GitHub Actions (lint + test + APK debug), khung 4 tab + mini-player rỗng | App chạy, build từ terminal |
| **1. Source engine + Reader** | 3 tuần | Rule engine + 5 site Việt (truyenfull, metruyenchu, wikidich, tvtruyen, xtruyen) kèm fixture test; dán link + tìm kiếm; WebView trong app với "Đọc chương này"; Reader cơ bản; tiến độ, lịch sử | Dán link → đọc, nhớ vị trí |
| **2. Audio player** | 3 tuần | TextSegmenter, TextNormalizer, SystemTtsEngine → file, Media3 service, mini-player + player, hẹn giờ, tốc độ / cao độ / giọng, tự chuyển chương, "Nghe chương này" trong WebView | Nghe nền ổn định, lock screen |
| **3. Tải truyện & offline** | 2 tuần | WorkManager download, chọn khoảng chương, tab Tải truyện, quản lý dung lượng, DoH, sao lưu / khôi phục ZIP | Tải cả bộ, nghe offline |
| **4. Dịch truyện offline** | 3 tuần | Convert engine (trie, Names / VietPhrase / Hán Việt / luật nhân) + test; nhập từ điển; 5 site Trung (52shuku, ixdzs8, zongheng, jjwxc, 17k); tab Dịch truyện; glossary theo bộ; ML Kit zh → vi; dịch cả bộ chạy nền | Dán link Trung → bản dịch tức thì |
| **5. Ebook + giọng neural** | 2 tuần | Nhập EPUB / TXT / ZIP / PDF, URL / Google Drive; sherpa-onnx + quản lý model Piper vi_VN; cache audio; highlight câu đang đọc | Nhập sách, giọng AI offline |
| **6. Hoàn thiện & phát hành** | 2 tuần | Nhạc nền, khoá màn hình, chia sẻ log, Source Tester, mở rộng lên 30+ site, kiểm tra cập nhật qua GitHub Releases, test Android 8–15 trên nhiều máy, chính sách riêng tư, phát hành APK trên GitHub Releases; nộp Play nếu muốn | Bản 1.0 |
| **7. Sau phát hành** | liên tục | Thêm site theo yêu cầu, LLM BYOK (P2), giọng online tuỳ chọn, MOBI / AZW3, Wear / Auto, thông báo chương mới | |

Tổng: **khoảng 16 tuần** đến bản 1.0 (giai đoạn 4 là nặng nhất vì convert engine thay thế cho LLM trả phí). Bản dùng nội bộ nghe được ở **tuần 7**.

### Tiêu chí hoàn thành mỗi giai đoạn
- Parser rule, convert engine, segmenter có unit test chạy xanh trên CI, không cần mạng.
- Player: nghe 2 giờ liên tục không ngắt; pin dưới 5%/giờ khi tắt màn hình; phục hồi đúng câu sau khi hệ thống kill app.
- Download: 500 chương không lỗi, tiếp tục sau mất mạng.
- Convert: một chương 3.000 chữ Hán dịch dưới 300 ms trên máy tầm trung.

---

## 7. Rủi ro & giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Website đổi cấu trúc | Cao | Rule cập nhật từ GitHub; Source Tester; fallback readability; nút báo lỗi kèm URL |
| Cloudflare / chặn bot | Cao | WebView mode, tái dùng cookie, giới hạn tốc độ tải, DoH |
| Bản quyền & chính sách Google Play | Cao | App không host nội dung; phân phối APK qua GitHub là kênh chính; cơ chế gỡ nguồn |
| Chất lượng giọng tiếng Việt miễn phí | Trung | Google TTS làm nền; Piper vi_VN cho người muốn giọng tự nhiên hơn; đo thử trên máy thật ở giai đoạn 2 |
| Chất lượng dịch convert thấp hơn LLM | Trung | Chuẩn convert là thứ người đọc truyện Trung đã quen; glossary theo bộ; ML Kit làm lựa chọn 2; BYOK LLM cho ai có key |
| Giấy phép từ điển cộng đồng | Trung | Chỉ ship từ điển có giấy phép rõ; còn lại cho người dùng tự nhập |
| sherpa-onnx tăng kích thước APK / RAM | Trung | Model tải theo yêu cầu; native lib chỉ cho `arm64-v8a` + `armeabi-v7a`; App Bundle split |
| Hệ thống kill service phát nền | Trung | Foreground service đúng chuẩn Android 14+, buffer audio trước, hướng dẫn tắt tối ưu pin |
| API online không chính thức (Edge TTS) ngừng | Thấp (vì chỉ tuỳ chọn) | Cô lập module, tắt mặc định, có cảnh báo |

---

## 8. Việc cần quyết định trước khi bắt đầu giai đoạn 0

1. **Tên app và package name** (đề xuất `vn.esstar.ainovel`).
2. **GitHub**: tên repo app và repo `ainovel-sources` (công khai để host rule).
3. **10 website ưu tiên** làm rule đầu tiên. Đề xuất theo app mẫu: truyenfull.live, metruyenchu.com.vn, wikidichvn.com, www.tvtruyen.com, xtruyen.net; 52shuku.net, ixdzs8.com, m.zongheng.com, jjwxc.net, 17k.com.
4. **Từ điển convert**: bạn đã có bộ VietPhrase / Names nào chưa, hay app chỉ hỗ trợ nhập?
5. **Kênh phát hành**: chỉ APK trên GitHub, hay nộp cả Google Play.

---

## 9. Bước đầu tiên khi bắt đầu giai đoạn 0

1. Tạo project Android (Empty Activity Compose, Kotlin DSL, minSdk 26), `git init`, commit đầu tiên.
2. Tạo cấu trúc module như mục 4.1, Version Catalog, Hilt, Room, DataStore.
3. Viết `CLAUDE.md` theo 5.3.
4. Theme dark M3 + 4 tab + mini-player placeholder; build và cài lên máy thật từ terminal.
5. Module `data/source` với rule truyenfull + fixture HTML + unit test đầu tiên.
6. GitHub Actions: lint + test + build APK debug.
