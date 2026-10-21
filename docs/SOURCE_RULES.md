# Đặc tả rule nguồn truyện (`assets/sources/<id>.json`)

Mỗi website là một file JSON, được `data/source` nạp lúc khởi động. Rule chỉ khai báo *cách lấy dữ liệu*, không có code. Khi site đổi giao diện, chỉ cần sửa file rule.

## Cú pháp selector

```
css@attr|regex:pattern
```

| Phần | Ý nghĩa |
|---|---|
| `css` | Truy vấn CSS của Jsoup. Để trống (bắt đầu bằng `@`) nghĩa là chính phần tử đang xét. Hỗ trợ `:has()`, `:contains()`, `:containsOwn()`, `[attr^=x]`. |
| `@attr` | Giá trị lấy ra: `text` (mặc định, đã chuẩn hoá khoảng trắng), `ownText` (bỏ phần tử con), `html`, hoặc tên thuộc tính. `href`, `src`, `data-*` được chuyển thành URL tuyệt đối. |
| `\|regex:pattern` | Hậu xử lý: giữ nhóm bắt 1 (hoặc toàn bộ khớp). Ví dụ `a@text\|regex:Chương (\d+)`. |

Ví dụ: `h3.truyen-title a@href`, `#total-page@value`, `.info a[itemprop=genre]@text`.

## Cấu trúc file

```jsonc
{
  "id": "truyenfull",                 // duy nhất, dùng làm sourceId trong DB
  "name": "Truyện Full",
  "baseUrl": "https://truyenfull.live",
  "hosts": ["truyenfull.live", "truyenfull.vn"],   // khớp host (bỏ www., m.) và subdomain
  "lang": "vi",
  "fetch": { "mode": "http", "headers": {}, "charset": null },   // mode: http | webview | webview-cf
  "urls": {                            // regex (containsMatchIn) để phân loại URL
    "chapter": "/chuong-\\d+",
    "book": "^https?://[^/]+/[^/]+/?$"
  },
  "search": {                          // bỏ qua nếu site không có tìm kiếm
    "url": "{base}/tim-kiem/?tukhoa={query}",   // {query} đã URL-encode
    "list": "css của từng dòng kết quả",
    "title": "…@text", "link": "…@href",
    "author": "…", "cover": "…", "latestChapter": "…",
    "next": "css link trang kết quả tiếp theo"
  },
  "book": {
    "title": "…", "author": "…", "cover": "…",
    "description": "…@html",           // HTML sẽ được làm sạch thành văn bản
    "status": "…", "genres": "… (nhiều kết quả)",
    "tocUrl": "… (nếu danh sách chương ở trang khác)"
  },
  "toc": {
    "list": "css của từng link chương",
    "title": "@text", "link": "@href",
    "totalPages": "#total-page@value", // cách 1: biết tổng số trang
    "pageUrl": "{url}trang-{page}/",   //   -> tải song song các trang 2..N
    "next": "css link trang sau",      // cách 2: lần theo link "sau" đến khi hết
    "reverse": false,                  // true nếu site liệt kê chương mới nhất trước
    "maxPages": 500
  },
  "chapter": {
    "title": "…",
    "content": "#chapter-c",           // phần tử chứa nội dung
    "remove": ["script", "div[id^=ads]"],       // CSS bị xoá trước khi lấy text
    "removeLines": ["^truyenfull"],             // regex (không phân biệt hoa thường) xoá cả dòng
    "bookUrl": "a.truyen-title@href",  // bắt buộc nếu muốn "Dán link chương" tự tìm truyện
    "prev": "#prev_chap@href", "next": "#next_chap@href"
  }
}
```

## Làm sạch nội dung

`ContentCleaner` luôn: xoá phần tử có `style` chứa `display:none` / `visibility:hidden` / `font-size:0`, xoá `[hidden]`, đổi `<br>` và thẻ khối thành ngắt đoạn, chuẩn hoá khoảng trắng (kể cả NBSP, khoảng trắng CJK), bỏ dòng trống. Sau đó áp `remove` và `removeLines` của rule.

## Quy trình thêm site mới

1. Dùng `curl -A "<UA trình duyệt>"` tải 4 trang: tìm kiếm, trang truyện (và 1 trang danh sách chương khác nếu có phân trang), trang chương. Lưu vào `data/source/src/test/resources/fixtures/<id>/` với tên `search.html`, `book.html`, `toc_page2.html`, `chapter.html`.
2. Viết `assets/sources/<id>.json`.
3. Viết `<Id>RuleTest.kt` theo mẫu `TruyenfullRuleTest`: kiểm tra classify, search, book info, toc (số chương, chỉ số, URL), chapter (tiêu đề, số đoạn, prev/next, không còn rác).
4. `.\gradlew.bat :data:source:testDebugUnitTest` xanh rồi mới commit.

## Giới hạn hiện tại

- Chưa có `json:` cho API trả JSON; chưa có mode `webview` trong fetcher (WebView mới chỉ là trình duyệt trong app).
- Chưa cập nhật rule từ xa; rule chỉ nằm trong assets.
