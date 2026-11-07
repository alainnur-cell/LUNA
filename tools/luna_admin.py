#!/usr/bin/env python3
"""
Admin tool for the GoMobile Sales app.

Uploads the DAILY TRACKING Excel (and optionally the ST/SO target-per-shop Excel) to
Firebase, manages who can sign in, and sends broadcasts to the team.

  python luna_admin.py import --tracking "DAILY TRACKING 12 OCT.xlsx" --targets "OCTOBER ST AND SO TARGET PER SHOP.xlsx"
  python luna_admin.py import --latest-in "~/Dropbox/DAILY TRACKING/OCT 2026" --dry-run
  python luna_admin.py managers --tracking "DAILY TRACKING 12 OCT.xlsx"
  python luna_admin.py add-user --email omar@example.com --name "OMAR NAAMAN" --password "Temp#2026"
  python luna_admin.py add-user --email me@example.com --name "ALAIN" --role admin --password "..."
  python luna_admin.py broadcast --title "Push X9e" --body "Focus on X9e stock in Tripoli shops this week."

Needs a Firebase service-account key (Firebase console > Project settings > Service accounts >
Generate new private key). Save it as tools/service-account.json, or pass --key PATH, or set
GOOGLE_APPLICATION_CREDENTIALS. Never commit that file.
"""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    import openpyxl
except ImportError:
    sys.exit("Missing dependency: run  pip install -r tools/requirements.txt")

HERE = Path(__file__).resolve().parent
DEFAULT_KEY = HERE / "service-account.json"
BATCH_LIMIT = 400  # Firestore allows 500 writes per batch


def norm(value) -> str:
    """Header / name normaliser: upper case, single spaces, trimmed."""
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip().upper()


def as_int(value) -> int:
    if value is None or value == "" or value == "-":
        return 0
    if isinstance(value, (int, float)):
        return int(round(value))
    try:
        return int(round(float(str(value).replace(",", "").replace("%", ""))))
    except ValueError:
        return 0


def as_float(value) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).replace(",", ""))
    except (TypeError, ValueError):
        return 0.0


def as_flag(value) -> bool:
    return as_int(value) != 0


# ---------------------------------------------------------------- Excel reading

@dataclass
class Sheet:
    name: str
    headers: dict[str, int]          # normalised header -> column index
    raw_headers: list[str]
    rows: list[tuple]
    header_row_index: int
    preamble: list[tuple] = field(default_factory=list)  # rows above the header


def read_sheet(path: Path, preferred: str | None, header_key: str = "SHOP CODE") -> Sheet:
    """Find the sheet (preferred name first) whose header row contains `header_key`."""
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    names = wb.sheetnames
    order = ([n for n in names if norm(n) == norm(preferred)] if preferred else []) + names
    try:
        for name in dict.fromkeys(order):
            ws = wb[name]
            preamble: list[tuple] = []
            for i, row in enumerate(ws.iter_rows(values_only=True)):
                if i > 15:
                    break
                cells = [norm(c) for c in row]
                if header_key in cells:
                    headers: dict[str, int] = {}
                    for idx, h in enumerate(cells):
                        if h and h not in headers:
                            headers[h] = idx
                    rows = [r for r in ws.iter_rows(min_row=i + 2, values_only=True)]
                    return Sheet(name, headers, [str(c or "") for c in row], rows, i, preamble)
                preamble.append(row)
    finally:
        wb.close()
    sys.exit(f"{path.name}: no sheet with a '{header_key}' header row was found.")


def col(sheet: Sheet, *candidates: str, pattern: str | None = None) -> int | None:
    for c in candidates:
        if norm(c) in sheet.headers:
            return sheet.headers[norm(c)]
    if pattern:
        for h, idx in sheet.headers.items():
            if re.fullmatch(pattern, h):
                return idx
    return None


def first(*indexes: int | None) -> int | None:
    """First column index that was found (0 is a valid index, so no `or`)."""
    return next((i for i in indexes if i is not None), None)


def cell(row: tuple, idx: int | None):
    if idx is None or idx >= len(row):
        return None
    return row[idx]


