# اختبار إشعارات OneSignal على أجهزة Android

الفرع: `fix/onesignal-recovery` — الحزمة: `com.sutstudio.numberedapp` — مشروع Firebase المطلوب: `numbered-app-a5916`

## قبل البناء (شرط أساسي)

الملف `app/google-services.json` يجب أن يكون منزّلاً من مشروع Firebase الجديد `numbered-app-a5916`
(Firebase Console > Project settings > Your apps > `com.sutstudio.numberedapp`).
فحص CI في `codemagic.yaml` يوقف البناء إذا كان `project_id` مختلفاً.

في OneSignal: Settings > Push & In-App > Google Android (FCM) يجب أن تكون بيانات الاعتماد
(Service Account JSON) من نفس المشروع `numbered-app-a5916`.

## تثبيت التطبيق والتقاط السجل

استخدم نسخة Debug (تعطي سجل OneSignal الكامل) على كل جهاز:

```
adb install -r app-debug.apk
adb logcat -c
adb logcat -s NumberedPush OneSignal
```

افتح التطبيق، اقبل صلاحية الإشعارات (Android 13+)، وانتظر بضع ثوانٍ.

## ما الذي تقرأه في السجل

سطر `Firebase config bundled in this build` يعرض `project_id` المضمّن في النسخة. يجب أن يكون `numbered-app-a5916`.
وإلا يظهر سطر `Firebase project mismatch`.

سطر `verdict=` (يظهر عند كل `onResume` وبعد طلب الصلاحية) يصنّف الحالة:

| verdict | المعنى | مكان الخلل |
|---|---|---|
| `DEVICE_NOTIFICATIONS_BLOCKED` | الصلاحية مرفوضة أو الإشعارات معطلة من إعدادات النظام | الجهاز / المستخدم |
| `ONESIGNAL_INIT_FAILED` | فشلت `initWithContext` (يظهر الاستثناء كاملاً) | التطبيق / OneSignal SDK |
| `NO_SUBSCRIPTION_ID` | لم ينشئ OneSignal اشتراكاً | تهيئة OneSignal أو الشبكة أو App ID |
| `NO_PUSH_TOKEN` | لا يوجد FCM token | `google-services.json` أو Firebase أو Google Play services (انظر `Google Play services=` في السجل) |
| `NOT_OPTED_IN` | الاشتراك موجود لكنه غير مفعّل | صلاحية الإشعارات |
| `APP_SIDE_OK` | الـ id والـ token موجودان والاشتراك مفعّل | جانب التطبيق سليم؛ إن لم يصل إشعار فراجع إعدادات OneSignal/FCM |

يظهر `subscriptionId` و`onesignalId` في السجل كاملين. `token` يظهر مقنّعاً.

## التحقق في OneSignal

1. Audience > Subscriptions: ابحث عن `subscriptionId` من السجل. يجب أن يظهر الجهاز بحالة Subscribed وبنوع Android Push.
2. أرسل رسالة تجريبية: Subscriptions > الجهاز > Send test message (أو أضفه كـ Test Subscription ثم أرسل من Messages).
3. سجّل النتيجة لكل جهاز في الجدول التالي.

## جدول النتائج

| الجهاز | إصدار Android | project_id في السجل | verdict | يظهر في Subscriptions | وصل الإشعار التجريبي |
|---|---|---|---|---|---|
| (Android 15) | 15 | | | | |
| (جهاز آخر) | | | | | |

## ملاحظات التوافق

- `minSdk 21` يوافق الحد الأدنى لـ OneSignal SDK 5.x.
- Android 13 فأعلى: الصلاحية `POST_NOTIFICATIONS` معلنة في الـ Manifest وتُطلب وقت التشغيل. Android 12 فأقل: لا حاجة لطلبها، ويجب أن يظهر الاشتراك مباشرة.
- لم يُرفع `compileSdk` أو `targetSdk` (34) لأن ذلك قرار منفصل عن هذه المشكلة، وتشغيل التطبيق على Android 15 مدعوم بهذه القيم.
