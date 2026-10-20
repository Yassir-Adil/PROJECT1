"""Fixture whitelist, extraction bounds and byte-provenance contracts. No network."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hypersim_fixture import choose_members, extract_selected, verify_fixture

SCENE = "ai_003_010"
CAMERA = "cam_00"
NAMES = [
    f"{SCENE}/_detail/metadata_scene.csv",
    f"{SCENE}/_detail/{CAMERA}/camera_keyframe_positions.hdf5",
    f"{SCENE}/_detail/{CAMERA}/camera_keyframe_orientations.hdf5",
    f"{SCENE}/_detail/{CAMERA}/camera_keyframe_frame_indices.hdf5",
    *[f"{SCENE}/images/scene_{CAMERA}_geometry_hdf5/frame.0000.{kind}.hdf5"
      for kind in ("depth_meters", "position", "semantic")],
]


class HypersimFixtureTest(unittest.TestCase):
    def archive(self, names=NAMES):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in names:
                archive.writestr(name, b"public fixture bytes")
        stream.seek(0)
        return zipfile.ZipFile(stream)

    def test_exact_required_members_only_sorted(self):
        with self.archive([*NAMES, f"{SCENE}/images/private-looking-unused.bin"]) as archive:
            result = choose_members(archive.infolist(), SCENE, CAMERA, [0])
            self.assertEqual(sorted(NAMES), [member.filename for member in result])

    def test_missing_duplicate_or_bad_spec_rejected(self):
        with self.archive(NAMES[:-1]) as archive:
            with self.assertRaises(ValueError):
                choose_members(archive.infolist(), SCENE, CAMERA, [0])
        with self.archive() as archive:
            infos = archive.infolist()
            with self.assertRaises(ValueError):
                choose_members([*infos, infos[0]], SCENE, CAMERA, [0])
            for scene, camera, frames in [("../scene", CAMERA, [0]), (SCENE, "../cam", [0]),
                                          (SCENE, CAMERA, []), (SCENE, CAMERA, [-1]),
                                          (SCENE, CAMERA, [True]), (SCENE, CAMERA, [0, 0])]:
                with self.subTest(scene=scene, camera=camera, frames=frames), self.assertRaises(ValueError):
                    choose_members(infos, scene, camera, frames)

    def test_size_and_compression_bounds_before_extract(self):
        with self.archive() as archive:
            infos = archive.infolist()
            infos[0].file_size = 33 * 1024 * 1024
            with self.assertRaises(ValueError):
                choose_members(infos, SCENE, CAMERA, [0])
        with self.archive() as archive:
            infos = archive.infolist()
            infos[0].external_attr = (0o120777 << 16)  # symlink, not regular bytes
            with self.assertRaises(ValueError):
                choose_members(infos, SCENE, CAMERA, [0])

    def test_extract_hashes_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary, self.archive() as archive:
            root = Path(temporary)
            selected = choose_members(archive.infolist(), SCENE, CAMERA, [0])
            records = extract_selected(archive, selected, root)
            self.assertEqual(7, len(records))
            for record in records:
                self.assertEqual(b"public fixture bytes", (root / record["path"]).read_bytes())
                self.assertEqual(hashlib.sha256(b"public fixture bytes").hexdigest(), record["sha256"])
                self.assertEqual(20, record["bytes"])
            with self.assertRaises(FileExistsError):
                extract_selected(archive, selected, root)

    def test_unsafe_extraction_member_never_escapes(self):
        for name in ("../escape", "/rooted", "C:/escape", "x\\escape", "x/../escape", "x:stream"):
            # ZipInfo normalizes backslashes on Windows when writing an archive;
            # mutate the raw member metadata to exercise the extractor's own gate.
            with tempfile.TemporaryDirectory() as temporary, self.archive(["placeholder"]) as archive:
                infos = archive.infolist()
                infos[0].filename = name
                with self.subTest(name=name), self.assertRaises(ValueError):
                    extract_selected(archive, infos, Path(temporary))

    def test_verify_all_hashes_not_just_manifest_presence(self):
        with tempfile.TemporaryDirectory() as temporary, self.archive() as archive:
            root = Path(temporary)
            records = extract_selected(archive, choose_members(archive.infolist(), SCENE, CAMERA, [0]), root)
            manifest = {"schema_version": 1, "status": "complete", "files": records}
            verify_fixture(root, manifest)
            (root / records[0]["path"]).write_bytes(b"modified")
            with self.assertRaises(ValueError):
                verify_fixture(root, manifest)
            for bad in ({"schema_version": 2, "status": "complete", "files": []},
                        {"schema_version": 1, "status": "incomplete", "files": records},
                        {"schema_version": 1, "status": "complete", "files": []}):
                with self.assertRaises(ValueError):
                    verify_fixture(root, bad)

    def test_manifest_path_and_duplicate_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            for path in ("../bad", "C:/bad", "/bad", "bad\\file"):
                manifest = {"schema_version": 1, "status": "complete", "files": [
                    {"path": path, "sha256": "0" * 64, "bytes": 1}]}
                with self.subTest(path=path), self.assertRaises(ValueError):
                    verify_fixture(Path(temporary), manifest)


if __name__ == "__main__":
    unittest.main()
