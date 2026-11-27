import SwiftUI
import LunarCore

/// Hôm nay (Android's TodayScreen, the web's views/TodayView.tsx): the selected day as a tear-off
/// calendar sheet, then its assessment, events, activities and giờ hoàng đạo. One column on phones.
struct TodayScreen: View {
    @Environment(AppState.self) private var state

    var body: some View {
        // The clock for "Giờ …" and the hour now, on the minute.
        TimelineView(.everyMinute) { context in
            let now = context.date
            let info = state.selectedInfo
            let viewingToday = state.selectedDate == today(now)
            ScrollView {
                VStack(spacing: 16) {
                    TearOffSheet(now: now)
                    Assessment(info: info)
                    DayEventsCard(day: state.selectedDate)
                    Activities(info: info)
                    GioHoangDaoCard(info: info, hourChi: viewingToday ? DayFacts.chiOfHour(Calendar.current.component(.hour, from: now)) : nil)
                }
                .padding(EdgeInsets(top: 16, leading: 16, bottom: 40, trailing: 16))
            }
        }
    }
}

private struct Assessment: View {
    let info: DayInfo
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let (icon, label, bg, fg): (String, String, Color, Color) = switch info.rating {
        case .good: ("hand.thumbsup", "Ngày tốt", c.goodSoft, c.good)
        case .bad: ("hand.thumbsdown", "Ngày xấu", c.badSoft, c.bad)
        case .neutral: ("minus", "Ngày bình thường", c.surface2, c.fg2)
        }
        let menh = state.menh
        Card(padding: 0) {
            InfoRow(icon: icon, bg: bg, fg: fg, title: label, detail: "Trực \(info.truc)")
            Hairline()
            HStack(spacing: 12) {
                Text(state.userChi.flatMap(chiEmoji) ?? "🧧")
                    .font(.system(size: 18))
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
                VStack(alignment: .leading, spacing: 0) {
                    Text("Tuổi của bạn").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                    Text(menh.map { "Mệnh \($0.element.info.name) · \($0.napAm)" } ?? "Báo ngày xung tuổi")
                        .font(AppFont.sans(12)).foregroundStyle(c.fg2).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button { state.selectTab(.tools) } label: {
                    Text(menh?.canChi ?? "Nhập ngày sinh")
                        .font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg).lineLimit(1)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            if Zodiac.clashes(state.userChi, info), let chi = state.userChi {
                Hairline()
                InfoRow(icon: "exclamationmark.circle", bg: c.clashSoft, fg: c.clash, title: "Xung với tuổi \(chi)", detail: "Nên cân nhắc việc quan trọng")
            }
        }
    }
}

/// "Việc nên làm": the three activities and the Trừ note.
struct Activities: View {
    let info: DayInfo
    @Environment(\.colors) private var c

    var body: some View {
        Card {
            SectionLabel(text: "Việc nên làm")
            VStack(spacing: 8) {
                ForEach([("wedding", "Cưới hỏi", "heart"), ("moving", "Chuyển nhà", "hammer"), ("opening", "Khai trương", "storefront")], id: \.0) { key, label, icon in
                    let ok = info.activities.contains(key)
                    HStack(spacing: 12) {
                        IconTile(icon: icon, bg: ok ? c.goodSoft : c.surface2, fg: ok ? c.good : c.fg3, size: 36, iconSize: 16, radius: 12)
                        Text(label).font(AppFont.sans(14, .semibold)).foregroundStyle(ok ? c.fg : c.fg2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Badge(text: ok ? "Tốt" : "Không hợp", fg: ok ? c.good : c.fg3, bg: ok ? c.goodSoft : c.surface2)
                    }
                }
            }
            .padding(.top, 12)
            if info.truc == "Trừ" {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "sparkles").font(.system(size: 12)).foregroundStyle(c.gold).padding(.top, 2)
                    Text("Ngày Trừ: tốt để dọn dẹp, chữa bệnh, giải toả mâu thuẫn.").font(AppFont.sans(12)).foregroundStyle(c.fg2)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12).fill(c.surface2))
                .padding(.top, 12)
            }
        }
    }
}

/// Giờ hoàng đạo: brand-soft card, one row of six tiles; the hour now in brand with a ring.
struct GioHoangDaoCard: View {
    let info: DayInfo
    let hourChi: Int?
    @Environment(\.colors) private var c

    var body: some View {
        Card(color: c.brandSoft) {
            HStack(spacing: 8) {
                Image(systemName: "clock").font(.system(size: 14, weight: .medium)).foregroundStyle(c.brand)
                Text("Giờ hoàng đạo").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
            }
            // Emoji clamp(18, 6vw, 28) as the web.
            let emoji = min(max(UIScreen.main.bounds.width * 0.06, 18), 28)
            HStack(alignment: .top, spacing: 6) {
                ForEach(info.gioHoangDao, id: \.chiIndex) { h in
                    let now = h.chiIndex == hourChi
                    VStack(spacing: 0) {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(now ? c.brand : c.surface)
                            .webShadow(c.shadowTint)
                            .aspectRatio(1, contentMode: .fit)
                            .overlay { Text(chiEmoji(h.chi) ?? "🕒").font(.system(size: emoji)) }
                            .padding(now ? 4 : 0)
                            .overlay { if now { RoundedRectangle(cornerRadius: 16).stroke(c.brand, lineWidth: 2) } }
                            .frame(maxWidth: 56)
                        Text(h.chi).font(AppFont.sans(14, .semibold)).foregroundStyle(now ? c.brand : c.fg).padding(.top, 6)
                        Text(h.label).font(AppFont.sans(11, now ? .semibold : .regular))
                            .foregroundStyle(now ? c.brand : c.fg2).lineLimit(1).fixedSize()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 12)
        }
    }
}

/// "Sự kiện" card: the day's own events (detail and editor come with the Sự kiện step).
struct DayEventsCard: View {
    let day: SolarDate
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let list = state.personalOn(day)
        Card {
            HStack {
                SectionLabel(text: "Sự kiện", icon: "note.text")
                Spacer()
                SoftButton(text: "Thêm", icon: "plus") {}
                    .disabled(true)
                    .opacity(0.5)
            }
            if list.isEmpty {
                Text("Chưa có sự kiện trong ngày này.").font(AppFont.sans(13)).foregroundStyle(c.fg3).padding(.top, 12)
            } else {
                VStack(spacing: 8) {
                    ForEach(list, id: \.event.id) { item in
                        HStack(spacing: 10) {
                            Circle().fill(c.event(item.event.color)).frame(width: 8, height: 8)
                            Text(item.event.title).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg).lineLimit(2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(PersonalEvents.timeLabel(item.event)).font(AppFont.sans(12)).foregroundStyle(c.fg2)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(c.eventSoft(item.event.color)))
                    }
                }
                .padding(.top, 12)
            }
        }
    }
}
