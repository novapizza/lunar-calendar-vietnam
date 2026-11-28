import SwiftUI
import LunarCore

// Personal events (Android's ui/EventSheets.kt, shared/design/events.md): the day's card, the list
// "Sự kiện của bạn", an occurrence's detail and the editor. Reminders are stored here; they are
// scheduled as notifications from step 7 of the iOS plan.

/// "17/10 âm" (leap months: "17/6 nhuận âm").
private func lunarLabel(_ d: SolarDate) -> String {
    guard d.isInRange else { return "" }
    let l = LunarCalendar.solarToLunar(d)
    return "\(l.day)/\(l.month)\(l.leap ? " nhuận" : "") âm"
}

/// "Cả ngày", "09:00 – 10:00", plus " · đến Thứ Năm 26/11" when the occurrence covers several days.
private func timeWithEnd(_ e: LunarEvent, _ start: SolarDate) -> String {
    let base = PersonalEvents.timeLabel(e)
    guard e.span > 1 else { return base }
    let last = start.plusDays(e.span - 1)
    return "\(base) · đến \(last.weekdayName) \(last.day)/\(last.month)"
}

private let repeats: [(String, String)] = [
    (LunarEvent.none, "Không lặp"), (LunarEvent.daily, "Hằng ngày"), (LunarEvent.weekly, "Hằng tuần"),
    (LunarEvent.monthly, "Hằng tháng"), (LunarEvent.yearly, "Hằng năm"),
]
private func repeatLabel(_ r: String) -> String { repeats.first { $0.0 == r }?.1 ?? r }

private struct ColorDot: View {
    let token: String?
    var size: CGFloat = 8
    @Environment(\.colors) private var c
    var body: some View { Circle().fill(c.event(token)).frame(width: size, height: size) }
}

// MARK: - Router

/// The sheet [AppState.sheet] names, shown in one system sheet (AppScaffold); a sheet opened over
/// another replaces it and the one under comes back when it closes.
struct AppSheets: View {
    @Environment(AppState.self) private var state

    var body: some View {
        switch state.sheet {
        case .quickDate:
            QuickDateSheet(start: state.selectedDate)
        case .auspicious:
            AuspiciousSheet()
        case .lunarEvents:
            LunarEventsSheet()
        case .about:
            AboutSheet()
        case .birthDate:
            BirthDateSheet(value: state.birthDate)
        case .personalEvents:
            PersonalEventsSheet()
        case .eventDetail:
            if let ref = state.detailRef, let e = state.eventById(ref.id) {
                EventDetailSheet(ref: ref, e: e).id(ref)
            } else {
                Color.clear.onAppear { state.closeSheet() }
            }
        case .eventEditor:
            if let t = state.editTarget {
                let original = if let b = t.builtin { BuiltinReminders.of(state.builtinReminders, b.ref) } else { t.id.flatMap(state.eventById) }
                EventEditorSheet(target: t, original: original).id(t)
            } else {
                Color.clear.onAppear { state.closeSheet() }
            }
        case nil:
            EmptyView()
        }
    }
}

// MARK: - The day's card

/// "Sự kiện" card (Hôm nay, the Calendar's panel): the day's occurrences (tap: detail) and "+ Thêm"
/// (the editor on that day).
struct DayEventsCard: View {
    let day: SolarDate
    var compact = false
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let list = state.personalOn(day)
        Card {
            HStack {
                SectionLabel(text: "Sự kiện", icon: "note.text")
                Spacer()
                SoftButton(text: "Thêm", icon: "plus") { state.openEditor(nil, day) }
                    .accessibilityLabel("Thêm sự kiện")
            }
            if list.isEmpty {
                Text("Chưa có sự kiện trong ngày này.").font(AppFont.sans(13)).foregroundStyle(c.fg3).padding(.top, compact ? 8 : 12)
            } else {
                VStack(spacing: 8) {
                    ForEach(list, id: \.event.id) { item in
                        Button { state.openDetail(item.event.id, item.start) } label: {
                            HStack(spacing: 10) {
                                ColorDot(token: item.event.color)
                                Text(item.event.title).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg).lineLimit(2)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text(PersonalEvents.timeLabel(item.event)).font(AppFont.sans(12)).foregroundStyle(c.fg2)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 12).fill(c.eventSoft(item.event.color)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, compact ? 8 : 12)
            }
        }
    }
}

// MARK: - Sự kiện của bạn

struct PersonalEventsSheet: View {
    @Environment(AppState.self) private var state

