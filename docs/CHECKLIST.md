# Checklist triển khai

Theo `docs/PLAN.md` mục 6. Cập nhật khi hoàn thành từng task. Ký hiệu: `[x]` xong, `[~]` đang làm, `[ ]` chưa làm.

## Giai đoạn 0 – Khởi tạo (xong 21/10/2026)
- [x] Project đa module, Version Catalog, convention plugins trong `build-logic`
- [x] Hilt, Room plugin, DataStore, Navigation, Compose BOM
- [x] Theme dark M3, 4 tab + mini-player placeholder
- [x] `CLAUDE.md`, `docs/PLAN.md`, `docs/DECISIONS.md`, README
- [x] GitHub Actions: lint + test + APK debug
- [x] Build debug từ terminal, unit test chạy
- [x] git init, commit đầu tiên
- [x] Cài APK lên máy thật và chụp màn hình (OPPO CPH2357, Android 14, 21/10/2026)

## Giai đoạn 1 – Source engine + Reader (bắt đầu 21/10/2026)
### Epic 1.1 Rule engine (`data/source`)
- [x] Model rule JSON (`SourceRule`) với kotlinx.serialization
- [x] Cú pháp selector: CSS + `@text/@html/@attr`, `regex:`
- [x] Parser: search, book, toc (phân trang theo `totalPages` hoặc `next`), chapter (content, prev/next, làm sạch)
- [x] `SourceRegistry`: nạp rule từ `assets/sources/`, resolve theo host
- [x] `HtmlFetcher` OkHttp (UA, timeout, charset)
- [x] Rule `truyenfull` + fixture HTML + unit test (search, book, toc 2 trang, chapter)
- [x] Unit test selector và URL resolve
### Epic 1.2 Dữ liệu (`core/database`, `core/storage`, `data/library`)
- [x] Room: `books`, `chapters`, `reading_progress`, `history`; DAO; Hilt module
- [x] `ChapterStore`: lưu nội dung chương ra file trong app storage
- [x] `LibraryRepository`: upsert truyện + chương, lấy nội dung (cache), tiến độ, lịch sử
- [x] Use case `OpenUrl` (resolve link truyện / chương); nội dung và tiến độ đi qua `LibraryRepository`
### Epic 1.3 Giao diện
- [x] Home: dán link từ clipboard, mở link, tìm kiếm trên các nguồn, danh sách website hỗ trợ
- [x] Màn hình chi tiết truyện: thông tin, danh sách chương, nút đọc / tiếp tục
- [x] Reader: hiển thị chương, cỡ chữ, chương trước/sau, danh sách chương, lưu vị trí
- [x] Lịch sử trong Tủ sách: mở lại đúng chương
- [x] WebView trong app: thanh URL, back/forward, "Đọc chương này"
### Epic 1.4 Chất lượng
- [x] Build + unit test xanh, cập nhật checklist, commit, push
- [x] Thêm site metruyenchu, wikidich, tvtruyen kèm fixture + 15 unit test (engine thêm `bookId`, `pageList`, `pageJson`, `pageHeaders`, placeholder `{base}`/`{bookId}`)
- [ ] xtruyen.net: cần mode `webview` hoặc giải mã API admin-ajax (nội dung và danh sách chương nạp bằng JS) – chuyển sang khi làm fetch mode `webview`
- [x] Thử metruyenchu (1856 chương qua 19 trang AJAX) và tvtruyen (2462 chương qua 50 trang) trên máy thật; đọc chương OK
- [x] Ảnh bìa: CDN của tvtruyen yêu cầu Referer của site → `BookCover` gửi Referer theo origin URL truyện, Coil dùng chung OkHttpClient của app
- [x] Kiểm tra trên máy thật: dán link truyenfull, đọc, lịch sử, WebView (đã sửa 4 lỗi phát hiện: thiếu serializer route, inset cộng đôi, trang chủ bị nhập thành truyện, popup quảng cáo chặn chạm trong WebView)
- [x] Tài liệu `docs/SOURCE_RULES.md` cập nhật (bảng rule, trường TOC mới, ghi chú xtruyen)

