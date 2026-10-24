# Camera AI xác chuột 0.4 — chỉ mục sự kiện & chuẩn bị dữ liệu học thêm

## 1. Dùng tính năng nhảy đến phát hiện

- Khi quay bản **0.4** trở đi, mô hình vẫn xử lý ảnh, khung AI tiếp tục được vẽ vào luồng MP4 như mã nguồn 0.3.
- Hệ thống ghi mốc **chuot_chet** khi có tối thiểu **2 lần quét** liên tiếp đạt tin cậy >= **45%** trong tối đa **2 giây**. Phát hiện liên tục được gộp chung nếu cách nhau không quá **5 giây**. Đây là các giá trị gợi ý, có thể chỉnh trong `ChiMucAI.kt`.
- Bấm **VIDEO ĐÃ LƯU**, chọn video, chọn **Xem + nhảy đến mốc AI**. Chạm mốc để nhảy đến **2 giây trước** lúc phát hiện.
- Mốc gần đúng, có thể chênh lệch với thời gian mã hóa thực của video. Khi xem mốc hãy kiểm tra bằng mắt; AI có thể nhầm.
- Khi video lưu xong, app tự lưu thêm file `Download/ThuXacChuot/<ten_video>_MocAI.csv` trên điện thoại (nếu lưu CSV thất bại sẽ báo). Có thể **Xuất danh sách mốc CSV** qua mục Chia sẻ (nội dung văn bản CSV). Trường `verified=no` nghĩa là chưa có người xác nhận. Đừng dùng trực tiếp CSV AI làm nhãn chuẩn.
- Danh sách mốc để tua được lưu trong **dữ liệu ứng dụng**, liên kết với URI MP4. Có CSV tự động để sao lưu nhưng **mốc không được nhúng vào MP4**, và bản 0.4 chưa có chức năng nhập lại CSV để khôi phục nút tua. Trước khi xóa ứng dụng/xóa dữ liệu hãy sao lưu video và CSV. Các video đã quay bằng bản 0.3 trở về trước sẽ không có chỉ mục tự động.

## 2. Thu thập ảnh gốc sạch để huấn luyện

- Nút **CHỤP ẢNH GỐC ĐỂ HUẤN LUYỆN** chụp ở lần ImageAnalysis kế tiếp (không có đường viền AI vẽ đè).
- Ảnh nằm ở **Thư viện > Pictures/ThuXacChuot/DuLieuHuanLuyen** (mục Ảnh trên Samsung S7 edge). Đó là ảnh lấy từ luồng phân tích để AI xử lý, độ phân giải thấp hơn ảnh camera thông thường. Khi cần chất lượng cao, hãy ghi video gốc bằng camera thường rồi lấy ảnh trên máy tính.
- Hãy chụp cả **chuột chết, mèo chết và cảnh không có vật cần tìm**; thay đổi góc nhìn/ánh sáng/nền và kích thước đối tượng.
- Mô hình **không tự học trên Samsung S7 edge**. Nút chụp chỉ tạo dữ liệu để mang sang máy tính/Colab.
- Các video MP4 do app ghi có khung AI đè lên, không nên làm nguồn chính để train vì mô hình dễ học nhầm khung/nhãn. Dùng **video gốc sạch** của camera hoặc ảnh gốc từ nút trên.

## 3. Huấn luyện từ video mới trên máy tính hoặc Google Colab

1. Chép video gốc (không khung AI) hoặc các ảnh gốc sang máy tính. Nếu có mốc CSV thì lưu file cùng video.
2. Để trích hình, chạy `pip install opencv-python`, sau đó:
   - `python huan_luyen/trich_xuat_anh.py --video raw.mp4 --interval 2 --out ./anh`
   - Hoặc có CSV: `python huan_luyen/trich_xuat_anh.py --video raw.mp4 --csv moc_ai.csv --out ./anh`
   Các khung hình lấy từ mốc chỉ là gợi ý, chưa phải nhãn đã xác minh.
3. Mở ảnh trong công cụ gán nhãn (ví dụ CVAT, Label Studio, Roboflow), **tự kiểm tra** và vẽ bbox đúng. Giữ class ID như app đang dùng: `0=chuot_chet`, `1=meo_chet`. Tách train/val/test theo **video nguồn** để không đánh giá sai do các frame gần nhau.
4. Để **fine-tune lan3**, cần mã huấn luyện và trọng số gốc (`.pt`/checkpoint) của lần train trước. **Không thể đơn giản đưa `.tflite` vào app để nó tự học**; tệp đó dùng suy luận. Nếu chỉ còn TFLite, cân nhắc huấn luyện lại kiến trúc tương ứng từ checkpoint khác.
5. Gộp dữ liệu cũ + mới (để giảm quên kiến thức cũ), huấn luyện qua Python/Colab và kiểm tra precision/recall trên video chưa từng train. Xuất định dạng `.tflite` đúng với input/output hiện tại.
6. App `BoPhatHien.kt` hiện dùng input ảnh RGB float32, 640x640 theo bố cục CHW và output dự kiến **[6, 8400]** (tọa độ + hai class). Nếu model mới có lớp mới, quantization hay tensor output khác, phải sửa trình nạp/giải mã trước khi dùng.
7. Cập nhật mô hình: chép model phù hợp vào thư mục `Android/data/vn.xacchuot.thu/files/model.tflite`, sau đó mở lại ứng dụng. Hoặc thay `app/src/main/assets/lan3.tflite` trong mã và build APK mới.

## 4. Đăng bản này lên GitHub

- ZIP giữ cấu trúc gốc: `.github/`, `app/`, `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`.
- Quan trọng: root `build.gradle.kts` đã được sửa Kotlin sang `2.4.10` để tương thích phụ thuộc LiteRT 2.3.0; **không ghi đè lại bằng file cũ Kotlin 2.2.20**.
- Lên GitHub commit các file đã thay đổi, vào **Actions > Build APK**. Chỉ có APK nếu workflow chạy thành công.
- Chưa có thử nghiệm build APK trên Samsung S7 edge thật; CameraX `OverlayEffect` và Camera+AI+Video có thể bị hạn chế phần cứng và tốc độ. Khi máy báo không hỗ trợ, cần phân tích lại log/hardware để tìm phương án thay thế.