    var body: some View {
        SheetFrame(title: "Sự kiện của bạn", icon: "note.text", subtitle: "Giỗ, sinh nhật, lịch hẹn… theo ngày âm hoặc dương",
                   onClose: { state.closeSheet() }) {
            PersonalEventsList(emptyText: "Thêm giỗ, sinh nhật, lịch hẹn… Sự kiện hiện trong lịch, danh sách sự kiện và có thể nhắc trước.")
        }
    }
}

/// The user's events, each opening its detail on its next occurrence; "Thêm" opens the editor.
struct PersonalEventsList: View {
    let emptyText: String
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c

    var body: some View {
        let t = today()
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                SectionLabel(text: "Sự kiện riêng (\(state.lunarEvents.count))")
                Spacer()
                SoftButton(text: "Thêm", icon: "plus") { state.openEditor(nil, state.selectedDate) }
                    .accessibilityLabel("Thêm sự kiện")
            }
            .padding(.bottom, 8)
            if state.lunarEvents.isEmpty {
                Text(emptyText).font(AppFont.sans(14)).foregroundStyle(c.fg2).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 16).padding(.vertical, 24)
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(c.line, style: StrokeStyle(lineWidth: 1, dash: [6, 4])))
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(state.lunarEvents.enumerated()), id: \.element.id) { i, e in
                        if i > 0 { Hairline() }
                        Button {
                            let day = PersonalEvents.nextOccurrence(e, t) ?? PersonalEvents.startOf(e) ?? t
                            state.openDetail(e.id, day)
                        } label: {
                            HStack(spacing: 12) {
                                ColorDot(token: e.color, size: 10)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(e.title).font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg)
                                    Text(PersonalEvents.describe(e) + (e.allDay ? "" : " · \(e.startTime)"))
                                        .font(AppFont.sans(12)).foregroundStyle(c.fg2)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.fg3)
                            }
                            .padding(.leading, 16).padding(.trailing, 12).padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .background(RoundedRectangle(cornerRadius: 16).fill(c.surface2))
            }
        }
    }
}

// MARK: - Detail

private struct DetailLine: View {
    let icon: String
    let text: String
    var strong = false
    @Environment(\.colors) private var c

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).font(.system(size: 14, weight: .medium)).foregroundStyle(c.fg3).frame(width: 18).padding(.top, 2)
            Text(text).font(AppFont.sans(14, strong ? .semibold : .regular)).foregroundStyle(strong ? c.fg : c.fg2)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// One occurrence: day, time, repeat, reminders, place, notes; Sửa / Xoá (this one or all for repeats).
struct EventDetailSheet: View {
    let ref: EventRef
    let e: LunarEvent
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    /// "edit" / "delete": which button asked "Chỉ lần này / Tất cả các lần".
    @State private var ask: String?

    var body: some View {
        let start = ref.start
        SheetFrame(
            title: e.title,
            heading: AnyView(HStack(spacing: 12) {
                Capsule().fill(c.event(e.color)).frame(width: 5, height: 32)
                Text(e.title).font(AppFont.sans(18, .bold)).tracking(-0.18).foregroundStyle(c.fg).accessibilityAddTraits(.isHeader)
            }),
            footer: AnyView(footer(start)),
            onClose: { state.closeSheet() }
        ) {
            VStack(alignment: .leading, spacing: 12) {
                DetailLine(icon: "calendar", text: "\(start.longLabel) · \(lunarLabel(start))", strong: true)
                DetailLine(icon: "clock", text: timeWithEnd(e, start))
                if e.isRepeating { DetailLine(icon: "repeat", text: PersonalEvents.describe(e)) }
                if !e.reminders.isEmpty {
                    DetailLine(icon: "bell", text: e.reminders.sorted().map { PersonalEvents.reminderLabel(e.allDay, $0) }.joined(separator: ", "))
                }
                if let l = e.location, !l.trimmingCharacters(in: .whitespaces).isEmpty { DetailLine(icon: "mappin.and.ellipse", text: l) }
                if let n = e.notes, !n.trimmingCharacters(in: .whitespaces).isEmpty { DetailLine(icon: "doc.text", text: n) }
            }
        }
    }

