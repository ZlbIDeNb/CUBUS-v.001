-keepattributes Signature
-keepattributes *Annotation*

# Retrofit and Gson use these classes through reflection. Their field names are
# part of the JSON contract with the CUBUS API and must not be obfuscated.
-keep interface ru.zilisnik.mobile.data.ApiService { *; }
-keep class ru.zilisnik.mobile.data.** { *; }
