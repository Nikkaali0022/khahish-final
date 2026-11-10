import fs from "node:fs";
import test, { before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  increment,
  setDoc,
  updateDoc,
  writeBatch,
  arrayUnion,
  query,
  where
} from "firebase/firestore";

const projectId = "demo-khahish-rules";
let env;

const activeStory = {
  ownerUid: "alice",
  ownerName: "Alice",
  text: "Hello",
  mediaUrl: "",
  mediaType: "text",
  createdAt: Date.now(),
  expiresAt: Date.now() + 60 * 60 * 1000,
  audience: "everyone"
};

before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { rules: fs.readFileSync("firestore.rules", "utf8") }
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

test("private user records are owner-only and login history is append-only", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "users/alice/fcmTokens/token1"), {
    token: "test-token",
    platform: "android",
    updatedAt: Date.now()
  }));
  await assertFails(getDoc(doc(db("bob"), "users/alice/fcmTokens/token1")));

  await assertSucceeds(setDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    time: Date.now(),
    deviceName: "Test device",
    platform: "Android"
  }));
  await assertFails(updateDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    deviceName: "tampered"
  }));
  await assertFails(updateDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    time: Date.now()
  }));
});

test("a username claim cannot be taken over by another account", async () => {
  await seed("usernames/alice_name", {
    uid: "alice",
    username: "alice_name",
    updatedAt: Date.now()
  });

  await assertFails(setDoc(doc(db("bob"), "usernames/alice_name"), {
    uid: "bob",
    username: "alice_name",
    updatedAt: Date.now()
  }));
  await assertSucceeds(setDoc(doc(db("alice"), "usernames/alice_name"), {
    uid: "alice",
    username: "alice_name",
    updatedAt: Date.now()
  }));
});

test("group creation requires at least three members", async () => {
  const group = {
    members: ["alice", "bob", "carol"],
    memberNames: { alice: "Alice", bob: "Bob", carol: "Carol" },
    isGroup: true,
    groupName: "Friends",
    groupAdmins: ["alice"],
    createdBy: "alice",
    createdAt: Date.now(),
    lastMessage: "Group created",
    lastMessageAt: Date.now(),
    updatedAt: Date.now(),
    disappearingDurationMs: 0
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/group-three"), group));

  await assertFails(setDoc(doc(db("alice"), "chats/group-two"), {
    ...group,
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" }
  }));
});

test("private chat is created only in the same batch that accepts its request", async () => {
  await seed("messageRequests/alice_bob", {
    senderUid: "alice",
    receiverUid: "bob",
    senderName: "Alice",
    receiverName: "Bob",
    status: "pending",
    createdAt: Date.now()
  });

  const receiverDb = db("bob");
  const batch = writeBatch(receiverDb);
  const now = Date.now();
  batch.update(doc(receiverDb, "messageRequests/alice_bob"), {
    status: "accepted",
    acceptedBy: "bob",
    handledBy: "bob",
    acceptedAt: now
  });
  batch.set(doc(receiverDb, "chats/alice_bob"), {
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" },
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: now,
    createdAt: now,
    createdBy: "bob",
    disappearingDurationMs: 0
  });
  await assertSucceeds(batch.commit());

  await assertFails(setDoc(doc(db("alice"), "chats/alice_carol"), {
    members: ["alice", "carol"],
    memberNames: { alice: "Alice", carol: "Carol" },
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: now,
    createdAt: now,
    createdBy: "alice",
    disappearingDurationMs: 0
  }));
});

test("messages cannot be created while impersonating another chat member", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });

  const validMessage = {
    senderId: "alice",
    text: "Hello",
    imageUrl: "",
    mediaType: "text",
    timestamp: Date.now(),
    deliveredTo: [],
    readBy: ["alice"]
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/alice_bob/messages/message1"), validMessage));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/message2"), {
    ...validMessage,
    senderId: "bob"
  }));
});

test("story audience and expiry are enforced", async () => {
  await seed("stories/active-public", activeStory);
  await seed("stories/expired-public", {
    ...activeStory,
    expiresAt: Date.now() - 60_000
  });
  await seed("stories/private", {
    ...activeStory,
    audience: "only_me"
  });

  await assertSucceeds(getDoc(doc(db("bob"), "stories/active-public")));
  await assertFails(getDoc(doc(db("bob"), "stories/expired-public")));
  await assertFails(getDoc(doc(db("bob"), "stories/private")));
  await assertSucceeds(getDoc(doc(db("alice"), "stories/private")));
});

