# پادناکِشت — برنامه اجرایی تکمیل اپلیکیشن

> **For agentic workers:** اجرای مرحله‌ای همین برنامه در شاخه مستقل؛ هر مرحله با آزمون واقعی و ثبت نتیجه بسته شود.

**Goal:** تبدیل کدبیس فعلی به اپلیکیشن حسابداری بومی، آفلاین، امن و قابل استفاده روزمره برای پادناکِشت، با حفظ داده‌ها و امکان مدیریت دستی کامل.

**Architecture:** حفظ Kotlin، Compose، Room و SQLCipher فعلی؛ منطق مالی و قواعد دامنه از رابط کاربری جدا می‌شود و عملیات چندجدولی در تراکنش Room باقی می‌ماند. تغییرات ساختاری تدریجی‌اند و هیچ بازنویسی یک‌باره یا مهاجرت به پلتفرم دیگر انجام نمی‌شود.

**Tech Stack:** Kotlin، Android Gradle Plugin، Jetpack Compose، Material 3، Room، SQLCipher، Coroutines/Flow، JUnit و AndroidX/Espresso.

**Spec:** `docs/superpowers/specs/2026-11-05-padnakasht-offline-accounting-design.md`

## Global Constraints
- اپلیکیشن کاملاً آفلاین است؛ Firebase، Firestore، cloud و sync چنددستگاهی اضافه نشود.
- منبع حقیقت داده، Room رمزگذاری‌شده با SQLCipher است.
- رابط فارسی، RTL، تاریخ شمسی و نمایش تومان حفظ شود.
- محاسبات مالی با عدد صحیح و `Math.addExact` / `Math.multiplyExact` / `Math.subtractExact` انجام شود؛ overflow نباید پنهان شود.
- ثبت فروش، دریافت وجه، اعتبار مرجوعی و بازپرداخت، رویدادهای جداگانه‌اند.
- داده واقعی حذف یا با داده نمونه جایگزین نشود؛ واردسازی اکسل اختیاری و ثبت دستی مسیر کامل و مستقل باشد.
- هر تغییر schema باید migration نسخه‌بندی‌شده و آزمون مهاجرت داشته باشد؛ fallback مخرب یا destructive migration ممنوع است.
- کلید امضای خصوصی و رمزها هرگز وارد Git نشوند.
- هیچ APK «نهایی» اعلام نشود مگر build، تست، امضا و نصب/اجرای واقعی شواهد داشته باشند.

## Review Focus
- پرداخت یا مرجوعی هم‌زمان و تکراری نباید مانده یا موجودی را دوبار تغییر دهد.
- مرجوعی نباید از مقدار فروش‌رفته یا اعتبار قابل برگشت همان قلم بیشتر شود.
- رویدادهای یک مشتری یا فاکتور دیگر نباید به حساب اشتباه متصل شوند.
- حذف/غیرفعال‌سازی مشتری یا کالا نباید اسناد تاریخی و ارتباطات مالی را بشکند.
- تغییر نسخه پایگاه داده، بازیابی پشتیبان یا قطع عملیات نباید داده موجود را از بین ببرد.

---

### Task 1: تثبیت خط مبنا و آزمون موتور حسابداری

