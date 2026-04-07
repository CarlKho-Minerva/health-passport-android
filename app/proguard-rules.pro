# ─── Health Passport ProGuard Rules ───────────────────────────────────────────
#
# Goal: run R8 to produce a mapping.txt for Play Console (deobfuscation file),
# while keeping the app functionally identical to an unobfuscated build.
#
# Strategy: disable renaming (-dontobfuscate). R8 will still shrink dead code
# and generate the required mapping.txt.
# ─────────────────────────────────────────────────────────────────────────────

# Do NOT rename any classes or members. This is the key flag that makes R8
# safe here: no rename means no risk of JNI or reflection breakage.
-dontobfuscate

# Preserve source file names and line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,Exception,InnerClasses,EnclosingMethod

# ── Nexa / AI SDK ─────────────────────────────────────────────────────────────
# The SDK uses JNI and internal reflection; keep all public surface.
-keep class com.nexa.** { *; }
-keep class ai.nexa.** { *; }
-dontwarn com.nexa.**
-dontwarn ai.nexa.**

# ── Our own app ───────────────────────────────────────────────────────────────
-keep class com.nexa.demo.** { *; }

# ── JNI ───────────────────────────────────────────────────────────────────────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { <methods>; }
-dontwarn kotlin.**

# ── kotlinx.serialization ─────────────────────────────────────────────────────
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── OkHttp / OkDownload ───────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep class com.liulishuo.okdownload.** { *; }
-dontwarn okhttp3.**
-dontwarn com.liulishuo.**

# ── Gson ──────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Markwon ───────────────────────────────────────────────────────────────────
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.**

# ── Android / AndroidX ────────────────────────────────────────────────────────
-keep class * extends android.app.Activity { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }
-keep class * extends android.view.View { *; }
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter { *; }