    @ViewBuilder private func footer(_ start: SolarDate) -> some View {
        if let ask {
            let edit = ask == "edit"
            VStack(spacing: 8) {
                Text(edit ? "Sửa sự kiện lặp lại" : "Xoá sự kiện lặp lại").font(AppFont.sans(14, .bold)).foregroundStyle(c.fg)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    GhostButton(text: "Chỉ lần này") {
                        if edit { self.ask = nil; state.openEditor(e.id, start, onlyThis: true) } else { state.deleteOccurrence(e.id, start); state.closeSheet() }
                    }
                    PrimaryButton(text: "Tất cả các lần", tall: true) {
                        if edit { self.ask = nil; state.openEditor(e.id, start) } else { state.deleteEvent(e.id); state.closeSheet() }
                    }
                }
                Button { self.ask = nil } label: {
                    Text("Huỷ").font(AppFont.sans(14, .semibold)).foregroundStyle(c.fg2).frame(maxWidth: .infinity).padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        } else {
            HStack(spacing: 8) {
                GhostButton(text: "Xoá") {
                    if e.isRepeating { ask = "delete" } else { state.deleteEvent(e.id); state.closeSheet() }
                }
                PrimaryButton(text: "Sửa", tall: true) {
                    if e.isRepeating { ask = "edit" } else { state.openEditor(e.id, start) }
                }
            }
        }
    }
}

// MARK: - Editor

private func hm(_ s: String) -> (Int, Int) {
    let p = s.split(separator: ":")
    guard p.count == 2, let h = Int(p[0]), let m = Int(p[1]) else { return (9, 0) }
    return (h, m)
}

private func hmText(_ h: Int, _ m: Int) -> String { String(format: "%02d:%02d", h, m) }

/// The editor's fields (Android's EventForm); [original] is the event edited (nil: a new one).
@Observable @MainActor
final class EventForm {
    let target: EventEditTarget
    let original: LunarEvent?
    var onlyThis: Bool { target.onlyThis }
    /// Setting a built-in day's reminder: only the repeat, its end and the reminders are the form's.
    var builtin: BuiltinDay? { target.builtin }

    var title: String
    var allDay: Bool
    var date: SolarDate
    var lunarEntry: Bool
    var lDay = 0
    var lMonth = 1
    var lYear = 2026
    var lLeap = false
    var startH: Int
    var startM: Int
    var endH: Int
    var endM: Int
    var days: Int
    var repeatKind: String
    var weekdays: Set<Int>
    var calendar: String
    var leapOnly: Bool
    /// 0 never, 1 until a day, 2 after a number of times.
    var endMode: Int
    var until: SolarDate
    var count: Int
    var reminders: [Int]
    var location: String
    var notes: String
    var color: String

    init(target: EventEditTarget, original: LunarEvent?) {
        self.target = target
        self.original = original
        let builtin = target.builtin
        title = original?.title ?? builtin?.title ?? ""
        allDay = original?.allDay ?? true
        date = if let original, !target.onlyThis { PersonalEvents.startOf(original) ?? target.day } else { target.day }
        lunarEntry = original.map { !target.onlyThis && $0.isLunarRule } ?? false
        let st = hm(original?.startTime ?? "09:00")
        let en = hm(original?.endTime ?? "10:00")
        startH = st.0
        startM = st.1
        endH = en.0
        endM = en.1
        days = original?.span ?? 1
        repeatKind = target.onlyThis ? LunarEvent.none
            : original?.repeatKind ?? builtin.map { BuiltinReminders.repeats($0.ref)[0] } ?? LunarEvent.none
        let wd = original.map { Set(PersonalEvents.weekdaysOf($0)) } ?? []
        weekdays = wd.isEmpty ? [target.day.weekdayIndex] : wd
        // A built-in day's reminder follows the day's own calendar (a one-off one is stored as solar).
        calendar = builtin?.calendar ?? original?.calendar ?? LunarEvent.lunar
        leapOnly = original?.isLeapMonth ?? true
        endMode = target.onlyThis ? 0 : original?.until != nil ? 1 : original?.count != nil ? 2 : 0
        until = original.flatMap(PersonalEvents.untilOf) ?? target.day.plusMonths(1)
        count = original?.count ?? 10
        reminders = original.map { Array(Set($0.reminders)).sorted() } ?? (builtin != nil ? [0] : [])
        location = original?.location ?? ""
        notes = original?.notes ?? ""
        color = original?.color ?? LunarEvent.gold

        let l = LunarCalendar.solarToLunar(date)
        lYear = l.year; lMonth = l.month; lLeap = l.leap
        // A lunar rule keeps its day (30 stays 30 in a 29-day month).
        if let original, original.isLunarRule, !target.onlyThis { lDay = original.lunarDay } else { lDay = l.day }
        if original == nil || original?.repeatKind != LunarEvent.yearly || original?.calendar != LunarEvent.lunar { leapOnly = true }
        // Built-in days are in regular months: their yearly reminder never keeps to a leap one.
        if builtin != nil { leapOnly = false }
    }

