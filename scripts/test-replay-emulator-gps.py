#!/usr/bin/env python3
"""Pruebas aisladas del transporte GPS usado por el replay del emulador."""

from __future__ import annotations

import importlib.util
import json
import math
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


SCRIPT_PATH = Path(__file__).with_name("replay-emulator-gps.py")
SPEC = importlib.util.spec_from_file_location("replay_emulator_gps", SCRIPT_PATH)
assert SPEC and SPEC.loader
REPLAY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPLAY)


def decode_fixed64_fields(payload: bytes) -> dict[int, float]:
    fields: dict[int, float] = {}
    offset = 0
    while offset < len(payload):
        tag = payload[offset]
        offset += 1
        field_number = tag >> 3
        wire_type = tag & 0x07
        if wire_type == 1:
            fields[field_number] = struct.unpack_from("<d", payload, offset)[0]
            offset += 8
        elif wire_type == 0:
            offset += 1
        else:
            raise AssertionError(f"Wire type inesperado: {wire_type}")
    return fields


class ReplayEmulatorGpsTest(unittest.TestCase):
    def test_gps_timing_preserves_a_can_only_prefix(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "replay.jsonl"
            events = [
                {"timestamp": 1_000, "source": "AIDL_CALLBACK"},
                {
                    "timestamp": 4_000,
                    "source": "GPS_LOCATION",
                    "latitude": 42.0,
                    "longitude": -7.0,
                },
            ]
            log.write_text(
                "".join(json.dumps(event) + "\n" for event in events),
                encoding="utf-8",
            )

            timeline, locations = REPLAY.parse_log(log)

        self.assertEqual(timeline.clock_key, "timestamp")
        self.assertEqual(timeline.start_tick, 1_000)
        self.assertEqual(timeline.end_tick, 4_000)
        self.assertEqual(
            timeline.delay_seconds(locations[0]),
            3.0,
        )

    def test_gps_timing_prefers_monotonic_clock_during_wall_clock_correction(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "replay.jsonl"
            events = [
                {
                    "timestamp": 10_000,
                    "elapsed_realtime_nanos": 1_000_000_000,
                    "source": "AIDL_CALLBACK",
                },
                {
                    "timestamp": 40_000,
                    "elapsed_realtime_nanos": 2_000_000_000,
                    "source": "GPS_LOCATION",
                    "latitude": 42.0,
                    "longitude": -7.0,
                },
                {
                    "timestamp": 20_000,
                    "elapsed_realtime_nanos": 3_000_000_000,
                    "source": "GPS_LOCATION",
                    "latitude": 42.1,
                    "longitude": -7.1,
                },
            ]
            log.write_text(
                "".join(json.dumps(event) + "\n" for event in events),
                encoding="utf-8",
            )

            timeline, locations = REPLAY.parse_log(log)

        self.assertEqual(timeline.clock_key, "elapsed_realtime_nanos")
        self.assertEqual(timeline.duration_seconds, 2.0)
        self.assertEqual(timeline.delay_seconds(locations[0]), 1.0)
        self.assertEqual(timeline.delay_seconds(locations[1]), 2.0)

    def test_mixed_timing_fields_fall_back_to_one_legacy_timeline(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "replay.jsonl"
            events = [
                {
                    "timestamp": 1_000,
                    "elapsed_realtime_nanos": 1_000_000_000,
                    "source": "AIDL_CALLBACK",
                },
                {
                    "timestamp": 2_500,
                    "source": "GPS_LOCATION",
                    "latitude": 42.0,
                    "longitude": -7.0,
                },
            ]
            log.write_text(
                "".join(json.dumps(event) + "\n" for event in events),
                encoding="utf-8",
            )

            timeline, locations = REPLAY.parse_log(log)

        self.assertEqual(timeline.clock_key, "timestamp")
        self.assertEqual(timeline.delay_seconds(locations[0]), 1.5)

    def test_gps_payload_preserves_recorded_speed_and_bearing(self) -> None:
        location = {
            "latitude": 42.31480239,
            "longitude": -7.882999,
            "altitude_m": 197.7,
            "speed_mps": 2.8,
            "bearing_degrees": 254.9,
        }

        fields = decode_fixed64_fields(REPLAY.build_gps_state_payload(location))

        self.assertTrue(math.isclose(fields[2], location["latitude"]))
        self.assertTrue(math.isclose(fields[3], location["longitude"]))
        self.assertTrue(
            math.isclose(
                fields[4],
                location["speed_mps"] * REPLAY.METRES_PER_SECOND_TO_KNOTS,
            )
        )
        self.assertTrue(math.isclose(fields[5], location["bearing_degrees"]))
        self.assertTrue(math.isclose(fields[6], location["altitude_m"]))

    def test_endpoint_is_resolved_for_the_requested_emulator_serial(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            running = Path(temporary)
            (running / "pid_101.ini").write_text(
                "port.serial=5556\ngrpc.port=8556\ngrpc.token=wrong\n",
                encoding="utf-8",
            )
            (running / "pid_202.ini").write_text(
                "port.serial=5554\ngrpc.port=8554\ngrpc.token=secret\n",
                encoding="utf-8",
            )

            endpoint = REPLAY.resolve_grpc_endpoint("emulator-5554", running)

        self.assertEqual(endpoint.address, "127.0.0.1:8554")
        self.assertEqual(endpoint.token, "secret")

    def test_endpoint_resolution_ignores_a_runtime_file_removed_mid_scan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            running = Path(temporary)
            removed = running / "pid_101.ini"
            removed.write_text("port.serial=5556\n", encoding="utf-8")
            valid = running / "pid_202.ini"
            valid.write_text(
                "port.serial=5554\ngrpc.port=8554\ngrpc.token=secret\n",
                encoding="utf-8",
            )
            original_reader = REPLAY.read_properties

            def read_with_disappearing_file(path: Path) -> dict[str, str]:
                if path == removed:
                    raise FileNotFoundError(path)
                return original_reader(path)

            with patch.object(REPLAY, "read_properties", read_with_disappearing_file):
                endpoint = REPLAY.resolve_grpc_endpoint("emulator-5554", running)

        self.assertEqual(endpoint.address, "127.0.0.1:8554")

    def test_client_sends_bearer_token_and_complete_gps_payload(self) -> None:
        rpc = Mock(return_value=b"")
        channel = Mock()
        channel.unary_unary.return_value = rpc
        fake_grpc = Mock()
        fake_grpc.insecure_channel.return_value = channel
        endpoint = REPLAY.GrpcEndpoint("127.0.0.1:8554", "secret")
        location = {
            "latitude": 42.31480239,
            "longitude": -7.882999,
            "speed_mps": 2.8,
            "bearing_degrees": 254.9,
        }

        with patch.dict("sys.modules", {"grpc": fake_grpc}):
            client = REPLAY.EmulatorGpsClient(endpoint)
            client.send(location)
            client.close()

        fake_grpc.insecure_channel.assert_called_once_with(endpoint.address)
        payload = rpc.call_args.args[0]
        fields = decode_fixed64_fields(payload)
        self.assertTrue(math.isclose(fields[5], 254.9))
        self.assertEqual(
            rpc.call_args.kwargs["metadata"],
            (("authorization", "Bearer secret"),),
        )
        channel.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
