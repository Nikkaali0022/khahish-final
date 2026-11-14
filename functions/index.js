const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();
const REGION = "us-central1";
const MAX_MULTICAST = 500;
const MAX_BATCH_WRITES = 450;

async function listAllAuthUsers() {
  const users = [];
  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    users.push(...page.users.map((user) => user.uid));
    pageToken = page.pageToken;
  } while (pageToken);
  return users;
}

async function loadRecipients(targetType, targetUid) {
  const uids = targetType === "user"
    ? [targetUid]
    : await listAllAuthUsers();

  const recipientMap = new Map(uids.map((uid) => [uid, []]));
  const tokenSnapshots = targetType === "user"
    ? await db.collection("users").doc(targetUid).collection("fcmTokens").get()
    : await db.collectionGroup("fcmTokens").get();

  for (const tokenDoc of tokenSnapshots.docs) {
    const uid = tokenDoc.ref.parent.parent && tokenDoc.ref.parent.parent.id;
    const token = tokenDoc.get("token");
    if (uid && token && recipientMap.has(uid)) {
      recipientMap.get(uid).push({ token, ref: tokenDoc.ref });
    }
  }
  return recipientMap;
}

async function writeInboxNotifications(recipients, broadcastId, title, body, kind) {
  const entries = [...recipients.keys()];
  for (let offset = 0; offset < entries.length; offset += MAX_BATCH_WRITES) {
    const batch = db.batch();
    for (const uid of entries.slice(offset, offset + MAX_BATCH_WRITES)) {
      const ref = db.collection("users").doc(uid).collection("notifications").doc(broadcastId);
      batch.set(ref, {
        id: broadcastId,
        title,
        body,
        kind,
        createdAt: Date.now(),
        read: false,
        source: "admin"
      });
    }
    await batch.commit();
  }
}

async function sendPushNotifications(recipients, broadcastId, title, body) {
  const tokenEntries = [];
  for (const [uid, tokens] of recipients.entries()) {
    for (const item of tokens) tokenEntries.push({ uid, ...item });
  }

  let sent = 0;
  let failed = 0;
  const invalidRefs = [];

  for (let offset = 0; offset < tokenEntries.length; offset += MAX_MULTICAST) {
    const chunk = tokenEntries.slice(offset, offset + MAX_MULTICAST);
    const response = await messaging.sendEachForMulticast({
      tokens: chunk.map((item) => item.token),
      notification: { title, body },
      data: {
        type: "admin",
        notificationId: broadcastId,
        title,
        body
      },
      android: {
        priority: "high",
        notification: { channelId: "khahish_messages" }
      }
    });

    sent += response.successCount;
    failed += response.failureCount;
    response.responses.forEach((result, index) => {
      if (!result.success && result.error &&
          ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]
            .includes(result.error.code)) {
        invalidRefs.push(chunk[index].ref);
      }
    });
  }

  for (const ref of invalidRefs) {
    await ref.delete().catch(() => {});
  }

  return { sent, failed, invalidTokensRemoved: invalidRefs.length };
}

exports.dispatchAdminBroadcast = onDocumentCreated(
  {
    document: "adminBroadcasts/{broadcastId}",
    region: REGION,
    retry: true,
    maxInstances: 5
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const ref = snapshot.ref;
    const now = Date.now();
    const claimed = await db.runTransaction(async (tx) => {
      const current = await tx.get(ref);
      if (!current.exists) return false;
      const state = current.get("dispatchStatus");
      const startedAt = Number(current.get("dispatchStartedAt") || 0);
      if (state === "sent" || (state === "processing" && now - startedAt < 5 * 60 * 1000)) {
        return false;
      }
      tx.update(ref, {
        dispatchStatus: "processing",
        dispatchStartedAt: now,
        dispatchEventId: event.id
      });
      return true;
    });

    if (!claimed) return;

    try {
      const data = (await ref.get()).data() || {};
      const title = String(data.title || "").trim().slice(0, 100);
      const body = String(data.body || "").trim().slice(0, 2000);
      const targetType = data.targetType === "user" ? "user" : "all";
      const targetUid = targetType === "user" ? String(data.targetUid || "") : "";

      if (!title || !body || (targetType === "user" && !targetUid)) {
        throw new Error("Broadcast title, body, or target is invalid.");
      }

      const recipients = await loadRecipients(targetType, targetUid);
      await writeInboxNotifications(recipients, ref.id, title, body, data.kind || "announcement");
      const push = await sendPushNotifications(recipients, ref.id, title, body);

      await ref.update({
        dispatchStatus: "sent",
        recipientCount: recipients.size,
        pushSent: push.sent,
        pushFailed: push.failed,
        invalidTokensRemoved: push.invalidTokensRemoved,
        dispatchCompletedAt: Date.now()
      });

      logger.info("Khahish admin broadcast delivered", {
        broadcastId: ref.id,
        targetType,
        recipientCount: recipients.size,
        pushSent: push.sent,
        pushFailed: push.failed
      });
    } catch (error) {
      logger.error("Khahish admin broadcast failed", {
        broadcastId: ref.id,
        message: error && error.message ? error.message : String(error)
      });
      await ref.update({
        dispatchStatus: "failed",
        dispatchError: String(error && error.message ? error.message : error).slice(0, 500),
        dispatchFailedAt: Date.now()
      }).catch(() => {});
      throw error;
    }
  }
);