    /// The start day in the solar calendar; nil when the lunar date entered does not exist.
    var start: SolarDate? {
        guard lunarEntry else { return date }
        if case .ok(let s, _) = Conversion.lunarToSolar(lDay, lMonth, lYear, lLeap) { return s }
        return nil
    }

    /// The lunar date of the rule: the one entered, else the start day's.
    func ruleLunar(_ s: SolarDate) -> (day: Int, month: Int, leap: Bool) {
        if lunarEntry { return (lDay, lMonth, lLeap) }
        let l = LunarCalendar.solarToLunar(s)
        return (l.day, l.month, l.leap)
    }

    var repeating: Bool { repeatKind != LunarEvent.none }
    var rule: Bool { repeatKind == LunarEvent.monthly || repeatKind == LunarEvent.yearly }

    var error: String? {
        guard let s = start else { return "Ngày âm này không có trong lịch." }
        if !s.isInRange { return "Lịch tính từ 31/1/1900 đến 31/12/2099." }
        if !allDay && days == 1 && endH * 60 + endM <= startH * 60 + startM { return "Giờ kết thúc phải sau giờ bắt đầu." }
        if repeatKind == LunarEvent.weekly && weekdays.isEmpty { return "Chọn ít nhất một ngày trong tuần." }
        if repeating && endMode == 1 && until < s { return "Ngày kết thúc phải từ ngày bắt đầu trở đi." }
        return nil
    }

    var titleOk: Bool { !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    func build() -> LunarEvent? {
        guard let s = start, error == nil, titleOk || builtin != nil else { return nil }
        let lunarRule = rule && calendar == LunarEvent.lunar
        let (ld, lm, leap): (Int, Int, Bool) = if lunarRule {
            { let r = ruleLunar(s); return (r.day, r.month, repeatKind == LunarEvent.yearly && r.leap && leapOnly) }()
        } else { (s.day, s.month, false) }
        // An old event without a start keeps none: a start would hide its earlier occurrences.
        let keepNoStart = rule && original != nil && !onlyThis && original?.start == nil && endMode != 2
        var e = if let original, !onlyThis { original } else {
            LunarEvent(id: UUID().uuidString.lowercased(), title: "", lunarDay: 1, lunarMonth: 1, isLeapMonth: false,
                       createdAt: Int(Date().timeIntervalSince1970 * 1000))
        }
        e.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        e.lunarDay = ld; e.lunarMonth = lm; e.isLeapMonth = leap
        e.calendar = lunarRule ? LunarEvent.lunar : LunarEvent.solar
        e.repeatKind = repeatKind
        e.start = keepNoStart ? nil : s.iso
        e.weekdays = repeatKind == LunarEvent.weekly ? weekdays.sorted() : []
        e.until = repeating && endMode == 1 ? until.iso : nil
        e.count = repeating && endMode == 2 ? count : nil
        e.exceptions = original != nil && !onlyThis && repeating ? original!.exceptions : []
        e.allDay = allDay
        e.startTime = allDay ? "09:00" : hmText(startH, startM)
        e.endTime = allDay ? "10:00" : hmText(endH, endM)
        e.days = days
        e.reminders = Array(Set(reminders)).sorted()
        let n = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        let loc = location.trimmingCharacters(in: .whitespacesAndNewlines)
        e.notes = n.isEmpty ? nil : n
        e.location = loc.isEmpty ? nil : loc
        e.color = color
        if let builtin {
            // shared/design/events.md: a monthly reminder is named by the ref ("Mồng 1").
            e.title = repeatKind == LunarEvent.monthly ? builtin.ref : builtin.title
            e.ref = builtin.ref
            e.allDay = true
            e.color = LunarEvent.gold
            e.notes = nil
            e.location = nil
        }
        return e
    }
}

/// "Sự kiện mới" / "Sửa sự kiện" / "Sửa lần này" / "Nhắc ngày này" (shared/design/events.md, "Screens").
struct EventEditorSheet: View {
    let target: EventEditTarget
    let original: LunarEvent?
    @Environment(AppState.self) private var state
    @Environment(\.colors) private var c
    @State private var f: EventForm
    @State private var notificationsOff = false

