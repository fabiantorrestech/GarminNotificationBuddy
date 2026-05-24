# Garmin Notification Buddy Connect IQ Scaffold

This directory contains a starter Connect IQ companion app scaffold that matches the Android app's `WATCH_APP_ID`:

- `9d736064-2f24-4a51-9c8a-24db4a9ed9d6`

Current scope:
- receives phone payloads over Connect IQ app messaging
- stores a small in-watch inbox buffer
- renders a basic recent-items list on-device

Still required before shipping on real hardware:
- compile with the Connect IQ SDK tooling
- validate UI fit on the Forerunner 55
- add explicit list/detail navigation and vibration handling if the device APIs differ from the current scaffold assumptions
