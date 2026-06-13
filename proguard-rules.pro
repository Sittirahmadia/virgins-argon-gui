# Aggressive production obfuscation profile for the release jar.
# Run with: ./gradlew obfuscateJar

-dontshrink
-dontoptimize
-overloadaggressively
-useuniqueclassmembernames
-repackageclasses ''
-flattenpackagehierarchy ''
-adaptclassstrings
-adaptresourcefilenames **.json,**.mixins.json
-adaptresourcefilecontents **.json,**.mixins.json

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keep class net.fabricmc.** { *; }
-keep class net.minecraft.** { *; }
-keep class org.spongepowered.** { *; }
-keep class dev.lvstrng.argon.mixin.** { *; }
-keep class dev.lvstrng.argon.Argon { *; }
-keep class dev.lvstrng.argon.ArgonClient { *; }
-keep class * implements net.fabricmc.api.ModInitializer { *; }
-keep class * implements net.fabricmc.api.ClientModInitializer { *; }
-keep @org.spongepowered.asm.mixin.Mixin class * { *; }
-keepclassmembers class * {
    @org.spongepowered.asm.mixin.* <fields>;
    @org.spongepowered.asm.mixin.* <methods>;
}

-dontwarn **
-ignorewarnings
-printmapping build/obfuscation/mapping.txt
-printseeds build/obfuscation/seeds.txt
