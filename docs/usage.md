# 使用说明

本仓库是一个 Tauri 悬浮窗插件 `tauri-plugin-floating-window`，主要用于移动端悬浮窗功能开发。当前仓库支持 Android 和 iOS 平台，Android 端已完成实现，iOS 端也已接入插件绑定。

## 目录概览

- 根目录：插件主代码
  - `src/`：Rust 插件实现
  - `guest-js/`：JS/TS API 封装
  - `dist-js/`：打包后发布产物
- `examples/tauri-app/`：Svelte + Vite 示例 App

## 运行环境

- Node.js + bun
- Rust toolchain（≥ 1.77.2）
- Android：Android Studio、Android SDK / NDK、JDK 17 或 21；本示例已用 JDK 21 验证
- iOS：Xcode / macOS（若编译 iOS 示例）

## 快速开始

**插件根目录不能直接启动 App。** `dev` 和 `tauri` 脚本都在
`examples/tauri-app/package.json` 中。本文使用示例依赖里的 `@tauri-apps/cli`，
通过 `bun run tauri` 调用，无需另装全局 `cargo-tauri`。

### 1. 构建插件 JS API

在仓库根目录安装依赖并构建插件：

```powershell
bun install
bun run build
```

### 2. 安装示例 App 依赖

```powershell
cd examples\tauri-app
bun install
```

### 3. 启动示例 App

#### 3.1 桌面开发模式

以下命令均在 `examples/tauri-app` 目录执行。桌面预览前端可使用：

```powershell
bun run tauri dev
```

这会同时启动前端和 Rust 后端。Android 悬浮窗功能需要在 Android 上验证。

> 注意：`bun tauri dev` 只是用于桌面预览，不等同于 Android/iOS 的移动端运行方式。

#### 3.2 Android 运行

先在当前 PowerShell 配置 Java 和 SDK。下面是本机已验证的安装路径；
其他机器请换成自己的路径，NDK 版本以 SDK Manager 中实际安装的版本为准。

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
$env:ANDROID_HOME = "D:\Software\Development\Android\SDK"
$env:NDK_HOME = "$env:ANDROID_HOME\ndk\27.3.13750724"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

& "$env:JAVA_HOME\bin\java.exe" -version
adb devices -l
```

这些赋值只影响当前终端及其启动的进程。Android Studio 的
Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK
也需选择 JDK 17 或 21；默认随 Studio 安装的 JDK 25 不适用于当前 Gradle 8.14.3。

**使用 Android Studio 调试真机或模拟器：**

```powershell
# 在 examples/tauri-app 下；选择电脑的 WLAN/以太网地址
bun run tauri android dev --open --force-ip-prompt

# 或明确指定该地址（示例 IP，换网络后需重新确认）
# bun run tauri android dev --open --host 192.168.50.159
```

命令会启动 Vite、准备 Rust 库并打开 `src-tauri/gen/android`。
**保持这个终端运行**，然后在 Studio 里等待 Gradle 同步完成，选择 `app`
运行配置和目标设备，点击 Run 或 Debug。仅手动打开 Gradle 目录而没有运行
Tauri CLI，无法完整准备 Rust 构建和开发服务。

- 真机：开启开发者选项和 USB 调试，USB 连接后在手机上确认调试授权。
  `adb devices -l` 应显示 `device`；`unauthorized` 表示尚未确认授权。
- 模拟器：在 Studio 的 Device Manager 启动 AVD，再从目标设备下拉框选择它。
- 使用电脑局域网 IP 时，真机需能访问该地址，通常让手机和电脑连接同一 Wi-Fi。
  不要选择代理、VPN 或 WSL 的虚拟网卡地址。本机自动选择曾命中 Mihomo 的
  `28.0.0.1`，应改选实际 WLAN 地址。示例 Vite 已读取 `TAURI_DEV_HOST`。

**直接从终端运行到设备：**

```powershell
bun run tauri android dev --force-ip-prompt
```

已有 `src-tauri/gen/android` 时无需重新初始化；仅新环境缺少该目录时先运行
`bun run tauri android init`。首次编译 Rust/Gradle 依赖可能需要几分钟。

App 启动后先点 Request Permission，在系统页允许悬浮窗，再返回 App 点 Create。
手机端授权弹窗需手动确认。调试前端可在电脑 Chrome 打开 `chrome://inspect/#devices`；
原生日志和 Kotlin 断点使用 Android Studio 的 Logcat/Debug。

