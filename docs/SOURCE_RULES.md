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
                                       //   placeholder: {url} (URL truyện, không có / cuối), {base}, {bookId}, {page},
                                       //   {bookSlug} (đoạn cuối của {url}), {page0} (= {page} - 1, cho API đếm nhóm từ 0)
    "bookId": "input[name=bid]@value", //   id nội bộ của truyện cho template có {bookId}
    "pageList": "ul li a",             //   selector dùng cho trang 2..N nếu là fragment HTML khác cấu trúc
    "pageJson": "data",                //   nếu trang 2..N là JSON: tên trường chứa HTML (đường dẫn chấm)
    "pageHeaders": { "X-Requested-With": "XMLHttpRequest" },  // header thêm cho trang 2..N
    "pageMethod": "POST",              //   GET (mặc định) | POST: áp cho mọi trang mục lục tải riêng (trang 1 qua toc.url/book.tocUrl
                                       //   và trang 2..N). POST luôn đi qua OkHttp vì WebView ẩn không gửi được form.
    "pageBody": "action=nt_load_chapters&parent_id={bookId}&page={page}",  // body form-urlencoded khi POST, cùng placeholder
                                       //   với pageUrl ({page} = 1 ở trang đầu); null/rỗng = form rỗng (Madara ajax/chapters/)
    "pageSize": 50, "totalChapters": "span[itemprop=numberOfPages]@text",  // cách 1b: site chỉ cho tổng số chương -> số trang = ceil(tổng/pageSize)
    "jsonItems": "chapters", "jsonTitle": "title",            // cách 1c: trang 2..N là JSON mảng object (vnthuquan)
                                       //   jsonTitle là tên trường hoặc template trên các trường ("Chương {chapter_number}")
    "jsonLink": "{url}/chuong-{chuongId}",                    //   URL chương ghép từ {url}, {base}, {bookSlug} và {tên trường}
                                       //   cách 1d: toàn bộ mục lục là JSON -> thêm "url" trỏ tới trang 1 của API (có thể dùng
                                       //   {bookId}); totalPages/totalChapters và bookId đọc từ trang truyện, bỏ "list"
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
    "prev": "#prev_chap@href", "next": "#next_chap@href",
    "nextPage": "a#next@href|regex:^(.*_\\d+\\.html)$",  // (tuỳ chọn) link phần tiếp của CÙNG chương (site chia chương thành xxx_2.html);
    "maxPages": 30                                        //   các phần được tải nối tiếp và ghép đoạn văn
  }
}
```

## Làm sạch nội dung

`ContentCleaner` luôn: xoá phần tử có `style` chứa `display:none` / `visibility:hidden` / `font-size:0`, xoá `[hidden]`, đổi `<br>` và thẻ khối thành ngắt đoạn, chuẩn hoá khoảng trắng (kể cả NBSP, khoảng trắng CJK), bỏ dòng trống. Sau đó áp `remove` và `removeLines` của rule.

## Quy trình thêm site mới

Công cụ trong `tools/sources/` (Python 3, cần `beautifulsoup4` + `lxml`; đặt biến môi trường `SURVEY_DIR` tới một thư mục ngoài repo để chứa HTML tải về):

1. `survey.py` – đọc `sites.txt` (mỗi dòng một host, mẫu `sites.example.txt`), tải trang chủ từng site, ghi `survey.json` (mã HTTP, theme đoán được: WordPress/Madara/Nuxt/Cloudflare…) và `html/<host>.html`.
2. `probe.py` – với site tĩnh, tự tìm một trang truyện + một trang chương, lưu `html/<host>__book.html`, `html/<host>__chapter.html` và `probe.json`.
3. `inspect_site.py <host>` – in cấu trúc trang đã lưu: tiêu đề, tác giả, ảnh bìa, nhóm link chương lớn nhất, phân trang, ứng viên khối nội dung, nút trước/sau. `snip.py <host> <file> <css>` in HTML của một selector để chốt rule.
4. Viết `assets/sources/<id>.json`; chép HTML (đã bỏ `<script>`, `<style>`, `<svg>`) vào `data/source/src/test/resources/fixtures/<id>/` với tên `book.html`, `toc_page2.html` (hoặc `.json`), `chapter.html`, `chapter_part2.html`, `search.html`; thêm URL các trang đó vào `fixtures/_urls.json`.
5. `.\gradlew.bat :data:source:testDebugUnitTest --tests "*RuleDumpTest"` rồi đọc `data/source/build/rule-dump.txt`: tựa, tác giả, số chương, chương đầu/cuối, trang 2, tiêu đề/đoạn đầu/đoạn cuối/prev/next của chương, kết quả tìm kiếm. Sửa rule đến khi đúng.
6. `python tools/sources/record_expect.py <id>` ghi `fixtures/<id>/expect.json`; `SiteFixturesTest` so sánh mọi giá trị đó với parser hiện tại nên site đổi giao diện hay parser hỏng sẽ lộ ngay. Rule phức tạp có thể thêm test riêng (`TruyenfullRuleTest`, `WebViewSitesRuleTest`…).
7. `.\gradlew.bat :data:source:testDebugUnitTest` xanh, cài APK thử trên máy ít nhất trang truyện + một chương, rồi mới commit.

## Các rule hiện có (cập nhật 22/10/2026)

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
| `metruyenhot`, `metruyenhotvn` | metruyenhot.me, metruyenhotvn.com | `?page=N`, 50 chương/trang; tổng số trang lấy từ link `li.nexts` | Hai host cùng template Laravel; không có mô tả truyện; nội dung `#chapt-content .chapter-c` |
| `truyenhoan` | truyenhoan.com | `{url}/trang-N/`, tổng trang từ link "Last" | Bản sao truyenfull; nút trước/sau cùng id `prev_chap` nên chọn theo icon |
| `truyennet` | truyennet.org | Cùng engine metruyenchu: AJAX JSON `/get/listchap/{bid}?page=N` | Tìm kiếm `/tim-kiem?s=` |
| `khotruyenchu` | khotruyenchu.fun | WordPress, `{url}/page/N/`, 50 bài/trang | Chương là bài viết `/chuong-N-…/`; bỏ thanh công cụ đọc, bình luận wpDiscuz |
| `truyennova` | truyennova.com (cùng engine truyenflow.com, chưa thử) | Một trang đủ mục lục | Tác giả/thể loại nằm trong khối mô tả (`p:has(strong)`) |
| `phithang` | phithang.com | WordPress theme WN, một trang (1.591 chương) | Tác giả lấy từ dòng "Tác giả:" trong synopsis; tìm kiếm WP `?s=` lọc bỏ link chương |
| `teamtruyen` | teamtruyen.com | Một trang (6.578 chương, ~5 MB) | Trang chương chứa cả danh sách chương; `prev/next` qua `span` con |
| `vnthuquan` | vnthuquan.org | 100 chương đầu trong modal, trang sau qua JSON `/api/tacpham/get-mucluc` (`jsonItems`) | `bookId` đọc từ JS `const tuaId = N`; không có ảnh bìa |
| `hatduahau`, `dualeotruyenfull` | hatduahau.com, dualeotruyenfull.net | WordPress theme init-manga, `{url}/chuong/page/N/` (24/trang); hatduahau thêm `?rev=1` | dualeo liệt kê mới → cũ nên `reverse: true`; tìm kiếm `?s=` chỉ giữ bài có link `/truyen/` hoặc `/doc-truyen/` |
| `aatruyen` | aatruyen.net | `?page=N`, 40 chương/trang | Bỏ khối "Các chương mới nhất" bằng `div.list-chapter-title.mt-4 + div.cnt-list-chapter` |
| `xalosach` | xalosach.com | `?P=N`, 100 chương/trang, tổng trang từ "Đi đến …/N" | Link prev/next khi không có chương là `Chưa có chương mới.html` nên lọc `[href*=chuong-]` |
| `tieuthuyet` | tieuthuyet.vn | `?page_chapter=N`, 15 chương/trang | Theme truyenfull biến thể; nội dung `div.chapter-content#dynamic-content` |
| `ranwenapp`, `tycqxs` | ranwenapp.com, tycqxs.net (zh) | `{url}/index_N.html`, 100 chương/trang; tổng trang từ "1/8" | Chương chia nhiều phần `xxx_2.html`: dùng `chapter.nextPage` ghép lại; chỉ dùng https (viết lại `http://`); meta `og:novel:*` cho tác giả/trạng thái |
| `256wx` | 256wx.org (zh) | Một trang; mỗi "chương" là một trang `/read/{id}/N.html` (第N页) | Site không chia chương thật; bỏ khối `div.page` trong nội dung |
| `nhatruyen` | nhatruyen.site | WordPress: 50 chương đầu inline, trang 2..N `POST wp-admin/admin-ajax.php` body `action=nt_load_chapters&parent_id={bookId}&page=N`; tổng trang từ `#nt-jump-input@max`, id truyện đọc từ JS `formData.append('parent_id', N)` | Tiêu đề chương có emoji ☀️ đầu dòng, bỏ bằng regex `^[^\p{L}\p{N}]*(.+)$` |
| `truyenchu` | truyenchu.net | Theme Madara: toàn bộ mục lục qua `POST {url}/ajax/chapters/` (form rỗng) làm trang 1, liệt kê mới → cũ (`reverse`) | GET cùng URL chỉ trả trang HTML thường; tìm kiếm `?s=&post_type=wp-manga`; tiêu đề chương lấy từ breadcrumb |
| `sstruyen`, `truyensac` | sstruyen.pics, truyensac.buzz | Next.js: 50 chương đầu trong HTML (`nav[aria-label="Danh sách chương"]`), trang sau JSON `https://api.<host>/novels/{bookId}/chapters?page=N&limit=50&sort=asc` (`jsonItems`); `novelId` đọc từ payload RSC trong `<script>`, tổng chương từ `span[itemprop=numberOfPages]` | URL chương ghép `{base}/doc-truyen/{bookSlug}-{slug}-{id}`; tiêu đề chương từ `og:description`; API cần UA trình duyệt (curl mặc định bị 403); trang tìm kiếm hai site khác template |
| `bientruyen` | bientruyen.com (viết lại sang www) | Toàn bộ mục lục JSON `/api/story-chapters/{storyId}?group={page0}&perGroup=50&pagination=number` (`toc.url` + `jsonItems`), `storyId` (UUID) trong payload RSC, số trang từ option cuối "Ch.251–258" + `pageSize` 50 | API trả tiêu đề gốc tiếng Trung nên `jsonTitle` = `Chương {chapter_number}`; chương VIP (từ 31) không đọc được; không có tác giả, không có tìm kiếm |

