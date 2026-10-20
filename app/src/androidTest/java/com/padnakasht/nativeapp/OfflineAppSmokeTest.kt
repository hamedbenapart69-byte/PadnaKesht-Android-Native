package com.padnakasht.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class OfflineAppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("Emulator screenshot capture failed: $name")
        val directory = composeRule.activity.getExternalFilesDir(null)
            ?: throw AssertionError("External files directory unavailable")
        FileOutputStream(File(directory, "padnakasht-$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not save screenshot: $name" }
        }
        bitmap.recycle()
    }

    @Test
    fun appStartsAuthenticatesAndNavigatesPrimarySections() {
        composeRule.onNodeWithText("امنیت پادناکِشت").assertIsDisplayed()
        composeRule.onNodeWithTag("pin-entry").performTextInput("123456")
        composeRule.onNodeWithTag("pin-confirmation").performTextInput("123456")
        composeRule.onNodeWithText("ایجاد PIN و ادامه").performClick()
        composeRule.onNodeWithText("نمای کلی").assertIsDisplayed()
        composeRule.onNodeWithText("پادناکِشت").assertIsDisplayed()
        capture("dashboard")

        composeRule.onNodeWithText("فروش و انبار").performClick()
        composeRule.onNodeWithText("فروش، مشتریان و کالاها").assertIsDisplayed()
        capture("business")

        composeRule.onNodeWithText("گزارش‌ها").performClick()
        composeRule.onNodeWithText("گزارش و تحلیل").assertIsDisplayed()
        capture("reports")
    }
}
