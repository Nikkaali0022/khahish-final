# Khahish Android

Khahish is an Android-first messaging app built with Kotlin, Jetpack Compose, Firebase Authentication, Cloud Firestore, Cloudinary media storage and Agora calling.

## Current feature work on `feature/agora-calling`

- Private chats, message receipts, replies, reactions, forwarding and disappearing messages
- Editable unique usernames, exact-username discovery, and receiver-controlled first-contact message requests
- Latest-message positioning, jump-to-latest button and swipe navigation between main tabs
- Group chats (3+ members), participant management and admin roles
- Message editing (sender's text messages within 15 minutes), starring and pinning
- Paginated in-chat search across full conversation history
- One-time location sharing, timed foreground live-location sharing, and contact-card sharing
- FCM device-token registration and Android notification receiving; authenticated push dispatch is integrated in the app but requires the Cloudflare Worker deployment and Firebase service-account secret
- Profile reporting and blocking; private-message sending checks the sender's blocked list
- Story audience selection, viewer records and viewer list
- Same-account JSON chat backup and restore
- Trusted-device records, remote session sign-out when a device is removed, and login history
- One-handed mode, reduced-motion controls, privacy dashboard, storage breakdown, data export and chat backup/restore
- Light, Dark and System theme selection (screen-by-screen dark-theme coverage is still being completed)
- Firestore security rules in `firestore.rules`

## Build and release policy

GitHub Actions validates the Android build and Firestore Rules emulator tests on the feature branch. The final APK is to be prepared only after the agreed implementation, backend configuration and real-device verification gates are complete. Do not install or distribute intermediate APK artifacts as final releases.

## Firestore rules deployment — required user-side Firebase action

The rules in `firestore.rules` are source files only; adding them to GitHub does **not** publish them to Firebase. The emulator security suite passing also does not mean production rules have been deployed.

Before relying on production security enforcement:
1. Open Firebase Console and select project **khahish-final**.
2. Open **Firestore Database → Rules**.
3. Review the repository's `firestore.rules` file and publish the reviewed version.
4. Verify sign-in, private chats, group creation/admin actions, stories, blocked messaging, calls and backup/restore against production.

A connected Firebase management integration may simplify this step, but it is not currently connected/available to this repository workflow. Never share service-account keys or private credentials in chat or commit them to GitHub.

## Server/platform work still required

- Deploy and configure the authenticated FCM sender in the Cloudflare Worker; store Firebase service-account JSON only as an encrypted Cloudflare secret and test message/incoming-call push delivery on two real Android devices.
- Complete server-side account-deactivation enforcement and notification routing.
- Complete full app-wide Urdu/Roman Urdu localization.
- Complete secure linked-device QR pairing and per-device key provisioning.
- Select an independently reviewed E2EE protocol/library compatible with the current minSdk 24 and licensing constraints; implement one-to-one encryption, encrypted media, local search, legacy-history migration and safe backup/restore. **E2EE is not active:** current message text, media URLs and chat previews are not end-to-end encrypted.
- Finish app-wide Dark Mode styling and verify all screens, dialogs, menus, calls, updates, media viewers and composer.
- Complete real-device QA for calling, notifications, media, permissions, search, groups, backup/restore, privacy and key lifecycle.

## Explicit product exclusions

Communities, Two-Step Verification, account deletion, Guest Mode, multiple-account switching, automatic media saving to device storage and TalkBack/Screen Reader features are excluded by the current product decisions. Do not reintroduce them unless the product owner changes those decisions.

## Important data and security notes

- Firestore Rules emulator tests are automated checks of the repository rules; production Firebase Rules deployment is a separate operation.
- One-time location sharing sends the device's last known location. Timed live-location sharing uses an Android foreground service and writes updates under `chats/{chatId}/liveLocations/{uid}`; publish and verify matching participant-only Firestore rules before relying on it.
- Chat backup restores JSON message records and media URLs, not separate copies of Cloudinary media files.
- Trusted-device records and remote sign-out are not equivalent to cryptographic linked-device pairing.
- `KhahishKeyVault.kt` is local encrypted key-storage groundwork only; it does not make message content E2EE.

## Khahish Owner Admin claim provisioning

The hidden Admin Panel and the admin-only Firestore Rules require a Firebase
Authentication ID token containing `admin: true`. The repository does not
store an admin password, service-account key, or other privileged credential.

The one-time provisioning script is deliberately restricted to the reviewed
Owner identity:
- Email: `pakistanivlogger57@gmail.com`
- Firebase Auth UID: `CWzzbORWciaU2zb3R375nNckWbi2`

Run it only from a trusted computer or Google Cloud Shell after granting the
signed-in operator/service account permission to manage Firebase Authentication
users in the **Khahish Firebase project**:

1. Install the repository's Node dependencies with `npm install`.
2. Authenticate with Application Default Credentials that have Firebase
   Authentication Admin permission. For a service-account key, keep the JSON
   file outside the repository and set `GOOGLE_APPLICATION_CREDENTIALS` to
   its local path. Never upload it to GitHub or share it in chat.
3. The app's checked-in Firebase configuration identifies the project ID as
   `khahish-final`. The script uses this as its default; set
   `FIREBASE_PROJECT_ID` explicitly only if your Firebase Console shows a
   different project ID.
4. Run `npm run grant:owner-admin`.
5. Confirm the script prints its verified success message. The script checks
   that the email and UID resolve to the same Firebase Auth user before it
   changes claims, preserves existing custom claims, and verifies the result.

After provisioning, the Owner must sign out and sign back in (or refresh the
Firebase ID token) before the Android Admin Panel sees the new claim.
This script only provisions the Owner claim; it does not deploy Firestore
Rules or Cloud Functions.

## Live Owner Admin Panel deployment

The Admin Panel now uses the following protected Firestore collections:
- `accountRestrictions/{uid}` for temporary/permanent bans. Active restrictions block normal account Firestore access; the Owner custom claim bypasses restrictions.
- `adminBroadcasts/{id}` as the Owner's announcement history.
- `adminAuditLogs/{id}` as an append-only record of Owner actions.
- `appConfig/global` for global app configuration.
- `users/{uid}/notifications/{id}` as each user's private in-app notification inbox.

### Spark (free-plan) notification delivery

The Android Owner Admin Panel can deliver announcements directly to user inboxes using authenticated Firestore batch writes. The Owner can target all public profiles or one verified UID. Notifications appear in the app's Notifications inbox when a user opens Khahish; users can mark individual notifications or all notifications as read. Each broadcast uses a stable notification document ID so a retry overwrites the same inbox entry instead of creating duplicates.

This Spark-compatible path does **not** send background FCM push alerts. Do not deploy `functions/index.js` on the Spark plan. True server-triggered background push delivery through the Firebase Cloud Function is an optional Blaze-plan feature and can incur charges. No billing upgrade is required for the in-app inbox.

After merging a rules change, deploy only Firestore Rules with:

```bash
firebase deploy --only firestore:rules --project khahish-final
```

The Owner should sign out and sign back in to Khahish after any custom-claim change so the refreshed ID token contains `admin: true`. Never paste service-account keys into ChatGPT, GitHub source files, or the Android app.

The group moderation section intentionally shows group metadata only; it does not expose private message contents or delete conversations. The Android client reads `appConfig/global` and blocks non-Owner app access while maintenance mode is enabled. The active Owner custom claim bypasses maintenance mode.
