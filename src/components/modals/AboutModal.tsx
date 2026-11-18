import { Info } from 'lucide-react';
import { Sheet } from '../Sheet';

interface Props {
  onClose: () => void;
}

const TECH = [
  { name: 'vn-lunar',     desc: 'Tính toán âm lịch Việt Nam theo công thức thiên văn.' },
  { name: 'date-fns',     desc: 'Xử lý và định dạng ngày tháng.' },
  { name: 'Lucide',       desc: 'Bộ biểu tượng giao diện.' },
  { name: 'Tailwind CSS', desc: 'Xây dựng giao diện.' },
];

export function AboutModal({ onClose }: Props) {
  return (
    <Sheet
      title="Thông tin ứng dụng"
      icon={<Info size={20} />}
      onClose={onClose}
      footer={<button onClick={onClose} className="btn-primary w-full py-3">Đã hiểu</button>}
    >
      <div className="flex flex-col gap-5 text-sm text-fg-2 leading-relaxed">
        <section>
          <h3 className="section-label mb-2">Phương pháp tính toán</h3>
          <div className="flex flex-col gap-2.5">
            <p>
              <strong className="text-fg">Âm lịch:</strong> dùng công thức thiên văn để xác định pha Mặt Trăng
              và 24 tiết khí, theo đúng lịch truyền thống Việt Nam.
            </p>
            <p>
              <strong className="text-fg">Ngày tốt / xấu:</strong> đánh giá dựa trên <em>Thập Nhị Trực</em> (12 Trực).
            </p>
            <p>
              <strong className="text-fg">Xung tuổi:</strong> theo quy tắc <em>Lục Xung</em> của Địa Chi
              (Tý–Ngọ, Sửu–Mùi, Dần–Thân, Mão–Dậu, Thìn–Tuất, Tỵ–Hợi).
            </p>
          </div>
        </section>

        <section>
          <h3 className="section-label mb-2">Công nghệ</h3>
          <ul className="rounded-2xl bg-surface-2 divide-y divide-line">
            {TECH.map(({ name, desc }) => (
              <li key={name} className="px-4 py-2.5">
                <p className="font-semibold text-fg">{name}</p>
                <p className="text-xs">{desc}</p>
              </li>
            ))}
          </ul>
        </section>

        <p className="rounded-2xl bg-gold-soft p-4 text-xs text-fg">
          Thông tin ngày tốt/xấu chỉ mang tính chất tham khảo dựa trên tài liệu cổ học phương Đông.
          Hãy cân nhắc kỹ trước các quyết định quan trọng.
        </p>
      </div>
    </Sheet>
  );
}