## Giai đoạn 2 – Audio player (bắt đầu 21/10/2026)
- [x] `TextSegmenter`, `TextNormalizer` + 11 unit test (tách câu, giới hạn độ dài, ngắt dấu phẩy, bỏ dấu chấm chống copy, gộp dấu câu lặp)
- [x] `SystemTtsEngine` (TextToSpeech → WAV, chọn giọng vi-VN, pitch; tốc độ do player xử lý để cache không phụ thuộc tốc độ)
- [x] `AudioCache` (LRU 500 MB theo hash engine+giọng+pitch+text), `ChapterAudioPipeline` (tổng hợp trước N câu, nối chương tự động)
- [x] Media3 `PlaybackService` (MediaSessionService, custom command chương trước/sau, sleep timer, publish trạng thái qua session extras)
- [x] Mini-player nối với `PlaybackController`; màn hình player: 5 nút, slider theo câu, tốc độ, hẹn giờ, danh sách chương, dừng
- [x] Hẹn giờ tắt (player), tốc độ / cao độ / chọn giọng / ngắt dấu phẩy / chế độ ổn định (Cài đặt, DataStore `TtsPreferences`)
- [x] Tự chuyển chương trong pipeline; lưu tiến độ theo đoạn khi đổi câu (reader và player dùng chung `reading_progress`)
- [x] Nút Nghe ở Reader (từ đoạn đang xem), chi tiết truyện (từ vị trí đã lưu), WebView ("Nghe chương này")
- [x] Thử trên máy thật (OPPO CPH2357): Google TTS vi-VN tổng hợp câu đầu sau ~3 giây, phát tiếp khi tắt màn hình, thông báo màn hình khoá có bìa + nút chương trước/sau, chuyển câu/chương, tốc độ 1.5x
- [ ] Hẹn giờ tắt: đã code, chưa đo trên máy
- [x] Quyền POST_NOTIFICATIONS: đã được cấp trên máy test, thông báo hiển thị

## Giai đoạn 3 – Tải truyện & offline (bắt đầu 21/10/2026)
- [x] `ChapterDownloadWorker` (Hilt, foreground dataSync, nghỉ 0,9–1,6 s giữa chương, backoff 429/403, retry 3 lần) + bảng `downloads` (Room v2 auto-migration); sheet chọn từ chương đầu / đang đọc và 30 / 100 / 300 / tất cả; thông báo tiến độ + hoàn tất; đã tải thử 30 chương truyenfull trên máy (~1,5 s/chương)
- [x] Tab Tải truyện: số chương đã tải / lưu tạm, dung lượng, tiến độ + huỷ, xoá dữ liệu đã tải; Cài đặt: tổng dung lượng chương + audio, nút xoá bộ nhớ tạm
- [x] DNS-over-HTTPS (Hệ thống / Google / Cloudflare) qua `SwitchableDns` cho OkHttp, có fallback; WebView không dùng DoH (giới hạn WebView)
- [ ] Sao lưu / khôi phục ZIP (chưa làm)
- [ ] Tải trước audio N chương kế tiếp khi sạc / Wi-Fi (P1, chưa làm)

