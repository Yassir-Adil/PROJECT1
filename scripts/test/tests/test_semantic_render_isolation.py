"""Source contracts only. They do not prove Android drawing or screen visibility."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[3]
SOURCES = ROOT / "android-app/app/src/debug/java/com/triguide/ai/diagnostics"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SemanticRenderIsolationTest(unittest.TestCase):
    def test_synthetic_entry_is_debug_only_and_shell_protected(self):
        relative = "android-app/app/src/"
        debug = ET.parse(ROOT / relative / "debug/AndroidManifest.xml").getroot()
        entries = [item for item in debug.findall("application/activity")
                   if item.get(ANDROID + "name", "").endswith(".SemanticRenderDiagnosticActivity")]
        self.assertEqual(1, len(entries))
        self.assertEqual("android.permission.DUMP", entries[0].get(ANDROID + "permission"))
        main = (ROOT / relative / "main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("SemanticRenderDiagnosticActivity", main)

    def test_synthetic_source_has_no_camera_model_geometry_or_speech_acquisition(self):
        source = (SOURCES / "SemanticRenderDiagnosticActivity.kt").read_text(encoding="utf-8")
        for forbidden in ("com.google.ar.core", "android.hardware.camera", "androidx.camera",
                          "DiagnosticSegmenter(", "SemanticDepthCell(", "SemanticGroundDiagnostic",
                          "TextToSpeech(", "FileOutputStream("):
            self.assertNotIn(forbidden, source)
        self.assertIn("SemanticPaintGrid.synthetic()", source)
        self.assertIn("handler.removeCallbacksAndMessages(null)", source)
        self.assertIn("session?.stop()", source)

    def test_live_expiry_callback_is_single_identity_checked_and_canceled_on_detach(self):
        source = (SOURCES / "SemanticFusionOverlayView.kt").read_text(encoding="utf-8")
        self.assertNotIn("postInvalidateDelayed(", source)
        self.assertIn("result === value", source)
        self.assertIn("removeCallbacks", source)
        self.assertIn("onDetachedFromWindow", source)

    def test_synthetic_pattern_retries_on_size_change_not_camera_updates(self):
        source = (SOURCES / "SemanticRenderDiagnosticActivity.kt").read_text(encoding="utf-8")
        self.assertIn("override fun onSizeChanged", source)
        self.assertIn("postInvalidateOnAnimation()", source)


if __name__ == "__main__":
    unittest.main()
