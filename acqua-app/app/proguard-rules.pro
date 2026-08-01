# Native runtime entry points are reached through JNI and reflection.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Commons Compress registers ZIP extra-field implementations with Class.newInstance().
# R8 must not merge these classes or remove their public no-argument constructors.
-keep class org.apache.commons.compress.archivers.zip.** implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
