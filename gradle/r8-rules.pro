# R8 shrink rules for FishModDungeons (shrinkJar task). Shrink + optimize, never rename.
-dontobfuscate
-dontwarn **
-ignorewarnings
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,LineNumberTable,SourceFile,Exceptions,MethodParameters

# Fabric entrypoints (fabric.mod.json)
-keep class fishmod.FishModInit { *; }
-keep class fishmod.utils.config.FishModMenu { *; }

# Mixins are applied by name; keep them byte-identical in spirit
-keep class fishmod.mixin.** { *; }
-keep class fishmod.pv.mixin.** { *; }
-keep interface fishmod.mixin.** { *; }

# Addon API: external FishModAddons jars link against these
-keep class fishmod.features.FishModAddonApi { *; }
-keep class fishmod.utils.events.** { public *; }

# Config (SettingRef/ColorDefaults read fields by name) and Gson models: keep every field
-keepclassmembers class fishmod.** { <fields>; }
-keepclassmembers class twitchbridge.** { <fields>; }
-keepclassmembers class fishmod.** { <init>(); }

# Kotlin objects/companions looked up by INSTANCE
-keepclassmembers class fishmod.** { public static ** INSTANCE; public static ** Companion; }

# Enums (valueOf/values used by config + Gson)
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Shaded practical-config is a nested jar, not shrunk; keep anything it calls back into
-keep class * implements fishmod.shaded.practicalconfig.** { *; }
-keep class * extends fishmod.shaded.practicalconfig.** { *; }
