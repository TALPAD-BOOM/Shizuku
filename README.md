# Shizuku TALPAD edition 学而思定制版

Fork from [yangFenTuoZi/Shizuku](https://github.com/yangFenTuoZi/Shizuku)

为学而思学习机定制的 `Shizuku`，解决了学而思学习机系统屏蔽了通知标题、正文和互动部分导致 `Shizuku` 无法进行无线调试配对的问题

- 需要配合安卓的“自由窗口模式”（`强制将 Activity 设为可调整大小`、`允许以多窗口模式显示不可调整大小的应用`、`启用可自由调整的窗口`）
- 打开主界面会同时以窗口形式打开设置主界面及 `Shizuku` 主界面，无线调试配对输入配对码部分改为以对话框交互
- 同时修复 `Shizuku 13.6.0` 版本引入的 `MT/NP 管理器` 启动时无法授权 Shell 权限的问题
  （修复在 [Shizuku-API](https://gitea.fumor.top/TALPAD-BOOM/Shizuku-API)，为避免与原仓库产生冲突，未更新 Git Submodules，编译时需要手动将子模块替换为上述仓库的 `talpadboom` 分支文件）

## 免责声明

此为 Shizuku 的 **分支版本**。若您需寻找 Rikka 开发的官方 Shizuku，此处并非正确渠道。  
请访问 [**_官方仓库_**](https://github.com/RikkaApps/Shizuku)

### 本仓库的变更

- ~~随机化 `/data/local/tmp/shizuku` 目录名称~~
- ~~自动删除 `/data/local/tmp/shizuku_starter` 文件~~
- 在 userdebug ROM 上启用 ADB root 权限
- 支持非 Root 设备自启动
- 支持自定义 ADB TCP/IP 端口

### 自启动功能用法

1. 按照无线 ADB 配对流程配置 Shizuku
2. 在 `设置` 中启用 `开机启动（无线调试）`
   - 启用前需先授予 `WRITE_SECURE_SETTINGS` 权限（可通过 `rish` 或使用电脑通过 ADB 完成 / **在 Shizuku 启动时通过 Manager 自动授权**）
   - 执行以下命令 `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`


> [!CAUTION]
> `WRITE_SECURE_SETTINGS` 为高危权限，仅建议明确风险后启用。开发者对后续可能产生的后果不承担责任。

> [!NOTE]
> 服务自动重启功能未经充分测试

## Background

When developing apps that requires root, the most common method is to run some commands in the su shell. For example, there is an app that uses the `pm enable/disable` command to enable/disable components.

This method has very big disadvantages:

1. **Extremely slow** (Multiple process creation)
2. Needs to process texts (**Super unreliable**)
3. The possibility is limited to available commands
4. Even if ADB has sufficient permissions, the app requires root privileges to run

Shizuku uses a completely different way. See detailed description below.

## User guide & Download

<https://shizuku.rikka.app/>

## How does Shizuku work?

First, we need to talk about how app use system APIs. For example, if the app wants to get installed apps, we all know we should use `PackageManager#getInstalledPackages()`. This is actually an interprocess communication (IPC) process of the app process and system server process, just the Android framework did the inner works for us.

Android uses `binder` to do this type of IPC. `Binder` allows the server-side to learn the uid and pid of the client-side, so that the system server can check if the app has the permission to do the operation.

Usually, if there is a "manager" (e.g., `PackageManager`) for apps to use, there should be a "service" (e.g., `PackageManagerService`) in the system server process. We can simply think if the app holds the `binder` of the "service", it can communicate with the "service". The app process will receive binders of system services on start.

Shizuku guides users to run a process, Shizuku server, with root or ADB first. When the app starts, the `binder` to Shizuku server will also be sent to the app.

The most important feature Shizuku provides is something like be a middle man to receive requests from the app, sent them to the system server, and send back the results. You can see the `transactRemote` method in `rikka.shizuku.server.ShizukuService` class, and `moe.shizuku.api.ShizukuBinderWrapper` class for the detail.

So, we reached our goal, to use system APIs with higher permission. And to the app, it is almost identical to the use of system APIs directly.

## Developer guide

### API & sample

https://github.com/RikkaApps/Shizuku-API

### Migrating from pre-v11

> Existing applications still works, of course.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Attention

1. ADB permissions are limited

   ADB has limited permissions and different on various system versions. You can see permissions granted to ADB [here](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Before calling the API, you can use `ShizukuService#getUid` to check if Shizuku is running user ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.

2. Hidden API limitation from Android 9

   As of Android 9, the usage of the hidden APIs is limited for normal apps. Please use other methods (such as <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 & ADB

   At present, the way Shizuku service gets the app process is to combine `IActivityManager#registerProcessObserver` and `IActivityManager#registerUidObserver` (26+) to ensure that the app process will be sent when the app starts. However, on API 26, ADB lacks permissions to use `registerUidObserver`, so if you need to use Shizuku in a process that might not be started by an Activity, it is recommended to trigger the send binder by starting a transparent activity.

4. Direct use of `transactRemote` requires attention

   * The API may be different under different Android versions, please be sure to check it carefully. Also, the `android.app.IActivityManager` has the aidl form in API 26 and later, and `android.app.IActivityManager$Stub` exists only on API 26.

   * `SystemServiceHelper.getTransactionCode` may not get the correct transaction code, such as `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` does not exist on API 25 and there is `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (this situation has been dealt with, but it is not excluded that there may be other circumstances). This problem is not encountered with the `ShizukuBinderWrapper` method.

## Developing Shizuku itself

### Build

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. Be aware that, in Android Studio, "Run/Debug configurations" - "Always install with package manager" should be checked, so that the server will use the latest code.

## License

All code files in this project are licensed under Apache 2.0

Under Apache 2.0 section 6, specifically:

* You are **FORBIDDEN** to use `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.

* You are **FORBIDDEN** to use `Shizuku` as app name or use `moe.shizuku.privileged.api` as application id or declare `moe.shizuku.manager.permission.*` permission.

## 鸣谢

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)

- [pixincreate/Shizuku](https://github.com/pixincreate/Shizuku)

- ...
