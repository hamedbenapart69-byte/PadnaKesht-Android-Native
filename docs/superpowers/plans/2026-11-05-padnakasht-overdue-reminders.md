# یادآوری محلی بدهی‌های بیش از ۳۰ روز — برنامه پیاده‌سازی

> **برای عامل اجرا:** از `superpowers:executing-plans` برای اجرای مرحله‌ای این برنامه استفاده کن. هر مرحله با آزمون RED→GREEN و ثبت نتیجه تمام می‌شود.

**هدف:** یادآوری روزانه و اختیاری مطالبات ثبت‌شده‌ای که بیش از ۳۰ روز از سررسیدشان گذشته است، بدون اینترنت و بدون افشای نام مشتری در اعلان.

**معماری:** قانون تشخیص بدهی معوق در لایه دامنه خالص است؛ Room/SQLCipher داده را می‌دهد؛ WorkManager اجرای محلی ۲۴ساعته را زمان‌بندی می‌کند؛ تنظیم opt-in در UI و SharedPreferences ذخیره می‌شود. ساخت دیتابیس بین Activity و Worker مشترک خواهد بود.

**فناوری:** Kotlin، Room، SQLCipher، WorkManager 2.12.0، NotificationCompat، Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-11-05-padnakasht-overdue-reminders-design.md`

## قیود سراسری
- عملیات حسابداری کاملاً آفلاین بماند؛ هیچ شرط شبکه یا endpoint اضافه نشود.
- قابلیت پیش‌فرض خاموش باشد؛ مجوز اعلان فقط پس از opt-in درخواست شود.
- اعلان فقط تعداد مشتریان و مبلغ کل را نمایش دهد؛ اطلاعات هویتی مشتری ممنوع.
- «بیش از ۳۰ روز» به‌صورت سخت‌گیرانه ageDays > 30 است.
- تمام محاسبات پولی با Long و Math.addExact/subtractExact انجام شوند.
- از DB رمزگذاری‌شده فعلی و تمام Migrationهای موجود استفاده شود؛ کلید یا رمز در کد/مخزن ذخیره نشود.

## تمرکز بازبینی
1. اختلاف روز در مرز ۳۰/۳۱ و تاریخ سررسید خالی.
2. بازپرداخت و برگشت کالا و ماندهٔ واقعی پس از هر دو.
3. چند فاکتور برای یک مشتری و جلوگیری از شمارش دوباره.
4. لغو فاکتور و پرداخت کامل و مانده صفر.
5. نبود مجوز اعلان، خاموشی اعلان سیستم و خطای بازکردن DB در پس‌زمینه.

---

### Task 1: قانون دامنه و آزمون مرزهای ۳۰/۳۱ روز

**Files**
- Create: `app/src/main/java/com/padnakasht/nativeapp/domain/OverdueDebtReminderRules.kt`
- Create: `app/src/test/java/com/padnakasht/nativeapp/domain/OverdueDebtReminderRulesTest.kt`

**Interface**
- `OverdueInvoiceCandidate(customerId: Long, dueEpochDay: Long, invoiceTotalMinor: Long, returnCreditsMinor: Long, receiptsMinor: Long, refundsMinor: Long, status: String)`
- `OverdueDebtSummary(invoiceCount: Int, customerCount: Int, totalOutstandingMinor: Long)`
- `OverdueDebtReminderRules.summarize(candidates: List<OverdueInvoiceCandidate>, todayEpochDay: Long, thresholdDays: Long = 30): OverdueDebtSummary`

- [ ] تست مرز ۳۰ و ۳۱ روز؛ ۳۰ حذف و ۳۱ شامل شود.
- [ ] تست تاریخ آینده، وضعیت CANCELLED، مانده صفر، برگشت، بازپرداخت، پرداخت کامل و چند فاکتور برای یک مشتری.
- [ ] اجرای `testDebugUnitTest` و مشاهده شکست تست‌های جدید پیش از پیاده‌سازی.
- [ ] پیاده‌سازی تابع خالص با محاسبات exact و بدون وابستگی Android.
- [ ] اجرای دوبارهٔ `testDebugUnitTest` و ثبت نتیجه.

### Task 2: Query امن Room و factory مشترک دیتابیس

**Files**
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessDao.kt`
- Create: `app/src/main/java/com/padnakasht/nativeapp/data/local/AppDatabaseFactory.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/MainActivity.kt`
- Create: `app/src/androidTest/java/com/padnakasht/nativeapp/OverdueDebtReminderDaoTest.kt`

**Interfaces**
- `BusinessDao.getOverdueInvoiceCandidates(cutoffEpochDay: Long): List<OverdueInvoiceCandidate>`
- `AppDatabaseFactory.open(context: Context, databaseName: String = "padnakasht.db"): AppDatabase`

