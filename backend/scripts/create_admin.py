"""Create an admin for the DelhiRaahi admin website (http://<server>:8010/admin).

    cd backend
    ../.venv/Scripts/python scripts/create_admin.py --email you@example.com --name "Your Name" --role owner

A random one-time password is written to data/admin/new_admin_password.txt (git-ignored), never printed,
so it doesn't end up in terminal logs. Sign in with it; the site then makes you choose your own
password. Delete the file afterwards. Roles: owner (everything) > agent (support, users) > viewer (read only).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.core.config import get_settings  # noqa: E402
from app.services.admin_store import AdminError, AdminStore, generate_password  # noqa: E402


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--email", required=True)
    p.add_argument("--name", default="")
    p.add_argument("--role", choices=["owner", "agent", "viewer"], default="owner")
    p.add_argument("--reset", action="store_true", help="the admin exists: give them a new one-time password")
    p.add_argument("--delete", action="store_true", help="remove this admin for good (keeps their audit entries)")
    a = p.parse_args()
    d = get_settings().crowdwise_data_dir / "admin"
    s = AdminStore(d / "admin.db")
    try:
        if a.delete:
            rows = s.q("SELECT id, role FROM admins WHERE email = ?", (a.email.strip().lower(),))
            if not rows:
                raise AdminError("No admin with that email.")
            if rows[0]["role"] == "owner" and s.owners() <= 1:
                raise AdminError("That is the last owner; add another owner first.")
            s.x("DELETE FROM admins WHERE id = ?", (rows[0]["id"],))
            s.audit(None, "cli", "admin_deleted", f"admin:{rows[0]['id']}", {"email": a.email.lower(), "via": "create_admin.py"}, None)
            print("Deleted.")
            return
        if a.reset:
            rows = s.q("SELECT id FROM admins WHERE email = ?", (a.email.strip().lower(),))
            if not rows:
                raise AdminError("No admin with that email.")
            pw = s.reset_password(rows[0]["id"])
            s.audit(None, "cli", "admin_password_reset", f"admin:{rows[0]['id']}", {"via": "create_admin.py"}, None)
        else:
            pw = generate_password()
            new_id = s.create_admin(a.email, a.name, a.role, pw, must_change=True)
            s.audit(None, "cli", "admin_created", f"admin:{new_id}", {"email": a.email.lower(), "role": a.role, "via": "create_admin.py"}, None)
    except AdminError as e:
        sys.exit(f"Error: {e}")
    out = d / "new_admin_password.txt"
    out.write_text(f"{a.email.strip().lower()}\n{pw}\n", encoding="utf-8")
    print(f"Done. One-time password saved in {out} (not printed). Sign in at /admin, choose a new password, then delete that file.")


if __name__ == "__main__":
    main()
