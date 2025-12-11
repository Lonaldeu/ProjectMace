# -----------------------------------------------------------------------------
#  General Obfuscation Settings
# -----------------------------------------------------------------------------
-dontnote
-dontwarn
-dontoptimize
-defaultpackage ''
-repackageclasses 'me.lonaldeu.projectmace.a'
-allowaccessmodification

# Keep attributes required for debug/runtime
-keepattributes *Annotation*,Signature,InnerClasses,SourceFile,LineNumberTable,EnclosingMethod

# -----------------------------------------------------------------------------
#  Bukkit/Paper Rules
# -----------------------------------------------------------------------------

# Keep main plugin class
-keep public class me.lonaldeu.projectmace.ProjectMacePlugin {
    public *;
}

# Keep Event Handlers
-keep public class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler public void *(**);
}

# Keep Command Executors (if any standard Bukkit ones remain)
-keep public class * implements org.bukkit.command.CommandExecutor {
    public boolean onCommand(**);
}
-keep public class * implements org.bukkit.command.TabCompleter {
    public java.util.List onTabComplete(**);
}

# Keep serializable classes (ConfigModels might be used reflectively by some libs, safest to keep fields)
# Although we map manually, keeping them avoids potential issues if we switch to reflective mapping later.
-keep class me.lonaldeu.projectmace.config.** {
    *;
}

# Keep License Validator result data classes so Gson doesn't break if we switch to auto-deserialization
-keep class me.lonaldeu.projectmace.license.LicenseValidator$ValidationResult {
    *;
}

# Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
