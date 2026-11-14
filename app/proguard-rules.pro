# jaudiotagger loads tag/frame classes reflectively.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**

# Glance instantiates widget action callbacks by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Room databases (WorkManager's, used by the widget) are created reflectively via their _Impl class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
