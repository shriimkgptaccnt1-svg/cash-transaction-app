# Cash

A small Android app for writing down household cash expenses, with a monthly dashboard.

## Install

Download **[releases/Cash.apk](releases/Cash.apk)** on your phone and open it. Android will ask you to allow installs from your browser or file manager the first time.

Needs Android 8.0 or newer.

## Using it

- **The sheet** has three columns: Date, Description, Amount. When the app opens, the cursor is already in the empty last row, today's date is filled in and the keyboard is up. The entry row always sits directly above the keyboard.
- **Description autofill:** type `f` and it fills in **Food**, type `l` and it fills in **Laundry**. Press *Next* to keep the suggestion. Keep typing something else and the suggestion disappears.
- **Amount:** *Next* moves to the amount. Press *Done* on the keyboard to save the row. A fresh row opens with today's date.
- **Date:** tap the date in the entry row to pick a different day (or *Yesterday*/*Today*).
- **Delete:** long-press a row.
- **Dashboard:** swipe left. It shows the month's total, the comparison with last month, Food / Laundry / Other totals, and a bar chart of spending per day (tap a bar to see that day's amount). Swipe right or press Back to return.

Data is stored only on the phone. Uninstalling the app deletes it.

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleRelease
```

Release signing reads `keystore.properties` in the project root (not committed):

```
storeFile=/path/to/cashbook-release.jks
storePassword=...
keyAlias=cashbook
keyPassword=...
```

Keep the keystore safe: updates to the installed app must be signed with the same key.
