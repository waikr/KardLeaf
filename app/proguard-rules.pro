# Markwon optional media/logging integrations are not bundled.
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn pl.droidsonroids.gif.GifDrawable

# Gson TypeToken needs generic signatures at runtime. SerializedName keeps the
# on-disk JSON contract stable even when R8 renames the backing fields.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
