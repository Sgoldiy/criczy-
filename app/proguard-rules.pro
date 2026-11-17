# kotlinx.serialization: keep generated serializers for the API DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.criczy.app.core.network.dto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.criczy.app.core.network.dto.**$$serializer { *; }

# Retrofit service interfaces are created reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
