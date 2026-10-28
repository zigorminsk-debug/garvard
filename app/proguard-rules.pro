# --- Гарвард: Справедливость ---
# Сохраняем JS-мост, вызываемый из JavaScript по имени
-keepclassmembers class edu.garvard.justice.course.MainActivity$Bridge {
    public *;
}
-keepattributes JavascriptInterface
-keep class android.webkit.JavascriptInterface
-dontwarn android.webkit.**
