# Chính sách riêng tư – AINovel Reader

Cập nhật: 21/10/2026

AINovel Reader là ứng dụng Android miễn phí, mã nguồn mở, không tài khoản, không quảng cáo và **không thu thập dữ liệu cá nhân**.

## Dữ liệu ứng dụng lưu trên máy bạn

Tất cả dữ liệu nằm trong bộ nhớ riêng của ứng dụng (app-specific storage) và bị xoá khi gỡ ứng dụng:

- Danh sách truyện bạn đã mở, danh sách chương, tiến độ đọc, lịch sử.
- Nội dung chương đã đọc hoặc tải về để dùng offline, ebook bạn nhập, ảnh bìa.
- Audio đã tổng hợp (bộ nhớ tạm), từ điển bạn nhập, từ riêng của truyện.
- Model giọng đọc và model dịch máy bạn chọn tải.
- Nhật ký kỹ thuật (`app.log`) chỉ ghi lỗi và sự kiện vận hành, không ghi nội dung truyện. Nhật ký chỉ rời máy khi bạn bấm "Chia sẻ nhật ký" và tự chọn nơi gửi.

Ứng dụng **không** xin quyền đọc toàn bộ bộ nhớ, danh bạ, vị trí hay micro. File bạn nhập (ebook, từ điển, sao lưu) được chọn qua hộp thoại của hệ thống và chỉ file đó được đọc.

## Kết nối mạng

Ứng dụng chỉ kết nối tới:

- Các website truyện bạn mở hoặc dán link, để tải trang và nội dung chương (như một trình duyệt). Các website này có chính sách riêng của họ.
- GitHub (`github.com`, `api.github.com`) khi bạn bấm "Kiểm tra bản mới" hoặc tải model giọng đọc sherpa-onnx.
- Google (ML Kit) khi bạn chọn tải model dịch máy; model chạy hoàn toàn trên máy sau khi tải.
- Máy chủ DNS-over-HTTPS của Google hoặc Cloudflare chỉ khi bạn bật tuỳ chọn này trong Cài đặt.

Không có máy chủ riêng của ứng dụng, không analytics, không Firebase, không định danh thiết bị.

## Quyền hệ thống

- `INTERNET`, `ACCESS_NETWORK_STATE`: tải trang và model.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`: phát audio nền và tải truyện khi màn hình tắt.
- `POST_NOTIFICATIONS`: thông báo điều khiển phát và tiến độ tải.

## Nội dung

Ứng dụng không lưu trữ hay phân phối nội dung truyện; nội dung được tải trực tiếp từ website bạn chọn về máy của bạn. Nếu bạn là chủ sở hữu nội dung và muốn gỡ một nguồn, hãy mở issue tại kho mã nguồn.

## Liên hệ

Kho mã nguồn và báo lỗi: https://github.com/thucnguyen79/AINovel_Reader
