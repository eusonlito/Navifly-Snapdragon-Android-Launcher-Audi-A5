#!/usr/bin/env python3
"""Mirror local file changes into an emulator-visible Downloads directory."""

from __future__ import annotations

import argparse
import os
from pathlib import Path, PurePosixPath
import shlex
import subprocess
import time


Snapshot = dict[str, tuple[int, int]]


def snapshot(directory: Path) -> Snapshot:
    result: Snapshot = {}
    for path in directory.rglob("*"):
        if not path.is_file():
            continue
        stat = path.stat()
        relative = path.relative_to(directory).as_posix()
        result[relative] = (stat.st_mtime_ns, stat.st_size)
    return result


def adb(adb_path: str, target: list[str], *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [adb_path, *target, *arguments],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )


def remove_remote_files(
    adb_path: str,
    target: list[str],
    destination: str,
    removed: set[str],
) -> bool:
    for relative in sorted(removed, key=lambda item: item.count("/"), reverse=True):
        parts = PurePosixPath(relative).parts
        if not parts or any(part in {"", ".", ".."} for part in parts):
            continue
        remote = str(PurePosixPath(destination).joinpath(*parts))
        command = f"rm -f {shlex.quote(remote)}"
        result = adb(adb_path, target, "shell", command)
        if result.returncode != 0:
            print(f"[FILES] No se pudo eliminar {relative}: {result.stdout.strip()}", flush=True)
            return False
    return True


def synchronize(
    adb_path: str,
    target: list[str],
    source: Path,
    destination: str,
    removed: set[str],
) -> bool:
    mkdir = adb(adb_path, target, "shell", "mkdir", "-p", destination)
    if mkdir.returncode != 0:
        print(f"[FILES] ADB no está disponible: {mkdir.stdout.strip()}", flush=True)
        return False
    if not remove_remote_files(adb_path, target, destination, removed):
        return False
    pushed = adb(adb_path, target, "push", "--sync", f"{source}{os.sep}.", f"{destination}/")
    if pushed.returncode != 0:
        print(f"[FILES] Error de sincronización: {pushed.stdout.strip()}", flush=True)
        return False
    print(f"[FILES] Cambios sincronizados en Downloads/A5-Cockpit.", flush=True)
    return True


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    device = parser.add_mutually_exclusive_group(required=True)
    device.add_argument("--serial")
    device.add_argument("--transport-id")
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", required=True)
    parser.add_argument("--interval", type=float, default=1.0)
    return parser.parse_args()


def main() -> int:
    args = parse_arguments()
    source = args.source.resolve()
    if not source.is_dir():
        raise SystemExit(f"No existe el directorio vigilado: {source}")
    if args.interval <= 0:
        raise SystemExit("El intervalo debe ser mayor que cero")

    target = ["-t", args.transport_id] if args.transport_id else ["-s", args.serial]
    previous = snapshot(source)
    try:
        while True:
            time.sleep(args.interval)
            if not source.is_dir():
                print(f"[FILES] El directorio vigilado ya no existe: {source}", flush=True)
                continue
            current = snapshot(source)
            if current == previous:
                continue
            removed = set(previous) - set(current)
            if synchronize(args.adb, target, source, args.destination, removed):
                previous = current
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
