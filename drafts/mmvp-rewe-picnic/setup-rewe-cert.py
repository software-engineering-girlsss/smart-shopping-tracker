#!/usr/bin/env python3
"""
Extract REWE mTLS certificate from the REWE Android APK.

Usage:
    python3 setup-rewe-cert.py [path/to/rewe.apk]

If no APK path is given, tries to pull it from a connected Android device via ADB.
Outputs:
    rewe.pem  — client certificate (PEM)
    rewe.key  — private key (PKCS8 PEM)

Then add to .env:
    REWE_CERT_FILE=rewe.pem
    REWE_KEY_FILE=rewe.key
"""
import os, sys, shutil, subprocess, tempfile
from zipfile import ZipFile
from pathlib import Path

MTLS_PASSWORD = b"NC3hDTstMX9waPPV"
OUT_CERT = "rewe.pem"
OUT_KEY  = "rewe.key"

def find_pfx(apk_path: str) -> bytes | None:
    with ZipFile(apk_path) as z:
        for name in z.namelist():
            if "mtls_prod.pfx" in name.lower():
                print(f"  Found: {name}")
                return z.read(name)
    return None

def extract_from_apk(apk_path: str) -> bool:
    print(f"Scanning {apk_path} ...")
    pfx_data = find_pfx(apk_path)
    if pfx_data is None:
        print("  mtls_prod.pfx not found in this APK.")
        return False

    try:
        from cryptography.hazmat.primitives.serialization import (
            pkcs12, Encoding, PrivateFormat, NoEncryption
        )
    except ImportError:
        print("Install cryptography: pip3 install cryptography")
        sys.exit(1)

    private_key, certificate, _ = pkcs12.load_key_and_certificates(pfx_data, MTLS_PASSWORD)

    with open(OUT_CERT, "wb") as f:
        f.write(certificate.public_bytes(Encoding.PEM))
    with open(OUT_KEY, "wb") as f:
        f.write(private_key.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption()))

    print(f"Saved {OUT_CERT} and {OUT_KEY}")
    return True

def try_adb() -> str | None:
    if not shutil.which("adb"):
        return None
    try:
        result = subprocess.run(["adb", "shell", "pm", "path", "de.rewe.app"],
                                capture_output=True, text=True, timeout=10)
        if result.returncode != 0 or "package:" not in result.stdout:
            return None
        device_path = result.stdout.strip().replace("package:", "")
        print(f"Found REWE APK on device: {device_path}")
        local = tempfile.mktemp(suffix=".apk")
        subprocess.run(["adb", "pull", device_path, local], check=True, timeout=60)
        return local
    except Exception as e:
        print(f"ADB pull failed: {e}")
        return None

def try_split_apks_via_adb() -> list[str]:
    """Some devices store split APKs — try to pull all of them."""
    if not shutil.which("adb"):
        return []
    try:
        result = subprocess.run(
            ["adb", "shell", "pm", "path", "de.rewe.app"],
            capture_output=True, text=True, timeout=10
        )
        paths = [line.replace("package:", "").strip()
                 for line in result.stdout.splitlines() if "package:" in line]
        if not paths:
            return []
        local_paths = []
        for i, p in enumerate(paths):
            local = tempfile.mktemp(suffix=f"_split{i}.apk")
            r = subprocess.run(["adb", "pull", p, local], capture_output=True, timeout=60)
            if r.returncode == 0:
                local_paths.append(local)
                print(f"  Pulled split APK: {p} → {local}")
        return local_paths
    except Exception as e:
        print(f"ADB split pull failed: {e}")
        return []

if __name__ == "__main__":
    if len(sys.argv) > 1:
        apk = sys.argv[1]
        if not os.path.exists(apk):
            print(f"File not found: {apk}")
            sys.exit(1)
        if not extract_from_apk(apk):
            print("\nThe provided APK does not contain mtls_prod.pfx.")
            print("This is usually a code-only split APK. Try providing the base APK from ADB.")
            sys.exit(1)
    else:
        print("No APK path given, trying ADB...")

        # Try single APK pull first
        apk = try_adb()
        if apk and extract_from_apk(apk):
            os.unlink(apk)
            sys.exit(0)

        # Try split APKs
        splits = try_split_apks_via_adb()
        found = False
        for s in splits:
            if extract_from_apk(s):
                found = True
            os.unlink(s)

        if not found:
            print("""
Could not extract the certificate automatically.

Manual steps:
  1. Install the REWE app on an Android device (real or emulator)
  2. Run:  adb pull $(adb shell pm path de.rewe.app | cut -d: -f2 | head -1) rewe.apk
  3. Run:  python3 setup-rewe-cert.py rewe.apk

Or on Windows, use the PowerShell script from:
  https://github.com/ByteSizedMarius/rewerse-engineering/blob/main/docs/rewerse-engineering.ps1
  (run with -Dl flag to download and extract automatically)

Then add to .env:
  REWE_CERT_FILE=rewe.pem
  REWE_KEY_FILE=rewe.key
""")
            sys.exit(1)

    print(f"\nDone! Add to your .env:\n  REWE_CERT_FILE={OUT_CERT}\n  REWE_KEY_FILE={OUT_KEY}")

    # Quick API test
    print("\nTesting REWE mobile API...")
    try:
        from rewerse import Rewerse
        import json
        client = Rewerse(cert=OUT_CERT, key=OUT_KEY)
        portfolio = client.get_service_portfolio("28759")
        market = portfolio.get("deliveryMarket", {}).get("wwIdent", "")
        print(f"  Market for 28759: {market}")
        if market:
            results = client.get_products(market, "Milch", objects_per_page=3)
            products = results.get("products", [])
            print(f"  Search 'Milch': {len(products)} result(s)")
            for p in products[:2]:
                price = p.get("listing", {}).get("currentRetailPrice", "?")
                print(f"    {p.get('title','')} — {price/100 if isinstance(price,int) else price}€")
    except ImportError:
        print("  (install rewerse to test: pip3 install rewerse)")
    except Exception as e:
        print(f"  API test error: {e}")
