# Called from native code (JNI GetMethodID / FindClass): R8 in a consumer must not strip or rename these.
-keepclassmembers class com.swordfish.libretrodroid.GLRetroView {
    private void sendCoreMessage(...);
    private void sendAchievementEvent(...);
    private void sendAchievementServerCall(...);
    private void sendRumbleEvent(...);
    private void refreshAspectRatio();
    private void onCoreFrameDrawn();
}
-keep class com.swordfish.libretrodroid.AchievementInfo { <init>(...); <fields>; }
