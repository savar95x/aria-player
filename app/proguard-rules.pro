# jaudiotagger loads tag/frame classes reflectively.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**

# Glance instantiates widget action callbacks by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
