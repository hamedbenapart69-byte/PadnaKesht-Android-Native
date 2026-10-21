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
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching { composeRule.onNodeWithText(text, substring = substring).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()
        composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
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

        composeRule.onNodeWithText("فروش و انبار").performScrollTo()
        composeRule.onNodeWithText("فروش و انبار").performClick()
        awaitDisplayed("فروش، مشتریان و کالاها")
        composeRule.onNodeWithText("نام مشتری").performTextInput("مشتری آزمایشی")
        hideKeyboard()
        composeRule.onNodeWithTag("register-customer").performScrollTo()
        composeRule.onNodeWithTag("register-customer").performClick()
        awaitExists("مشتری ثبت شد.")
        awaitVisibleByScrolling("مشتری ثبت شد.")
        awaitVisibleByScrolling("مشتری آزمایشی", substring = true)
        capture("customers")

        composeRule.onNodeWithText("کالاها").performScrollTo()
        composeRule.onNodeWithText("کالاها").performClick()
        composeRule.onNodeWithText("نام محصول").performTextInput("محصول آزمایشی")
        composeRule.onNodeWithText("کد یکتای محصول").performTextInput("CI-TEST-001")
        composeRule.onNodeWithText("موجودی اولیه").performTextInput("10")
        composeRule.onNodeWithText("قیمت واحد (واحد خرد پول)").performTextInput("100000")
        hideKeyboard()
        composeRule.onNodeWithTag("register-product").performScrollTo()
        composeRule.onNodeWithTag("register-product").performClick()
        awaitExists("محصول ثبت شد.")
        awaitVisibleByScrolling("محصول ثبت شد.")
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
        awaitVisibleByScrolling("فاکتور ثبت شد و موجودی به‌روزرسانی شد.")
        awaitVisibleByScrolling("فاکتور #۱", substring = true)
        capture("invoice-summary")

        composeRule.onNodeWithTag("main-content-list").performScrollToIndex(0)
        composeRule.onNodeWithText("گزارش‌ها").performClick()
        awaitDisplayed("گزارش و تحلیل")
        awaitDisplayed("خلاصه فروش فاکتوری")
        awaitDisplayed("جمع مبلغ فاکتورهای قطعی: ۱۰۰٬۰۰۰")
        capture("reports")
        // LazyColumn composes off-screen rows on demand; move to the export card first.
        composeRule.onNodeWithTag("main-content-list").performScrollToIndex(5)
        awaitVisibleByScrolling("خروجی دفتر تراکنش‌های عمومی")
    }
}
