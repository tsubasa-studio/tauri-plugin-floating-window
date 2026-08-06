# 使用说明

本仓库是一个 Tauri 插件 `tauri-plugin-floating-window`，附带一个 Svelte + Vite 示例 App。

## 目录结构

- 根目录：插件本体（Rust `src/`，JS API `guest-js/`，构建产物 `dist-js/`）
- `examples/tauri-app/`：使用该插件的示例 App

## 前置环境

- Node.js + pnpm
- Rust toolchain（≥ 1.77.2）
- Windows：Microsoft C++ Build Tools（MSVC）+ WebView2

## 快速开始

### 1. 构建插件 JS API

示例 App 通过 `file:../../` 依赖插件 JS，需先在根目录构建：

```powershell
pnpm install
pnpm build
```

### 2. 安装示例 App 依赖

```powershell
cd examples\tauri-app
pnpm install
```

### 3. 预览前端

**方式 A：仅前端 UI（Vite dev server）**

```powershell
pnpm dev
```

访问 http://localhost:1420。注意 Tauri IPC 不可用，"Ping" 按钮会报错。

**方式 B：完整 Tauri App（推荐，可验证插件功能）**

```powershell
pnpm tauri dev
```

同时启动 Vite 与 Rust 后端，弹出原生窗口，此时插件功能可正常调用。

## 构建生产包

```powershell
pnpm tauri build
```