test("live location writes are limited to the matching chat member UID", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });
  const point = {
    uid: "alice",
    latitude: 33.7,
    longitude: 73.0,
    accuracy: 10,
    updatedAt: Date.now(),
    startedAt: Date.now(),
    expiresAt: Date.now() + 60_000,
    isActive: true
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/alice_bob/liveLocations/alice"), point));
  await assertFails(setDoc(doc(db("bob"), "chats/alice_bob/liveLocations/alice"), {
    ...point,
    uid: "alice"
  }));
});

test("community membership can only add or remove the signed-in member", async () => {
  await seed("communities/community1", {
    name: "Test",
    description: "",
    ownerUid: "alice",
    members: ["alice"],
    memberCount: 1,
    createdAt: Date.now()
  });

  await assertSucceeds(updateDoc(doc(db("bob"), "communities/community1"), {
    members: arrayUnion("bob"),
    memberCount: increment(1)
  }));
  await assertFails(updateDoc(doc(db("bob"), "communities/community1"), {
    members: arrayUnion("carol"),
    memberCount: increment(1)
  }));
});

test("deactivation requests are allowed but deletion requests are denied", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "accountRequests/alice"), {
    uid: "alice",
    type: "deactivate",
    status: "pending",
    createdAt: Date.now(),
    email: "alice@example.test",
    note: "Requested in test"
  }));
  await assertFails(setDoc(doc(db("bob"), "accountRequests/bob"), {
    uid: "bob",
    type: "delete",
    status: "pending",
    createdAt: Date.now()
  }));
});

test("call records are visible only to participants and support incoming-call queries", async () => {
  await seed("calls/call1", {
    callerUid: "alice",
    calleeUid: "bob",
    callerName: "Alice",
    calleeName: "Bob",
    participants: ["alice", "bob"],
    channelName: "khahish_test",
    callType: "voice",
    status: "ringing",
    createdAt: Date.now(),
    updatedAt: Date.now()
  });

  await assertSucceeds(getDoc(doc(db("bob"), "calls/call1")));
  await assertFails(getDoc(doc(db("carol"), "calls/call1")));
  await assertSucceeds(getDocs(query(
    collection(db("bob"), "calls"),
    where("calleeUid", "==", "bob")
  )));
  await assertSucceeds(getDocs(query(
    collection(db("alice"), "calls"),
    where("participants", "array-contains", "alice")
  )));
  await assertSucceeds(updateDoc(doc(db("bob"), "calls/call1"), {
    status: "accepted",
    updatedAt: Date.now()
  }));
  await assertFails(updateDoc(doc(db("carol"), "calls/call1"), {
    status: "ended",
    updatedAt: Date.now()
  }));
});

test("caller timeout and incoming-call back navigation can close ringing calls", async () => {
  const base = {
    callerUid: "alice",
    calleeUid: "bob",
    callerName: "Alice",
    calleeName: "Bob",
    participants: ["alice", "bob"],
    channelName: "khahish_timeout",
    callType: "voice",
    status: "ringing",
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
  await seed("calls/caller-timeout", base);
  await seed("calls/callee-back", { ...base, channelName: "khahish_back" });

  await assertSucceeds(updateDoc(doc(db("alice"), "calls/caller-timeout"), {
    status: "missed",
    updatedAt: Date.now()
  }));
  await assertSucceeds(updateDoc(doc(db("alice"), "calls/caller-timeout"), {
    status: "ended",
    updatedAt: Date.now()
  }));
  await assertSucceeds(updateDoc(doc(db("bob"), "calls/callee-back"), {
    status: "ended",
    updatedAt: Date.now()
  }));
});

test("backup imports preserve historical sender labels but are explicitly unverified", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });

  const restoredAt = Date.now();
  const imported = {
    senderId: "bob",
    text: "Historical message from backup",
    imageUrl: "",
    mediaType: "text",
    timestamp: restoredAt - 60_000,
    deliveredTo: ["alice"],
    readBy: ["bob", "alice"],
    restoredFromBackup: true,
    restoredBy: "alice",
    restoredAt
  };
  const importedRef = doc(db("alice"), "chats/alice_bob/messages/backup_alice_old1");
  await assertSucceeds(setDoc(importedRef, imported));
  await assertSucceeds(updateDoc(importedRef, {
    text: "Re-imported historical message",
    restoredAt: Date.now()
  }));
  await assertFails(updateDoc(importedRef, {
    text: "Another member must not edit Alice's imported record"
  }).then(() => { throw new Error("Expected the other account update to fail."); }));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/backup_alice_forged"), {
    ...imported,
    restoredBy: "bob"
  }));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/backup_alice_future"), {
    ...imported,
    timestamp: restoredAt + 60_000
  }));
});
