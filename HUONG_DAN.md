# CAMERA AI XÁC CHUỘT – BẢN 0.3: GHI KHUNG VÀO VIDEO MP4

Bản **0.3** thay thế bản thử tốc độ và bản quay video thường 0.2. Ứng dụng sử dụng `lan3.tflite` để nhận diện `chuot_chet` / `meo_chet`, vẽ khung màu và độ tin cậy **trực tiếp vào hình đang được mã hóa thành video MP4**.

## Chức năng

- Camera sau, quét AI thời gian thực, có khung phát hiện và nhãn lớp.
- **BẮT ĐẦU QUAY / DỪNG QUAY**: MP4 lưu cả khung AI, trạng thái phát hiện, âm thanh nếu được cấp quyền.
- **VIDEO ĐÃ LƯU**: xem, chia sẻ hoặc xóa video do app ghi.
- **Đèn pin**, chỉnh tốc độ quét, thống kê tải AI/nhiệt độ như bản thử nghiệm.
- Tệp MP4 lưu ở `Movies/ThuXacChuot`, tên `XacChuot_AI_yyyyMMdd_HHmmss_SSS.mp4`.

## Giới hạn bắt buộc phải biết

- Một số điện thoại cũ (có thể bao gồm **Samsung S7 Edge**) không hỗ trợ đồng thời camera preview, quay video, phân tích AI và xử lý overlay. Ứng dụng thử HD, rồi SD. Nếu vẫn không được, **nút quay bị khóa**, AI vẫn hoạt động trên màn hình. App KHÔNG ghi video trơn và giả làm video AI.
- AI có thể báo sai. Khung là kết quả dự đoán, không phải xác nhận chắc chắn.
- AI chạy chậm có thể làm khung trong MP4 trễ so với chuyển động; video sử dụng kết quả phân tích mới nhất khi vẽ.
- Không quay video nền. Bản 0.4 có nút chụp ảnh gốc JPEG để chuẩn bị huấn luyện; chưa thử build và thử quay trực tiếp trên S7 Edge trong môi trường này.

## Cập nhật dự án GitHub

1. Giải nén ZIP bản mới `thu-xac-chuot-AI-co-moc-va-huan-luyen.zip`.
2. Vào bên trong thư mục `thu-xac-chuot-main` vừa giải nén. Kiểm tra có `.github`, `app`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.
3. Vào repository `thu-xac-chuot` trên GitHub, chọn nhánh `main`, nhấn **Add file → Upload files**.
4. Tải lên **nội dung** của `thu-xac-chuot-main` (giữ nguyên đường dẫn thư mục); thay thế các file trùng tên. Quan trọng có **file mới** `app/src/main/java/vn/xacchuot/thu/KhungVideoAI.kt`.
5. **Commit changes**. Vào **Actions → Build APK**; nếu workflow chưa tự chạy, nhấn **Run workflow**.
6. Khi dấu xanh, vào lần chạy, phần **Artifacts → app-debug-apk**, tải ZIP, giải nén lấy `app-debug.apk` để cài lên điện thoại.
7. Nếu dấu đỏ, tải ZIP **log lần chạy mới** và gửi để chẩn đoán.

## Kiểm tra thực tế để chắc chắn khung được ghi trong MP4

1. Mở app, cấp quyền Camera và Micro nếu muốn thu âm. Trên Android 7–9 cấp thêm quyền Bộ nhớ.
2. Chờ AI tải xong. Khi hiển thị **Sẵn sàng • MP4 ghi khung AI**, nhấn **BẮT ĐẦU QUAY**.
3. Hướng camera vào một cảnh thử mô hình có thể phát hiện; phải thấy khung/nhãn trên preview (nếu AI nhận ra).
4. Nhấn **DỪNG QUAY**, mở **VIDEO ĐÃ LƯU** hoặc Thư viện/Video của máy.
5. Phát lại tệp MP4 bằng ứng dụng phát video độc lập để xác nhận **khung, nhãn, phần trăm tin cậy có trong file video** (không chỉ trong app camera).
6. Nếu máy báo không hỗ trợ, **không có MP4 AI**; cần thay kiến trúc xử lý (ví dụ phân tích video sau khi quay) hoặc dùng thiết bị hỗ trợ.

## File kỹ thuật

- `app/src/main/java/vn/xacchuot/thu/KhungVideoAI.kt`: vẽ bằng CameraX `OverlayEffect(PREVIEW | VIDEO_CAPTURE)`. Biến đổi tọa độ ảnh AI sang cảm biến và khung video.
- `app/src/main/java/vn/xacchuot/thu/MainActivity.kt`: kết nối preview, quét AI và VideoCapture trong `UseCaseGroup`; lưu MP4, danh sách video.
- `app/src/main/java/vn/xacchuot/thu/PhatHien.kt`: giải mã mô hình AI (giữ nguyên).
- `app/src/main/assets/lan3.tflite`: mô hình huấn luyện (giữ nguyên).
- `app/build.gradle.kts`: thêm `camera-effects` tương thích với CameraX 1.4.1.


---

## Camera AI 0.4 — Tua theo mốc AI và huấn luyện mới

Xem file **`HUONG_DAN_MOC_AI_VA_HUAN_LUYEN.md`**: nút Xem + nhảy đến mốc AI, xuất bảng CSV, chụp ảnh gốc không overlay, và cách trích ảnh/gán nhãn/huấn luyện mô hình mới.
