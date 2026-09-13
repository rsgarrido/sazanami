# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# jaudiotagger includes optional Java SE artwork decoding methods. Sazanami
# reads embedded artwork bytes through Android APIs and never calls these
# desktop-only paths.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream

# Jaudiotagger constructs ID3 frame-body classes dynamically by name, so R8 must
# preserve those classes and their constructors for metadata reads and writes.
-keep class org.jaudiotagger.tag.id3.framebody.** { *; }

# WavTag initializes its logger from WavTag.class.getPackage().getName(), so
# preserve this package name to prevent R8 from moving it into the unnamed package.
-keeppackagenames org.jaudiotagger.tag.wav

# Glance instantiates ActionCallback implementations through their public
# zero-argument constructors.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
