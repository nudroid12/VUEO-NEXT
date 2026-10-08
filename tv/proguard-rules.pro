# Preserve readable class and member names in diagnostic stack traces.
# Code optimization and code/resource shrinking remain enabled.
-dontobfuscate

# VUEO TV release shrinking rules.
#
# Android/Compose/Media3/OkHttp dependencies ship consumer rules. Keep the
# JNI-facing native bridge surface explicitly because :shared:core contains
# QuickJS native runtime code used by TV providers.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
