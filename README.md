# LUNA: GoMobile Sales app

This Android app shows each salesman the figures for their own shops: ST, SO, stock, aging,
achievement against target, and daily sell-out. It also shows broadcasts that the admin sends
to the whole team.

- **Area Managers** see only the shops where they are the Area Manager.
- **Regional Managers** see every shop under them, with a breakdown per Area Manager.
- **Admins** see all shops and can post or delete broadcasts from the app.

The data comes from your usual Excel files: the **DAILY TRACKING** workbook (`Mastersheet`
sheet), plus an optional **ST AND SO TARGET PER SHOP** workbook. You upload them with one
command. Firebase handles sign-in and stores the data.

```
Excel (Dropbox / email) ──► tools/luna_admin.py import ──► Firebase ──► app on each phone
```

## What the app shows

| Screen | Contents |
|---|---|
| Dashboard | ST and SO vs target with achievement %, stock, convert rate (SO ÷ ST), zero/low-stock and 3/6/10-day no-sell-out counts, a daily SO chart, and (for regional managers and admins) one row per Area Manager |
| Shops | Search by shop, code, manager or region. Filter by no SO, zero stock or low stock. Sort by SO, ST, stock, aging, lowest SO %, or name |
| Shop detail | All figures for one shop, plus stock cover (days of stock at the current average SO) and its daily SO chart |
| Broadcasts | Messages from the admin, with an unread badge on the tab |

"Aging" comes from the `3 / 6 / 10 DAYS 0 SO` columns, meaning how long a shop has gone
without any sell-out.

## One-time setup

### 1. Create the Firebase project
1. Go to <https://console.firebase.google.com> and create a project (the free plan is enough).
2. **Build > Authentication > Get started**, then enable **Email/Password**.
3. **Build > Firestore Database > Create database**, in production mode.
4. **Project settings > General > Add app > Android**, with package name `com.gomobile.luna`.
   Download `google-services.json` and put it at `app/google-services.json`.
5. **Project settings > Service accounts > Generate new private key**. Save it as
   `tools/service-account.json`.

Both files are in `.gitignore`. Never commit them, and never share the service-account key:
it gives full access to the data.

### 2. Deploy the security rules
The rules make sure each salesman can only read their own shops.
```bash
npm install -g firebase-tools
cd firebase
firebase login
firebase use --add            # choose your project
firebase deploy --only firestore:rules
```

### 3. Install the admin tool
```bash
cd tools
python3 -m venv .venv && source .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 4. Create accounts
List the exact names from your sheet first. Each account must be linked to its name exactly
as it appears in the Area Manager / Regional Manager column:
```bash
python luna_admin.py managers --tracking "DAILY TRACKING 12 OCT.xlsx"

python luna_admin.py add-user --email you@company.com --name "ALAIN" --role admin --password "ChangeMe#1"
python luna_admin.py add-user --email omar@company.com --name "OMAR NAAMAN" --password "ChangeMe#1"
python luna_admin.py add-user --email tony@company.com --name "TONY ABI AAD" --password "ChangeMe#1"
python luna_admin.py users
```
Salesmen can change their password with **Forgot password?** on the sign-in screen.

> Names are matched after converting to upper case and trimming spaces, but spelling still
> has to match. For example, the tracking file has `ALI SAHILI` while the target file has
> `ALI SAHLI`. The tracking file is the one that counts.

### 5. Build the app
Open the project in Android Studio and press Run, or build from the command line:
```bash
./gradlew assembleDebug        # creates app/build/outputs/apk/debug/app-debug.apk
```
Send the APK to the team, or publish it via Google Play (internal testing works well for a
private team app). For Play you need a signed release build: **Build > Generate Signed Bundle**.

## Daily routine: publish new figures

```bash
cd tools && source .venv/bin/activate

# Use a specific file
python luna_admin.py import --tracking "~/Dropbox/DAILY TRACKING/OCT 2026/DAILY TRACKING 12 OCT.xlsx"

# Or pick the newest tracking file in your synced Dropbox folder
python luna_admin.py import --latest-in "~/Dropbox/DAILY TRACKING/OCT 2026"

# Check what will be uploaded without uploading anything
python luna_admin.py import --latest-in "~/Dropbox/DAILY TRACKING/OCT 2026" --dry-run

# Add targets and post a broadcast in the same run
python luna_admin.py import --latest-in "~/Dropbox/DAILY TRACKING/OCT 2026" \
  --targets "NOVEMBER ST AND SO TARGET PER SHOP.xlsx" \
  --announce "Figures updated to 12 Oct. Focus on the 10-day zero-SO shops."
```

Each import replaces the previous snapshot. Shops missing from the new file are removed, and
every phone updates within seconds.

**Target columns:** the importer finds headers like `ST TARGET`, `SO TGT` or `OCT ST TARGET`
on its own. If your target file uses other names (for example `OCT FINAL TGT`), use
`--st-target-col` / `--so-target-col` to say which column is which.

**Columns read from the Mastersheet:** Shop Code, Shop Name, Area Manager, Regional
Manager, Region, Promoter, KA Level, PSI STOCK, `<MON> ST`, `<MON> SO` (falls back to TTL
ST/SO), AVG SO, GROUP A/B ST/SO, 3/6/10 DAYS 0 SO, and the daily columns 1st, 2nd, 3rd and so
on. The header row is found automatically by its `Shop Code` cell, so the date rows above it
do no harm.

## Broadcasts
- **From the app:** sign in with an admin account, open **Broadcasts**, and tap **New broadcast**.
- **From the computer:** `python luna_admin.py broadcast --title "..." --body "..."`

## Project layout
```
app/                         Android app (Kotlin + Jetpack Compose)
  src/main/java/com/gomobile/luna/
    MainActivity.kt          navigation and bottom tabs
    AppViewModel.kt          sign-in session and live data
    data/                    models and Firebase access
    ui/screens/              Login, Dashboard, Shops, Shop detail, Broadcasts
firebase/firestore.rules     who can read what
tools/luna_admin.py          Excel import, user management, broadcasts
```

## Possible next steps
- Push notifications when a broadcast is posted (Firebase Cloud Messaging plus a small Cloud Function).
- Automatic import when a new tracking file lands in Dropbox or arrives by email.
- Per-model stock and aging (needs the model-level sheet).
