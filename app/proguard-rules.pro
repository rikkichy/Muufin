# Add project specific ProGuard rules here.

# ── Retrofit ──────────────────────────────────────────────
-keepattributes Signature
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation interface cat.ri.muufin.data.JellyfinApi { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.internal.platform.**

# ── kotlinx-serialization ─────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-keep class cat.ri.muufin.model.dto.** { *; }
-keep class cat.ri.muufin.model.dto.**$$serializer { *; }
-keepclassmembers class cat.ri.muufin.model.dto.** {
    *** Companion;
}
-keepclasseswithmembers class cat.ri.muufin.model.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── JakeWharton converter ────────────────────────────────
-keep class com.jakewharton.retrofit2.converter.kotlinx.serialization.** { *; }

# ── OkHttp ────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Media3 / ExoPlayer ───────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