    init(target: EventEditTarget, original: LunarEvent?) {
        self.target = target
        self.original = original
        _f = State(initialValue: EventForm(target: target, original: original))
    }

    private var title: String {
        if target.builtin != nil { return "Nhắc ngày này" }
        if original == nil { return "Sự kiện mới" }
        return target.onlyThis ? "Sửa lần này" : "Sửa sự kiện"
    }

    private var subtitle: String? {
        let d = target.day
        if let b = target.builtin {
            return "\(b.title) · \(d.day)/\(d.month)/\(String(d.year)) · \(lunarLabel(d))"
        }
        return target.onlyThis ? "Chỉ ngày \(d.day)/\(d.month)/\(String(d.year)), các lần khác giữ nguyên" : nil
    }

    var body: some View {
        SheetFrame(title: title, icon: target.builtin != nil ? "bell.badge" : "note.text", subtitle: subtitle,
                   footer: AnyView(footer), onClose: { state.closeSheet() }) {
            form
        }
        .interactiveDismissDisabled(f.titleOk && target.builtin == nil)
        .modifier(NotificationsOffAlert(shown: $notificationsOff))
        // Notifications for reminders (shared/design/events.md): asked when the form opens with
        // reminders and when one is added; when they are off, the alert offers Settings.
        .onAppear { if !f.reminders.isEmpty { askNotifications(alert: true) } }
    }

    /// Asks iOS the first time (and registers the reminders once allowed); [alert]: offer Settings when
    /// they are off. Lưu asks without the alert, as the sheet closes.
    private func askNotifications(alert: Bool) {
        let state = state
        Task { @MainActor in
            switch await Reminders.ask() {
            case .justAllowed: state.scheduleReminders()
            case .off: if alert { notificationsOff = true }
            case .allowed, .refused: break
            }
        }
    }

    @ViewBuilder private var footer: some View {
        if let b = target.builtin {
            HStack(spacing: 8) {
                if original != nil { GhostButton(text: "Tắt nhắc") { state.removeBuiltinReminder(b.ref); state.closeSheet() } }
                PrimaryButton(text: "Lưu", enabled: !f.reminders.isEmpty && f.error == nil, tall: true) {
                    guard let e = f.build() else { return }
                    state.saveBuiltinReminder(e)
                    askNotifications(alert: false)
                    state.closeSheet()
                }
                .layoutPriority(1)
            }
        } else {
            PrimaryButton(text: "Lưu", enabled: f.titleOk && f.error == nil, tall: true) {
                guard let e = f.build() else { return }
                if target.onlyThis, let original { state.saveOccurrence(seriesId: original.id, day: target.day, copy: e) } else { state.saveEvent(e) }
                if !e.reminders.isEmpty { askNotifications(alert: false) }
                state.closeSheet()
            }
        }
    }

