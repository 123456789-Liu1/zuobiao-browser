# xraymobile · 本地代理内核（可选）

把 [Xray-core](https://github.com/XTLS/Xray-core)（MPL-2.0）用 Go 编成 Android 可用的 `.so`，
供「设置 → 本地代理核心（实验）」调用。

**默认不打包进仓库**：`.so` 与生成的 `classes.jar` 都是二进制，见根目录 `.gitignore`。
需要时按下面步骤自己生成，然后重新 `assembleRelease`。

## 环境
- Go ≥ 1.24
- Android NDK（本机用 28.2.13676358）
- `gomobile`：`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`

## 生成

```bash
# 1. 在 tools/xraymobile 目录下
export GOPROXY=https://goproxy.cn,direct
export GOSUMDB=off
export ANDROID_HOME=/path/to/Sdk
export ANDROID_NDK_HOME=/path/to/Sdk/ndk/28.2.13676358

# 2. 打包 AAR（arm64 给真机，x86_64 给模拟器）
gomobile bind -androidapi 24 -target=android/arm64 -o xraymobile-arm64.aar .

# 3. 拆出 jar 与 so
unzip -o xraymobile-arm64.aar -d out-arm64
cp out-arm64/classes.jar ../../app/libs/xraymobile.jar
mkdir -p ../../app/src/main/jniLibs/arm64-v8a
cp out-arm64/jni/arm64-v8a/libgojni.so ../../app/src/main/jniLibs/arm64-v8a/

# 4. 去符号（63MB → 32MB）
$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/*/bin/llvm-strip \
  --strip-unneeded ../../app/src/main/jniLibs/arm64-v8a/libgojni.so
```

灰度/真机都要 arm64；模拟器用 `-target=android/amd64`，产物放 `jniLibs/x86_64/`。

## 说明
- 应用侧通过反射调用 `xraymobile.Xraymobile`，因此 **没有这两个产物时也能正常编译**，
  只是设置页会显示「未打包进本安装包」。
- `proguard-rules.pro` 里已加 `-keep class xraymobile.**` 与 `go.**`，否则 R8 会裁掉反射目标。
- 许可证：Xray-core 为 MPL-2.0（本仓库未修改其源码）；gomobile 为 BSD-3-Clause。
  再分发时请保留对应声明。