def parse_tracking(path: Path) -> tuple[dict[str, dict], dict]:
    sheet = read_sheet(path, "Mastersheet")
    c = {
        "code": col(sheet, "Shop Code"),
        "name": col(sheet, "Shop Name"),
        "areaManager": col(sheet, "Area Manager"),
        "regionalManager": col(sheet, "Regional Manager"),
        "region": col(sheet, "Region"),
        "promoter": col(sheet, "Promoter"),
        "kaLevel": col(sheet, "KA Level"),
        "stock": col(sheet, "PSI Stock", "Stock", "TTL Stock"),
        # Monthly columns are named after the month (e.g. "OCT ST"); fall back to the totals.
        "st": first(col(sheet, pattern=r"[A-Z]{3,9} ST"), col(sheet, "TTL ST")),
        "so": first(col(sheet, pattern=r"[A-Z]{3,9} SO"), col(sheet, "TTL SO")),
        "avgSo": col(sheet, "Avg SO"),
        "groupASt": col(sheet, "Group A ST"),
        "groupBSt": col(sheet, "Group B ST"),
        "groupASo": col(sheet, "Group A SO"),
        "groupBSo": col(sheet, "Group B SO"),
        "zeroSo3": col(sheet, "3 Days 0 SO"),
        "zeroSo6": col(sheet, "6 Days 0 SO"),
        "zeroSo10": col(sheet, "10 Days 0 SO"),
        "stTarget": col(sheet, pattern=r"([A-Z]{3,9} )?ST (TARGET|TGT)"),
        "soTarget": col(sheet, pattern=r"([A-Z]{3,9} )?SO (TARGET|TGT)"),
    }
    missing = [k for k in ("code", "name", "areaManager", "stock", "st", "so") if c[k] is None]
    if missing:
        sys.exit(f"{path.name} [{sheet.name}]: required columns not found: {', '.join(missing)}")

    daily_cols = sorted(
        ((int(m.group(1)), idx) for h, idx in sheet.headers.items()
         if (m := re.fullmatch(r"(\d{1,2})(ST|ND|RD|TH)", h))),
    )

    shops: dict[str, dict] = {}
    for row in sheet.rows:
        code = norm(cell(row, c["code"]))
        if not code or code in ("TOTAL", "TTL", "GRAND TOTAL"):
            continue
        st, so = as_int(cell(row, c["st"])), as_int(cell(row, c["so"]))
        shops[code] = {
            "name": str(cell(row, c["name"]) or "").strip(),
            "areaManager": norm(cell(row, c["areaManager"])),
            "regionalManager": norm(cell(row, c["regionalManager"])),
            "region": str(cell(row, c["region"]) or "").strip(),
            "promoter": "" if norm(cell(row, c["promoter"])) in ("", "-") else str(cell(row, c["promoter"])).strip(),
            "kaLevel": str(cell(row, c["kaLevel"]) or "").strip(),
            "stock": as_int(cell(row, c["stock"])),
            "st": st,
            "so": so,
            "avgSo": round(as_float(cell(row, c["avgSo"])), 2),
            "groupASt": as_int(cell(row, c["groupASt"])),
            "groupBSt": as_int(cell(row, c["groupBSt"])),
            "groupASo": as_int(cell(row, c["groupASo"])),
            "groupBSo": as_int(cell(row, c["groupBSo"])),
            "zeroSo3": as_flag(cell(row, c["zeroSo3"])),
            "zeroSo6": as_flag(cell(row, c["zeroSo6"])),
            "zeroSo10": as_flag(cell(row, c["zeroSo10"])),
            "dailySo": [as_int(cell(row, idx)) for _, idx in daily_cols],
            "stTarget": as_int(cell(row, c["stTarget"])),
            "soTarget": as_int(cell(row, c["soTarget"])),
        }

    # Above the header the Mastersheet holds the date range, e.g. "10/1/26  10/12/26".
    dates = [v for r in sheet.preamble for v in r if isinstance(v, (dt.datetime, dt.date))]
    info = {
        "sheet": sheet.name,
        "periodStart": min(dates) if dates else None,
        "periodEnd": max(dates) if dates else None,
        "stColumn": sheet.raw_headers[c["st"]],
        "soColumn": sheet.raw_headers[c["so"]],
        "days": len(daily_cols),
    }
    return shops, info


