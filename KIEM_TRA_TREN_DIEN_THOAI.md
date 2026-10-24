# Kiểm tra thực tế bản 0.5 trên Samsung S7 Edge

Đây là mã nguồn chưa được biên dịch/chạy trên Samsung S7 Edge trong môi trường tạo gói. Cần thử các mục dưới đây.

1. GitHub Actions > Build APK hoàn tất dấu xanh, không có lỗi Kotlin/Media3.
2. Mở app và cho phép Camera, Mic, Bộ nhớ. Ống kính hiển thị ảnh; AI chạy.
3. Bật Bíp, cho camera thấy một vật mô hình cho là `chuot_chet` nhiều frame liên tiếp. Ngưỡng 45–79% báo 1 bíp; từ 80% báo 3 bíp. Không lặp không ngừng khi giữ nguyên vật.
4. Nhấn Quay, quay ít nhất 20 giây, để xuất hiện vật ở giây ~10, dừng. Máy phải lưu GỐC trước.
5. Giữ ứng dụng ở màn hình camera đến khi trạng thái `ĐÃ LƯU CẢ 2 VIDEO`. Trên S7 Edge tác vụ có thể lâu, gây nóng và thất bại vì codec.
6. Mở thư viện: Movies/ThuXacChuot/GOC chứa video không khung; Movies/ThuXacChuot/AI chứa video có khung đỏ/xanh và nhãn.
7. Trong app chọn `2 VIDEO / MỐC` → chọn lần quay → xem AI với trình phát lớn, bấm một mốc để nhảy sớm hơn 2 giây. Thử nút Toàn màn hình.
8. Nếu AI MP4 không tạo được: GỐC vẫn phải còn; vào danh sách chọn `Tạo lại MP4 AI` để thử lại; gửi GitHub log hoặc Android Logcat nếu lỗi xảy ra trên điện thoại.
9. Thử quay không có phát hiện: AI MP4 vẫn là video đầy đủ, không thêm khung giả.
10. Chép MP4 GỐC về máy tính; dùng `huan_luyen/trich_xuat_anh.py` tạo JPG để gán nhãn trên https://www.makesense.ai/.

Lưu ý: Video bản cũ 0.4 KHÔNG có lại GỐC sạch sau nâng cấp. Điểm 80% chỉ là confidence của mô hình, không được xem như xác suất đúng đã kiểm nghiệm. Loa bíp có thể lọt vào micro video gốc. Quá trình xuất làm tăng dung lượng lưu trữ xấp xỉ gấp đôi.
