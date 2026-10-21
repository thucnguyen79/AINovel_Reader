# Phát hành AINovel Reader

## Chuẩn bị một lần

1. Tải AAR sherpa-onnx (git-ignore): `python tools/sherpa/fetch_sherpa_aar.py`.
2. Tạo keystore ký bản phát hành (giữ file và mật khẩu cẩn thận; mất là không cập nhật được cho người dùng cũ):

```bash
keytool -genkeypair -v -keystore keystore/ainovel-release.jks -alias ainovel -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=AINovel Reader, O=esstar, C=VN"
```

3. Tạo `keystore.properties` ở thư mục gốc (git-ignore):

```
storeFile=keystore/ainovel-release.jks
storePassword=...
keyAlias=ainovel
keyPassword=...
```

Không có file này, `assembleRelease` vẫn chạy nhưng ký bằng debug key (chỉ để thử trên máy).

## Mỗi lần phát hành

1. Tăng `versionCode` và `versionName` trong `app/build.gradle.kts` (versionCode = major*100 + minor*10 + patch, ví dụ 1.0.0 → 100).
2. `.\gradlew.bat testDebugUnitTest lintDebug :app:assembleRelease` phải xanh.
3. APK nằm ở `app/build/outputs/apk/release/`: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-universal-release.apk`.
4. Cài thử bản arm64 lên máy thật, kiểm tra: mở truyện, nghe (giọng hệ thống và neural), dịch, nhập ebook, sao lưu / khôi phục.
5. Commit, gắn tag `vX.Y.Z`, push tag. Workflow `release.yml` build lại và đính kèm APK vào GitHub Release (cần secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); hoặc tạo Release thủ công và tải APK lên.
6. Tên asset phải chứa `arm64` để nút "Kiểm tra bản mới" trong app chọn đúng file; tag phải có dạng `v1.2.3`.

## Kiểm tra bản mới trong app

`UpdateChecker` gọi `https://api.github.com/repos/thucnguyen79/AINovel_Reader/releases/latest` (không key, không theo dõi), so sánh `tag_name` với `versionName`. Prerelease không được tính là "latest" trên GitHub nên không hiện với người dùng.

## Giới hạn hiện tại

- Chỉ thử trên OPPO CPH2357 (Android 14). Android 8–13 và 15 chưa được kiểm tra trên máy thật; minSdk 26 và các API dùng đều có sẵn từ API 26, nhưng hãy thử khi có máy.
- Chưa nộp Google Play; kênh chính là APK trên GitHub Releases (xem ADR-002 và mục rủi ro trong PLAN).
