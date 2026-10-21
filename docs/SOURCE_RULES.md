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
  "fetch": { "mode": "http", "headers": {}, "charset": null,   // mode: http (OkHttp) | webview (WebView ẩn, chạy JS)
             "waitFor": null, "timeoutMs": 25000 },            // waitFor: selector CSS WebView chờ xuất hiện rồi mới trả DOM
                                       // charset: gợi ý khi header/meta không nói (vd "GB18030"); fetcher tự sniff <meta charset>
                                       // search/book/toc/chapter có thể ghi đè riêng bằng "mode" và "waitFor" của phần đó
  "urls": {                            // regex (containsMatchIn) để phân loại URL
    "rewrite": [                       // (tuỳ chọn) viết lại URL trước khi phân loại/tải: host mobile -> host chuẩn, bỏ query theo dõi
      { "pattern": "^https?://m\\.site\\.com/", "replacement": "https://www.site.com/" }
    ],
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
    "url": "{url}?tabsName=catalogue",  // (tuỳ chọn) mẫu URL mục lục khi không có book.tocUrl; {url} = URL truyện bỏ query
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
| `zongheng` | zongheng.com (zh) | Trang `detail/{id}?tabsName=catalogue` render bằng JS: `book`/`toc` dùng `webview`, chờ `.chapter-list--item`; chương ở `read.zongheng.com` tải bằng HTTP | Giới thiệu chỉ có ở tab mặc định nên chờ `.detail-work-info--introduction` |
| `ixdzs8` | ixdzs8.com (zh) | Trang sách tĩnh, `div.clist ul.u-chapter li a` đủ 448 chương | Trang chương có bước "正在验证浏览器" bằng JS nên `chapter.mode = webview`, chờ `article.page-content p` |
| `17k` | h5.17k.com (zh; link www/m được viết lại sang h5) | `book/{id}.html` (thông tin) + `list/{id}.html` (mục lục `dl.listCont dd a.name`, nạp bằng XHR) | WAF Aliyun chặn HTTP thường và luôn đẩy WebView về bản h5, nên rule viết theo h5: toàn bộ `webview`; tựa/tác giả lấy từ `og:title` |

Chưa hỗ trợ: **xtruyen.net** (theme Madara tuỳ biến: nội dung chương và danh sách chương nạp bằng `admin-ajax.php` có nonce, tìm kiếm là ứng dụng JS). Cần mode `webview` hoặc giải mã API; để lại cho giai đoạn sau.

## Mode `webview`

`WebViewHtmlFetcher` (core/network) tải trang trong một WebView ẩn (JS bật, không tải ảnh), chờ `onPageFinished`, rồi poll đến khi
`document.readyState === "complete"` và selector `waitFor` xuất hiện (tối đa `timeoutMs`, mặc định 25 giây), và trả
`document.documentElement.outerHTML`. Cookie trang đặt (WAF, xác minh trình duyệt) được chép sang cookie jar của OkHttp cùng host.
Mỗi lúc chỉ một trang; chậm hơn HTTP vài giây nên chỉ bật cho phần cần (`book`, `toc` hay `chapter`), không bật cho cả rule nếu không bắt buộc.
`fetch.headers["User-Agent"]` đổi được UA của WebView (kèm Client Hints desktop khi UA là Windows/Mac); WebView ẩn được layout 1280x1024 để JS kiểm tra bề rộng không coi là mobile.
Lưu ý `waitFor` chạy bằng `document.querySelector` nên giá trị thuộc tính có dấu `:` phải đặt trong ngoặc kép (`meta[property="og:title"]`).
Cookie của WebView dùng chung cả tiến trình (trình duyệt trong app và fetcher), nên site đã "nhớ" bản mobile ở trình duyệt sẽ trả bản mobile cho fetcher; rule 17k vì vậy viết theo h5.
Fixture của các site này là DOM đã render, lấy từ trình duyệt thật.

## Dịch Trung → Việt (tóm tắt, chi tiết trong ADR-004/006)

- Thứ tự tra: glossary (truyện > toàn cục) > Names > VietPhrase (khớp dài nhất) > Hán Việt từng chữ; sau đó luật nhân.
- File người dùng nhập ở tab Dịch truyện: `Names.txt`, `VietPhrase.txt`, `LuatNhan.txt` (UTF-8, `Hán=Việt`, nghĩa phụ cách bằng `/`).
- Chế độ: Convert, Hán Việt, Dịch máy (ML Kit, tải model theo yêu cầu), Nguyên văn.

## Giới hạn hiện tại

- Mode `webview` chạy tuần tự một trang một lúc và không phát hiện CAPTCHA; site hiện CAPTCHA thật vẫn thất bại.
- Chưa cập nhật rule từ xa; rule chỉ nằm trong assets.