## Giai đoạn 4 – Dịch truyện offline (bắt đầu 21/10/2026)
### Epic 4.1 Convert engine (`data/translate`)
- [x] `IndexedDictionary`: file nhị phân mmap, khoá sắp xếp theo byte UTF-8, tra `longestMatch` không nạp cả từ điển vào heap
- [x] `DictionaryTextParser` đọc định dạng QuickTranslator (`Hán=Việt`, nghĩa đầu trước `/`)
- [x] `ConvertEngine`: glossary > Names > VietPhrase (khớp dài nhất) > Hán Việt từng chữ; 3 chế độ Convert / Hán Việt / Nguyên văn; đổi dấu câu Trung, viết hoa đầu câu
- [x] Từ điển Hán Việt có sẵn (~18.000 chữ, Thiều Chửu + phiên âm cộng đồng + Unihan, sinh bằng `tools/hanviet/gen_hanviet.py`)
- [x] Unit test `ConvertEngineTest` (6 test)
- [x] Luật nhân: mẫu `{0}的{1}={1} của {0}` áp lên chuỗi token sau khi tra từ điển, nhiều lượt, luật dài nhất thắng; 5 luật mặc định, nhập LuatNhan.txt để thay
- [x] Nhiều nghĩa: engine giữ token nguồn ↔ đích và các nghĩa khác (VietPhrase `a/b/c`, Hán Việt) cho Reader
### Epic 4.2 Từ điển và tích hợp
- [x] `DictionaryManager`: nhập Names.txt / VietPhrase.txt qua SAF → build `.dict` trong `files/dicts/`, meta.json, gỡ từ điển
- [x] `TranslationRepository` + `TranslatePreferences` (chế độ mặc định), `GetReadableChapterUseCase`
- [x] Reader: sách `lang = zh` hiển thị bản convert, menu đổi Convert / Hán Việt / Nguyên văn trên thanh tiêu đề
- [x] Nghe: `ChapterAudioPipeline` đọc văn bản đã convert (cùng use case với Reader)
- [x] Glossary theo bộ truyện: bảng `glossary` (Room v3, book_id null = mọi truyện), ưu tiên trên mọi từ điển; Reader chạm từ → sheet chọn nghĩa khác / nhập nghĩa / lưu cho truyện hoặc mọi truyện; menu "Từ riêng của truyện" để xem và xoá
- [x] Dịch cả bộ chạy nền: không cần, convert theo chương khi mở (< 100 ms/chương); ML Kit có cache đoạn trong bộ nhớ
### Epic 4.3 Site Trung
- [x] Fetcher hỗ trợ charset: sniff `<meta charset>`, GBK/GB2312 → GB18030, `search.queryCharset` để mã hoá từ khoá
- [x] `52shuku` (52shuku.net) + fixture + test
- [x] `jjwxc` (jjwxc.net, GB18030) + fixture + test
- [x] Fetcher mode `webview` (`WebViewHtmlFetcher`, `RoutingHtmlFetcher`, `fetch.waitFor`, ghi đè mode theo phần, chép cookie sang OkHttp)
- [x] `zongheng` (book/toc webview, chương HTTP), `ixdzs8` (chương webview), `17k` (bản h5, toàn bộ webview) + fixture DOM đã render + test
- [x] `urls.rewrite` viết lại link mobile (m.zongheng, www/m.17k) về host rule hiểu; `toc.url` mẫu URL mục lục
- [x] Kiểm tra trên máy 21/10/2026: mở sách + chương 1 của cả ba site từ tab Dịch truyện / trình duyệt trong app
### Epic 4.4 Tab Dịch truyện
- [x] Dán link truyện Trung (dùng chung `OpenUrlUseCase`), chip site Trung, danh sách "Truyện đã dịch" từ lịch sử
- [x] Chọn chế độ mặc định, nhập / thay / gỡ từ điển
- [x] ML Kit zh → vi: chế độ "Dịch máy", model tải theo yêu cầu (Wi-Fi) trong tab Dịch truyện, xoá được; thiếu model thì tự quay về Convert
- [x] Kiểm tra trên máy thật 21/10/2026: mở jjwxc (GB18030) từ tab Dịch truyện, đổi Convert / Hán Việt / Nguyên văn trong Reader, nhập Names.txt + VietPhrase.txt qua SAF, nghe chương bằng văn bản đã convert

