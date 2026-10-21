# AINovel Reader

Ứng dụng Android miễn phí để **đọc và nghe truyện** (text-to-speech) từ các website truyện được hỗ trợ, tải về đọc/nghe offline, nhập ebook và dịch truyện Trung → Việt bằng công cụ chạy trên máy.

- Không tài khoản, không backend, không quảng cáo, không gửi dữ liệu ra ngoài.
- TTS: giọng hệ thống Android, giọng neural on-device (sherpa-onnx / Piper).
- Dịch: convert engine Hán-Việt offline, ML Kit on-device; LLM tuỳ chọn với key của chính bạn.

Kế hoạch chi tiết: [docs/PLAN.md](docs/PLAN.md). Quyết định kiến trúc: [docs/DECISIONS.md](docs/DECISIONS.md). Quy ước cho Claude Code: [CLAUDE.md](CLAUDE.md).

## Build

Yêu cầu: JDK 17+ (dự án dùng JDK 21), Android SDK với platform 36 và build-tools 36.0.0, biến môi trường `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug
```

APK debug nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Trạng thái

Giai đoạn 0 (khung dự án) hoàn thành: đa module, convention plugins, theme, 4 tab, mini-player placeholder, CI. Các giai đoạn tiếp theo theo `docs/PLAN.md` mục 6.
