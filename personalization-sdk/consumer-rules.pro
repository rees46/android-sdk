# Consumer R8/ProGuard rules shipped with the SDK.
#
# Huawei push (HMS) is an OPTIONAL, compileOnly dependency. HmsMessagingService and HmsTokenSource
# reference com.huawei.** classes that are absent unless the host app opts in by adding the HMS
# artifacts (com.huawei.hms:push, com.huawei.agconnect:agconnect-core). Without these rules, R8 in a
# FCM-only consumer would fail on the missing references. The HMS service is only ever loaded on a
# Huawei device where the host provided those artifacts, so suppressing the warnings is safe.
-dontwarn com.huawei.**
-dontwarn com.huawei.agconnect.**
