package com.padnakasht.nativeapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class OfflineAppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val resolver = composeRule.activity.contentResolver
        val relativePath = Environment.DIRECTORY_PICTURES + "/PadnaKeshtCI/"
        // Delete any prior run's image so adb pull cannot accidentally return stale UI.
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("padnakasht-$name.png", relativePath)
        )
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("Emulator screenshot capture failed: $name")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "padnakasht-$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
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

    // PIN hashing runs off the UI thread, so Compose idling does not cover it.
    private fun awaitDisplayed(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 30_000) {
                runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess
            }
            composeRule.waitForIdle()
        } catch (e: Throwable) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("SMOKE_TREE")
            throw AssertionError("Text was not displayed in time: $text", e)
        }
    }

    @Test
    fun appStartsAuthenticatesAndCapturesPrimarySections() {
        awaitDisplayed("امنیت پادناکِشت")
        composeRule.onNodeWithTag("pin-entry").performTextInput("123456")
        composeRule.onNodeWithTag("pin-confirmation").performTextInput("123456")
        composeRule.onNodeWithText("ایجاد PIN و ادامه").performClick()

        // Wait for a visible dashboard-only heading, not a tab label that can exist
        // behind an overlay or be matched while authentication is still processing.
        awaitDisplayed("خلاصه مالی")
        composeRule.onNodeWithText("ایجاد PIN و ادامه").assertDoesNotExist()
        awaitDisplayed("پادناکِشت")
        capture("dashboard")

        composeRule.onNodeWithText("فروش و انبار").performClick()
        awaitDisplayed("فروش، مشتریان و کالاها")
        composeRule.onNodeWithText("ثبت مشتری").performScrollTo()
        awaitDisplayed("ثبت مشتری")
        capture("customers")

        composeRule.onNodeWithText("ثبت نهایی فاکتور").performScrollTo()
        awaitDisplayed("ثبت نهایی فاکتور")
        capture("invoice")

        composeRule.onNodeWithText("گزارش‌ها").performClick()
        awaitDisplayed("گزارش و تحلیل")
        awaitDisplayed("خروجی گزارش دفتر مالی")
        capture("reports")
    }
}