### Site đã khảo sát (22/10/2026) nhưng chưa làm rule

- **Mục lục nạp bằng JS/API riêng, HTML tĩnh chỉ có vài chục chương**: metruyenchu.co, truyenc.net, zuminovel.com, vtruyenchu.org, truyenfast.com, wikicv.org, truyendichfull.site, docfull.vip, dichtienghoa.net, truyen-h.com, sitruyencv.com, chivi.app, 10sto.com. Cách đã dùng cho sstruyen/truyensac/bientruyen (22/10/2026): tải các chunk `_next/static/chunks/*.js` của trang truyện, grep `fetch(`/`chapters` để tìm API JSON, rồi mô tả bằng `jsonItems` (+ `toc.url` nếu cả trang 1 cũng là JSON); id truyện thường nằm trong payload RSC (`script:containsData(...)@html|regex:`).
- **Mục lục nạp bằng POST** (`admin-ajax.php` hoặc Madara `ajax/chapters`): gaumeoteam.com, xtruyen.net (chưa làm); plnovel.com đang trả HTTP 500 phía server ("PHP installation appears to be missing the MySQL extension", kiểm tra 22/10/2026) nên chưa khảo sát được, thử lại sau. Engine đã hỗ trợ `pageMethod`/`pageBody` (xem nhatruyen, truyenchu).
- **Nội dung chương mã hoá bằng JS**: truyentronbo.com (`data-compressed`), docln.sbs (`data-c`); thungtruyen.com đòi "xác minh nhanh". Cần `webview` và chọn đúng selector sau khi giải mã.
- **Mục lục theo quyển/tập, nhiều trang lồng nhau**: valvrareteam.net (light novel), docln.sbs.
- **Chặn bot / Cloudflare / không mở được từ server**: wattpad.com, truyenyy.co, sstruyen.net, zingtruyen.store, lacatruyen.vip, loppytoon.com, truyenhdc.com, vietnamthuquan.eu (ngắt kết nối); truyencn.com, inkitt.com, teenfic.net, vip.audiotruyenfull.org, caytruyen.site, giatocvuongtai.com, webtruyendich.com (403); mọi blog `*.wordpress.com` (Cloudflare "Checking your browser"); msvtruyen.com, truyencom.com, metruyenhot.com (lỗi TLS).
- **Chết / không phải site truyện chữ**: xtruyen.net, truyenmoikk.com, itruyenchuz.org, kenhtruyenfull.com (không phân giải DNS); lovetruyen.pro (521); dasactruyen.com (bị khoá); tieuthuyetdammy.net, dichngay.com, linhdi.vn, dammy.me (trang trống/chuyển hướng); isach.info (chủ yếu tải ebook), truyenc.com (truyện cười/audio), storya.click (chọn ngôn ngữ), noveltoon.vn / sangtacviet.xyz / webnovel.vn / khoaitay.cc / truyen2u.com / davulacam.info (ứng dụng JS hoặc cần đăng nhập).
- **Chưa khảo sát kỹ** (trang chủ mở được nhưng probe không tìm ra trang truyện): metruyensangtac.com, navyteams.com (nội dung chương nạp JS), truyenflow.com, phamvuc.com, tacgiaviet.com, mongtruyen.com, aztruyen.top, truyenh.top, dammy.cc, monkeydd.com, hangtruyenchu.com, comnguoinovel.com, lnkuro.top, tinhngoncoc.net, tieuthuyetmang.com, itruyen.org, truyendich.space.

## Mode `webview`

`WebViewHtmlFetcher` (core/network) tải trang trong một WebView ẩn (JS bật, không tải ảnh), chờ `onPageFinished`, rồi poll đến khi
`document.readyState === "complete"` và selector `waitFor` xuất hiện (tối đa `timeoutMs`, mặc định 25 giây), và trả
`document.documentElement.outerHTML`. Cookie trang đặt (WAF, xác minh trình duyệt) được chép sang cookie jar của OkHttp cùng host.
Mỗi lúc chỉ một trang; chậm hơn HTTP vài giây nên chỉ bật cho phần cần (`book`, `toc` hay `chapter`), không bật cho cả rule nếu không bắt buộc.
Trang mục lục khai báo `pageMethod: POST` luôn được tải bằng OkHttp (`HtmlFetcher.post`, body form-urlencoded) kể cả khi rule ở mode `webview`, vì WebView ẩn không gửi được form; cookie WebView đã chép sang OkHttp nên request POST vẫn qua được WAF đã xác minh.
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
