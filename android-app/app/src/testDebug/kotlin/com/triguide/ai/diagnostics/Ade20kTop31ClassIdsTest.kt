package com.triguide.ai.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ade20kTop31ClassIdsTest {
    @Test
    fun acceptsIgnoreAndEveryTop31ClassIdIncludingUpperBoundary() {
        assertTrue(Ade20kTop31ClassIds.isValid(Ade20kTop31ClassIds.IGNORE_OR_BACKGROUND_ID))
        assertTrue(Ade20kTop31ClassIds.isValid(1))
        assertTrue(Ade20kTop31ClassIds.isValid(Ade20kTop31ClassIds.FOREGROUND_CLASS_COUNT))
        (1..Ade20kTop31ClassIds.FOREGROUND_CLASS_COUNT).forEach { classId ->
            assertFalse(Ade20kTop31ClassIds.labelName(classId) == "invalid_id")
        }
        assertTrue(Ade20kTop31ClassIds.labelName(1) == "wall")
        assertTrue(Ade20kTop31ClassIds.labelName(4) == "floor")
        assertTrue(Ade20kTop31ClassIds.labelName(31) == "armchair")
        assertTrue(Ade20kTop31ClassIds.labelName(0) == "ignore/background")
    }

    @Test
    fun rejectsIdsOutsideIgnoreAndTop31Range() {
        assertFalse(Ade20kTop31ClassIds.isValid(-1))
        assertFalse(Ade20kTop31ClassIds.isValid(Ade20kTop31ClassIds.FOREGROUND_CLASS_COUNT + 1))
        assertTrue(Ade20kTop31ClassIds.labelName(32) == "invalid_id")
    }
}
