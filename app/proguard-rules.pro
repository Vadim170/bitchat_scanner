# Project-specific R8 rules. Compose, AndroidX lifecycle and the AGP-generated
# manifest rules ship their own consumer keeps, so only the gaps are listed here.

# Readable release stack traces; mapping.txt is published as a CI artifact.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# osmdroid ships no consumer rules and resolves tile/archive providers by name.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ViewModels are instantiated reflectively by the default factory.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Standard Parcelable / Serializable safety.
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