## Giai đoạn 5 – Ebook + giọng neural (bắt đầu 21/10/2026)
### Epic 5.1 Nhập ebook (`data/ebook`)
- [x] Parser thuần JVM + unit test: EPUB 2/3 (container → OPF → spine, tiêu đề từ NCX/nav, ảnh bìa), TXT (tách chương theo tiêu đề Việt/Trung, cắt phần khi không có tiêu đề, tự nhận UTF-8/UTF-16/GB18030), ZIP chứa TXT (sắp xếp tự nhiên), PDF chữ (pdfbox-android, nối dòng bị ngắt)
- [x] `EbookRepository`: nhập từ SAF hoặc link tải trực tiếp / Google Drive, lưu thành sách `source_id = ebook` với nội dung chương sẵn, bảng `ebooks` (Room v4), ảnh bìa trong `files/covers`
- [x] Tab EBook: nút Nhập file / Nhập từ link, danh sách, xoá; mở sách như truyện web (đọc, nghe, dịch nếu tiếng Trung)
- [ ] MOBI / AZW3 (không có thư viện miễn phí gọn; để sau)
- [ ] Mở file từ app khác (intent ACTION_VIEW) – để sau
### Epic 5.2 Giọng neural (`tts/neural`)
- [x] sherpa-onnx 1.13.8 (AAR từ GitHub Releases, repo Maven cục bộ `third_party/m2`, script `tools/sherpa/fetch_sherpa_aar.py`)
- [x] `NeuralModelManager`: 3 model Piper vi_VN (VAIS1000 medium, 25hours low, VIVOS x_low), tải tiếp tục được, giải nén tar.bz2, trạng thái/tiến độ, xoá
- [x] `NeuralTtsEngine` (VITS + espeak-ng-data, đọc số bằng chữ) và `CompositeTtsEngine` chọn engine theo voice id `neural:<model>`
- [x] Cài đặt: mục "Giọng AI offline" tải / dùng / xoá model; giọng neural xuất hiện trong danh sách giọng
- [x] Kiểm tra trên máy 21/10/2026: nhập EPUB 5 chương, tải VIVOS (20 giây), nghe bằng giọng neural, highlight đoạn đang đọc
### Epic 5.3 Reader
- [x] Highlight đoạn đang được đọc (theo `PlaybackState.paragraphIndex`) khi chương đang mở trùng chương đang phát
- [x] Highlight từng câu: `TextSegmenter` tách trên văn bản gốc và giữ `start/end` của câu trong đoạn, chuẩn hoá cho engine theo từng câu; pipeline phát `segmentStart/End` qua MediaItem extras → `PlaybackState`
- [x] Tự cuộn tới câu đang đọc; kéo tay thì tạm dừng theo dõi, nút "Về câu đang đọc" bật lại
- [x] Nút tai nghe không phát lại khi chương đang phát (chỉ mở màn hình Đang nghe); nhấn giữ một đoạn để nghe từ đó
- [x] Màn hình Đang nghe có nút "Mở văn bản" tới đúng chương, thay Reader đang có trên back stack
- [x] Kiểm tra trên máy 21/10/2026 với truyện truyenfull chương 44

## Giai đoạn 6 – Hoàn thiện & phát hành (bắt đầu 21/10/2026)
### Epic 6.1 Dữ liệu người dùng
- [x] Sao lưu / khôi phục ZIP (`BackupRepository`): truyện, chương, tiến độ, lịch sử, từ riêng, ebook kèm nội dung và bìa; khôi phục gộp theo URL truyện, giữ tiến độ mới hơn
- [x] Chia sẻ nhật ký: `FileLoggingTree` ghi INFO+ vào `files/logs/app.log` (xoay 512 KB), gửi qua FileProvider chỉ cho thư mục logs
- [ ] Tải trước audio N chương kế tiếp khi sạc / Wi-Fi (P1, chưa làm)
### Epic 6.2 Phát hành
- [x] Cấu hình release: R8 minify + shrink, rule cho sherpa-onnx / pdfbox / serialization, ký bằng `keystore.properties` (git-ignore, fallback debug), split ABI arm64 / armv7 / universal, version 1.0.0 (100)
- [x] Kiểm tra bản mới qua GitHub Releases API (`UpdateChecker`), nút Tải bản mới trong Cài đặt › Giới thiệu
- [x] Chính sách riêng tư `docs/PRIVACY.md`, mục Giới thiệu (phiên bản, mã nguồn, giấy phép thư viện)
- [x] Workflow `release.yml`: tag `v*` → build, ký từ secrets, đính APK vào Release; CI tải AAR sherpa-onnx trước khi build
- [x] Lint: sửa hết lỗi mức Error (LocalResources, UnstableApi, BOM, onRenderProcessGone)
- [x] Build release thử trên máy (arm64, minified)
- [ ] Nhạc nền khi nghe (P2, chưa làm)
- [ ] Source Tester trong app, mở rộng 30+ site (làm dần sau 1.0)
- [ ] Test Android 8–13 và 15 trên máy thật (chỉ có OPPO Android 14)
- [ ] Nộp Google Play (không trong phạm vi 1.0; kênh chính là GitHub Releases)
- [ ] Gắn tag `v1.0.0` và tạo Release đầu tiên (người dùng quyết định thời điểm)
