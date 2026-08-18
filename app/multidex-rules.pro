-keep class bid.xyenon.caffeine.coloros.hook.** { *; }
-keep class io.github.libxposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-keep class bid.xyenon.caffeine.coloros.ui.MainActivity {
    public boolean isLSPosedHookActive();
}
