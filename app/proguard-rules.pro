# UniFeed ProGuard Rules

# ShortcutBadger 用反射找启动器实现，必须保留
-keep class me.leolin.shortcutbadger.** { *; }

# Keep Room entities
-keep class com.succh.unifeed.data.database.entity.** { *; }

# Keep data classes
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# jsoup
-dontwarn org.jsoup.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
