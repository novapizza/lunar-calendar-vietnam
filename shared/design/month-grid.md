# Month grids: the small date under each day

Every month grid of both apps (web: Calendar page, event date picker, birth date picker, quick
date picker; Android: the same four and the Lịch tháng widget) writes the other calendar's date
small under the day (decided with the user 2026-11-24):

- the day alone ("16"), or "day/month" ("16/10") on the first of a month and on the selected day
  (in the widget, which has no selection: today);
- after a leap lunar month, a small raised "n" at the top right of the month ("1/6ⁿ"), about 0.7
  of the size;
- colours and weights as before (mồng 1 and rằm in the lunar colour, bold).

One implementation per platform: Android `core/SmallDate` (the rule, tested) with
`ui/SmallDateText` (Compose) and the widget's own two-Text version (Glance has no spans); web
`components/SmallDate.tsx`. In a widget cell too narrow for "16/10" and its dots, that date gets
smaller type.
