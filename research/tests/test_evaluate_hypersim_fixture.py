"""Bounded local HDF5 input contract; no network or physical-device claims."""
from pathlib import Path
import sys
import tempfile
import unittest

import numpy as np
import h5py

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from evaluate_hypersim_fixture import read_hdf5_array, parse_dimension


class Hdf5InputTest(unittest.TestCase):
    def test_official_csv_dimensions_accept_integer_float_not_fractional(self):
        self.assertEqual(768, parse_dimension("768.0"))
        self.assertEqual(1024, parse_dimension("1024"))
        for value in ("768.5", "nan", "inf", "0", "-1", True, "not-a-number"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_dimension(value)

    def test_valid_numeric_and_nan_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "data.hdf5"
            with h5py.File(path, "w") as file:
                file.create_dataset("dataset", data=np.array([[0.0, np.nan, 2.0]], dtype=np.float16))
            values = read_hdf5_array(path)
            self.assertEqual((1, 3), values.shape)
            self.assertTrue(np.isnan(values[0, 1]))
            self.assertEqual(0, values[0, 0])

    def test_missing_string_bool_and_huge_dataset_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            for kind in ("missing", "string", "bool", "huge", "area", "scalar"):
                path = Path(temporary) / f"{kind}.hdf5"
                with h5py.File(path, "w") as file:
                    if kind == "missing":
                        file.create_dataset("other", data=[1])
                    elif kind == "huge":
                        file.create_dataset("dataset", shape=(10_000_000, 3), chunks=True, dtype="f8")
                    elif kind == "area":
                        file.create_dataset("dataset", shape=(1025, 1024), chunks=True, dtype="f4")
                    elif kind == "scalar":
                        file.create_dataset("dataset", data=1.0)
                    else:
                        file.create_dataset("dataset", data=[b"not numbers"] if kind == "string" else [True])
                with self.subTest(kind=kind), self.assertRaises(ValueError):
                    read_hdf5_array(path)

    def test_external_link_refused_without_following(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "link.hdf5"
            with h5py.File(path, "w") as file:
                file["dataset"] = h5py.ExternalLink("never-open-this.hdf5", "dataset")
            with self.assertRaises(ValueError):
                read_hdf5_array(path)

    def test_external_storage_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "external.hdf5"
            with h5py.File(path, "w") as file:
                file.create_dataset("dataset", shape=(1, 3), dtype="f8", external=[("unused.raw", 0, 24)])
            with self.assertRaises(ValueError):
                read_hdf5_array(path)

    def test_virtual_dataset_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "virtual.hdf5"
            layout = h5py.VirtualLayout(shape=(1, 3), dtype="f4")
            layout[:] = h5py.VirtualSource("never-open-this.hdf5", "dataset", shape=(1, 3))
            with h5py.File(path, "w") as file:
                file.create_virtual_dataset("dataset", layout)
            with self.assertRaises(ValueError):
                read_hdf5_array(path)


if __name__ == "__main__":
    unittest.main()
