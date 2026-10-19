# AirPlay TV

為 Android 電視設計的 AirPlay 接收器：簡約介面、方向鍵操作、繁體中文設定，
以及獨立的全螢幕配對碼畫面。

[下載 APK](https://github.com/henry753951/airplay-tv/releases/latest) · [English](../README.md)

## 使用方式

1. 在電視安裝 Releases 的簽章 APK，開啟 AirPlay TV 並允許需要的權限。
2. 手機、Mac 與電視連到同一區域網路，保持配對碼驗證開啟。
3. iPhone／iPad：「控制中心 → 螢幕鏡像輸出 → AirPlay TV」，輸入電視上的代碼。
4. Mac：「控制中心 → 螢幕鏡像輸出」；需要延伸桌面時，到「系統設定 → 顯示器」
   選擇延伸模式，實際選項依 macOS 與接收相容性而定。

支援背景接收、開機自啟及連線時顯示接收畫面。待機能否保持網路或喚醒螢幕，
取決於電視韌體與電源設定；完全關閉或拔除電源時無法接收。
啟用「連線時開啟」可能在輸入配對碼之前點亮螢幕。

## 相容性

Android 6.0／API 23 以上；本版僅提供 ARM 32 位元 `armeabi-v7a`。
解析度、幀率、硬體解碼、聲音延遲可調整。受 DRM 保護的影片不支援。
此專案不保證所有 Android TV 韌體與 Apple 裝置版本皆相容。

## 自行建置

使用 JDK 21、Android SDK 37.0、NDK 27.0.12077973、CMake 3.22.1，設定 `ANDROID_HOME`：

```sh
git clone --recurse-submodules https://github.com/henry753951/airplay-tv.git
cd airplay-tv
./gradlew :app:assembleRelease :app:lintRelease --max-workers 4
```

Release 輸出未簽章；自行安裝前需簽署，或使用 `assembleDebug` 開發。
詳見 [英文文件](../README.md)、[安全說明](../SECURITY.md) 與 [發布方式](RELEASING.md)。

GPL-3.0。採用 android-airplay-server 與 UxPlay；完整來源與授權列於
[第三方聲明](../THIRD_PARTY_NOTICES.md)。本專案與 Apple Inc. 無關。