    @ViewBuilder private var form: some View {
        let start = f.start
        VStack(alignment: .leading, spacing: 16) {
            if let b = target.builtin {
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Lặp lại")
                    SelectField(value: f.repeatKind, options: BuiltinReminders.repeats(b.ref), label: repeatLabel) { f.repeatKind = $0 }
                }
            } else {
                FormTextField(placeholder: "Tên sự kiện, vd: Giỗ ông nội", text: $f.title)

                Toggle(isOn: Binding(get: { f.allDay }, set: { on in
                    f.allDay = on
                    // Reminder options differ: keep only those the new kind offers.
                    let allowed = Set(PersonalEvents.reminderOptions(on).map(\.minutes))
                    f.reminders = f.reminders.filter { allowed.contains($0) }
                })) {
                    Text("Cả ngày").font(AppFont.sans(15, .semibold)).foregroundStyle(c.fg)
                }
                .tint(c.brand)

                // Ngày bắt đầu: an edited lunar rule keeps its own day (30 stays 30) until another day is picked.
                DatePickerField(value: start ?? f.date, label: "Ngày bắt đầu") { f.date = $0; f.lunarEntry = false }

                if !f.allDay {
                    VStack(spacing: 8) {
                        TimeSelects(label: "Bắt đầu", h: f.startH, m: f.startM) { h, m in
                            // Moving the start keeps the length when the end would come before it.
                            let len = (f.endH * 60 + f.endM) - (f.startH * 60 + f.startM)
                            f.startH = h; f.startM = m
                            if f.days == 1 && f.endH * 60 + f.endM <= h * 60 + m {
                                let end = min(h * 60 + m + max(len, 60), 23 * 60 + 55)
                                f.endH = end / 60; f.endM = end % 60
                            }
                        }
                        TimeSelects(label: "Kết thúc", h: f.endH, m: f.endM) { h, m in f.endH = h; f.endM = m }
                    }
                }

                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 0) {
                        FieldLabel(text: "Kéo dài")
                        SelectField(value: f.days, options: Array(1...30), label: { "\($0) ngày" }) { f.days = $0 }
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        FieldLabel(text: "Lặp lại")
                        SelectField(value: f.repeatKind, options: repeats.map(\.0), label: repeatLabel) { f.repeatKind = $0 }
                    }
                }
                if f.days > 1, let start {
                    let last = start.plusDays(f.days - 1)
                    Hint(text: "Đến \(last.longLabel)" + (f.allDay ? "" : ", \(hmText(f.endH, f.endM))"))
                }
            }

            if f.repeatKind == LunarEvent.weekly {
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Vào các ngày")
                    HStack(spacing: 6) {
                        ForEach(Array(PersonalEvents.weekdayShort.enumerated()), id: \.offset) { i, w in
                            let on = f.weekdays.contains(i)
                            Button { if on { f.weekdays.remove(i) } else { f.weekdays.insert(i) } } label: {
                                Text(w).font(AppFont.sans(13, .semibold)).foregroundStyle(on ? c.onBrand : c.fg)
                                    .frame(maxWidth: .infinity, minHeight: 40)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(on ? c.brand : c.surface2))
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }
            }

            if f.rule, let start { ruleNote(start) }

            if f.repeating {
                VStack(alignment: .leading, spacing: 8) {
                    FieldLabel(text: "Kết thúc").padding(.bottom, -8)
                    SelectField(value: f.endMode, options: [0, 1, 2], label: { ["Không bao giờ", "Đến ngày", "Sau … lần"][$0] }) { f.endMode = $0 }
                    if f.endMode == 1 { DatePickerField(value: f.until, label: "Đến ngày", min: start) { f.until = $0 } }
                    if f.endMode == 2 { SelectField(value: f.count, options: Array(1...100), label: { "Sau \($0) lần" }) { f.count = $0 } }
                }
            }

            remindersField

            if target.builtin == nil {
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Địa điểm")
                    FormTextField(placeholder: "Thêm địa điểm", text: $f.location)
                }
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Ghi chú")
                    FormTextField(placeholder: "Thêm ghi chú", text: $f.notes, minLines: 2)
                }
                VStack(alignment: .leading, spacing: 0) {
                    FieldLabel(text: "Màu")
                    HStack(spacing: 12) {
                        ForEach(Array(LunarEvent.colors.enumerated()), id: \.offset) { i, token in
                            let on = f.color == token
                            Button { f.color = token } label: {
                                Circle().fill(c.event(token)).frame(width: 28, height: 28)
                                    .overlay { if on { Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(.white) } }
                                    .padding(4)
                                    .overlay { if on { Circle().strokeBorder(c.fg, lineWidth: 2) } }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Màu \(i + 1)")
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }
            }

            if let error = f.error, f.titleOk { Text(error).font(AppFont.sans(13)).foregroundStyle(c.bad) }
        }
        .padding(.top, 4)
    }

