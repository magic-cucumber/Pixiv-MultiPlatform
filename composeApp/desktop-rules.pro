# kotlinx serialization
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }

# JNA
-keep class com.sun.jna.* { *; }
-keep class * extends com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

-ignorewarnings

-optimizations !method/inlining/unique
-optimizations !method/specialization/parametertype
-optimizations !method/specialization/returntype

-printconfiguration build/compose/binaries/main-release/proguard/configuration.txt
-printmapping build/compose/binaries/main-release/proguard/mapping.txt
-printseeds build/compose/binaries/main-release/proguard/seeds.txt
-printusage build/compose/binaries/main-release/proguard/usage.txt
