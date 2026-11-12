# BouncyCastle is only used to create the phone's certificate; keep it intact
# because parts of it are looked up by name.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
