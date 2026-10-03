Replace tv/src/main/java/com/vueo/tv/ui/app/TvCrashRecoveryPopup.kt.
Fixes the unresolved nativeKeyEvent import reported in logs_100455998466.zip. KeyEvent nativeKeyEvent member access remains in the D-pad handlers. Includes the previous crash dialog scroll fix.
No build or device test performed. ZIP integrity verified.
