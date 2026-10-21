package com.padnakasht.nativeapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class OfflineAppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("Emulator screenshot capture failed: $name")
        val resolver = composeRule.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "padnakasht-$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PadnaKeshtCI")
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Could not create public screenshot entry: $name")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Could not encode screenshot: $name"
                }
            } ?: throw AssertionError("Could not open screenshot output: $name")
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            bitmap.recycle()
        }
    }

    // PIN hashing runs off the UI thread, so Compose idling does not cover it:
    // wait for the node and dump the semantics tree to logcat if it never shows.
    private fun awaitDisplayed(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 20_000) {
                runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess
            }
        } catch (e: Throwable) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("SMOKE_TREE")
            throw AssertionError("Text was not displayed in time: $text", e)
        }
    }

    @Test
    fun appStartsAuthenticatesAndNavigatesPrimarySections() {
        awaitDisplayed("امنیت پادناکِشت")
        composeRule.onNodeWithTag("pin-entry").performTextInput("123456")
        composeRule.onNodeWithTag("pin-confirmation").performTextInput("123456")
        composeRule.onNodeWithText("ایجاد PIN و ادامه").performClick()
        awaitDisplayed("نمای کلی")
        awaitDisplayed("پادناکِشت")
        capture("dashboard")

        composeRule.onNodeWithText("فروش و انبار").performClick()
        awaitDisplayed("فروش، مشتریان و کالاها")
        capture("business")

        composeRule.onNodeWithText("گزارش‌ها").performClick()
        awaitDisplayed("گزارش و تحلیل")
        capture("reports")
    }
}
