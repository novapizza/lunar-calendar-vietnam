# Personal events (Sự kiện của bạn)

One design for the web app and the Android app (decided with the user 2026-11-23): events like a
regular calendar app (time or all day, full repeats, reminders, notes, location, colour), whose
monthly and yearly repeats can follow the lunar or the solar calendar. Both apps must produce the
same dates: `shared/test-vectors/events.json` is checked by both.

## Stored shape

Both apps keep the list as JSON under the key `lunarEvents` (web: localStorage; Android:
SharedPreferences `licham`), one object per event. Fields are optional unless marked; an absent
field has the default shown. Events saved before this design (only `id, title, lunarDay,
lunarMonth, isLeapMonth, createdAt`, sometimes `calendar` and `repeat`) read as lunar, yearly,
all day, no start, no reminders, and must keep working unchanged.

| Field | Type | Default | Meaning |
|---|---|---|---|
| `id` * | string | | uuid |
| `title` * | string | | |
| `createdAt` * | number | | ms since epoch |
| `calendar` | `"lunar"` \| `"solar"` | `"lunar"` | calendar of `lunarDay` / `lunarMonth` (used by monthly and yearly) |
| `repeat` | `"none"` \| `"daily"` \| `"weekly"` \| `"monthly"` \| `"yearly"` | `"yearly"` | |
| `lunarDay` * | 1-30 lunar, 1-31 solar | | day of the monthly / yearly rule, in `calendar` |
| `lunarMonth` * | 1-12 | | month of the yearly rule, in `calendar` |
| `isLeapMonth` * | boolean | | lunar yearly only: the leap copy of `lunarMonth` |
| `start` | `"YYYY-MM-DD"` (solar) | none | first day. Required for none, daily, weekly; for monthly / yearly a lower bound |
| `weekdays` | number[] 0=Mon … 6=Sun | `[weekday of start]` | weekly only |
| `until` | `"YYYY-MM-DD"` | none | last day an occurrence may start (inclusive) |
| `count` | number ≥ 1 | none | at most this many occurrences counted from `start` (removed ones count too); ignored without `start` |
| `exceptions` | `"YYYY-MM-DD"`[] | `[]` | occurrence start days removed ("chỉ lần này") |
| `allDay` | boolean | `true` | |
| `startTime`, `endTime` | `"HH:mm"` | `"09:00"`, `"10:00"` | timed events; `endTime` is on the last day |
| `days` | number ≥ 1 | `1` | an occurrence covers its start day and the `days - 1` days after it |
| `reminders` | number[] (minutes) | `[]` | minutes before the reminder base: the start time, or 08:00 of the start day when all day |
| `notes`, `location` | string | | |
| `color` | `"gold"` \| `"brand"` \| `"lunar"` \| `"good"` \| `"clash"` | `"gold"` | colour token of the theme |

Fields with their default value may be left out when saving (Android's kotlinx encoder leaves out
defaults; the web writes what it has). `lunarDay` / `lunarMonth` / `isLeapMonth` are always written
(old readers need them); for none / daily / weekly they hold the start day in `calendar`.

## Old notes

The day notes of the old "Ghi chú" card (key `userNotes`: `{id, date, content, createdAt}`) were
removed on 2026-11-23 in favour of events. On load, each app moves every note into `lunarEvents`
once — same `id` and `createdAt`, `repeat: "none"`, `calendar: "solar"`, `start` = the note's day,
all day, gold, `title` = the first line (60 characters at most, else 59 + "…", "Ghi chú" when
empty), `notes` = the whole text when it differs from the title — writes the events, and only
then deletes `userNotes`.

## Occurrence rule

The candidate start days of an event, in order:

- **none**: `start`.
- **daily**: every day from `start`.
- **weekly**: every day from `start` whose weekday is in `weekdays`.
- **monthly, solar**: in every month, day `min(lunarDay, days in that month)`.
- **monthly, lunar**: in every lunar month, leap months included, day `lunarDay`; day 30 in a
  29-day month falls on 29.
- **yearly, solar**: day `min(lunarDay, days in month)` of month `lunarMonth` (29/2 → 28/2).
- **yearly, lunar**: day `lunarDay` (30 → 29 in a 29-day month) of lunar month `lunarMonth`, its
  leap copy when `isLeapMonth` (years without that leap month have no occurrence).

Then: drop candidates before `start` (when set) and after `until`; with `start` and `count`, keep
the first `count` candidates; finally drop days in `exceptions`. What is left are the occurrences.
An occurrence starting on day D covers D … D + days - 1: "the events of a day" are the
occurrences covering it. Dates outside the lunar tables (1900-01-31 … 2099-12-31) have none.

Creating a monthly or yearly event from a start day takes `lunarDay` / `lunarMonth` from that day
in the chosen calendar, and for lunar yearly `isLeapMonth` = the start day is in a leap month (the
form shows "Chỉ tháng nhuận" then, ticked, and may untick it).

## Reminders

For each occurrence the reminder fires at base − minutes, base = start day + `startTime`, or start
day 08:00 when all day. Options: timed — đúng giờ (0), 5, 10, 15, 30 phút, 1 giờ (60), 2 giờ (120),
1 ngày (1440), 2 ngày (2880), 1 tuần (10080) trước; all day — vào ngày lúc 8:00 (0), 1 ngày trước
(1440), 2 ngày trước (2880), 1 tuần trước (10080). The notification: title = event title; text =
"Hôm nay · 09:00 – 10:00" / "Ngày mai · Cả ngày" / "Thứ Năm 26/11 · Cả ngày", plus " · 10/10 âm"
for lunar repeats; tapping opens the app on that day.

