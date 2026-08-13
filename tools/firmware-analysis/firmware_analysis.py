#!/usr/bin/env python3
"""Incremental, content-addressed analysis of A5 Inspector snapshots."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DEFAULT_CACHE = REPO_ROOT / ".firmware-cache"
DEFAULT_DOCS = REPO_ROOT / "docs" / "feature-native-telemetry"
CORE_PACKAGES = {
    "com.szchoiceway.eventcenter": 0,
    "com.szchoiceway.ksw_dashboard": 0,
    "com.szchoiceway.providers.settings": 0,
    "com.szchoiceway.customerui": 1,
    "com.szchoiceway.fatset": 1,
    "com.szchoiceway.settings": 1,
    "com.szchoiceway.testtools": 1,
    "com.szchoiceway.updatemcu": 1,
    "com.ivicar.avm": 1,
    "com.szchoiceway.ambient": 2,
    "com.szchoiceway.btsuite": 2,
    "com.szchoiceway.transmitbt": 2,
    "com.szchoiceway.logcapture": 2,
    "com.szchoiceway.navigation": 2,
    "com.szchoiceway.dsp": 2,
    "com.szchoiceway.equalizer": 2,
}


class SnapshotResult:
    def __init__(self, snapshot_id: str, manifest_path: Path):
        self.snapshot_id = snapshot_id
        self.manifest_path = manifest_path
        self.complete_marker = manifest_path.parent / "COMPLETE"


def read_valid_manifest(snapshot_dir: Path, cache: Path) -> dict | None:
    manifest_path = snapshot_dir / "manifest.json"
    if not ((snapshot_dir / "COMPLETE").is_file() and manifest_path.is_file()):
        return None
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if (
            manifest.get("schema") != 1
            or manifest.get("snapshot_id") != snapshot_dir.name
        ):
            return None
        for entry in manifest["entries"]:
            stored = cache / entry["object"]
            if not stored.is_file() or stored.stat().st_size != entry["size"]:
                return None
    except (KeyError, OSError, TypeError, ValueError, json.JSONDecodeError):
        return None
    return manifest


def package_document_name(package_name: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9._-]+", "_", package_name).strip("._-")
    return f"APP-{safe or 'unknown'}.md"


def classify_entry(name: str) -> str:
    if name.endswith("/base.apk") and name.startswith("packages/"):
        return "apk"
    if name.endswith(".so"):
        return "native-library"
    if name.startswith("reports/"):
        return "report"
    return "other"


def _store_entry(archive: zipfile.ZipFile, info: zipfile.ZipInfo, cache: Path):
    objects = cache / "objects"
    temp_dir = objects / ".tmp"
    temp_dir.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    with archive.open(info) as source, tempfile.NamedTemporaryFile(
        dir=temp_dir, delete=False
    ) as temporary:
        temp_path = Path(temporary.name)
        try:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
                temporary.write(block)
        except Exception:
            temp_path.unlink(missing_ok=True)
            raise
    object_hash = digest.hexdigest()
    destination = objects / object_hash[:2] / object_hash
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        temp_path.unlink()
    else:
        os.replace(temp_path, destination)
    return object_hash, destination.relative_to(cache).as_posix()


def ingest(source: Path, cache: Path = DEFAULT_CACHE) -> SnapshotResult:
    source = Path(source).resolve()
    cache = Path(cache).resolve()
    source_handle = source.open("rb")
    try:
        snapshot_id = hashlib.file_digest(source_handle, "sha256").hexdigest()
        source_size = os.fstat(source_handle.fileno()).st_size
        source_handle.seek(0)
    except Exception:
        source_handle.close()
        raise
    snapshot_dir = cache / "snapshots" / snapshot_id
    manifest_path = snapshot_dir / "manifest.json"
    if read_valid_manifest(snapshot_dir, cache) is not None:
        source_handle.close()
        return SnapshotResult(snapshot_id, manifest_path)

    cache.mkdir(parents=True, exist_ok=True)
    staging = cache / "snapshots" / f".{snapshot_id}.partial"
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    entries = []
    try:
        with source_handle, zipfile.ZipFile(source_handle) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                object_hash, object_path = _store_entry(archive, info, cache)
                entries.append({
                    "path": info.filename,
                    "size": info.file_size,
                    "compressed_size": info.compress_size,
                    "crc32": f"{info.CRC:08x}",
                    "sha256": object_hash,
                    "object": object_path,
                    "kind": classify_entry(info.filename),
                })
        manifest = {
            "schema": 1,
            "snapshot_id": snapshot_id,
            "source_name": source.name,
            "source_size": source_size,
            "ingested_at": datetime.now(timezone.utc).isoformat(),
            "entries": entries,
        }
        (staging / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        (staging / "COMPLETE").write_text("ok\n", encoding="utf-8")
        shutil.rmtree(snapshot_dir, ignore_errors=True)
        os.replace(staging, snapshot_dir)
    except Exception:
        source_handle.close()
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return SnapshotResult(snapshot_id, manifest_path)


def load_snapshot(cache: Path, snapshot_id: str | None):
    snapshots = cache / "snapshots"
    if snapshot_id:
        candidates = [snapshots / snapshot_id]
    else:
        candidates = sorted(
            (path for path in snapshots.glob("*") if (path / "COMPLETE").is_file()),
            key=lambda path: (path / "COMPLETE").stat().st_mtime_ns,
            reverse=True,
        )
    for candidate in candidates:
        manifest = read_valid_manifest(candidate, cache)
        if manifest is not None:
            return manifest
    raise SystemExit("No hay ningún snapshot completo y válido; ejecuta ingest primero")


def object_path(cache: Path, entry: dict) -> Path:
    return cache / entry["object"]


def read_json_entry(cache: Path, entries: dict, path: str):
    entry = entries.get(path)
    if not entry:
        return None
    return json.loads(object_path(cache, entry).read_text(encoding="utf-8"))


def run_text(command: list[str], limit: int = 500_000) -> str:
    try:
        result = subprocess.run(
            command, capture_output=True, check=False, timeout=60
        )
    except subprocess.TimeoutExpired as error:
        raise SystemExit(f"El comando {command[0]} superó 60 segundos") from error
    if result.returncode != 0:
        detail = result.stderr[:limit].decode("utf-8", errors="replace").strip()
        raise SystemExit(
            f"El comando {command[0]} falló ({result.returncode}): {detail}"
        )
    output = (result.stdout + result.stderr)[:limit]
    return output.decode("utf-8", errors="replace")


def aapt_metadata(apk: Path) -> dict:
    output = run_text(["aapt", "dump", "badging", str(apk)])
    permissions = re.findall(r"uses-permission: name='([^']+)'", output)
    launchable = re.findall(r"launchable-activity: name='([^']+)'", output)
    sdk = re.search(r"sdkVersion:'([^']+)'", output)
    target = re.search(r"targetSdkVersion:'([^']+)'", output)
    return {
        "permissions": sorted(set(permissions)),
        "launchable_activities": launchable,
        "min_sdk": sdk.group(1) if sdk else None,
        "target_sdk": target.group(1) if target else None,
    }


def apk_entries(apk: Path) -> dict:
    with zipfile.ZipFile(apk) as archive:
        dex_files = []
        native_libraries = []
        for info in archive.infolist():
            name = info.filename
            if re.fullmatch(r"classes\d*\.dex", name):
                dex_files.append(name)
            elif name.startswith("lib/") and name.endswith(".so"):
                native_libraries.append(name)
    return {
        "dex_files": dex_files,
        "native_libraries": sorted(native_libraries),
    }


def apk_metadata(cache: Path, apk_sha256: str, apk: Path) -> dict:
    path = cache / "apk-metadata" / f"{apk_sha256}.json"
    if path.is_file():
        cached = json.loads(path.read_text(encoding="utf-8"))
        if cached.get("schema") == 1:
            return cached["metadata"]
    metadata = {**aapt_metadata(apk), **apk_entries(apk)}
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        {"schema": 1, "metadata": metadata}, ensure_ascii=False, indent=2
    ) + "\n"
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=path.parent, delete=False
    ) as temporary:
        temporary.write(payload)
        temporary_path = Path(temporary.name)
    os.replace(temporary_path, path)
    return metadata


def write_text_if_changed(path: Path, content: str):
    if path.is_file() and path.read_text(encoding="utf-8") == content:
        return
    path.write_text(content, encoding="utf-8")


def replace_generated_section(path: Path, generated: str, package: dict):
    start = "<!-- GENERATED:START -->"
    end = "<!-- GENERATED:END -->"
    section = f"{start}\n{generated.rstrip()}\n{end}"
    if path.exists():
        current = path.read_text(encoding="utf-8")
        if start in current and end in current:
            current = re.sub(
                re.escape(start) + r".*?" + re.escape(end),
                section,
                current,
                flags=re.DOTALL,
            )
        else:
            current = section + "\n\n" + current
    else:
        current = (
            f"# {package['package']}\n\n{section}\n\n"
            "## Relevancia para la telemetría\n\nPendiente de revisión.\n\n"
            "## Interfaces y datos\n\nPendiente de revisión.\n\n"
            "## Evidencias y hallazgos\n\n"
            "| Confianza | Evidencia | Interpretación |\n"
            "|---|---|---|\n"
            "| Pendiente | — | — |\n\n"
            "## Búsquedas realizadas\n\n"
            "- Pendiente.\n\n"
            "## Preguntas pendientes\n\n"
            "- Pendiente.\n"
        )
    write_text_if_changed(path, current.rstrip() + "\n")


def priority_for(package_name: str) -> int:
    return CORE_PACKAGES.get(package_name, 3)


def analysis_status(cache: Path, apk_sha256: str) -> str:
    decompile_dir = cache / "jadx" / apk_sha256
    if not jadx_cache_complete(decompile_dir):
        return "indexada"
    exit_code_file = decompile_dir / "JADX_EXIT_CODE"
    if (
        exit_code_file.is_file()
        and exit_code_file.read_text(encoding="utf-8").strip() != "0"
    ):
        return "decompilada_parcial"
    return "decompilada"


def jadx_cache_complete(directory: Path) -> bool:
    exit_code = directory / "JADX_EXIT_CODE"
    if not (
        (directory / "COMPLETE").is_file()
        and (directory / "sources").is_dir()
        and exit_code.is_file()
    ):
        return False
    try:
        int(exit_code.read_text(encoding="utf-8").strip())
    except ValueError:
        return False
    return True


def build_catalog(manifest: dict, cache: Path, docs: Path):
    entries = {entry["path"]: entry for entry in manifest["entries"]}
    inventory = read_json_entry(cache, entries, "reports/packages.json") or []
    inventory_by_package = {
        item.get("package"): item for item in inventory if item.get("package")
    }
    packages = []
    for path, entry in sorted(entries.items()):
        match = re.fullmatch(r"packages/([^/]+)/base\.apk", path)
        if not match:
            continue
        package_name = match.group(1)
        apk = object_path(cache, entry)
        source = inventory_by_package.get(package_name, {})
        package = {
            "package": package_name,
            "label": source.get("label", ""),
            "version_name": source.get("version_name", ""),
            "version_code": source.get("version_code"),
            "uid": source.get("uid"),
            "system_app": source.get("system_app"),
            "sha256": entry["sha256"],
            "size": entry["size"],
            "object": entry["object"],
            "priority": priority_for(package_name),
            "status": analysis_status(cache, entry["sha256"]),
            **apk_metadata(cache, entry["sha256"], apk),
        }
        packages.append(package)

    docs.mkdir(parents=True, exist_ok=True)
    catalog = {
        "schema": 1,
        "snapshot_id": manifest["snapshot_id"],
        "source_name": manifest["source_name"],
        "packages": packages,
    }
    write_text_if_changed(
        docs / "APPS.json",
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
    )
    snapshot_summary = {
        key: manifest[key]
        for key in ("schema", "snapshot_id", "source_name", "source_size", "ingested_at")
    }
    snapshot_summary["entry_count"] = len(manifest["entries"])
    snapshot_summary["unique_objects"] = len({entry["sha256"] for entry in manifest["entries"]})
    write_text_if_changed(
        docs / "SNAPSHOT.json",
        json.dumps(snapshot_summary, ensure_ascii=False, indent=2) + "\n",
    )

    rows = []
    for package in sorted(packages, key=lambda item: (item["priority"], item["package"])):
        doc = package_document_name(package["package"])
        rows.append(
            f"| {package['priority']} | [{package['package']}]({doc}) | "
            f"{package['version_name'] or '—'} | {package['status']} |"
        )
        generated = (
            "## Identidad de la captura\n\n"
            f"- **Snapshot:** `{manifest['snapshot_id']}`\n"
            f"- **APK SHA-256:** `{package['sha256']}`\n"
            f"- **Versión:** `{package['version_name'] or 'desconocida'}`\n"
            f"- **UID:** `{package['uid']}` · **Sistema:** `{package['system_app']}`\n"
            f"- **Prioridad:** nivel {package['priority']} · **Estado:** `{package['status']}`\n"
            f"- **DEX:** {len(package['dex_files'])} · **Librerías nativas:** "
            f"{len(package['native_libraries'])}\n"
            f"- **Permisos declarados:** {len(package['permissions'])}"
        )
        replace_generated_section(docs / doc, generated, package)

    write_text_if_changed(
        docs / "APPS.md",
        "# Catálogo de aplicaciones del firmware\n\n"
        "La prioridad `0` es el núcleo de telemetría; `1`, consumidores directos; "
        "`2`, referencias dirigidas; y `3`, catálogo mecánico.\n\n"
        "| Nivel | Aplicación | Versión | Estado |\n|---:|---|---|---|\n"
        + "\n".join(rows)
        + "\n",
    )
    return catalog


def load_or_build_catalog(manifest: dict, cache: Path, docs: Path) -> dict:
    catalog_path = docs / "APPS.json"
    if catalog_path.is_file():
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        if catalog.get("snapshot_id") == manifest["snapshot_id"]:
            return catalog
    return build_catalog(manifest, cache, docs)


def find_package(catalog: dict, package_name: str) -> dict:
    package = next(
        (item for item in catalog["packages"] if item["package"] == package_name),
        None,
    )
    if not package:
        raise SystemExit(f"Paquete no encontrado: {package_name}")
    return package


def decompile(cache: Path, docs: Path, snapshot_id: str | None, package_name: str):
    manifest = load_snapshot(cache, snapshot_id)
    catalog = load_or_build_catalog(manifest, cache, docs)
    package = find_package(catalog, package_name)
    destination = cache / "jadx" / package["sha256"]
    marker = destination / "COMPLETE"
    if jadx_cache_complete(destination):
        build_catalog(manifest, cache, docs)
        return destination
    if marker.exists():
        shutil.rmtree(destination)
    staging = destination.with_name(destination.name + ".partial")
    shutil.rmtree(staging, ignore_errors=True)
    staging.parent.mkdir(parents=True, exist_ok=True)
    apk = cache / package["object"]
    try:
        result = subprocess.run(
            ["jadx", "--deobf", "-d", str(staging), str(apk)],
            check=False,
            timeout=30 * 60,
        )
    except subprocess.TimeoutExpired as error:
        shutil.rmtree(staging, ignore_errors=True)
        raise SystemExit(f"JADX superó 30 minutos para {package_name}") from error
    if result.returncode != 0 and not (staging / "sources").is_dir():
        shutil.rmtree(staging, ignore_errors=True)
        raise SystemExit(f"JADX falló para {package_name}: {result.returncode}")
    (staging / "JADX_EXIT_CODE").write_text(
        f"{result.returncode}\n", encoding="utf-8"
    )
    (staging / "COMPLETE").write_text("ok\n", encoding="utf-8")
    shutil.rmtree(destination, ignore_errors=True)
    os.replace(staging, destination)
    build_catalog(manifest, cache, docs)
    return destination


def search(cache: Path, docs: Path, package_name: str, pattern: str, max_hits: int):
    catalog = json.loads((docs / "APPS.json").read_text(encoding="utf-8"))
    package = find_package(catalog, package_name)
    source = cache / "jadx" / package["sha256"]
    if not jadx_cache_complete(source):
        raise SystemExit("La aplicación no está decompilada")
    with tempfile.TemporaryFile(mode="w+", encoding="utf-8") as errors:
        process = subprocess.Popen(
            [
                "rg",
                "-n",
                "-i",
                "--max-count",
                str(max_hits),
                "--",
                pattern,
                str(source / "sources"),
            ],
            text=True,
            stdout=subprocess.PIPE,
            stderr=errors,
        )
        assert process.stdout is not None
        terminated_at_limit = False
        try:
            for index, line in enumerate(process.stdout):
                if index >= max_hits:
                    terminated_at_limit = True
                    process.terminate()
                    break
                print(line, end="")
        finally:
            process.stdout.close()
            try:
                return_code = process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                process.kill()
                return_code = process.wait()
        if return_code not in (0, 1) and not terminated_at_limit:
            errors.seek(0)
            detail = errors.read(8_000).strip()
            raise SystemExit(f"rg falló ({return_code}): {detail}")


def verify_docs(docs: Path):
    catalog = json.loads((docs / "APPS.json").read_text(encoding="utf-8"))
    missing = []
    stale = []
    for package in catalog["packages"]:
        path = docs / package_document_name(package["package"])
        if not path.is_file():
            missing.append(path.name)
            continue
        content = path.read_text(encoding="utf-8")
        generated = re.search(
            r"<!-- GENERATED:START -->(.*?)<!-- GENERATED:END -->",
            content,
            flags=re.DOTALL,
        )
        if not generated or package["sha256"] not in generated.group(1):
            stale.append(path.name)
    if missing or stale:
        raise SystemExit(f"Documentos ausentes={missing}; desactualizados={stale}")
    print(f"OK: {len(catalog['packages'])} documentos sincronizados")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--docs", type=Path, default=DEFAULT_DOCS)
    commands = parser.add_subparsers(dest="command", required=True)
    ingest_parser = commands.add_parser("ingest")
    ingest_parser.add_argument("zip", type=Path)
    index_parser = commands.add_parser("index")
    index_parser.add_argument("--snapshot")
    decompile_parser = commands.add_parser("decompile")
    decompile_parser.add_argument("package")
    decompile_parser.add_argument("--snapshot")
    search_parser = commands.add_parser("search")
    search_parser.add_argument("package")
    search_parser.add_argument("pattern")
    search_parser.add_argument("--max-hits", type=int, default=80)
    commands.add_parser("status")
    commands.add_parser("verify-docs")
    return parser.parse_args()


def main():
    args = parse_args()
    cache = args.cache.resolve()
    docs = args.docs.resolve()
    if args.command == "ingest":
        result = ingest(args.zip, cache)
        print(result.snapshot_id)
    elif args.command == "index":
        manifest = load_snapshot(cache, args.snapshot)
        catalog = build_catalog(manifest, cache, docs)
        print(f"{len(catalog['packages'])} aplicaciones indexadas")
    elif args.command == "decompile":
        print(decompile(cache, docs, args.snapshot, args.package))
    elif args.command == "search":
        search(cache, docs, args.package, args.pattern, args.max_hits)
    elif args.command == "status":
        catalog = json.loads((docs / "APPS.json").read_text(encoding="utf-8"))
        for package in sorted(catalog["packages"], key=lambda item: (item["priority"], item["package"])):
            state = analysis_status(cache, package["sha256"])
            print(
                f"{package['priority']}\t{state}\t{package['package']}\t"
                f"{package['sha256'][:12]}"
            )
    elif args.command == "verify-docs":
        verify_docs(docs)


if __name__ == "__main__":
    main()
