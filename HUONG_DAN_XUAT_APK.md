# Hướng dẫn xuất file APK (Bản v2 đã fix chuẩn GitHub Actions)

## Các bước tải lên GitHub từ đầu
1. Tạo một Repository mới trên GitHub (ví dụ `vn-edmonton-widget-v2`).
2. Giải nén file `VN-Edmonton-Android-Widget-Project-v2.zip` vào một thư mục trên máy tính.
3. Mở thư mục đó, chọn tất cả file bên trong (`app`, `gradle`, `.github`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`) và kéo thả vào trang **uploading an existing file** của repo mới.
   - *Lưu ý:* Nếu Windows ẩn thư mục `.github` khi kéo thả, sau khi Commit xong anh bấm **Add file > Create new file**, gõ `.github/workflows/build-apk.yml` và dán nội dung từ file `build-apk.yml` trong gói giải nén.
4. Ngay khi Commit xong, GitHub Actions sẽ tự động chạy build (`gradle :app:assembleDebug`). Chờ 1–2 phút đến khi hiện dấu tích xanh, bấm vào lần chạy đó và tải `VN-Edmonton-Widget-Debug-APK` ở mục **Artifacts**.