- **Android**: one exact alarm (`setExactAndAllowWhileIdle` when exact alarms are allowed, else
  `setAndAllowWhileIdle`) for the earliest reminder still to come; when it fires, post every
  reminder due in the last 15 minutes not yet posted, then schedule the next. Rescheduled when
  events change, at app start, after boot, time and time zone changes. Notification channel
  "Nhắc sự kiện". When the app may not post notifications (2026-11-24, a popup instead of a line
  of text): opening the editor with reminders, or adding one, shows the system's POST_NOTIFICATIONS
  request (Android 13+); when the system no longer asks (denied twice: the answer comes back within
  400 ms) or notifications are off in Settings, a dialog "Bật thông báo để nhận nhắc" offers "Mở
  cài đặt" (the app's notification settings) or "Để sau". Lưu asks the system again, no dialog.
- **Web**: only while the page or the installed PWA is open (no push server): a timer for the
  next reminder shows it through the service worker's `showNotification`. Permission is asked when
  the editor opens with reminders, when one is added and on save; a browser never asks again once
  blocked, so then a line under the reminders says so. The form says "Trên web, nhắc chỉ hiện khi
  trang đang mở".

## Reminders for built-in days

Built-in days (solar and lunar holidays, observances, festivals, mồng 1, rằm) can get reminders
too (decided with the user 2026-11-24), with the same repeat, end and reminder choices as the
user's events:

- **Ref**: a built-in day is known by its title, except every month's mồng 1 and rằm, which are
  "Mồng 1" and "Ngày rằm" (their titles name the month).
- **Stored** as events with a `ref` field under their own key `builtinReminders` (web:
  localStorage; Android: SharedPreferences `licham`), at most one per ref, so that versions
  without this feature never list them. They follow every rule of this document (occurrences,
  reminders, notification text); they are never listed, exported or shown on the month grid.
- **The form** ("Nhắc ngày này", the event editor's reminder mode): the day's title and date,
  read only; Lặp lại with Hằng tháng (mồng 1 / rằm only, their default), Hằng năm (the others'
  default) and Không lặp; the end; the reminders (all-day choices, "vào ngày, lúc 8:00" at first).
  Lưu saves it: `title` = the ref for a monthly reminder, else the day's title; `calendar` = the
  day's own (lunar or solar); `lunarDay` / `lunarMonth` from the tapped day; `start` = the tapped
  day (an edit keeps an earlier start); `allDay`, gold, `isLeapMonth` false. With a reminder kept,
  the footer's left button is "Tắt nhắc", which deletes it.
- **Bell**: a built-in day's row shows the bell when its ref has a reminder with an occurrence on
  that day.

## Screens (both apps)

- **Event editor** (sheet "Sự kiện mới" / "Sửa sự kiện"): tiêu đề; "Cả ngày" switch; ngày bắt đầu
  (selects day / month / year, with a Dương / Âm switch to enter it as a lunar date, leap checkbox
  when that lunar year has the month's leap copy; the other calendar's date shown under it); giờ
  bắt đầu / kết thúc (hour and minute in 5-minute steps) when not all day; kéo dài (1-30 ngày);
  lặp lại (Không lặp / Hằng ngày / Hằng tuần / Hằng tháng / Hằng năm); weekly: weekday chips
  T2 … CN; monthly / yearly: Âm lịch / Dương lịch switch and the rule in words ("Ngày 10 âm hằng
  tháng", "Ngày 10/3 âm hằng năm"); kết thúc (Không bao giờ / Đến ngày / Sau … lần) when repeating;
  nhắc (chips with ×, "+ Thêm nhắc" select); địa điểm; ghi chú; màu (5 swatches). Footer: Lưu.
- **Event detail** (sheet): colour bar, title, the occurrence's day ("Thứ Tư, 25/11/2026 · 17/10
  âm"), time ("Cả ngày" / "09:00 – 10:00", "đến Thứ Năm 26/11" when several days), repeat in words
  with its end, reminders, location, notes. Buttons Sửa and Xoá. On a repeating event both ask
  "Chỉ lần này" or "Tất cả các lần": this one only = add the day to `exceptions` (delete), or add
  it to `exceptions` and save the edited copy as a new `repeat: "none"` event on that day (edit);
  all = change or delete the series.
- **Day lists**: the Calendar's selected-day panel and the Today page get a card "Sự kiện" with
  the day's occurrences (colour dot, title, "Cả ngày" or "09:00 – 10:00") — tap opens the detail —
  and "+ Thêm" opening the editor on that day.
- **Sự kiện tab**: occurrences in the list (first on their day), tile in the event's colour, the
  time in the subtitle, then the kind "· Cá nhân" (2026-11-24, was "Của bạn": the user's own
  events, which can be edited and deleted), and a small gold bell on any event whose occurrence
  has a reminder, built-in days included; tap opens the detail. Tapping a
  built-in day opens its reminder form (next section). Header button "Của bạn (n)" / "Thêm sự
  kiện" opens the sheet "Sự kiện của bạn": all events (title, rule in words), tap → detail,
  "+ Thêm" → editor.
- **Month grid**: the dot beside the solar day takes the colour of the day's first event (gold for
  a note alone); large cells show the event's name when the day has no holiday. Legend
  "Ghi chú, sự kiện".
- **Tear-off sheet and widgets**: the day's name line is holiday, else the first event of the day,
  else observance, else mồng 1 / rằm (as now).
- **Export (.ics)**: one VEVENT with RRULE (FREQ, BYDAY, UNTIL / COUNT, EXDATE) for none, daily,
  weekly, solar monthly with day ≤ 28 and solar yearly except 29/2; one VEVENT per occurrence in
  the chosen years for the rest (lunar repeats, solar day > 28). Timed events use floating local
  DTSTART / DTEND; reminders become VALARM DISPLAY; notes → DESCRIPTION, location → LOCATION.