- [ ] تست DAO برای تاریخ سررسید، fallback به تاریخ ثبت، فاکتور لغوشده و جمع دریافت/برگشت/بازپرداخت بنویس.
- [ ] اجرای تست instrumented و مشاهده شکست مورد انتظار.
- [ ] Query projection با subqueryهای مجموع sales_returns/payments/payment_refunds اضافه شود.
- [ ] Factory با DatabasePassphraseProvider، SupportOpenHelperFactory و تمام Migrationهای ۱→۱۳ ساخته شود.
- [ ] MainActivity از factory استفاده کند و مسیر restore-validation همچنان نام دیتابیس و passphrase سفارشی خودش را حفظ کند.
- [ ] اجرای تست‌های DAO و کل suite.

### Task 3: Worker، Notification و Scheduler آفلاین

**Files**
- Modify: `app/build.gradle.kts` — WorkManager 2.12.0
- Modify: `app/src/main/AndroidManifest.xml` — POST_NOTIFICATIONS
- Create: `app/src/main/java/com/padnakasht/nativeapp/reminder/OverdueDebtReminderWorker.kt`
- Create: `app/src/main/java/com/padnakasht/nativeapp/reminder/DebtReminderScheduler.kt`
- Create: `app/src/test/java/com/padnakasht/nativeapp/reminder/DebtReminderNotificationTextTest.kt`

**Interfaces**
- `DebtReminderScheduler.enable(context)`
- `DebtReminderScheduler.disable(context)`
- `OverdueDebtReminderWorker.doWork()`

- [ ] تست متن اعلان: فقط تعداد مشتری و مبلغ فارسی، بدون نام/شماره/شناسه.
- [ ] اجرای تست جدید و مشاهده شکست.
- [ ] Worker با DB رمزگذاری‌شده خلاصه را بخواند؛ DB در finally بسته شود؛ در نبود permission اعلان منتشر نشود؛ خطای موقت DB به Result.retry منتهی شود.
- [ ] Notification channel با اهمیت DEFAULT و متن غیرشخصی ساخته شود.
- [ ] Scheduler یک کار دوره‌ای یکتا با interval=24 HOURS و یک اجرای فوری بسازد؛ هیچ NetworkConstraint تنظیم نشود؛ disable هر دو و اعلان قبلی را لغو کند.
- [ ] اجرای unit tests و Android lint.

### Task 4: تنظیم opt-in در رابط فارسی و مجوز Android 13+

**Files**
- Create: `app/src/main/java/com/padnakasht/nativeapp/ui/DebtReminderSettings.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/padnakasht/nativeapp/OfflineAppSmokeTest.kt`

- [ ] UI test برای حالت پیش‌فرض خاموش و متن توضیحی opt-in اضافه شود.
- [ ] اجرای تست جدید و مشاهده شکست.
- [ ] Switch در بخش گزارش‌ها/تنظیمات محلی نمایش داده شود.
- [ ] API 33+ مجوز فقط پس از روشن‌کردن Switch درخواست شود؛ رد مجوز قابلیت را خاموش نگه دارد و راهنمای تنظیمات نشان دهد.
- [ ] با opt-in، تنظیم ذخیره و Scheduler.enable اجرا شود؛ با opt-out، Scheduler.disable اجرا شود.
- [ ] تست emulator برای فعال/غیرفعال‌سازی و عدم overlap با bottom navigation اجرا شود.

### Task 5: مستندات، بازبینی و CI کامل

**Files**
- Modify: `docs/STATUS.md`
- Modify: `docs/RELEASE-SIGNING.md` فقط اگر وضعیت CI تغییر کند.

- [ ] `testDebugUnitTest`, `lintDebug`, `assembleDebug` و instrumentation روی emulator اجرا شوند.
- [ ] نتیجه واقعی ۳۸+ آزمون، screenshotها و APK SHA-256 ثبت شود.
- [ ] بررسی دستی نهایی برای مرزهای ۳۰/۳۱، privacy notification، permission denial، no-network و بازکردن/بستن DB انجام شود.
- [ ] Commitهای کوچک و قابل بازبینی برای هر Task ثبت شوند.
- [ ] PR #5 به‌روزرسانی شود؛ merge بدون تأیید انسانی انجام نشود.

## ریسک‌ها و تصمیم‌ها
- WorkManager اجرای تقریبی و قابل‌تعویق دارد؛ ساعت دقیق تضمین نمی‌شود.
- اعلان در Android 13+ بدون مجوز کاربر نمایش داده نمی‌شود.
- تاریخ سررسید خالی با تاریخ ثبت فاکتور جایگزین می‌شود.
- بدهی‌های عمومی/اصلاحات دستی مشتری بدون فاکتور در این نسخه اعلان نمی‌شوند؛ فقط مطالبات فاکتورهای قطعی پوشش داده می‌شود.
