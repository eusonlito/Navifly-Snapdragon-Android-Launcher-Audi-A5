#!/usr/bin/env python3
"""Reproduce en el emulador las posiciones GPS de un log real del logger."""

from __future__ import annotations

import argparse
import json
import os
import struct
import tempfile
import time
from pathlib import Path
from typing import NamedTuple


RELEVANT_PROVIDER_KEYS = {
    "KESAIWEI_RECORD_BELT",
    "KESAIWEI_RECORD_PARK",
    "KSW_DATA_SMALL_LIGHT_ON",
}

GPS_SERVICE_METHOD = "/android.emulation.control.EmulatorController/setGps"
DEFAULT_SATELLITES = 8
METRES_PER_SECOND_TO_KNOTS = 1.9438444924406
GPS_FIELD_LATITUDE = 2
GPS_FIELD_LONGITUDE = 3
GPS_FIELD_SPEED = 4
GPS_FIELD_BEARING = 5
GPS_FIELD_ALTITUDE = 6
GPS_FIELD_SATELLITES = 7


class GrpcEndpoint(NamedTuple):
    address: str
    token: str


def read_properties(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator:
            properties[key.strip()] = value.strip()
    return properties


def grpc_runtime_directories() -> list[Path]:
    candidates: list[Path] = []
    runtime_dir = os.environ.get("XDG_RUNTIME_DIR")
    if runtime_dir:
        candidates.append(Path(runtime_dir) / "avd" / "running")
    if hasattr(os, "getuid"):
        candidates.append(Path("/run/user") / str(os.getuid()) / "avd" / "running")
    user = os.environ.get("USER")
    if user:
        candidates.append(Path("/tmp") / f"android-{user}" / "avd" / "running")
    candidates.append(Path(tempfile.gettempdir()) / "avd" / "running")
    return list(dict.fromkeys(candidates))


def resolve_grpc_endpoint(serial: str, running_directory: Path | None = None) -> GrpcEndpoint:
    serial_port = serial.removeprefix("emulator-")
    directories = [running_directory] if running_directory else grpc_runtime_directories()
    for directory in directories:
        if directory is None or not directory.is_dir():
            continue
        for path in sorted(directory.glob("pid_*.ini")):
            try:
                properties = read_properties(path)
            except OSError:
                # El emulador puede retirar el fichero al cerrarse mientras se
                # inspeccionan otros AVD activos.
                continue
            if properties.get("port.serial") != serial_port:
                continue
            grpc_port = properties.get("grpc.port")
            token = properties.get("grpc.token")
            if grpc_port and token:
                return GrpcEndpoint(f"127.0.0.1:{grpc_port}", token)
    searched = ", ".join(str(path) for path in directories)
    raise RuntimeError(
        f"No se encontró el endpoint gRPC de {serial}. Directorios consultados: {searched}"
    )


def encode_fixed64(field_number: int, value: float) -> bytes:
    return bytes([(field_number << 3) | 1]) + struct.pack("<d", value)


def build_gps_state_payload(location: dict[str, object]) -> bytes:
    """Serializa los campos usados de EmulatorController.GpsState."""
    payload = bytearray()
    payload.extend(encode_fixed64(GPS_FIELD_LATITUDE, float(location["latitude"])))
    payload.extend(encode_fixed64(GPS_FIELD_LONGITUDE, float(location["longitude"])))
    speed = location.get("speed_mps")
    if isinstance(speed, (int, float)):
        # EmulatorController documents m/s, but emulator 36.x forwards this
        # field to its GPS backend as knots. Convert here so Location.speed
        # observed inside Android matches the recorded m/s value.
        payload.extend(
            encode_fixed64(
                GPS_FIELD_SPEED,
                max(0.0, float(speed)) * METRES_PER_SECOND_TO_KNOTS,
            )
        )
    bearing = location.get("bearing_degrees")
    if isinstance(bearing, (int, float)):
        payload.extend(encode_fixed64(GPS_FIELD_BEARING, float(bearing) % 360.0))
    altitude = location.get("altitude_m")
    if isinstance(altitude, (int, float)):
        payload.extend(encode_fixed64(GPS_FIELD_ALTITUDE, float(altitude)))
    payload.extend(bytes([(GPS_FIELD_SATELLITES << 3), DEFAULT_SATELLITES]))
    return bytes(payload)


class ReplayTimeline(NamedTuple):
    """Clock contract shared with Kotlin ReplayTimeline.

    A complete replay uses elapsed realtime only when every replayable record
    has it. Otherwise the complete cycle falls back to legacy wall timestamps;
    clocks are never mixed within one CAN/GPS replay.
    """

    clock_key: str
    ticks_per_second: int
    start_tick: int
    end_tick: int

    @property
    def duration_seconds(self) -> float:
        return max(0, self.end_tick - self.start_tick) / self.ticks_per_second

    def delay_seconds(self, event: dict[str, object]) -> float:
        tick = event.get(self.clock_key)
        if not isinstance(tick, int):
            raise ValueError(f"El evento no contiene {self.clock_key}")
        return max(0, tick - self.start_tick) / self.ticks_per_second


class EmulatorGpsClient:
    def __init__(self, endpoint: GrpcEndpoint) -> None:
        try:
            import grpc
        except ModuleNotFoundError as error:
            raise RuntimeError(
                "El replay GPS completo necesita el módulo Python 'grpcio'. "
                "Instálalo con: python3 -m pip install -r requirements-emulator.txt"
            ) from error
        self._channel = grpc.insecure_channel(endpoint.address)
        self._set_gps = self._channel.unary_unary(
            GPS_SERVICE_METHOD,
            request_serializer=lambda payload: payload,
            response_deserializer=lambda payload: payload,
        )
        self._metadata = (("authorization", f"Bearer {endpoint.token}"),)

    def send(self, location: dict[str, object]) -> None:
        self._set_gps(
            build_gps_state_payload(location),
            timeout=3,
            metadata=self._metadata,
        )

    def close(self) -> None:
        self._channel.close()


def parse_log(path: Path) -> tuple[ReplayTimeline, list[dict[str, object]]]:
    locations: list[dict[str, object]] = []
    replayable_count = 0
    complete_monotonic_clock = True
    complete_wall_clock = True
    minimum_monotonic: int | None = None
    maximum_monotonic: int | None = None
    minimum_wall: int | None = None
    maximum_wall: int | None = None

    with path.open(encoding="utf-8") as source:
        for line in source:
            try:
                event = json.loads(line)
            except (json.JSONDecodeError, TypeError):
                continue

            source_type = event.get("source")
            provider_key = event.get("key")
            replayable = source_type == "AIDL_CALLBACK" or (
                source_type in {"SYSVAR_INITIAL", "SYSVAR_CHANGE"}
                and provider_key in RELEVANT_PROVIDER_KEYS
            ) or source_type == "GPS_LOCATION"
            if not replayable:
                continue
            replayable_count += 1

            monotonic_tick = event.get("elapsed_realtime_nanos")
            if isinstance(monotonic_tick, int):
                minimum_monotonic = (
                    monotonic_tick
                    if minimum_monotonic is None
                    else min(minimum_monotonic, monotonic_tick)
                )
                maximum_monotonic = (
                    monotonic_tick
                    if maximum_monotonic is None
                    else max(maximum_monotonic, monotonic_tick)
                )
            else:
                complete_monotonic_clock = False

            wall_tick = event.get("timestamp")
            if isinstance(wall_tick, int):
                minimum_wall = wall_tick if minimum_wall is None else min(minimum_wall, wall_tick)
                maximum_wall = wall_tick if maximum_wall is None else max(maximum_wall, wall_tick)
            else:
                complete_wall_clock = False

            if source_type == "GPS_LOCATION":
                latitude = event.get("latitude")
                longitude = event.get("longitude")
                if isinstance(latitude, (int, float)) and isinstance(longitude, (int, float)):
                    locations.append(event)

    if replayable_count == 0:
        raise ValueError("El log no contiene eventos reproducibles")
    if not locations:
        raise ValueError("El log no contiene posiciones GPS")
    if complete_monotonic_clock:
        clock_key, ticks_per_second = "elapsed_realtime_nanos", 1_000_000_000
        start_tick, end_tick = minimum_monotonic, maximum_monotonic
    elif complete_wall_clock:
        clock_key, ticks_per_second = "timestamp", 1_000
        start_tick, end_tick = minimum_wall, maximum_wall
    else:
        raise ValueError("El log no contiene una cronología completa")
    if start_tick is None or end_tick is None:
        raise ValueError(f"El log no contiene una cronología completa en {clock_key}")
    return ReplayTimeline(
        clock_key,
        ticks_per_second,
        start_tick,
        end_tick,
    ), locations


def replay(
    client: EmulatorGpsClient,
    timeline: ReplayTimeline,
    locations: list[dict[str, object]],
) -> None:
    started = time.monotonic()
    for location in locations:
        target = timeline.delay_seconds(location)
        delay = target - (time.monotonic() - started)
        if delay > 0:
            time.sleep(delay)
        client.send(location)
    remaining = timeline.duration_seconds - (time.monotonic() - started)
    if remaining > 0:
        time.sleep(remaining)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--loop", action="store_true")
    parser.add_argument("--loop-pause", type=float, default=2.0)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    timeline, locations = parse_log(args.log)
    if args.check:
        print(
            f"GPS validado: {len(locations)} posiciones · "
            f"duración {timeline.duration_seconds:.3f}s · reloj {timeline.clock_key}"
        )
        return
    endpoint = resolve_grpc_endpoint(args.serial)
    client = EmulatorGpsClient(endpoint)
    try:
        # Sitúa el mapa en la primera posición real desde el primer frame. El
        # mismo punto vuelve a emitirse después en su instante del recorrido.
        client.send(locations[0])
        while True:
            replay(client, timeline, locations)
            if not args.loop:
                return
            time.sleep(max(0.0, args.loop_pause))
    finally:
        client.close()


if __name__ == "__main__":
    main()
