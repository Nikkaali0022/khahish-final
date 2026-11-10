# Khahish Android

Khahish is an Android-first messaging app built with Kotlin, Jetpack Compose, Firebase Authentication, Cloud Firestore, Cloudinary media storage and Agora calling.

## Current feature work on `feature/agora-calling`

- Private chats, message receipts, replies, reactions, forwarding and disappearing messages
- Unique editable usernames, exact-username discovery, and receiver-controlled first-contact message requests
- Latest-message positioning, jump-to-latest button and swipe navigation between main tabs
- Group chat creation, group participant management and admin roles
- Communities with membership, posts and post reporting
- Message editing (sender's text messages within 15 minutes), starring and pinning
- Full in-chat search across the conversation messages currently loaded by the app
- One-time location sharing, timed foreground live-location sharing, and contact-card sharing
- FCM device-token registration and Android notification receiving (trusted push sender/backend still required)
- Profile reporting and blocking; private-message sending checks the sender's blocked list
- Story audience selection, viewer records and viewer list
- Same-account JSON chat backup and restore
- Trusted-device records, remote session sign-out when a device is removed, and login history
- One-handed mode, reduced-motion controls, privacy dashboard, storage breakdown, data export and chat backup/restore
- Firestore security rules in `firestore.rules`

## Build APK

GitHub Actions builds the APK when changes are pushed to `feature/agora-calling` or `main`.

1. Open the repository's **Actions** tab.
2. Open the latest **Build Khahish APK** run.
3. Wait for a green successful result.
4. Download the `Khahish-APK` artifact.

Do not merge the pull request until the APK has been installed and tested on real devices.

## Firestore rules deployment — required

The rules in `firestore.rules` are source files only; adding them to GitHub does **not** publish them to Firebase.

1. Open Firebase Console and select project **khahish-final**.
2. Open **Firestore Database → Rules**.
3. Review the repository's `firestore.rules` file.
4. Paste the rules into the Firebase Rules editor and publish them.
5. Test sign-in, private chats, group creation/admin actions, stories, communities, blocked messaging, calls and backup/restore after publishing.

Story audience privacy and server-side block enforcement depend on the matching Firestore rules being published. Do not treat these controls as security-enforced until that deployment is complete.

## Known server/platform work still required

- End-to-end encryption with reviewed device-key provisioning, key exchange and recovery; existing plaintext history must remain readable
- Two-step verification: Firebase's native MFA requires Identity Platform; a Spark-compatible trusted-backend alternative must be designed without pretending a local app lock is account MFA
- Linked-device pairing requires a separate web/desktop client plus secure QR-based device provisioning
- FCM push sender for messages and incoming calls; the Android receiver and token registration are now in the app, but the trusted sender/backend is not
- Server-side account deactivation workflow
- Full app-wide Urdu/Roman Urdu localization and a complete TalkBack/accessibility audit
- Firestore rules review/deployment, emulator/security testing and real-device QA

Account deletion is intentionally excluded from Khahish.

One-time location sharing sends the device's last known location. Timed live-location sharing now uses an Android foreground service and writes updates under `chats/{chatId}/liveLocations/{uid}`; the matching participant-only Firestore rules must be reviewed and published before relying on it. Chat backup restores JSON message records and media URLs, not separate copies of Cloudinary media files.
