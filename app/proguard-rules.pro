# --- Kotlin / coroutines / serialization -------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-dontwarn kotlinx.coroutines.**
# kotlinx.serialization: keep generated serializers of our models (rules for the runtime ship with the lib).
-keepclassmembers @kotlinx.serialization.Serializable class vn.esstar.ainovel.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class vn.esstar.ainovel.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- sherpa-onnx: JNI looks classes and fields up by name ---------------------------------------
-keep class com.k2fsa.sherpa.onnx.** { *; }

# --- pdfbox-android --------------------------------------------------------------------------------
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**

# --- commons-compress (only tar + bzip2 are used) ---------------------------------------------------
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**

# --- OkHttp / Okio / Jsoup -----------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn org.jsoup.**

# --- Timber / Room / Hilt / Media3 / ML Kit ship their own consumer rules -----------------------

# --- ML Kit: components are discovered reflectively via manifest metadata; R8 full mode strips
#     registrars and model managers (release crashed with NPE in RemoteModelManager.getInstance()).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.common.internal.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
