# Add project specific ProGuard rules here.
# Keep ML Kit classes
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# Keep Room entities
-keep class com.example.barcodescanner.data.database.entities.** { *; }

# Keep Moshi
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
