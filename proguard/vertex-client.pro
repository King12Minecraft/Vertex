# ProGuard configuration for the OBFUSCATED VertexClient.jar (./build.sh --obfuscate).
#
# Goal: raise the cost of reading/copying the client (class, method and field names become short and
# meaningless, debug line numbers and source file names are dropped) - NOT to make it undecompilable, which
# no bytecode tool can do. The server stays the authority for everything that matters, so this is a
# speed bump, not the lock.
#
# Deliberately obfuscate-only: no shrinking and no optimisation (preverification stays on - Java 7+ classes need their stack maps). The app is small, and shrinking/optimising
# is where reflection-style surprises (a class that looks unused but is loaded by name) come from.

-dontshrink
-dontoptimize

# Entry point.
-keep public class Vertex { public static void main(java.lang.String[]); }

# Package names stay: java.util.prefs nodes are named after the package of the class that asks
# (Preferences.userNodeForPackage), so renaming packages would silently reset every saved setting
# (theme, cursor, pinned games, collapsed sidebar groups...).
-keeppackagenames **

# Wire compatibility with the (un-obfuscated) server. Messages are sent with Java serialization, which
# matches classes by name and fields by name: every Serializable class (and every enum, which serializes by
# constant name) in a package the server shares keeps its name and its fields, so a message written by this
# client deserializes on the server and the other way round. Methods of those classes may still be renamed.
#
# Only the SHARED packages are listed. pages, ui, theme and engine are client-only (they never cross the wire),
# so they are renamed completely - which is where most of the code is. Swing components are Serializable too,
# which is why a blanket "keep every Serializable" rule would have kept the whole UI readable.
#
# ADDING A NEW SHARED PACKAGE: add it to the list below. If you forget, build.sh --obfuscate fails in
# proguard/WireCompatCheck (it compares every shared Serializable class against the plain build).
-keepnames class account.**, admin.**, ai.**, chat.**, dominion.**, economy.**, forum.**, games.**, mechanics.**, net.**, social.** implements java.io.Serializable
-keepclassmembers class account.**, admin.**, ai.**, chat.**, dominion.**, economy.**, forum.**, games.**, mechanics.**, net.**, social.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
    <fields>;
}
# Enums are kept everywhere (they are used by name: valueOf, Preferences, flat files).
-keepnames enum *
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# Debug info: drop line numbers and original file names (the point), keep what Swing/inner classes need.
-keepattributes InnerClasses,EnclosingMethod,Signature