**Files:**
- Inspect: `app/src/main/java/com/padnakasht/nativeapp/domain/BusinessAccounting.kt`
- Inspect: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessDao.kt`
- Inspect: `app/src/main/java/com/padnakasht/nativeapp/data/BusinessRepository.kt`
- Test: `app/src/test/java/com/padnakasht/nativeapp/domain/BusinessAccountingTest.kt`
- Test: `app/src/androidTest/java/com/padnakasht/nativeapp/data/BusinessDaoAccountingTest.kt` (create if absent)

**Interfaces:**
- Existing domain entry point: `BusinessAccounting.summarize(invoices, payments, returns, refunds): BusinessAccountingSummary`.
- Existing persistence entry points: `BusinessDao.postInvoice`, `recordPayment`, `recordReturn`, `recordRefund`, `adjustStock`.

- [ ] اجرای baseline با `bash scripts/ci-gradle.sh unit testDebugUnitTest` و ثبت نتیجه واقعی.
- [ ] افزودن آزمون‌های دامنه برای مبالغ منفی، overflow، برگشت بیش از اعتبار فروش، پرداخت بیش از مانده و رویداد متعلق به فاکتور لغوشده؛ ابتدا هر تست باید در وضعیت مورد انتظار شکست بخورد.
- [ ] افزودن آزمون Room برای اتمیک‌بودن فاکتور و موجودی، جلوگیری از پرداخت تکراری با idempotency key، و اتصال صحیح refund به payment همان فاکتور.
- [ ] اصلاح حداقلی منطق دامنه/DAO تا همه آزمون‌ها سبز شوند؛ هیچ قانون مالی جدیدی بدون پوشش تست اضافه نشود.
- [ ] اجرای مجدد unit و instrumentation tests و ثبت خروجی CI.

### Task 2: مدیریت دستی کامل حساب مشتری

**Files:**
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessEntities.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessDao.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/BusinessRepository.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/ui/BusinessManagement.kt`
- Create: `app/src/androidTest/java/com/padnakasht/nativeapp/data/CustomerBalanceAdjustmentTest.kt`

**Interfaces:**
- New persisted adjustment record: customer ID, signed amount delta in the existing money unit, date, reason and stable ID.
- Positive delta increases receivable; negative delta decreases receivable. Zero is invalid.
- Repository exposes add/update customer and record balance adjustment; DAO updates customer balance stream from invoices, payments, returns and adjustments.
- Customer records with financial history are archived/deactivated, never hard-deleted.

- [ ] افزودن تست شکست‌خورده برای مانده افتتاحیه مثبت، اعتبار منفی، اصلاح افزایشی/کاهشی، و مشتری بدون فاکتور.
- [ ] افزودن migration نسخه ۹ به ۱۰ برای جدول رویداد اصلاح حساب و وضعیت فعال/بایگانی مشتری، با foreign key و index لازم.
- [ ] پیاده‌سازی query مانده مشتری به‌صورت مجموع رویدادهای ثبت‌شده، بدون تغییر محاسبه مانده فاکتورهای منفرد.
- [ ] افزودن ویرایش نام/تلفن/یادداشت مشتری، بایگانی و بازگردانی مشتری، و ثبت مانده افتتاحیه/اصلاح حساب با شرح اجباری.
- [ ] آزمون migration از نسخه ۹ و حفظ مشتریان، فاکتورها و پرداخت‌های قبلی.
- [ ] اجرای تست‌ها و بررسی عدم امکان حذف اسناد تاریخی از مسیر UI.

### Task 3: مدیریت امن کالا و گردش انبار

