# Progress and next steps

_Status as of 7 Nov 2026_

## Done
- Android app (Kotlin + Jetpack Compose) with Dashboard, Shops, Shop detail and Broadcasts screens.
  It builds with `./gradlew assembleDebug`.
- Firebase sign-in and Firestore data, with security rules so each salesman only sees his own
  shops (`firebase/firestore.rules`).
- Admin tool `tools/luna_admin.py` to import the DAILY TRACKING Excel, manage accounts and send
  broadcasts. A dry run on "DAILY TRACKING 12 OCT.xlsx" matched the sheet's own totals:
  1,099 shops, ST 2,717, SO 3,456, stock 18,508.
- Merged into `main` via PR #1.

## Waiting on Alain
1. **Firebase project** (see README, "One-time setup"):
   - Create the project and enable Email/Password sign-in and Firestore.
   - Add the Android app `com.gomobile.luna` and save its `google-services.json` to `app/`.
   - Generate a service-account key and save it as `tools/service-account.json`.
   - Never commit or share either file.
2. **Team list:** email, and exact name as in the tracking file, for each person. Also say which
   Regional Managers need accounts, and who besides Alain should be an admin.
   - Area Managers in the tracking file: Ali Abdelrazzak, Ali Sahili, Alphonse El Khoury,
     Charbel Feghali, Christopher Abi Fadel, Fatmeh Gharib, Khawla Alaa Dine, Mohammad Chami,
     Omar Naaman, Remah Al Anaz, Rim Nassar, Rony Shehadeh.
   - Regional Managers: Hassan Moukadem, Ziad El Jawhary, Tony Abi Aad, Mohammad Chami.
3. **Targets:** what are `OCT FINAL TGT` and `OCT FINAL STO` in "OCTOBER ST AND SO TARGET PER
   SHOP.xlsx"? Their sums don't match the ST/SO targets on the Dashboard sheet, so for now the
   importer only loads targets from columns named like `ST TARGET` / `SO TARGET`, or ones named
   with `--st-target-col` / `--so-target-col`.
4. **Aging:** keep "days without sell-out" (3/6/10 days) only, or also add stock age per model
   or per IMEI? If stock age is wanted, it needs that sheet.
5. **Optional:** push notifications for broadcasts? APK or Google Play distribution?

## Known data quirks
- Name spelling differs between files (`ALI SAHILI` in tracking vs `ALI SAHLI` in targets).
  Accounts must use the tracking-file spelling.
- Source files are in Dropbox:
  - `/DAILY TRACKING/<MONTH> 2026/DAILY TRACKING <day> <MON>.xlsx`
  - `/WORK/HONOR/2026/TARGET/<MONTH>/`
