# ProGuard rules for SMS Forwarder app
# Preserves JavaMail classes needed for email sending at runtime

# Keep JavaMail classes - required for SMTP email functionality
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }

# Keep Room database entities and DAOs
-keep class com.banking.smsforwarder.data.** { *; }

# Keep Kotlin coroutines internal classes
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
