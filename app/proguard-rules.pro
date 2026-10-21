# JNI entry points and the callback interface used from native code.
-keep class app.pzip.core.archive.RarNative { *; }
-keep class * implements app.pzip.core.archive.RarNative$Callback { *; }
-keep interface app.pzip.core.archive.RarNative$Callback { *; }

# commons-compress optional codecs we do not ship.
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**

# libVLC calls back into these classes from native code.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# libVLC calls back into these classes from native code.
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**
