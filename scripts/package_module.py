"""
ArchiveTune (2026)
© Rukamori — github.com/rukamori
GPL-3.0 License | Contributors: see git history
Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
"""

import argparse
import hashlib
import io
import json
import os
import struct
from pathlib import Path
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description="Package an ArchiveTune .arch module.")
    parser.add_argument("directory", type=Path, help="Directory containing manifest.json and provider.js")
    parser.add_argument("output", type=Path, help="Destination .arch file (replaced if it exists)")
    args = parser.parse_args()
    try:
        directory = args.directory.expanduser().resolve()
        output = args.output.expanduser().resolve()
        if output.suffix.lower() != ".arch":
            raise ValueError("The output filename must end in .arch")
        manifest_bytes = (directory / "manifest.json").read_bytes()
        script = (directory / "provider.js").read_bytes()
        if len(manifest_bytes) > 128 * 1024 or not 0 < len(script) <= 3 * 1024 * 1024:
            raise ValueError("Manifest must be at most 128 KiB; provider.js must be nonempty and at most 3 MiB")
        manifest = json.loads(manifest_bytes.decode("utf-8-sig"))
        script.decode("utf-8-sig")
        if not isinstance(manifest, dict) or manifest.get("format") != "archivetune.module":
            raise ValueError("Expected format archivetune.module")
        if any(type(manifest.get(key)) is not int or manifest[key] != 1 for key in ("formatVersion", "apiVersion")):
            raise ValueError("Only formatVersion 1 and apiVersion 1 are supported")
        if manifest.get("entry") != "provider.js" or not isinstance(manifest.get("module"), dict):
            raise ValueError("Expected entry provider.js and a module object")
        manifest["sha256"] = hashlib.sha256(script).hexdigest()
        files = [("provider.js", script)]
        icon_name = manifest["module"].get("icon")
        if "icon" in manifest["module"]:
            if icon_name != "icon.png":
                raise ValueError("The optional icon must be named icon.png")
            icon = (directory / icon_name).read_bytes()
            if not 24 <= len(icon) <= 256 * 1024 or icon[:8] != b"\x89PNG\r\n\x1a\n" or icon[12:16] != b"IHDR":
                raise ValueError("icon.png must be a PNG of at most 256 KiB")
            width, height = struct.unpack(">II", icon[16:24])
            if not 1 <= width <= 1024 or not 1 <= height <= 1024:
                raise ValueError("icon.png dimensions must be between 1 and 1024 pixels")
            manifest["iconSha256"] = hashlib.sha256(icon).hexdigest()
            files.append(("icon.png", icon))
        else:
            manifest.pop("iconSha256", None)
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
        if len(manifest_bytes) > 128 * 1024:
            raise ValueError("Packed manifest exceeds 128 KiB")
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in [("manifest.json", manifest_bytes)] + files:
                entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                archive.writestr(entry, data)
        package = buffer.getvalue()
        if len(package) > 4 * 1024 * 1024:
            raise ValueError("Package exceeds 4 MiB")
        with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".tmp", delete=False) as temporary:
            temporary_path = Path(temporary.name)
            try:
                temporary.write(package)
                temporary.flush()
                os.fsync(temporary.fileno())
            except BaseException:
                temporary.close()
                temporary_path.unlink(missing_ok=True)
                raise
        try:
            os.replace(temporary_path, output)
        finally:
            temporary_path.unlink(missing_ok=True)
        print(f"Created {output} ({len(package)} bytes)")
        print(f"provider.js SHA-256: {manifest['sha256']}")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
