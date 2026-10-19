# AirPlay TV

在同一個 Android TV App 接收 AirPlay 與 iPhone 內建 Apple TV 遙控器。
共用電視名稱、簡約介面與背景服務，提供繁體中文／英文及分層設定。

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

## iPhone 遙控器

開啟「控制中心 → Apple TV 遙控器」，選擇同一個電視名稱，輸入電視顯示的遙控器配對碼。
AirPlay 與遙控器分別驗證。需要方向按鈕時，到 iPhone「設定 → 輔助使用 → Apple TV 遙控器」
啟用方向按鈕；實際選項依 iOS 而定。

電視控制需具備輸入管道。內建本機 ADB 僅連至電視自己的 loopback，需要開發者偵錯及一次信任授權；
App 不會自行開啟偵錯或修改系統安全設定。也提供選用的系統無障礙服務入口，能力依韌體而異。

## 待機與畫面

「保持待機可連線」讓兩個接收功能共用一個 CPU 喚醒鎖。「省電」在沒有連線時釋放喚醒鎖，
部分電視可能因此暫停探索或無法自動喚醒。投屏結束會釋放解碼器與 GL 資源。
實際耗電需在目標電視量測；完全斷電無法接收。

色彩範圍預設「自動」，保留來源與解碼器的判斷；可手動選擇完整／有限範圍協助排查。
本機灰階測試頁可與瀏覽器投屏的[同色階頁面](grayscale.html)比對，不能證明投屏無壓縮，也無法補回來源壓縮損失。
解碼器輸出色彩資訊會記錄於診斷紀錄。

## 從兩個 App 升級

使用相同簽署金鑰可更新 AirPlay TV 並保留其設定。原 Remote TV 屬於另一個 Android 沙箱，
配對資料及偵錯授權無法自動搬移；請先完成整合版配對與授權，驗證後再停止或移除獨立 Remote TV，
避免重複接收器。名稱只需設定一次，兩個接收功能同步使用。

## 相容性

Android 8.0／API 26 以上；本版僅提供 ARM 32 位元 `armeabi-v7a`。
解析度、幀率、硬體解碼、聲音延遲可調整。受 DRM 保護的影片不支援。
此專案不保證所有 Android TV 韌體與 Apple 裝置版本皆相容。

## 自行建置

使用 JDK 21、Rust 1.98.0、Android SDK 37.0、NDK 27.0.12077973、CMake 3.22.1，設定 `ANDROID_HOME`：

```sh
git clone --recurse-submodules https://github.com/henry753951/airplay-tv.git
cd airplay-tv
rustup target add armv7-linux-androideabi
scripts/build-remote.sh
(cd remote && cargo test --locked -p atv-core)
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --max-workers 4
```

Release 輸出未簽章；自行安裝前需簽署，或使用 `assembleDebug` 開發。
詳見 [英文文件](../README.md)、[安全說明](../SECURITY.md) 與 [發布方式](RELEASING.md)。

GPL-3.0。採用 android-airplay-server、UxPlay、atv-core 與 pyatv 協定參考；完整來源與授權列於
[第三方聲明](../THIRD_PARTY_NOTICES.md)。本專案與 Apple Inc. 無關。
