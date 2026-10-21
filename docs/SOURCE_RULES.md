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
                                       // charset: gợi ý khi header/meta không nói (vd "GB18030"); fetcher tự sniff <meta charset>
  "urls": {                            // regex (containsMatchIn) để phân loại URL
    "chapter": "/chuong-\\d+",
    "book": "^https?://[^/]+/[^/]+/?$"
  },
  "search": {                          // bỏ qua nếu site không có tìm kiếm
    "url": "{base}/tim-kiem/?tukhoa={query}",   // {query} đã URL-encode
    "queryCharset": "GBK",             // (tuỳ chọn) bảng mã để encode {query}, mặc định UTF-8
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
    "pageUrl": "{url}/trang-{page}/",  //   -> tải song song các trang 2..N
                                       //   placeholder: {url} (URL truyện, không có / cuối), {base}, {bookId}, {page}
    "bookId": "input[name=bid]@value", //   id nội bộ của truyện cho template có {bookId}
    "pageList": "ul li a",             //   selector dùng cho trang 2..N nếu là fragment HTML khác cấu trúc
    "pageJson": "data",                //   nếu trang 2..N là JSON: tên trường chứa HTML (đường dẫn chấm)
    "pageHeaders": { "X-Requested-With": "XMLHttpRequest" },  // header thêm cho trang 2..N
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

## Các rule hiện có (21/10/2026)

| id | Host | Danh sách chương | Ghi chú |
|---|---|---|---|
| `truyenfull` | truyenfull.live (.vn, .vision, .io, .tv) | `#total-page` + `{url}/trang-N/` | Popup quảng cáo chặn chạm trong WebView, đã xử lý ở trình duyệt |
| `metruyenchu` | metruyenchu.com.vn, metruyenchuvn.com | Trang 1 inline, trang 2..N qua AJAX JSON `/get/listchap/{bookId}?page=N` | Văn bản có chèn dấu chấm chống copy (`t.h.u.ố.c`), cần chuẩn hoá ở TextNormalizer |
| `wikidich` | wikidichvn.com | Cùng engine với metruyenchu | |
| `tvtruyen` | tvtruyen.space (tvtruyen.com chuyển hướng) | `?page=N`, 50 chương/trang, tổng số trang lấy từ phân trang | Trang truyện ~300 KB |
| `52shuku` | 52shuku.net (zh) | `ul.list li.mulu a` một trang | Tìm kiếm `/so/search.php?q=`; tiêu đề kết quả có số thứ tự, lọc bằng regex |
| `jjwxc` | jjwxc.net (zh, GB18030) | `tr[itemprop=chapter] a[itemprop=url]` một trang; chương khoá (VIP) không có link nên bị bỏ qua | Tìm kiếm cần `queryCharset: GBK`; nội dung `.noveltext`, bỏ "作者有话要说" |

Chưa hỗ trợ: **xtruyen.net** (theme Madara tuỳ biến: nội dung chương và danh sách chương nạp bằng `admin-ajax.php` có nonce, tìm kiếm là ứng dụng JS). Cần mode `webview` hoặc giải mã API; để lại cho giai đoạn sau.

Site Trung đã khảo sát (21/10/2026) nhưng chưa làm rule vì HTML tĩnh không đủ:
- **zongheng.com**: danh sách chương render bằng JS, API JSON trả 404 khi gọi ngoài trình duyệt.
- **ixdzs8.com**: trang xác minh trình duyệt bằng JS trước khi trả nội dung.
- **17k.com**: WAF Aliyun chặn request không có cookie từ trình duyệt.
Cả ba cần `fetch.mode: webview` (tải trang bằng WebView ẩn rồi lấy DOM); sẽ làm khi fetcher có mode này.

## Giới hạn hiện tại

- Chưa có mode `webview` trong fetcher (WebView mới chỉ là trình duyệt trong app).
- Chưa cập nhật rule từ xa; rule chỉ nằm trong assets.
