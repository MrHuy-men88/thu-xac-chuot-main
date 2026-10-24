# Bản 0.5: hai video, mốc AI xem lớn và âm báo

Ứng dụng dành cho **Samsung S7 Edge (Android 7+)**; đây là **mã nguồn thử nghiệm**, chưa xác nhận đã build hoặc quay được trên máy thật.

## Một lần quay tạo hai file MP4 khác nhau

1. **GỐC — `XacChuot_GOC_yyyyMMdd_HHmmss_SSS.mp4`**: hình camera nguyên vẹn, KHÔNG in khung AI. Dùng trích ảnh làm dữ liệu huấn luyện. Lưu tại `Movies/ThuXacChuot/GOC`.
2. **AI — `XacChuot_AI_yyyyMMdd_HHmmss_SSS.mp4`**: video lấy từ GỐC và ghép khung, tên lớp, điểm mô hình tại từng thời điểm. Lưu tại `Movies/ThuXacChuot/AI`. Bản AI được xuất **sau khi dừng quay**, không ghi đồng thời bằng hai bộ encoder. Vì phải mã hóa lại video, nhất là với Samsung S7 Edge, bản AI có thể mất nhiều thời gian để tạo và làm máy nóng. Cần để app mở đến khi thấy **ĐÃ LƯU CẢ 2 VIDEO**.

Nếu tạo bản AI thất bại hoặc bạn đóng app trước khi xong, video GỐC vẫn còn. Vào `2 VIDEO / MỐC` → chọn lần quay → `Tạo lại MP4 AI` để thử lại. Dữ liệu khung AI ở bộ nhớ riêng ứng dụng: **gỡ app/xóa dữ liệu ứng dụng sẽ mất các mốc**; hãy sao lưu video và CSV quan trọng trước khi gỡ.

**Các video từ bản 0.4 không tự trở thành video GỐC sạch** sau khi nâng cấp; cần quay mới bằng bản 0.5 để có hai file.

Khung được lưu theo mẫu AI khoảng 4 lần/giây, không phải mô hình quét đủ 30 fps. Trên một số thiết bị, do tỷ lệ cắt ảnh/độ trễ giữa ImageAnalysis và video, thời gian và vị trí khung có thể lệch; cần thử và đối chiếu với thực tế.

## Xem và tua mốc

Chọn `2 VIDEO / MỐC` → một lần quay → `XEM VIDEO AI + MỐC (MÀN HÌNH LỚN)` hoặc `XEM VIDEO GỐC`.

- Video chiếm phần lớn màn hình, nút `⛶ Toàn màn hình` để ẩn danh sách và xem lớn hơn, chạm video hoặc Back để thoát.
- Bên dưới có danh sách mốc ở thời điểm AI phát hiện; chạm một mốc sẽ tua tới **2 giây trước**.
- Bạn có thể chia sẻ riêng GỐC, AI, hoặc xuất file CSV các mốc.
- Lưu ý số % là **điểm do mô hình tạo**, không phải độ chính xác đã được kiểm nghiệm.

## Âm báo trực tiếp

- AI nhận diện lớp `chuot_chet` ở điểm từ **0.45 đến dưới 0.80** qua ít nhất 2 lần quét liên tiếp → **1 tiếng bíp**.
- Điểm từ **0.80 trở lên** qua ít nhất 2 lần quét liên tiếp → **3 tiếng bíp**.
- Gộp các kết quả liên tục thành cùng một sự kiện để tránh chuông lặp dồn dập. Có thể báo nâng cấp từ 1 lên 3 bíp khi độ tin cậy tăng.
- Bấm `Bíp cảnh báo: BẬT / TẮT` để điều khiển tiếng bíp.
- Nếu đang quay có micro, **bíp từ loa có thể lọt vào âm thanh video GỐC**. Muốn âm thanh nguyên bản, hãy tắt bíp hoặc quay không ghi âm.

## Huấn luyện tiếp bằng makesense.ai

Website https://www.makesense.ai/ nhận **ảnh** để gán nhãn bounding box. Nếu chỉ có video MP4, cần trích frame từ video GỐC **trước** khi đưa lên website:

```bash
pip install opencv-python
python huan_luyen/trich_xuat_anh.py --video XacChuot_GOC_xxx.mp4 --interval 2 --out anh_moi
```

Hoặc trích nhiều frame gần những mốc đã phát hiện:

```bash
python huan_luyen/trich_xuat_anh.py --video XacChuot_GOC_xxx.mp4 --csv XacChuot_GOC_xxx_MocAI.csv --out anh_moi
```

Dùng makesense.ai gán nhãn ảnh với **chuot_chet** và **meo_chet**, xuất chuẩn YOLO, kiểm tra nhãn bằng mắt. Giữ cả dữ liệu cũ và ảnh mới trước khi huấn luyện lại trên Colab/máy tính rồi xuất `.tflite` mới. Ứng dụng chỉ giúp **thu thập**, không tự huấn luyện.

## Cập nhật GitHub

Giải nén ZIP, vào thư mục `thu-xac-chuot-main`, tải lên GitHub tất cả file bên trong (giữ nguyên đường dẫn `app/src/main/java/vn/xacchuot/thu/`). Chạy GitHub Actions > `Build APK`. File `.tflite` đã kèm theo. Nếu GitHub báo lỗi, gửi ZIP log; không khẳng định APK thành công trước khi chạy thực tế.
