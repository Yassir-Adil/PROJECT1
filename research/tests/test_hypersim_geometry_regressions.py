"""Independent numeric regressions identified during Codex source review."""
from pathlib import Path
import sys
import unittest
import warnings
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hypersim_geometry import unproject_range, residual_summary


class GeometryReviewRegressions(unittest.TestCase):
    def setUp(self):
        self.matrix = np.diag([1.0, 1.0, -1.0])

    def test_ray_matrix_positive_scale_does_not_change_geometry(self):
        expected = unproject_range(np.ones((1, 1)), self.matrix, np.eye(3), np.zeros(3), 1.0)
        for scale in (1e-8, 1e200):
            with self.subTest(scale=scale):
                scaled = unproject_range(np.ones((1, 1)), self.matrix * scale, np.eye(3), np.zeros(3), 1.0)
                np.testing.assert_allclose(expected[0], scaled[0], rtol=1e-12, atol=1e-12)

    def test_complex_inputs_rejected_not_cast_to_real(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            with self.assertRaises(ValueError):
                unproject_range(np.array([[1 + 2j]]), self.matrix, np.eye(3), np.zeros(3), 1.0)
            with self.assertRaises(ValueError):
                residual_summary(np.ones((1, 1, 3), dtype=complex), np.ones((1, 1, 3)), 1, np.ones((1, 1), dtype=int))

    def test_boolean_metric_scale_rejected(self):
        for scale in (True, np.bool_(True)):
            with self.subTest(scale=scale), self.assertRaises(ValueError):
                unproject_range(np.ones((1, 1)), self.matrix, np.eye(3), np.zeros(3), scale)

    def test_unrepresentable_finite_point_residual_fails_explicitly(self):
        # Do not publish Infinity/NaN, silently exclude a huge error, or claim zero.
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            with self.assertRaises(ValueError):
                residual_summary(np.full((1, 1, 3), 1e308), np.full((1, 1, 3), -1e308),
                                 1.0, np.ones((1, 1), dtype=int))


if __name__ == "__main__":
    unittest.main()
