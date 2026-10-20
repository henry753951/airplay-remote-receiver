<img src="assets/icon.svg" width="88" alt="AirPlay & Remote Receiver 圖示" />

# AirPlay & Remote Receiver

將 iPhone、iPad 或 Mac 的畫面與音訊傳送到 Android TV，並透過 iPhone 控制中心內建的
Apple TV 遙控器操作電視。

**[下載 APK · v0.2.0-rc6](https://github.com/henry753951/airplay-remote-receiver/releases/tag/v0.2.0-rc6)** ·
[所有版本](https://github.com/henry753951/airplay-remote-receiver/releases) · [English](../README.md)

## 功能

- AirPlay 螢幕鏡像與音訊串流。
- 支援 iPhone 內建 Apple TV 遙控器，無需另外安裝手機 App。
- Mac 延伸桌面，實際支援依傳送端而定。
- 適合電視操作的介面、清楚的遙控焦點、流暢轉場與螢幕配對碼。
- 繁體中文與英文。
- 投屏與遙控共用同一個電視名稱。
- 背景接收、開機自啟與支援機型的螢幕喚醒。
- 分類清楚的影像、音訊、遙控與電源設定。

## 系統需求

- Android 8.0／API 26 以上。
- ARM 32 位元（`armeabi-v7a`）。
- 電視與 Apple 裝置連到同一個網路，並允許裝置探索。

## 開始使用

1. 從 [Releases](https://github.com/henry753951/airplay-remote-receiver/releases) 安裝簽章 APK，開啟 **AirPlay & Remote Receiver**。
2. 在「設定」選擇電視名稱，依畫面提示完成權限設定。
3. 在 iPhone／iPad 開啟「控制中心 → 螢幕鏡像輸出」，選擇你的電視。
4. 輸入電視顯示的配對碼。

Mac 請使用「控制中心 → 螢幕鏡像輸出」。需要延伸桌面時，到「系統設定 → 顯示器」選擇延伸模式；
實際選項依 macOS 與裝置相容性而定。

在電視選擇「中斷投放」、投放時按返回鍵，或回到電視首頁，都會結束目前投放。AirPlay 探索與已啟用的遙控接收服務仍會待命，供下次連線使用。

### 使用 iPhone 遙控

開啟「控制中心 → Apple TV 遙控器」，選擇你的電視並輸入配對碼。
首次使用請依電視提示完成控制授權；部分機型需先在開發人員設定啟用偵錯，才能使用遙控功能。

偏好方向按鈕時，可在 iPhone「設定 → 輔助使用 → Apple TV 遙控器」啟用；
實際選項依 iOS 版本而定。[Apple 使用指南](https://support.apple.com/en-gb/guide/iphone/iph2ec3a3aa6/ios)

## 設定

- **影像：**解析度、幀率與色彩選項。
- **音訊：**音量與緩衝。
- **遙控：**裝置配對與電視控制。
- **電源：**背景接收、開機自啟與待機偏好。

## 相容性

播放品質、編解碼格式與遙控功能依電視韌體及 Apple 裝置版本而異。
部分電視在鏡像時可能無法正確呈現接近黑色或白色的灰階。不支援受 DRM 保護的影片。

背景接收與螢幕喚醒需要電視在待機時保持網路連線。完全關閉或拔除電源時無法接收。

## 自行建置

使用 JDK 21、Rust 1.98.0、Android SDK 37.0、NDK 27.0.12077973、CMake 3.22.1，設定 `ANDROID_HOME`：

```sh
git clone --recurse-submodules https://github.com/henry753951/airplay-remote-receiver.git
cd airplay-remote-receiver
rustup target add armv7-linux-androideabi
scripts/build-remote.sh
(cd remote && cargo test --locked -p atv-core)
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --max-workers 4
```

Release 輸出未簽章；自行安裝前需簽署，或使用 `assembleDebug` 開發。
詳見 [英文文件](../README.md)、[安全說明](../SECURITY.md) 與 [發布方式](RELEASING.md)。

GPL-3.0。採用 android-airplay-server、UxPlay、atv-core 與 pyatv 協定參考；完整來源與授權列於
[第三方聲明](../THIRD_PARTY_NOTICES.md)。本專案與 Apple Inc. 無關。
