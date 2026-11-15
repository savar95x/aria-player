# jaudiotagger loads tag/frame classes reflectively.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**

# The widget is plain RemoteViews (no Glance/WorkManager), so it needs no keep rules.
