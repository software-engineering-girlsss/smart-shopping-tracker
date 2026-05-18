#!/usr/bin/env python3
"""
Test script for Picnic API authentication and search.
Usage:
  python3 test_picnic.py --email you@example.com --password yourpassword
  python3 test_picnic.py --email you@example.com --password yourpassword --search "Milch"

Reads PICNIC_EMAIL / PICNIC_PASSWORD from .env if not passed as args.
"""
import hashlib
import json
import sys
import uuid
import os
import argparse

try:
    import requests
except ImportError:
    print("Install requests: pip3 install requests")
    sys.exit(1)

BASE_URL = "https://storefront-prod.de.picnicinternational.com/api/15"
AGENT = "30100;1.15.233-10148"

DEVICE_ID_FILE = ".picnic-device-id"

def get_device_id():
    if os.path.exists(DEVICE_ID_FILE):
        return open(DEVICE_ID_FILE).read().strip()
    did = str(uuid.uuid4())
    open(DEVICE_ID_FILE, "w").write(did)
    return did

def base_headers(auth_token=None):
    h = {
        "x-picnic-agent": AGENT,
        "x-picnic-did": get_device_id(),
        "Content-Type": "application/json; charset=UTF-8",
    }
    if auth_token:
        h["x-picnic-auth"] = auth_token
    return h

def md5(s):
    return hashlib.md5(s.encode()).hexdigest()

def login(email, password):
    body = {"key": email, "secret": md5(password), "client_id": 30100}
    print(f"\n[1] POST /user/login")
    print(f"    body: {json.dumps({**body, 'secret': '***'})}")

    r = requests.post(f"{BASE_URL}/user/login", json=body, headers=base_headers())
    print(f"    status: {r.status_code}")
    print(f"    headers: x-picnic-auth={r.headers.get('x-picnic-auth', '<none>')}")

    body_text = r.text
    print(f"    body: {body_text[:500]}")

    if not r.ok:
        print(f"ERROR: login failed ({r.status_code})")
        return None

    auth_token = r.headers.get("x-picnic-auth")
    if not auth_token:
        print("ERROR: no x-picnic-auth token in response headers")
        return None

    # Check if 2FA is required
    try:
        data = r.json()
    except Exception:
        data = {}

    if data.get("second_factor_authentication_required") is True:
        print("\n[2FA] 2FA required — triggering SMS...")
        r2 = requests.post(
            f"{BASE_URL}/user/2fa/generate",
            json={"channel": "SMS"},
            headers=base_headers(auth_token),
        )
        print(f"    /user/2fa/generate status: {r2.status_code} body: {r2.text[:200]}")

        otp = input("Enter OTP from SMS: ").strip()
        r3 = requests.post(
            f"{BASE_URL}/user/2fa/verify",
            json={"otp": otp},
            headers=base_headers(auth_token),
        )
        print(f"    /user/2fa/verify status: {r3.status_code} body: {r3.text[:200]}")
        if not r3.ok:
            print("ERROR: 2FA verify failed")
            return None
        # New token may be issued after 2FA
        auth_token = r3.headers.get("x-picnic-auth", auth_token)
    else:
        print("    second_factor_authentication_required =", data.get("second_factor_authentication_required"))

    print(f"\nLogin OK! token={auth_token[:12]}...")
    return auth_token


def search(auth_token, query):
    import urllib.parse
    encoded = urllib.parse.quote(query)
    url = f"{BASE_URL}/pages/search-page-results?search_term={encoded}"
    print(f"\n[search] GET {url}")
    r = requests.get(url, headers=base_headers(auth_token))
    print(f"    status: {r.status_code}")
    print(f"    body (first 2000 chars):\n{r.text[:2000]}")


def load_env():
    env = {}
    for path in [".env", os.path.expanduser("~/.env")]:
        if os.path.exists(path):
            for line in open(path):
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    env[k.strip()] = v.strip().strip('"').strip("'")
    return env

if __name__ == "__main__":
    env = load_env()
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", default=env.get("PICNIC_EMAIL", ""))
    parser.add_argument("--password", default=env.get("PICNIC_PASSWORD", ""))
    parser.add_argument("--token", default="", help="use a saved auth token directly (skip login)")
    parser.add_argument("--search", default="Milch", help="product to search after login")
    args = parser.parse_args()

    if args.token:
        token = args.token
        print(f"Using provided token: {token[:20]}...")
    elif os.path.exists(".picnic-auth-token"):
        token = open(".picnic-auth-token").read().strip()
        print(f"Using saved token from .picnic-auth-token: {token[:20]}...")
    else:
        if not args.email or not args.password:
            print("Provide --email and --password or set PICNIC_EMAIL/PICNIC_PASSWORD in .env")
            sys.exit(1)
        token = login(args.email, args.password)

    if token:
        search(token, args.search)
