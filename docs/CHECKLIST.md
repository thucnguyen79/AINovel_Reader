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
- [ ] Cài APK lên máy thật và chụp màn hình (chờ thiết bị)

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
- [ ] Thêm 4 site Việt tiếp theo (metruyenchu, wikidich, tvtruyen, xtruyen) kèm fixture
- [ ] Kiểm tra trên máy thật: dán link truyenfull, đọc, lịch sử, WebView (chờ thiết bị)
- [ ] Tài liệu `docs/SOURCE_RULES.md` (đã viết) cập nhật khi thêm `json:` / mode `webview`

## Giai đoạn 2 – Audio player
- [ ] `TextSegmenter`, `TextNormalizer` + test
- [ ] `SystemTtsEngine` (TextToSpeech → file)
- [ ] `AudioCache`, `PlaybackQueue`
- [ ] Media3 `MediaSessionService`, notification, lock screen
- [ ] Mini-player + player đầy đủ, 5 nút điều khiển
- [ ] Hẹn giờ tắt, tốc độ / cao độ / chọn giọng
- [ ] Tự chuyển chương, phục hồi đúng câu
- [ ] "Nghe chương này" trong WebView

## Giai đoạn 3 – Tải truyện & offline
- [ ] WorkManager download, chọn khoảng chương, thông báo tiến độ
- [ ] Tab Tải truyện, quản lý dung lượng
- [ ] DNS-over-HTTPS
- [ ] Sao lưu / khôi phục ZIP

## Giai đoạn 4 – Dịch truyện offline
- [ ] Convert engine (trie, Names / VietPhrase / Hán Việt / luật nhân) + test
- [ ] Nhập từ điển, glossary theo bộ
- [ ] 5 site Trung + fixture
- [ ] Tab Dịch truyện, dịch cả bộ chạy nền
- [ ] ML Kit zh → vi

## Giai đoạn 5 – Ebook + giọng neural
- [ ] Nhập EPUB / TXT / ZIP / PDF, URL / Google Drive
- [ ] sherpa-onnx + quản lý model Piper vi_VN
- [ ] Highlight câu đang đọc

## Giai đoạn 6 – Hoàn thiện & phát hành
- [ ] Nhạc nền, khoá màn hình, chia sẻ log
- [ ] Source Tester, 30+ site
- [ ] Kiểm tra cập nhật qua GitHub Releases
- [ ] Test Android 8–15, chính sách riêng tư
- [ ] Phát hành APK 1.0
