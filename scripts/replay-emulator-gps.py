#!/usr/bin/env python3
"""Reproduce en el emulador las posiciones GPS de un log real del logger."""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path


RELEVANT_PROVIDER_KEYS = {
    "KESAIWEI_RECORD_BELT",
    "KESAIWEI_RECORD_PARK",
    "KSW_DATA_SMALL_LIGHT_ON",
}


def parse_log(path: Path) -> tuple[int, int, list[dict[str, object]]]:
    replay_start: int | None = None
    replay_end: int | None = None
    locations: list[dict[str, object]] = []

    with path.open(encoding="utf-8") as source:
        for line in source:
            try:
                event = json.loads(line)
            except (json.JSONDecodeError, TypeError):
                continue

            timestamp = event.get("timestamp")
            if not isinstance(timestamp, int):
                continue

            source_type = event.get("source")
            provider_key = event.get("key")
            if source_type == "AIDL_CALLBACK" or (
                source_type in {"SYSVAR_INITIAL", "SYSVAR_CHANGE"}
                and provider_key in RELEVANT_PROVIDER_KEYS
            ):
                replay_start = timestamp if replay_start is None else min(replay_start, timestamp)
                replay_end = timestamp if replay_end is None else max(replay_end, timestamp)

            if source_type == "GPS_LOCATION":
                latitude = event.get("latitude")
                longitude = event.get("longitude")
                if isinstance(latitude, (int, float)) and isinstance(longitude, (int, float)):
                    locations.append(event)

    if replay_start is None:
        raise ValueError("El log no contiene eventos reproducibles")
    if not locations:
        raise ValueError("El log no contiene posiciones GPS")
    return replay_start, replay_end or replay_start, locations


def send_fix(adb: str, serial: str, location: dict[str, object]) -> None:
    longitude = str(location["longitude"])
    latitude = str(location["latitude"])
    altitude = location.get("altitude_m")
    command = [adb, "-s", serial, "emu", "geo", "fix", longitude, latitude]
    if isinstance(altitude, (int, float)):
        command.append(str(altitude))
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def replay(
    adb: str,
    serial: str,
    start_timestamp: int,
    end_timestamp: int,
    locations: list[dict[str, object]],
) -> None:
    started = time.monotonic()
    for location in locations:
        target = (int(location["timestamp"]) - start_timestamp) / 1000.0
        delay = target - (time.monotonic() - started)
        if delay > 0:
            time.sleep(delay)
        send_fix(adb, serial, location)
    remaining = (end_timestamp - start_timestamp) / 1000.0 - (time.monotonic() - started)
    if remaining > 0:
        time.sleep(remaining)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--loop", action="store_true")
    parser.add_argument("--loop-pause", type=float, default=2.0)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    replay_start, replay_end, locations = parse_log(args.log)
    if args.check:
        print(
            f"GPS validado: {len(locations)} posiciones · "
            f"duración {(replay_end - replay_start) / 1000.0:.3f}s"
        )
        return
    # Sitúa el mapa en la primera posición real desde el primer frame. El mismo
    # punto vuelve a emitirse después en su instante original del recorrido.
    send_fix(args.adb, args.serial, locations[0])
    while True:
        replay(args.adb, args.serial, replay_start, replay_end, locations)
        if not args.loop:
            return
        time.sleep(max(0.0, args.loop_pause))


if __name__ == "__main__":
    main()
