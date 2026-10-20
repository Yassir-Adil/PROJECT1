"""Independent network-budget and type regressions; no actual requests."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hypersim_transport import RangeReader

URL = "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip"


class TransportReviewRegressions(unittest.TestCase):
    def test_failed_connection_attempt_counts_against_request_budget(self):
        calls = []
        def fail(request, *, timeout):
            calls.append(request)
            raise OSError("simulated connection failure")
        with RangeReader(URL, 10, '"pin"', opener=fail, max_requests=1) as reader:
            with self.assertRaises(OSError):
                reader.read(1)
            self.assertEqual(1, reader.request_count)
            with self.assertRaises(OSError):
                reader.read(1)
            self.assertEqual(1, len(calls))
            self.assertEqual(0, reader.tell())

    def test_float_minus_one_is_not_a_valid_integer_read_size(self):
        def forbidden(*args, **kwargs):
            raise AssertionError("invalid read type reached the network")
        with RangeReader(URL, 10, '"pin"', opener=forbidden) as reader:
            with self.assertRaises(TypeError):
                reader.read(-1.0)


if __name__ == "__main__":
    unittest.main()
