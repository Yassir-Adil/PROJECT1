"""Source contract, not an Android measurement or proof of visible overlays."""
from pathlib import Path
import re
import unittest


class DiagnosticViewportLayoutTest(unittest.TestCase):
    def test_changing_status_text_reserves_fixed_lines_instead_of_resizing_camera(self):
        root = Path(__file__).resolve().parents[3]
        source = (root / "android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthDiagnosticActivity.kt").read_text(encoding="utf-8")
        for name, budget in (("statusView", 6), ("surfaceStatusView", 5), ("semanticStatusView", 4)):
            with self.subTest(view=name):
                body = re.search(rf"\b{name}\s*=\s*TextView\(this\)\.apply\s*\{{(.*?)\n\s*\}}", source, re.S)
                self.assertIsNotNone(body, f"Missing reviewed status view: {name}")
                self.assertRegex(body[1], rf"\bsetLines\({budget}\)", "Dynamic text must not resize the ARCore viewport")


if __name__ == "__main__":
    unittest.main()
