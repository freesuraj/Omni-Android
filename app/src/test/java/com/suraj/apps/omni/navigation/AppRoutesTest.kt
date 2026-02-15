package com.suraj.apps.omni.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {
    @Test
    fun routes_are_non_empty_and_unique() {
        val routes = listOf(
            AppRoutes.LIBRARY,
            AppRoutes.AUDIO,
            AppRoutes.SETTINGS,
            AppRoutes.DASHBOARD,
            AppRoutes.QUIZ,
            AppRoutes.NOTES,
            AppRoutes.SUMMARY,
            AppRoutes.QA,
            AppRoutes.ANALYSIS,
            AppRoutes.PAYWALL
        )

        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.toSet().size)
    }
}
