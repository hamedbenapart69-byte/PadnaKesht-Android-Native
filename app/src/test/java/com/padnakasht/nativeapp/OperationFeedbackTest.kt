package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.ui.OperationFeedback
import com.padnakasht.nativeapp.ui.classifyOperationMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationFeedbackTest {
    @Test
    fun everySuccessMessageShownByTheBusinessScreenIsSuccess() {
        listOf(
            "مشتری ثبت شد.",
            "محصول ثبت شد.",
            "اصلاح موجودی و سابقه ثبت شد.",
            "قلم به سبد فاکتور اضافه شد.",
            "فاکتور ثبت شد و موجودی به‌روزرسانی شد.",
            "دریافت ثبت شد.",
            "برگشت ثبت شد؛ موجودی و اعتبار مشتری به‌روزرسانی شد."
        ).forEach { assertEquals(it, OperationFeedback.Success, classifyOperationMessage(it)) }
    }

    @Test
    fun everyFailureMessageShownByTheBusinessScreenIsFailure() {
        listOf(
            "ثبت مشتری ناموفق بود: خطای نامشخص",
            "ثبت محصول ناموفق بود؛ نام، کد یکتا، موجودی و قیمت را بررسی کنید.",
            "اصلاح موجودی انجام نشد؛ موجودی و علت را بررسی کنید.",
            "ثبت فاکتور انجام نشد؛ موجودی و اطلاعات را بررسی کنید.",
            "ثبت دریافت ناموفق بود؛ مانده فاکتور را بررسی کنید.",
            "برگشت ثبت نشد؛ تعداد باقی‌مانده، فاکتور و علت را بررسی کنید.",
            "لغو فاکتور ناموفق بود: خطای نامشخص"
        ).forEach { assertEquals(it, OperationFeedback.Failure, classifyOperationMessage(it)) }
    }

    @Test
    fun validationPromptsAndProgressNoticesStaySilent() {
        listOf(
            null,
            "",
            "مشتری و حداقل یک قلم کالا لازم است.",
            "عملیات قبلی هنوز در حال انجام است.",
            "شناسه فاکتور و مبلغ معتبر لازم است.",
            "تخفیف باید بین صفر و جمع فاکتور باشد.",
            "کالا، مقدار غیرصفر و علت اصلاح را وارد کنید."
        ).forEach { assertEquals(it, OperationFeedback.None, classifyOperationMessage(it)) }
    }
}
