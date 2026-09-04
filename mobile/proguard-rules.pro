# VUEO Mobile release shrinking rules.
#
# Android/Compose/OkHttp dependencies ship their own consumer rules. Keep only
# the JNI-facing native bridge surface explicitly so R8 cannot rename/remove a
# method that a packaged native library expects to resolve by name.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
