# Add project specific ProGuard rules here.

# ── Retrofit ──────────────────────────────────────────────
-keepattributes Signature
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.internal.platform.**

# ── kotlinx-serialization ─────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class cat.ri.muufin.model.dto.**$$serializer { *; }
-keepclassmembers class cat.ri.muufin.model.dto.** {
    *** Companion;
}
-keepclasseswithmembers class cat.ri.muufin.model.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp ────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Media3 / ExoPlayer ───────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
