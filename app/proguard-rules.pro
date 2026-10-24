# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# on-device AI (LiteRT-LM talks to its Kotlin classes from native code, so nothing may be renamed or removed)
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class helium314.keyboard.ai.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
