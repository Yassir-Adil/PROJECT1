"""Source wiring guards, not SDK execution, sensor association or a phone benchmark."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
DEBUG = ROOT / "android-app/app/src/debug"
SOURCES = DEBUG / "java/com/triguide/ai/diagnostics"


class SemanticRgbContractTest(unittest.TestCase):
    def test_all_gl_fatal_paths_use_the_same_immediate_fence(self):
        source = (SOURCES / "RawDepthDiagnosticActivity.kt").read_text(encoding="utf-8")
        created = source.split("override fun onSurfaceCreated",1)[1].split("override fun onSurfaceChanged",1)[0]
        drawn = source.split("override fun onDrawFrame",1)[1].split("private fun maybeSampleSurfaces",1)[0]
        self.assertTrue("runOnUiThread" not in created,"GL initialization must fence before posting stop")
        self.assertTrue("runOnUiThread" not in drawn,"GL camera/frame errors must fence before posting stop")
        self.assertIn('failFrame("background_init_',created)
        self.assertIn('failFrame("camera_unavailable_runtime"',drawn)
        self.assertIn('failFrame("gl_frame_',drawn)
    def test_fatal_processing_fences_work_before_asynchronous_ui_stop(self):
        source = (SOURCES / "RawDepthDiagnosticActivity.kt").read_text(encoding="utf-8")
        self.assertTrue("private fun failFrame(" in source,"Missing immediate fatal-processing fence")
        helper = source.split("private fun failFrame(",1)[1].split("private fun requestStop",1)[0]
        self.assertLess(helper.index("frameFailurePending.compareAndSet"),helper.index("semanticDiagnostic?.stop()"))
        self.assertLess(helper.index("semanticDiagnostic?.stop()"),helper.index("runOnUiThread"))
        body = source.split("private fun processFrame(frame: Frame)",1)[1].split("private fun sampleDenseDepth",1)[0]
        self.assertIn("!frameFailurePending.get() && semanticRgbFallbackEnabled",body)
        self.assertIn('failFrame("depth_process_',body)
    def test_fallback_is_explicit_opt_in_after_current_raw_opportunity(self):
        source = (SOURCES / "RawDepthDiagnosticActivity.kt").read_text(encoding="utf-8")
        self.assertIn("getBooleanExtra(EXTRA_SEMANTIC_RGB_FALLBACK, false)",source)
        body = source.split("private fun processFrame(frame: Frame)",1)[1].split("private fun sampleDenseDepth",1)[0]
        self.assertGreater(body.index("captureRgbOnly(frame)"),body.index("RawDepthPairScope.withFrame"))
        self.assertTrue("!stopping.get() && !frameFailurePending.get() && semanticRgbFallbackEnabled" in body,
                        "Fallback must check both terminal stop and immediate failure fence")

    def test_rgb_path_uses_sdk_view_to_image_not_old_depth_or_second_camera_owner(self):
        source = (SOURCES / "ArCoreSemanticDiagnostic.kt").read_text(encoding="utf-8")
        self.assertEqual(1,source.count("frame.acquireCameraImage()"))
        body = source.split("private fun sampleRgbPoints",1)[1].split("private fun samplePoints",1)[0]
        self.assertIn("Coordinates2d.VIEW_NORMALIZED",body)
        self.assertIn("Coordinates2d.IMAGE_NORMALIZED",body)
        self.assertNotIn("TEXTURE_NORMALIZED",body)
        self.assertNotIn("SemanticGroundDiagnostic",body)

    def test_synthetic_lab_copy_is_in_debug_string_resources_not_concatenated_literals(self):
        strings = ET.parse(DEBUG / "res/values/semantic_diagnostics.xml").getroot()
        values = {item.get("name"):item.text for item in strings.findall("string")}
        self.assertIn("synthetic_render_disclaimer",values)
        self.assertIn("synthetic_render_stop",values)
        self.assertIn("ليست كاميرا",values["synthetic_render_disclaimer"])
        source = (SOURCES / "SemanticRenderDiagnosticActivity.kt").read_text(encoding="utf-8")
        self.assertIn("R.string.synthetic_render_disclaimer",source)
        self.assertIn("R.string.synthetic_render_stop",source)


if __name__ == "__main__": unittest.main()
