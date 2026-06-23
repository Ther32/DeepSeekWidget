# 构建指南

## 方法一：GitHub Actions（推荐，无需本地工具）

1. 在 GitHub 上创建一个新仓库
2. 把本项目的所有文件推送到 GitHub：
   ```powershell
   cd D:\Code\test\DeepSeekWidget
   git init
   git add .
   git commit -m "init"
   git remote add origin https://github.com/你的用户名/DeepSeekWidget.git
   git push -u origin main
   ```
3. 打开 GitHub 仓库页面 → **Actions** 标签
4. 等待 "Build APK" 工作流运行完成
5. 在运行结果中找到 **Artifacts** → 下载 `DeepSeekWidget-Debug-APK.zip`
6. 解压后得到 `app-debug.apk`，传到手机安装即可

每次 `git push` 都会自动构建新版 APK。

## 方法二：本地命令行构建（需安装工具）

### 安装依赖

1. **安装 JDK 17**（必选）
   ```powershell
   # 用 winget 安装
   winget install EclipseAdoptium.Temurin.17.JDK
   ```

2. **安装 Android SDK 命令行工具**（必选）
   - 下载 https://developer.android.com/studio#command-line-tools-only
   - 解压到 `C:\Android\cmdline-tools`
   - 运行以下命令安装 SDK：
   ```powershell
   set ANDROID_HOME=C:\Android
   %ANDROID_HOME%\cmdline-tools\bin\sdkmanager "platforms;android-34" "build-tools;34.0.0"
   ```

3. **生成 Gradle Wrapper**（必选）
   ```powershell
   gradle wrapper --gradle-version=8.5
   ```

### 构建 APK

```powershell
# Debug APK（可直接安装）
gradlew assembleDebug

# Release APK
gradlew assembleRelease
```

APK 生成位置：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

> **注意**：Release APK 需要签名才能安装。Debug APK 可直接用于测试。
