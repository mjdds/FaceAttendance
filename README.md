# 智能刷脸考勤系统 FaceAttendance

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-24-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)]()
[![Co-created with CodeBuddy](https://img.shields.io/badge/Co--created%20with-CodeBuddy-5B4BDB.svg)](https://www.codebuddy.ai/)

一款完全离线运行的 Android 刷脸考勤应用：端侧人脸检测与特征比对、多人同时独立签到、
考勤记录多维筛选、已录入人脸库管理。人脸数据不出设备，无需服务器，开箱即用。

## 功能特性

- **多人同框签到**：画面中每张人脸独立累计在屏时长，累计满 0.1 秒即判定签到成功，
  多人一起入镜互不影响；同一人当天只记一次，重复入镜有宽限期不会清零误判。
- **端侧识别，离线可用**：ML Kit 人脸检测 + FaceNet（TFLite）128 维特征 L2/余弦比对，
  识别稳定后自动采集样本补充特征库，越用越准。
- **考勤记录多维筛选**：可任意组合
  - 日期：单日（含前后翻页）/ 日期区间 / 全部时间；
  - 班级：全部班级 / 指定班级 / 未分班；
  - 人物：全部人物或指定学生。
  - 例如查"某天某班某号同学的记录""某位同学的全部签到""某班选定日期内的签到"。
- **已录入人脸库**：Dock 栏独立页面，支持按班级筛选、姓名/班级搜索，
  可直接编辑每个人的姓名与班级（改名/调班自动同步历史记录，**人脸特征数据绝不改动**）。
- **本地数据库**：人员、特征、样本图、考勤记录均存于本机 SQLite；
  支持手动导出/导入 JSON，切后台自动备份到公共目录，卸载重装可一键恢复。
- **语音播报**：系统离线 TTS、百度智能云（官方 API，自配 Key）等多引擎可选。
- **液态玻璃 Dock**：正常模式为真实模糊折射的液态玻璃底部导航，低端机可在设置中开启省流模式。

## 下载安装

- 打开 [最新 Release](https://github.com/mjdds/FaceAttendance/releases/latest)，
  下载页面中最新的 APK 附件（约 96 MB）。
- 传到 Android 7.0（minSdk 24）及以上手机安装，首次安装需允许"安装未知来源应用"。
- 首次启动授予相机权限即可使用，签到与识别全程离线，无需联网。

## 技术栈

- 语言：Kotlin 2.3，100% Jetpack Compose（单 Activity）
- 相机：CameraX（ImageAnalysis 分析流）
- 人脸检测：Google ML Kit Face Detection
- 特征提取/比对：TensorFlow Lite + FaceNet（160×160 → 128 维）
- 存储：原生 SQLite（`SQLiteOpenHelper`）
- 后台：Kotlin Coroutines；网络：OkHttp（仅用于在线语音合成）
- UI：Material 3、AndroidLiquidGlass（液态玻璃 Dock）

## 隐私说明

- 人脸图片、特征向量、考勤记录**全部保存在本机数据库**，不经过任何应用自有服务器。
- 唯一的网络请求来自"在线语音播报"：仅把待朗读的文字发送给你选择的语音服务，
  不发送任何人脸信息。选择"系统语音（离线）"后完全不联网。
- 不含任何统计 SDK、广告 SDK、崩溃上报 SDK。

## 环境要求

- JDK 17（Gradle 8.13 / AGP 8.13）
- Android SDK：compileSdk 36，minSdk 24（Android 7.0+），targetSdk 36

## 构建方法

需要 JDK 17，源码构建执行 `./gradlew assembleRelease`（Windows 用 `gradlew.bat`），
未配置签名时自动使用 debug 签名，产物在 `app/build/outputs/apk/release/`。
正式签名的配置方法见 `keystore.properties.example` 中的注释
（`keystore.properties` 与 `*.keystore` 已被 `.gitignore` 排除，不会提交）。

## 开源许可

本项目以 [Apache License 2.0](LICENSE) 发布，可自由使用、修改、分发（含商用），
唯需保留原始版权与许可声明。

## 第三方组件与合规提示

详见 [NOTICE](NOTICE)，此处仅列重点：

1. 所有第三方库（AndroidX、CameraX、ML Kit、TensorFlow Lite、OkHttp、Coroutines、
   AndroidLiquidGlass 等）均为 **Apache License 2.0**，已逐一向 Maven Central 构件
   POM 与上游仓库核实，与本项目许可证兼容。
2. `app/src/main/assets/facenet.tflite` 权重源自第三方转换的 FaceNet 模型，
   其训练数据集可能仅限研究用途，**商业分发前请自行替换为授权清晰的同规格模型**
   （同名替换文件即可，无需改代码）。
3. 有道词典、百度翻译网页版、Edge 在线语音属于非公开授权接口，
   相关代码仅作学习研究演示，正式或商业使用请改用官方授权服务（如已内置的百度智能云引擎）。

## 商标声明

- 本项目的液态玻璃视觉效果基于开源社区项目
  [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
  （Apache License 2.0，版权归原作者 Kyant0 所有）实现。
- “Liquid Glass”是 **Apple Inc.** 的商标及界面设计语言名称。本项目是**独立的第三方作品**，
  与 Apple Inc. 没有任何关联，未获得其认可、批准或赞助；项目名称 **FaceAttendance**
  本身不含任何第三方商标。
- Android、Google Play、ML Kit、TensorFlow 是 Google LLC 或相关权利人的商标，
  Kotlin 是 JetBrains s.r.o. 的商标；文中提及仅用于说明技术来源与兼容性。

## 共创声明

本项目由 **mjdds** 与 AI 编程助手 [CodeBuddy](https://www.codebuddy.ai/) 共创完成：

- **CodeBuddy** 参与了代码编写、技术方案与项目文档的生成；
- **mjdds** 负责产品设计、真机测试、审核修订与发布维护。

如发现侵权或合规问题，欢迎提 Issue，维护者会及时处理。

## 贡献

欢迎提交 Issue 与 Pull Request；提交代码即表示你同意以 Apache License 2.0 授权你的贡献。