官方流程参考：[Tauri 移动端开发与 Android Studio](https://v2.tauri.app/develop/#using-xcode-or-android-studio)。

#### 3.2.1 常见启动错误

| 报错或现象 | 处理 |
| --- | --- |
| `cargo: no such command: tauri` | 使用示例目录里的 `bun run tauri android dev --open`；Bun CLI 和 Cargo 子命令是两种安装方式 |
| `Script not found "dev"` | 当前在插件根目录；先 `cd examples\tauri-app` |
| `Unsupported class file major version 69` | 使用 JDK 21，同时核对 Studio 的 Gradle JDK |
| SDK/NDK 找不到，或路径混用 C 盘和 D 盘 | 核对 `ANDROID_HOME`、`NDK_HOME` 和 `src-tauri/gen/android/local.properties` 的 `sdk.dir` 指向实际安装位置 |
| 设备白屏、连不上 Vite | 确认终端仍在运行、设备能访问终端打印的 `http://<电脑IP>:1420/`，并选对 `--host` 地址 |
| `Port 1420 is already in use` | 先停止自己之前启动的 Vite/Tauri 开发进程，再启动一份 |

#### 3.3 iOS 运行（macOS 环境）

要在 iOS 模拟器上运行示例 App，请使用：

```bash
bun run tauri ios dev
```

> 如果只想预览前端界面，可运行 `bun dev`，但此时 Tauri IPC 不可用，悬浮窗插件功能不能正常工作。

## 使用插件 API

在前端代码中通过 `tauri-plugin-floating-window-api` 导入：

```ts
import {
  checkPermission,
  requestPermission,
  create,
  show,
  hide,
  moveWindow,
  destroy,
  onFloatingWindowClick,
  onFloatingWindowClosed,
} from "tauri-plugin-floating-window-api";
```

### 主要方法

- `checkPermission(): Promise<boolean>`
  - 检查移动端悬浮窗权限。Android 上检查 `SYSTEM_ALERT_WINDOW`。
- `requestPermission(): Promise<boolean>`
  - 请求悬浮窗权限。Android 上会跳转系统权限设置页。
- `create(config?: CreateConfig): Promise<string>`
  - 创建并显示悬浮窗，初始化完成后返回 `tag`。未授权时 reject；需授权后重新调用。
- `show(tag?: string): Promise<void>`
  - 显示浮窗。
- `hide(tag?: string): Promise<void>`
  - 隐藏浮窗。
- `moveWindow(tag: string, x: number, y: number, animated?: boolean): Promise<void>`
  - 移动悬浮窗到指定坐标，支持动画切换。
- `destroy(tag?: string): Promise<void>`
  - 销毁悬浮窗。
- `onFloatingWindowClick(callback: (tag: string) => void): Promise<UnlistenFn>`
  - 通过原生插件 channel 监听 WebView 轻点事件；拖动、取消不触发，页面按钮仍可响应。调用返回的函数取消监听。
- `onFloatingWindowClosed(callback: (tag: string) => void): Promise<UnlistenFn>`
  - 监听页面调用 `AndroidFloatingWindow.close()` 的关闭事件，取消监听方式相同。

事件订阅和注销需要 `floating-window:allow-register-listener` 与
`floating-window:allow-remove-listener`，已包含在 `floating-window:default` 中。
仅配置 `core:default` 无法调用本插件的监听命令。

### create 配置参数

```ts
interface CreateConfig {
  tag?: string;
  width?: number;
  height?: number;
  x?: number;
  y?: number;
  url?: string;
  notification?: {
    title?: string;
    body?: string;
    channelName?: string;
    channelDescription?: string;
  };
}
```

- `tag`: 浮窗标识，默认值为 `default`
- `width` / `height`: 物理像素，必须为正数；省略时分别使用 260dp / 320dp 对应的像素大小
- `x` / `y`: 非负物理像素，默认 (0, 0)，与 `moveWindow` 单位一致；相对于 FloatingX 可用区域，受系统 inset 和屏幕边界约束
- `url`: 加载页面地址，用于移动端 WebView 内容。打包后优先匹配真实文件（例如 `/floating.html`），仅不存在且无文件扩展名的 HTML 主框架导航回退到 `index.html`
- `notification`: 前台服务的应用级文案配置。最后一次 `create` 提供的配置生效，`show` 保留配置；空白或省略字段使用应用名称和中性文案。保存至应用私有偏好，在服务重建时恢复；省略整个配置重新创建窗口可恢复默认值

### 简单示例

```ts
const granted = await checkPermission();
if (!granted) {
  await requestPermission();
  // 此调用只打开设置页，不等待用户完成授权。
  // 返回应用后重新检查 checkPermission()，确认后再调用 create()。
  return;
}

const tag = await create({
  width: 240,
  height: 240,
  x: 100,
  y: 100,
});

await show(tag);
await moveWindow(tag, 200, 300, true);
```

## 构建生产包

打包命令也在 `examples/tauri-app` 执行。当前示例使用模板 identifier
`com.tauri.dev`，Tauri 的正式打包检查会拒绝该值；打包前需改成自己的应用标识，
并同步原生 Android 项目的包名/namespace。这不影响上面的 Android 开发命令。

```powershell
# 桌面包
bun run tauri build
# Android APK
bun run tauri android build --apk
```

## 说明

- `examples/tauri-app/` 的 `package.json` 使用 `file:../../` 来依赖本插件 JS API。
- Android 端已实现悬浮窗权限与窗口生命周期管理。
- iOS 端插件绑定已接入，需在 Xcode/macOS 环境中编译测试。

## 宿主通知配置与回归示例

```ts
await create({
  url: "http://tauri.localhost/floating.html",
  notification: {
    title: "My App is active",
    body: "Tap to return to My App",
    channelName: "My App overlay status",
    channelDescription: "Keeps the app overlay visible",
  },
});
```

应用品牌和业务措辞由宿主提供。默认标题是宿主应用名称；默认正文说明
悬浮窗正在运行并可点击返回应用。前台服务保持 `START_NOT_STICKY`，保存
文案不等于自动恢复被杀死的窗口。

示例提供默认/自定义文案切换、两组几何参数、并发创建及点击/关闭日志。
完整操作见 [示例回归检查](../examples/tauri-app/REVIEW-REGRESSION.md)。