def apply_targets(shops: dict[str, dict], path: Path, st_col: str | None, so_col: str | None) -> list[str]:
    sheet = read_sheet(path, None)
    code_idx = col(sheet, "Shop Code")
    st_idx = col(sheet, st_col) if st_col else col(sheet, pattern=r"([A-Z]{3,9} )?(FINAL )?ST (TARGET|TGT)")
    so_idx = col(sheet, so_col) if so_col else col(sheet, pattern=r"([A-Z]{3,9} )?(FINAL )?SO (TARGET|TGT)")
    notes = []
    if st_col and st_idx is None:
        sys.exit(f"{path.name}: column '{st_col}' not found. Columns: {', '.join(h for h in sheet.raw_headers if h)}")
    if so_col and so_idx is None:
        sys.exit(f"{path.name}: column '{so_col}' not found. Columns: {', '.join(h for h in sheet.raw_headers if h)}")
    if st_idx is None and so_idx is None:
        sys.exit(
            f"{path.name}: could not tell which columns hold the ST and SO targets.\n"
            f"Pass them explicitly, e.g. --st-target-col \"OCT FINAL TGT\" --so-target-col \"...\".\n"
            f"Columns found: {', '.join(h for h in sheet.raw_headers if h)}"
        )
    matched = 0
    unknown = []
    for row in sheet.rows:
        code = norm(cell(row, code_idx))
        if not code:
            continue
        shop = shops.get(code)
        if shop is None:
            unknown.append(code)
            continue
        matched += 1
        if st_idx is not None:
            shop["stTarget"] = as_int(cell(row, st_idx))
        if so_idx is not None:
            shop["soTarget"] = as_int(cell(row, so_idx))
    notes.append(
        f"targets: {matched} shops matched"
        f" (ST from '{sheet.raw_headers[st_idx] if st_idx is not None else '-'}',"
        f" SO from '{sheet.raw_headers[so_idx] if so_idx is not None else '-'}')"
    )
    if unknown:
        notes.append(f"targets: {len(unknown)} shop codes not in the tracking file, ignored (e.g. {', '.join(unknown[:5])})")
    return notes


def latest_tracking(folder: str) -> Path:
    files = [Path(p) for p in glob.glob(os.path.join(os.path.expanduser(folder), "*.xls*"))
             if not Path(p).name.startswith("~$")]
    tracking = [p for p in files if "TRACKING" in p.name.upper()] or files
    if not tracking:
        sys.exit(f"No Excel files found in {folder}")
    return max(tracking, key=lambda p: p.stat().st_mtime)


# ---------------------------------------------------------------- Firebase

def firebase(key: str | None):
    try:
        import firebase_admin
        from firebase_admin import auth, credentials, firestore
    except ImportError:
        sys.exit("Missing dependency: run  pip install -r tools/requirements.txt")
    if not firebase_admin._apps:
        key_path = key or os.environ.get("GOOGLE_APPLICATION_CREDENTIALS") or str(DEFAULT_KEY)
        if not Path(key_path).exists():
            sys.exit(f"Service-account key not found at {key_path}. See the top of this file.")
        firebase_admin.initialize_app(credentials.Certificate(key_path))
    return firestore.client(), auth, firestore


def cmd_import(args):
    path = latest_tracking(args.latest_in) if args.latest_in else Path(args.tracking)
    print(f"Reading {path}")
    shops, info = parse_tracking(path)
    notes = [f"tracking: {len(shops)} shops from sheet '{info['sheet']}', "
             f"ST='{info['stColumn']}', SO='{info['soColumn']}', {info['days']} daily SO columns"]
    if args.targets:
        notes += apply_targets(shops, Path(args.targets), args.st_target_col, args.so_target_col)
    for n in notes:
        print("  " + n)

    managers: dict[str, int] = {}
    for s in shops.values():
        managers[s["areaManager"]] = managers.get(s["areaManager"], 0) + 1
    print(f"  {len(managers)} area managers: " + ", ".join(f"{m or '(blank)'} ({n})" for m, n in sorted(managers.items())))

    end = info["periodEnd"]
    period = args.period or (end.strftime("%b %Y") if end else "")
    if isinstance(end, dt.date) and not isinstance(end, dt.datetime):
        end = dt.datetime.combine(end, dt.time())
    as_of = end.replace(hour=23, minute=59) if end else dt.datetime.now()
    if args.dry_run:
        print(f"Dry run: nothing uploaded. Period '{period}', as of {as_of:%d %b %Y}.")
        return

    db, _, firestore = firebase(args.key)
    col_ref = db.collection("shops")
    existing = {d.id for d in col_ref.select([]).stream()}
    batch, n = db.batch(), 0

    def flush():
        nonlocal batch, n
        if n:
            batch.commit()
        batch, n = db.batch(), 0

    for code, data in shops.items():
        batch.set(col_ref.document(code), data)
        n += 1
        if n >= BATCH_LIMIT:
            flush()
    stale = existing - shops.keys()
    for code in stale:
        batch.delete(col_ref.document(code))
        n += 1
        if n >= BATCH_LIMIT:
            flush()
    batch.set(db.collection("meta").document("report"), {
        "asOf": as_of.astimezone(dt.timezone.utc) if as_of.tzinfo else as_of.replace(tzinfo=dt.timezone.utc),
        "periodLabel": period,
        "sourceFile": path.name,
        "uploadedAt": firestore.SERVER_TIMESTAMP,
        "shopCount": len(shops),
    })
    n += 1
    flush()
    print(f"Uploaded {len(shops)} shops, removed {len(stale)} that are no longer in the file.")

    if args.announce:
        db.collection("broadcasts").add({
            "title": f"New figures: {period or path.stem}",
            "body": args.announce,
            "author": "Admin",
            "createdAt": firestore.SERVER_TIMESTAMP,
        })
        print("Broadcast sent.")


