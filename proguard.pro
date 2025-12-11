# -----------------------------------------------------------------------------
#  General Obfuscation Settings (Hardened for Commercial Release)
# -----------------------------------------------------------------------------
-dontnote
-dontwarn

# Enable optimization for harder reverse engineering
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Aggressive repackaging
-defaultpackage ''
-repackageclasses 'me.lonaldeu.projectmace.a'
-allowaccessmodification

# Keep only essential attributes (removed SourceFile/LineNumberTable for production)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

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

# Keep root config class (required for Bukkit YAML reflection)
-keep class me.lonaldeu.projectmace.config.MaceConfig {
    *;
}
-keep class me.lonaldeu.projectmace.config.LicenseConfig {
    *;
}

# Keep License Validator result data classes so Gson doesn't break
-keep class me.lonaldeu.projectmace.license.LicenseValidator$ValidationResult {
    *;
}

# Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Obfuscate all other config data classes (they're mapped manually, not by reflection)
-keepclassmembers class me.lonaldeu.projectmace.config.** {
    <init>(...);
}
