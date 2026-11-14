/**
 * One-time, trusted-environment provisioning for the Khahish Owner account.
 *
 * Run only from a trusted machine/Cloud Shell with Firebase Authentication
 * Admin privileges. Never put service-account credentials in the app or repo.
 */
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const OWNER_UID = "CWzzbORWciaU2zb3R375nNckWbi2";
const OWNER_EMAIL = "pakistanivlogger57@gmail.com";
const projectId =
  process.env.FIREBASE_PROJECT_ID ||
  process.env.GOOGLE_CLOUD_PROJECT ||
  process.env.GCLOUD_PROJECT ||
  "khahish-final";

if (!projectId) {
  throw new Error(
    "Set FIREBASE_PROJECT_ID to the Khahish Firebase project ID before running."
  );
}

initializeApp({
  credential: applicationDefault(),
  projectId,
});

const auth = getAuth();

async function main() {
  // Resolve by both identifiers so a typo or mismatched account cannot be promoted.
  const [byUid, byEmail] = await Promise.all([
    auth.getUser(OWNER_UID),
    auth.getUserByEmail(OWNER_EMAIL),
  ]);

  if (
    byUid.uid !== OWNER_UID ||
    byUid.email?.toLowerCase() !== OWNER_EMAIL ||
    byEmail.uid !== OWNER_UID
  ) {
    throw new Error(
      "Owner identity mismatch. No custom claims were changed."
    );
  }

  await auth.setCustomUserClaims(OWNER_UID, {
    ...(byUid.customClaims ?? {}),
    admin: true,
  });

  const verified = await auth.getUser(OWNER_UID);
  if (verified.customClaims?.admin !== true) {
    throw new Error("Firebase did not confirm the admin custom claim.");
  }

  console.log(
    "Verified: the configured Khahish Owner account now has admin=true."
  );
  console.log("The owner must sign out and sign in again (or refresh the ID token).");
}

main().catch((error) => {
  console.error("Owner admin provisioning failed:", error.message);
  process.exitCode = 1;
});
