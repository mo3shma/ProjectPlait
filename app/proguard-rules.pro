# R8 removes code the app never uses (mostly Compose's large icon set) so the download is smaller.
# Libraries that reach into Java from native code or by reflection are kept whole, so nothing they need goes missing.

# Speech: sherpa-onnx (Whisper), whose native code reads Java fields by name.
-keep class com.k2fsa.** { *; }

# On-device language models, text embedding and protobuf messages.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.odml.** { *; }

-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Text in photos.
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }

# Keep the names in crash logs readable.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

-dontwarn com.sun.jna.**
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.auto.value.**
