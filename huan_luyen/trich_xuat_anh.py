"""Tách ảnh chưa có nhãn từ video để người dùng gán nhãn trước khi train.

pip install opencv-python
python trich_xuat_anh.py --video raw.mp4 --interval 2 --out ./dataset_images
python trich_xuat_anh.py --video raw.mp4 --csv moc_ai.csv --out ./dataset_images
CSV schema from app: video,time_seconds,label,confidence,verified
"""
import argparse
import csv
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description='Tách ảnh từ video để kiểm tra và gán nhãn')
    parser.add_argument('--video', required=True, help='Video cần tách ảnh (ưu tiên video gốc không overlay)')
    parser.add_argument('--csv', help='Bảng mốc AI do app chia sẻ, lưu thành .csv')
    parser.add_argument('--interval', type=float, default=2, help='Giãn cách khung hình khi không có CSV, giây')
    parser.add_argument('--out', default='anh_can_gan_nhan')
    args = parser.parse_args()
    if args.interval <= 0:
        parser.error('--interval phải lớn hơn 0')

    import cv2
    video = Path(args.video)
    if not video.is_file():
        parser.error(f'Không thấy video: {video}')
    dest = Path(args.out)
    dest.mkdir(parents=True, exist_ok=True)
    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        parser.error('Không mở được video')
    duration = cap.get(cv2.CAP_PROP_FRAME_COUNT) / max(1.0, cap.get(cv2.CAP_PROP_FPS))
    times = set()
    if args.csv:
        with open(args.csv, newline='', encoding='utf-8-sig') as f:
            for row in csv.DictReader(f):
                if row.get('video') and row['video'] != video.name:
                    continue
                try:
                    t = float(row['time_seconds'])
                except (ValueError, KeyError):
                    continue
                for diff in (-2, -1, 0, 1, 2):
                    times.add(max(0, round(t + diff, 2)))
    else:
        t = 0.0
        while t <= duration and len(times) < 100000:
            times.add(round(t, 3))
            t += args.interval

    n = 0
    with open(dest / 'DANH_SACH_CAN_KIEM_TRA.csv', 'w', newline='', encoding='utf-8-sig') as index:
        writer = csv.writer(index)
        writer.writerow(['image', 'video', 'time_seconds', 'manually_verified'])
        for t in sorted(times):
            if t > duration:
                continue
            cap.set(cv2.CAP_PROP_POS_MSEC, t * 1000.0)
            ok, frame = cap.read()
            if not ok:
                continue
            name = f'{video.stem}_{int(t*1000):09d}ms.jpg'
            if cv2.imwrite(str(dest / name), frame):
                writer.writerow([name, video.name, f'{t:.3f}', 'no'])
                n += 1
    cap.release()
    print(f'Đã trích {n} ảnh vào {dest}. Chúng chưa có nhãn!')
    print('Bước tiếp: mở ảnh, loại ảnh trùng/sai, gán nhãn bbox rồi mới dùng cho huấn luyện.')


if __name__ == '__main__':
    main()
