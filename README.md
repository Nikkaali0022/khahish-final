# Khahish Android

Khahish is an Android-first messaging app built with Kotlin, Jetpack Compose, Firebase Authentication, Cloud Firestore, Cloudinary media storage and Agora calling.

## Current feature work on `feature/agora-calling`

- Private chats, message receipts, replies, reactions, forwarding and disappearing messages
- Latest-message positioning, jump-to-latest button and swipe navigation between main tabs
- Group chat creation, group participant management and admin roles
- Communities with membership, posts and post reporting
- Message editing (sender's text messages within 15 minutes), starring and pinning
- Recent message search across the latest 100 messages per conversation
- One-time location sharing and contact-card sharing
- Profile reporting and blocking; private-message sending checks the sender's blocked list
- Story audience selection, viewer records and viewer list
- Same-account JSON chat backup and restore
- Trusted-device records, remote session sign-out when a device is removed, and login history
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

- End-to-end encryption with a reviewed key-exchange and recovery design
- Firebase Auth multi-factor / two-step verification setup
- Linked-device pairing and a web/desktop client (trusted-device revocation is separate)
- Push notifications for background messages and incoming calls, including server-side FCM delivery
- Server-side account deactivation and scheduled account deletion workers
- Full app-wide Urdu/Roman Urdu localization and a complete accessibility audit
- Production Firebase rules deployment, emulator/security testing and real-device QA

Location sharing currently sends the device's last known location; it is not continuous live-location tracking. Chat backup restores JSON message records and media URLs, not separate copies of Cloudinary media files.