def cmd_managers(args):
    shops, _ = parse_tracking(Path(args.tracking))
    names: dict[str, set[str]] = {}
    for s in shops.values():
        if s["areaManager"]:
            names.setdefault(s["areaManager"], set()).add("Area")
        if s["regionalManager"]:
            names.setdefault(s["regionalManager"], set()).add("Regional")
    print("Use these exact names with add-user --name:")
    for name in sorted(names):
        print(f"  {name:30s} {'/'.join(sorted(names[name]))}")


def cmd_add_user(args):
    db, auth, firestore = firebase(args.key)
    try:
        user = auth.get_user_by_email(args.email)
        print(f"Account exists for {args.email}")
        if args.password:
            auth.update_user(user.uid, password=args.password)
            print("  password updated")
    except auth.UserNotFoundError:
        if not args.password:
            sys.exit("New account: --password is required (they can change it with 'Forgot password?').")
        user = auth.create_user(email=args.email, password=args.password, display_name=args.name)
        print(f"Created account for {args.email}")
    db.collection("users").document(user.uid).set({
        "email": args.email,
        "name": norm(args.name),
        "role": args.role,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    })
    print(f"  linked to '{norm(args.name)}' as {args.role} (uid {user.uid})")


def cmd_users(args):
    db, _, _ = firebase(args.key)
    for d in db.collection("users").stream():
        u = d.to_dict()
        print(f"  {u.get('email',''):35s} {u.get('name',''):28s} {u.get('role','')}")


def cmd_remove_user(args):
    db, auth, _ = firebase(args.key)
    user = auth.get_user_by_email(args.email)
    db.collection("users").document(user.uid).delete()
    auth.delete_user(user.uid)
    print(f"Removed {args.email}")


def cmd_broadcast(args):
    db, _, firestore = firebase(args.key)
    db.collection("broadcasts").add({
        "title": args.title,
        "body": args.body,
        "author": args.author,
        "createdAt": firestore.SERVER_TIMESTAMP,
    })
    print("Broadcast sent.")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--key", help="Path to the Firebase service-account JSON key")
    sub = p.add_subparsers(dest="cmd", required=True)

    i = sub.add_parser("import", help="Upload the latest figures")
    src = i.add_mutually_exclusive_group(required=True)
    src.add_argument("--tracking", help="DAILY TRACKING .xlsx file")
    src.add_argument("--latest-in", help="Folder: use the newest *TRACKING*.xlsx in it")
    i.add_argument("--targets", help="ST AND SO TARGET PER SHOP .xlsx file (optional)")
    i.add_argument("--st-target-col", help="Header of the ST target column in the targets file")
    i.add_argument("--so-target-col", help="Header of the SO target column in the targets file")
    i.add_argument("--period", help='Label shown in the app, e.g. "Oct 2026" (default: from the file)')
    i.add_argument("--announce", help="Also send a broadcast with this text")
    i.add_argument("--dry-run", action="store_true", help="Parse and summarise without uploading")
    i.set_defaults(func=cmd_import)

    m = sub.add_parser("managers", help="List the manager names found in a tracking file")
    m.add_argument("--tracking", required=True)
    m.set_defaults(func=cmd_managers)

    a = sub.add_parser("add-user", help="Create or update a sign-in and link it to a manager name")
    a.add_argument("--email", required=True)
    a.add_argument("--name", required=True, help="Exactly as in the Area/Regional Manager column")
    a.add_argument("--role", choices=["manager", "admin"], default="manager")
    a.add_argument("--password")
    a.set_defaults(func=cmd_add_user)

    sub.add_parser("users", help="List app users").set_defaults(func=cmd_users)

    r = sub.add_parser("remove-user", help="Delete a user's sign-in")
    r.add_argument("--email", required=True)
    r.set_defaults(func=cmd_remove_user)

    b = sub.add_parser("broadcast", help="Send a message to everyone")
    b.add_argument("--title", required=True)
    b.add_argument("--body", required=True)
    b.add_argument("--author", default="Admin")
    b.set_defaults(func=cmd_broadcast)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
