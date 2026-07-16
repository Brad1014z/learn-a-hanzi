# Firebase setup — the one-time console checklist (M4, spec 12)

The app builds and runs **without any of this** — the Family screen runs in clearly
labeled preview mode on offline fakes. Do these steps once to light up the real cloud
layer (all on the **free Spark plan**; no billing needed until M4.5's Cloud Functions).

## 1. Create the Firebase project
1. [console.firebase.google.com](https://console.firebase.google.com) → **Add project**
   → name it (e.g. `learn-a-hanzi`) → disable Google Analytics (constitution: no
   trackers) → Create.

## 2. Register the Android app
1. Project overview → **Add app → Android**.
2. Package name: `io.github.brad1014z.hanzi`
3. Add the **debug signing SHA-1** (required for Google sign-in):
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | grep SHA1
   ```
   (Add each family dev machine's debug SHA-1, and later the CI/release one.)
4. Download **google-services.json** → put it at `app/google-services.json`.
   It is gitignored on purpose — every developer machine drops in its own copy.

## 3. Enable Google sign-in
1. Build → **Authentication → Get started → Sign-in method → Google → Enable**
   (pick your support email) → Save.

## 4. Create the Firestore database
1. Build → **Firestore Database → Create database** → production mode → nearest region.
2. **Rules** tab → paste the contents of [`firestore.rules`](./firestore.rules) → Publish.

## 5. Rebuild and verify
```bash
./gradlew :app:assembleDebug   # plugin activates automatically when the json exists
```
Install on two phones, sign in on both, share the friend code from one, enter it on
the other, confirm — finish a quest each and watch the weekly board.

## Troubleshooting
- **Sign-in sheet never appears / error 10:** the SHA-1 in the console doesn't match
  the keystore that signed the APK. Add the right one, re-download the json.
- **"Preview mode" banner still showing:** `app/google-services.json` missing at build
  time — the conditional plugin only activates when the file exists.
- **PERMISSION_DENIED in logs:** rules not published, or reading a profile with no
  friend edge (expected — that's the rules working).
