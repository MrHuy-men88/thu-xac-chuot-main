# Camera AI xác chuột v0.7 – thử độ nhạy trước khi huấn luyện

## Cải tiến
- Nút `AI: 30%` cạnh nút Quét trên màn hình. Nhấn để đổi qua các mức:
  1. **30% – CÂN BẰNG** (mặc định, từ v0.6 là 45%).
  2. **25% – RẤT NHẠY**: thấy thêm các kết quả điểm thấp; dễ báo sai hơn.
  3. **45% – ÍT BÁO NHẦM**: chỉ hiện các phát hiện chắc hơn.
- Nút thay đổi **ngưỡng điểm nhận diện**, không thay đổi chất lượng mô hình và không phải phần trăm xác suất chính xác.
- Cùng một ngưỡng được dùng cho khung trên camera, mẫu khung đưa vào video AI, tạo mốc phát hiện và yêu cầu xác nhận 2 lần trước tiếng bíp.
- Khi đang quay, không cho đổi độ nhạy giữa chừng, để mốc trên cùng video nhất quán.
- Lưu độ nhạy đã chọn để lần mở ứng dụng sau vẫn giữ nguyên.
- Giữ hai video GỐC và AI, xuất mốc CSV, bíp 1 hoặc 3 lần, và mốc nhảy khi xem lại.

## Thử nghiệm đề xuất
1. Quét cùng một cảnh 30–60 giây ở 30% rồi 25%, tiếp theo 45%; chọn tốc độ Quét **tối đa** và đảm bảo không quá nóng.
2. Ghi nhận **bỏ sót** và **báo nhầm** ở từng ngưỡng. Điểm 25% không có nghĩa AI chính xác 25%.
3. Đối tượng nhỏ ở xa: thử đến gần hơn hoặc sử dụng zoom camera (nếu bổ sung sau). Chỉ tăng độ phân giải nguồn mà vẫn thu nhỏ về 640x640 không bảo đảm thấy vật nhỏ hơn.
4. Quay video **GỐC** trong các điều kiện ánh sáng/góc/chất nền thực tế; trích ảnh đưa lên makesense.ai, vẽ khung nhãn `chuot_chet` / `meo_chet` để huấn luyện.
5. Giữ checkpoint huấn luyện (ví dụ `.pt`) + nhãn/dữ liệu cũ; `.tflite` thường là file triển khai, không phải checkpoint phù hợp để tiếp tục huấn luyện YOLO.
6. Trước khi đưa mô hình mới lên máy, kiểm thử trên các video CHƯA dùng huấn luyện và so sánh số bỏ sót, số báo nhầm và tốc độ.

## Lưu ý
Mã nguồn được rà soát ở mức tĩnh, chưa chạy Gradle/Android SDK hoặc thử nghiệm trên Samsung S7 Edge. Hãy build APK qua GitHub Actions rồi thử trên máy thật trước khi sử dụng chính thức.
