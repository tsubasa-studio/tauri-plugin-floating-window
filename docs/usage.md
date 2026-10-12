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
- Android：Android SDK / NDK
- iOS：Xcode / macOS（若编译 iOS 示例）

## 快速开始

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

如果希望在桌面上调试前端和插件逻辑，可使用：

```powershell
bun tauri dev
```

这会同时启动前端和 Rust 后端，适合快速验证插件 API 调用。

> 注意：`bun tauri dev` 只是用于桌面预览，不等同于 Android/iOS 的移动端运行方式。

#### 3.2 Android 运行

要在 Android 设备/模拟器上运行示例 App，请使用：

```powershell
cargo tauri android dev
```

如果你更习惯 Android Studio，也可以直接打开生成的 Android 项目：

`examples/tauri-app/src-tauri/gen/android`

在 Android Studio 中导入该目录为 Gradle 项目，然后运行或调试即可。

#### 3.3 iOS 运行（macOS 环境）

要在 iOS 模拟器上运行示例 App，请使用：

```bash
cargo tauri ios dev
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

```powershell
bun tauri build
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