**Files:**
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessEntities.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/BusinessDao.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/data/BusinessRepository.kt`
- Modify: `app/src/main/java/com/padnakasht/nativeapp/ui/BusinessManagement.kt`
- Test: `app/src/androidTest/java/com/padnakasht/nativeapp/data/BusinessDaoAccountingTest.kt`

**Interfaces:**
- Product updates preserve SKU uniqueness and never rewrite historical invoice line prices.
- Stock changes are represented by append-only stock movements; the current stock is updated atomically.
- Products referenced by invoices are archived, not physically deleted.

- [ ] افزودن تست برای ویرایش کالا بدون تغییر قیمت تاریخی فاکتور، جلوگیری از SKU تکراری، عدم امکان موجودی منفی و بایگانی کالای دارای سابقه.
- [ ] افزودن repository/DAO عملیات update و archive با اعتبارسنجی ورودی.
- [ ] افزودن فرم ویرایش کالا، جست‌وجو/فیلتر و نمایش کالاهای فعال و بایگانی‌شده در UI.
- [ ] اجرای آزمون‌های Room و UI برای ثبت/ویرایش کالا و اصلاح موجودی.

### Task 4: جداسازی معماری و بهبود تجربه کاربری

**Files:**
- Refactor: `app/src/main/java/com/padnakasht/nativeapp/MainActivity.kt`
- Refactor: `app/src/main/java/com/padnakasht/nativeapp/ui/BusinessManagement.kt`
- Reuse: `app/src/main/java/com/padnakasht/nativeapp/ui/DesignComponents.kt`
- Reuse: `app/src/main/java/com/padnakasht/nativeapp/ui/theme/`
- Create only where justified: focused screen, state and ViewModel files under `app/src/main/java/com/padnakasht/nativeapp/ui/`

**Interfaces:**
- MainActivity is the application composition root: database/security setup, navigation host and dependency wiring only.
- Business screens consume immutable UI state and explicit user actions; Composables do not perform financial calculations or own database workflows.
- Shared typography, spacing, color, surfaces and buttons remain centralized in the existing theme/design components.

- [ ] استخراج baseline screenshots and UI smoke-test coverage before refactor.
- [ ] انتقال داده‌گیری و عملیات از composables به ViewModel/repository boundaries without changing accounting semantics.
- [ ] تقسیم صفحه مشتری، کالا، فاکتور و گزارش به اجزای مسئولیت‌محور با حفظ navigation و state.
- [ ] بررسی RTL، صفحه کوچک، کیبورد باز، چرخش/بازسازی Activity، وضعیت خالی، loading، خطا و جلوگیری از ثبت دوباره.
- [ ] افزودن/به‌روزرسانی UI tests برای جریان ثبت مشتری، کالا، فاکتور، دریافت، مرجوعی و بازگشت به صفحه اصلی.
- [ ] اجرای کامل تست‌ها و مقایسه رفتار و اسکرین‌شات‌ها.

### Task 5: پشتیبان، خروجی و انتشار قابل اتکا

**Files:**
- Inspect/Modify: `app/src/main/java/com/padnakasht/nativeapp/report/`
- Inspect/Modify: `app/src/main/java/com/padnakasht/nativeapp/security/`
- Inspect/Modify: `app/src/main/java/com/padnakasht/nativeapp/MainActivity.kt`
- Modify if required: `.github/workflows/android-ci.yml`
- Inspect: `scripts/ci-gradle.sh`, `scripts/ci-emulator.sh`

**Interfaces:**
- Backup and restore remain local and encrypted; restore validates database integrity before replacing the active database.
- PDF/Excel output is generated from domain/report data, not from UI text.
- Release signing uses externally stored secrets and never commits keystore material.

- [ ] آزمون تهیه و بازیابی پشتیبان روی دیتابیس آزمایشی، integrity check و مقایسه تعداد/جمع رکوردها.
- [ ] آزمون خروجی PDF و Excel برای فارسی، RTL، تاریخ شمسی، تومان و مجموعه داده خالی/بزرگ.
- [ ] اجرای unit tests، Android instrumentation/UI tests و assembleDebug.
- [ ] بررسی وضعیت signing secrets فقط با نام متغیرها؛ هیچ secret value چاپ یا ذخیره نشود.
- [ ] در صورت وجود کلید معتبر در secret store، ساخت Release، بررسی امضا/package/version و SHA-256؛ در غیر این صورت release unsigned را تحویل نهایی تلقی نکن و blocker را دقیق ثبت کن.
- [ ] تهیه راهنمای نصب، ورود اولیه، ثبت دستی داده‌ها، پشتیبان‌گیری و بازیابی.

---

## معیار پایان
- همه تست‌های واحد و instrumentation مرتبط سبز باشند.
- مهاجرت پایگاه داده از نسخه قبلی بدون از دست رفتن داده تأیید شود.
- مشتری و کالا از داخل برنامه قابل ثبت، ویرایش و بایگانی باشند؛ مانده افتتاحیه و اصلاح حساب قابل ثبت و ردیابی باشد.
- فاکتور، دریافت، مرجوعی، بازپرداخت و موجودی از نظر محاسبات و روابط داده درست عمل کنند.
- رابط فارسی/RTL و تاریخ شمسی در مسیرهای اصلی قابل استفاده باشد.
- Debug APK ساخته و روی شبیه‌ساز نصب و اجرا شده باشد.
- Release APK فقط در صورت امضای معتبر و تأیید نصب/اجرا نهایی محسوب شود.
