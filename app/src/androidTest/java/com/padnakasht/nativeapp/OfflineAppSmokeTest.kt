package com.padnakasht.nativeapp

import android.content.ContentValues
import android.view.KeyEvent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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

    private fun hideKeyboard() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun awaitExists(text: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 30_000) {
                runCatching { composeRule.onNodeWithText(text).fetchSemanticsNode() }.isSuccess
            }
        } catch (e: Throwable) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("SMOKE_TREE")
            throw AssertionError("Text did not appear in time: $text", e)
        }
    }

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

    private fun awaitVisibleByScrolling(text: String, substring: Boolean = false) {
        // A LazyColumn item may not exist in the semantics tree until its parent
        // list has been scrolled into range. Do not wait 30 seconds for an
        // off-screen node before attempting to scroll the parent.
        val target = composeRule.onNodeWithText(text, substring = substring)
        val mainList = composeRule.onNodeWithTag("main-content-list")
        var found = runCatching { target.fetchSemanticsNode() }.isSuccess
        if (!found) {
            for (index in 0..12) {
                runCatching { mainList.performScrollToIndex(index) }
                composeRule.waitForIdle()
                found = runCatching { target.fetchSemanticsNode() }.isSuccess
                if (found) break
            }
        }
        if (!found) {
            composeRule.onRoot(useUnmergedTree = true).printToLog("SMOKE_TREE")
            throw AssertionError("Text never entered the semantics tree after scrolling parent list: $text")
        }
        target.performScrollTo()
        target.assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun scrollToTop() {
        composeRule.onNodeWithTag("main-content-list").performScrollToIndex(0)
        composeRule.waitForIdle()
    }

    private fun awaitSecurityGateGone() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onNodeWithText("امنیت پادناکِشت").assertIsDisplayed()
            }.isFailure
        }
        composeRule.waitForIdle()
    }

    @Test
    fun appStartsAuthenticatesAndCapturesPrimarySections() {
        awaitDisplayed("امنیت پادناکِشت")
        composeRule.onNodeWithTag("pin-entry").performTextInput("123456")
        composeRule.onNodeWithTag("pin-confirmation").performTextInput("123456")
        composeRule.onNodeWithText("ایجاد PIN و ادامه").performClick()

        // The submit button changes its label while hashing the PIN. Wait for the
        // security screen itself to disappear, not merely for that button label.
        awaitSecurityGateGone()
        awaitDisplayed("خلاصه مالی")
        awaitDisplayed("پادناکِشت")
        capture("dashboard")
        // The quick-entry panel is below the fold on a 1080x1920 emulator;
        // assert visibility only after bringing the lazy-list item into view.
        awaitVisibleByScrolling("ثبت سریع تراکنش")
        capture("quick-transaction")

        // Primary navigation is persistent; it is intentionally outside the scrolling content.
        composeRule.onNodeWithText("فروش و انبار").performClick()
        awaitVisibleByScrolling("فروش، مشتریان و کالاها")
        composeRule.onNodeWithText("نام مشتری").performTextInput("مشتری آزمایشی")
        hideKeyboard()
        composeRule.onNodeWithTag("register-customer").performScrollTo()
        composeRule.onNodeWithTag("register-customer").performClick()
        // A snackbar is transient and may disappear before the test observes it.
        // Verify the durable result in the customer list instead of timing the toast.
        awaitVisibleByScrolling("مشتری آزمایشی", substring = true)
        capture("customers")

        composeRule.onNodeWithText("کالاها").performScrollTo()
        composeRule.onNodeWithText("کالاها").performClick()
        composeRule.onNodeWithText("نام محصول").performTextInput("محصول آزمایشی")
        composeRule.onNodeWithText("کد یکتای محصول").performTextInput("CI-TEST-001")
        composeRule.onNodeWithText("موجودی اولیه").performTextInput("10")
        composeRule.onNodeWithText("قیمت واحد (تومان)").performTextInput("100000")
        hideKeyboard()
        composeRule.onNodeWithTag("register-product").performScrollTo()
        composeRule.onNodeWithTag("register-product").performClick()
        // Success notices are transient and are not list items; assert the persisted product below.
        awaitExists("محصول ثبت شد.")
        awaitVisibleByScrolling("محصول آزمایشی", substring = true)
        capture("inventory")

        composeRule.onNodeWithText("فاکتورها").performScrollTo()
        composeRule.onNodeWithText("فاکتورها").performClick()
        awaitDisplayed("صدور فاکتور")
        composeRule.onNodeWithTag("select-invoice-customer").performScrollTo()
        composeRule.onNodeWithTag("select-invoice-customer").performClick()
        composeRule.onNodeWithText("مشتری آزمایشی").performClick()
        composeRule.onNodeWithTag("select-invoice-product").performScrollTo()
        composeRule.onNodeWithTag("select-invoice-product").performClick()
        composeRule.onNodeWithText("محصول آزمایشی", substring = true).performClick()
        composeRule.onNodeWithText("افزودن قلم به فاکتور").performScrollTo()
        composeRule.onNodeWithText("افزودن قلم به فاکتور").performClick()
        awaitVisibleByScrolling("قلم به سبد فاکتور اضافه شد.")
        composeRule.onNodeWithText("صدور فاکتور").performScrollTo()
        capture("invoice")
        composeRule.onNodeWithText("ثبت نهایی فاکتور").performScrollTo()
        awaitDisplayed("ثبت نهایی فاکتور")
        composeRule.onNodeWithText("ثبت نهایی فاکتور").performClick()
        // Confirm the write operation, then capture the real post-submission invoice screen.
        // The invoice row can be outside the composed range of the nested business section;
        // the reports screen below independently verifies the persisted 100,000 toman total.
        awaitExists("فاکتور ثبت شد و موجودی به‌روزرسانی شد.")
        capture("invoice-summary")

        composeRule.onNodeWithText("گزارش‌ها").performClick()
        awaitDisplayed("گزارش و تحلیل")
        awaitDisplayed("خلاصه فروش فاکتوری")
        awaitDisplayed("جمع مبلغ فاکتورهای قطعی: ۱۰۰٬۰۰۰ تومان")
        capture("reports")
        // LazyColumn composes off-screen rows on demand; move to the export card first.
        composeRule.onNodeWithTag("main-content-list").performScrollToIndex(5)
        awaitVisibleByScrolling("گزارش کامل فروش و جریان نقدی")
    }
}
