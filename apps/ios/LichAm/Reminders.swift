import SwiftUI
import UIKit
import UserNotifications
import LunarCore
import os

/// Event reminders on iOS (shared/design/events.md, "Reminders"). Android sets one alarm and the next
/// when it goes off; iOS has no such wake-up, and Apple's documentation says the system keeps the 64
/// soonest local notifications of an app. So the next 64 reminders are registered at once, each with a
/// calendar trigger at its minute (local time, so a time zone change keeps 08:00 at 08:00). They are
/// replaced when events change, when the app becomes active (start, back from the background) and on a
/// clock or time zone change. Tapping one opens the app on that day.
enum Reminders {
    static let limit = 64
    private static let prefix = "reminder|"
    private static let log = Logger(subsystem: "com.coding4pizza.lunar", category: "reminders")
    @MainActor private static var running: Task<Void, Never>?

    /// Replaces the registered reminders with the next ones of [events]. One run at a time, in order,
    /// so a quick second edit never leaves the first one's reminders behind.
    @MainActor static func schedule(_ events: [LunarEvent]) {
        let previous = running
        running = Task {
            await previous?.value
            await replace(events)
        }
    }

    private static func replace(_ events: [LunarEvent]) async {
        let center = UNUserNotificationCenter.current()
        let old = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: old)
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else {
            log.info("not allowed (status \(status.rawValue, privacy: .public)), removed \(old.count, privacy: .public)")
            return
        }
        let fires = PersonalEvents.nextReminders(events, from: nextMinute(), limit: limit)
        var added = 0
        for (i, f) in fires.enumerated() {
            do {
                try await center.add(request(f, index: i))
                added += 1
            } catch {
                log.error("add failed: \(error.localizedDescription, privacy: .public)")
            }
        }
        // What the system now holds (its own count and its next trigger date), not what was sent.
        let held = await center.pendingNotificationRequests().filter { $0.identifier.hasPrefix(prefix) }
        let nextDate = held.compactMap { ($0.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() }.min()
        log.notice("registered \(added, privacy: .public) of \(fires.count, privacy: .public), first at \(fires.first.map { "\($0.at)" } ?? "-", privacy: .public); system holds \(held.count, privacy: .public), next calendar trigger \(nextDate?.formatted(.iso8601) ?? "-", privacy: .public)")
    }

    /// The next whole minute: a trigger at the current minute would already be in the past.
    private static func nextMinute(_ now: Date = Date()) -> LocalDateTime {
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        return LocalDateTime(c.year!, c.month!, c.day!, c.hour!, c.minute!).plusMinutes(1)
    }

    private static func request(_ f: ReminderFire, index: Int) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = f.event.title
        // "Hôm nay" / "Ngày mai" as read on the day it shows.
        let text = PersonalEvents.reminderText(f.event, f.start, f.at.date)
        let place = f.event.location?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        content.body = place.isEmpty ? text : "\(text)\n\(place)"
        content.sound = .default
        content.userInfo = ["link": "com.coding4pizza.lunar://open?tab=calendar&d=\(f.start.iso)"]
        let when = DateComponents(year: f.at.date.year, month: f.at.date.month, day: f.at.date.day,
                                  hour: f.at.hour, minute: f.at.minute)
        var trigger: UNNotificationTrigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: false)
        #if DEBUG
        // UI tests: the first reminder shows 3 s after it is registered (its banner and its tap).
        if index == 0 && CommandLine.arguments.contains("-ui-reminder-soon") {
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
        }
        #endif
        return UNNotificationRequest(identifier: "\(prefix)\(f.event.id)|\(f.start.iso)|\(f.minutes)", content: content, trigger: trigger)
    }

    enum Permission { case allowed, justAllowed, refused, off }

    /// Asks the system the first time (.justAllowed / .refused); .off = turned off, and iOS never asks
    /// again, so only Settings can turn them back on.
    static func ask() async -> Permission {
        let center = UNUserNotificationCenter.current()
        switch await center.notificationSettings().authorizationStatus {
        case .notDetermined:
            let ok = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
            return ok ? .justAllowed : .refused
        case .denied:
            return .off
        default:
            return .allowed
        }
    }
}

/// The app delegate, for notifications only: a reminder shows as a banner while the app is open too,
/// and tapping one opens its link (com.coding4pizza.lunar://open?tab=calendar&d=…).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private var open: ((URL) -> Void)?
    /// A link tapped before the app's state was connected (a start from the notification).
    private var pending: URL?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// Where tapped links go; a link that came first is opened now.
    func connect(_ open: @escaping (URL) -> Void) {
        self.open = open
        if let url = pending {
            pending = nil
            open(url)
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let link = (response.notification.request.content.userInfo["link"] as? String).flatMap(URL.init(string:))
        DispatchQueue.main.async {
            if let link {
                if let open = self.open { open(link) } else { self.pending = link }
            }
            completionHandler()
        }
    }
}

/// "Bật thông báo để nhận nhắc" (Android's NotificationsOffDialog): notifications are off and iOS will
/// not ask again; "Mở cài đặt" opens the app's notification settings.
struct NotificationsOffAlert: ViewModifier {
    @Binding var shown: Bool

    func body(content: Content) -> some View {
        content.alert("Bật thông báo để nhận nhắc", isPresented: $shown) {
            Button("Để sau", role: .cancel) {}
            Button("Mở cài đặt") {
                if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
            }
        } message: {
            Text("Thông báo của app đang tắt nên lời nhắc sẽ không hiện. Mở cài đặt và bật thông báo cho Lịch Âm.")
        }
    }
}