    @ViewBuilder private func ruleNote(_ start: SolarDate) -> some View {
        let yearly = f.repeatKind == LunarEvent.yearly
        VStack(alignment: .leading, spacing: 0) {
            if target.builtin == nil {
                SegmentedChoice(options: [(LunarEvent.lunar, "Âm lịch"), (LunarEvent.solar, "Dương lịch")], selected: f.calendar) { f.calendar = $0 }
            }
            if f.calendar == LunarEvent.lunar {
                let r = f.ruleLunar(start)
                let head = yearly ? "Ngày \(r.day)/\(r.month)\(r.leap && f.leapOnly ? " nhuận" : "") âm hằng năm" : "Ngày \(r.day) âm hằng tháng"
                let tail = r.day == 30 ? ". Tháng thiếu (29 ngày): rơi vào ngày 29." : !yearly ? ", cả tháng nhuận." : "."
                Hint(text: head + tail, color: c.fg)
                if yearly && r.leap && target.builtin == nil {
                    Toggle(isOn: $f.leapOnly) { Text("Chỉ tháng nhuận").font(AppFont.sans(14)).foregroundStyle(c.fg) }
                        .tint(c.brand).padding(.top, 8)
                }
            } else {
                let d = start.day
                Hint(text: yearly
                     ? "Ngày \(d)/\(start.month) hằng năm" + (start.month == 2 && d == 29 ? ". Năm không nhuận: rơi vào 28/2." : ".")
                     : "Ngày \(d) hằng tháng" + (d > 28 ? ". Tháng không có ngày này: rơi vào ngày cuối tháng." : "."),
                     color: c.fg)
            }
        }
    }

    /// Nhắc: chips with ×, "+ Thêm nhắc".
    private var remindersField: some View {
        VStack(alignment: .leading, spacing: 0) {
            FieldLabel(text: "Nhắc")
            FlowLayout(spacing: 8) {
                ForEach(f.reminders, id: \.self) { m in
                    HStack(spacing: 0) {
                        Text(PersonalEvents.reminderLabel(f.allDay, m)).font(AppFont.sans(13, .semibold)).foregroundStyle(c.brand)
                        Button { f.reminders.removeAll { $0 == m } } label: {
                            Image(systemName: "xmark").font(.system(size: 11, weight: .bold)).foregroundStyle(c.brand)
                                .frame(width: 28, height: 28).contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Bỏ nhắc \(PersonalEvents.reminderLabel(f.allDay, m))")
                    }
                    .padding(.leading, 12).padding(.trailing, 4).padding(.vertical, 2)
                    .background(Capsule().fill(c.brandSoft))
                }
                let options = PersonalEvents.reminderOptions(f.allDay).filter { !f.reminders.contains($0.minutes) }
                if !options.isEmpty {
                    Menu {
                        ForEach(options, id: \.minutes) { o in
                            Button(o.label) {
                                f.reminders = Array(Set(f.reminders + [o.minutes])).sorted()
                                askNotifications(alert: true)
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus").font(.system(size: 12, weight: .semibold)).foregroundStyle(c.fg2)
                            Text("Thêm nhắc").font(AppFont.sans(13, .semibold)).foregroundStyle(c.fg)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 7)
                        .background(Capsule().fill(c.surface2))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct TimeSelects: View {
    let label: String
    let h: Int
    let m: Int
    let onChange: (Int, Int) -> Void
    @Environment(\.colors) private var c

    var body: some View {
        HStack(spacing: 8) {
            Text(label).font(AppFont.sans(14)).foregroundStyle(c.fg).frame(width: 72, alignment: .leading)
            SelectField(value: h, options: Array(0...23), label: { String(format: "%02d giờ", $0) }) { onChange($0, m) }
            SelectField(value: m, options: Array(stride(from: 0, through: 55, by: 5)), label: { String(format: "%02d phút", $0) }) { onChange(h, $0) }
        }
    }
}

/// Children left to right, wrapping to new lines (Compose's FlowRow).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, line: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > width { y += line + spacing; x = 0; line = 0 }
            x += s.width + spacing
            line = max(line, s.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: y + line)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, line: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { y += line + spacing; x = bounds.minX; line = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing
            line = max(line, s.height)
        }
    }
}
