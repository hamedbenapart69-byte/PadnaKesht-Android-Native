package com.padnakasht.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.security.PinAuthStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinAuthStoreInstrumentedTest {
    @Test
    fun configureAndVerifyAcceptPersianArabicAndAsciiDigits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("local_pin_verifier", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = PinAuthStore(context)

        val persianPin = "۱۲۳۴۵۶".toCharArray()
        val asciiConfirmation = "123456".toCharArray()
        assertTrue(store.configure(persianPin, asciiConfirmation))
        assertArrayEquals(CharArray(6) { '\u0000' }, persianPin)
        assertArrayEquals(CharArray(6) { '\u0000' }, asciiConfirmation)

        val arabicIndicPin = "١٢٣٤٥٦".toCharArray()
        assertTrue(store.verify(arabicIndicPin))
        assertArrayEquals(CharArray(6) { '\u0000' }, arabicIndicPin)

        assertTrue(store.verify("123456".toCharArray()))
    }
}
