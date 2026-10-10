#!/usr/bin/env bash
# Epub Maker - ขั้นที่ 1: ติดตั้ง Android SDK + สร้างโปรเจกต์เปล่าที่ build เป็น APK ได้
# รันจากโฟลเดอร์ repo:  bash setup.sh
set -e

SDK="$HOME/android-sdk"
GRADLE_VER="8.7"

echo "== 1/5 ติดตั้ง JDK 17 =="
if ! [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/javac ]; then
  sudo apt-get update -y
  sudo apt-get install -y openjdk-17-jdk unzip curl
fi
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

echo "== 2/5 ติดตั้ง Android SDK =="
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -L -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o /tmp/cmdtools.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm /tmp/cmdtools.zip
fi
export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# จำค่าไว้ใช้ใน terminal ครั้งต่อไป
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc"; then
  {
    echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
    echo "export ANDROID_HOME=$SDK"
    echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH'
  } >> "$HOME/.bashrc"
fi

echo "== 3/5 สร้างไฟล์โปรเจกต์ =="
mkdir -p app/src/main/java/com/mimee/epubmaker

cat > .gitignore <<'EOF'
.gradle/
build/
local.properties
*.apk
.idea/
EOF

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "EpubMaker"
include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
EOF

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF

cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mimee.epubmaker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mimee.epubmaker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="Epub Maker"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > app/src/main/java/com/mimee/epubmaker/MainActivity.kt <<'EOF'
package com.mimee.epubmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("ยังไม่ได้เลือกไฟล์ PDF") }
    var page by remember { mutableStateOf<Bitmap?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                info = "กำลังเปิด..."
                val r = withContext(Dispatchers.IO) { runCatching { renderFirstPage(ctx, uri) } }
                r.onSuccess { (n, bmp) ->
                    info = "เปิดสำเร็จ: $n หน้า (แสดงหน้า 1)"
                    page = bmp
                }
                r.onFailure { info = "เปิดไม่ได้: ${it.message}" }
            }
        }
    }

    Column(Modifier.statusBarsPadding().padding(16.dp)) {
        Text("Epub Maker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { pick.launch(arrayOf("application/pdf")) }) { Text("เลือกไฟล์ PDF") }
        Spacer(Modifier.height(12.dp))
        Text(info)
        Spacer(Modifier.height(12.dp))
        page?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "หน้า 1",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

fun renderFirstPage(ctx: Context, uri: Uri): Pair<Int, Bitmap> {
    val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException("เปิดไฟล์ไม่ได้")
    val renderer = PdfRenderer(pfd)
    try {
        val count = renderer.pageCount
        val p = renderer.openPage(0)
        try {
            val scale = 2
            val bmp = Bitmap.createBitmap(p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return count to bmp
        } finally {
            p.close()
        }
    } finally {
        renderer.close()
        pfd.close()
    }
}
EOF

echo "== 4/5 สร้าง Gradle wrapper =="
if [ ! -f gradlew ]; then
  if [ ! -x "$HOME/gradle/gradle-$GRADLE_VER/bin/gradle" ]; then
    mkdir -p "$HOME/gradle"
    curl -L -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
    unzip -q -o /tmp/gradle.zip -d "$HOME/gradle"
    rm /tmp/gradle.zip
  fi
  "$HOME/gradle/gradle-$GRADLE_VER/bin/gradle" wrapper --gradle-version "$GRADLE_VER"
fi
chmod +x gradlew

echo "== 5/5 build APK =="
./gradlew assembleDebug

echo
echo "เสร็จแล้ว! APK อยู่ที่: app/build/outputs/apk/debug/app-debug.apk"
echo "คลิกขวาที่ไฟล์ใน Explorer ด้านซ้าย แล้วเลือก Download"
