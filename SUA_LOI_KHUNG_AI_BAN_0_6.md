# Sửa lỗi khung AI - phiên bản 0.6

## Lỗi được xác định từ mã nguồn 0.5

1. `MainActivity.kt` chỉ gọi `lopPhu.datKetQua(kq)` khi `!coTheQuetKhiQuay`. Do đó, chính khi camera hỗ trợ quay video cùng AI, khung **không xuất hiện trực tiếp trên camera**.
2. `XuatVideoAI.kt` vẽ trên `CanvasOverlay` của Media3 mà không xóa bitmap của khung trước. Bitmap được dùng lại, khiến **khung cũ chồng lên khung mới** ở bản MP4 AI.
3. Bản cũ hiển thị toàn bộ các khung có điểm từ 20% và có thể lên tới 20 khung, dễ che hết video.

## Cách khắc phục

- Khung AI hiển thị ngay lúc quét, cả trước/trong khi quay.
- Lần quét mới không phát hiện: khung mất ngay. Nếu camera/AI ngừng cập nhật: khung mất sau **1 giây**.
- Video AI dọn sạch overlay của khung cũ **mỗi frame**; giới hạn tồn tại khung theo thời gian là **900ms** khi không có mẫu mới.
- Chỉ vẽ tối đa **3 khung** có điểm mô hình từ **45% trở lên**. Ngưỡng này để giảm nhiễu, không phải xác suất chính xác được chứng nhận.
- Trong khi quay: bảng thống kê dài được rút xuống một dòng để không che cảnh quay.
- Vẫn giữ **2 video GỐC (sạch) và AI (có khung), danh sách mốc tua, còi 1/3 tiếng**.

## Thay file lên GitHub

Cách ít rủi ro: giải nén ZIP `thu-xac-chuot-v0.6-sua-khung-truc-tiep.zip` và chép **nội dung bên trong thư mục** `thu-xac-chuot-main` lên repository, bảo toàn các đường dẫn.

Chỉ những file thay đổi từ v0.5: `MainActivity.kt`, `LopPhu.kt`, `XuatVideoAI.kt`, `DuLieuKhungAI.kt`, `QuyTacKhungAI.kt` (mới), và `app/build.gradle.kts` (tăng phiên bản). Bản vá chỉ có những file này cũng được đính kèm.

Vào GitHub Actions > Build APK; nếu workflow màu xanh thì tải file APK mới. Gỡ APK cũ hoặc cập nhật bằng APK mới (cùng package, cần cùng chữ ký).

## Thử trên điện thoại

1. Mở camera, đưa vật thể vào khung để AI phát hiện. **Khung phải hiện tức thì**, không cần chờ quay xong.
2. Di chuyển camera ra nơi không có đối tượng hoặc che vật khỏi hình. **Khung phải biến mất**, không tạo dấu vết chồng lên nhau.
3. Bấm quay khoảng 15-30 giây; video GỐC phải sạch, video AI phải chỉ hiện khung vào đúng các thời điểm có kết quả.
4. Bấm các mốc AI để tua kiểm tra. Kiểm tra còi 1 hoặc 3 tiếng khi mô hình đạt ngưỡng.

**Lưu ý:** đây là mã nguồn đã sửa; cần build trên GitHub và thử với Samsung S7 Edge để xác nhận hiệu năng và tương thích CameraX/Media3.
