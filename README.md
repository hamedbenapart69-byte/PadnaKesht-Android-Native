# پادناکِشت — Android Native

اپلیکیشن بومی مدیریت کسب‌وکار و حسابداری آفلاین، با Kotlin، Jetpack Compose، Room و معماری لایه‌ای.

## وضعیت
این مخزن تازه از صفر آغاز شده است. تا زمانی که آزمون‌های پذیرش اجرا نشده‌اند، هیچ APK را نسخهٔ نهایی/تأییدشده تلقی نکنید.

## معماری هدف
- `app`: نقطهٔ ورود اندروید و Compose UI
- `core/domain`: مدل‌ها و قوانین مالی مستقل از Android
- `core/data`: Room، DAOها و repositoryها
- `core/security`: نگهداری کلید و احراز هویت محلی
- `core/analytics`: تحلیل و پیش‌بینی محلی

## ساخت
ساخت با GitHub Actions انجام می‌شود. ابتدا workflow باید dependencyها را دریافت و unit testها و debug APK را بسازد؛ خروجی debug به معنی تأیید امنیت یا آمادگی انتشار نیست.
