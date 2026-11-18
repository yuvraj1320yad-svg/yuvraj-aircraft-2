# Get3 - Android project

Python (pywebview) app ka Android version. YouTube / Instagram / Facebook + custom platforms,
recents, floating bolt button, search bar, login save - sab same.

## APK banane ke 2 tarike

### 1) Android Studio (PC)
1. Android Studio install karo (Koala ya naya).
2. File > Open > ye folder (Get3Android) select karo, Gradle sync hone do (internet chahiye).
3. Build > Build Bundle(s) / APK(s) > Build APK(s)
4. APK milegi: app/build/outputs/apk/debug/app-debug.apk -> phone me install karo.

### 2) Bina Android Studio ke (GitHub se)
1. Zip extract karo, saari files ek naye GitHub repo me upload karo (.github folder ke saath).
2. Repo > Actions > "Build APK" > Run workflow.
3. Khatam hone par "Get3-apk" artifact download karo.

## Customize
- App ka naam: app/src/main/res/values/strings.xml
- Built-in platforms / suggestions: app/src/main/java/com/yuvraj/get3/Data.kt
- Home screen design: app/src/main/assets/home.html
