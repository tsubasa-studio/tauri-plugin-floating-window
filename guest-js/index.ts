import { addPluginListener, invoke } from "@tauri-apps/api/core";
import type { UnlistenFn } from "@tauri-apps/api/event";

export interface CreateConfig {
  tag?: string;
  /** Physical pixels; defaults to 260dp converted for the device. */
  width?: number;
  /** Physical pixels; defaults to 320dp converted for the device. */
  height?: number;
  /** Physical pixels from the overlay work area origin; defaults to 0. */
  x?: number;
  /** Physical pixels from the overlay work area origin; defaults to 0. */
  y?: number;
  url?: string;
  /** App-wide notification: latest create wins; blank/omitted fields use neutral defaults. */
  notification?: NotificationConfig;
}

export interface NotificationConfig {
  title?: string;
  body?: string;
  channelName?: string;
  channelDescription?: string;
}

/** 检查是否已授予悬浮窗权限 (SYSTEM_ALERT_WINDOW) */
export async function checkPermission(): Promise<boolean> {
  const r = await invoke<{ granted: boolean }>(
    "plugin:floating-window|check_permission",
  );
  return r.granted;
}

/** 请求悬浮窗权限，会跳转到系统设置页 */
export async function requestPermission(): Promise<boolean> {
  const r = await invoke<{ granted: boolean }>(
    "plugin:floating-window|request_permission",
  );
  return r.granted;
}

/** 创建并显示悬浮窗，返回浮窗 tag */
export async function create(config?: CreateConfig): Promise<string> {
  const r = await invoke<{ tag: string }>("plugin:floating-window|create", {
    payload: config ?? {},
  });
  return r.tag;
}

/** 显示悬浮窗 */
export async function show(tag?: string): Promise<void> {
  await invoke("plugin:floating-window|show", { payload: { tag } });
}

/** 隐藏悬浮窗 */
export async function hide(tag?: string): Promise<void> {
  await invoke("plugin:floating-window|hide", { payload: { tag } });
}

/** 移动悬浮窗到指定坐标 */
export async function moveWindow(
  tag: string,
  x: number,
  y: number,
  animated?: boolean,
): Promise<void> {
  await invoke("plugin:floating-window|move_window", {
    payload: { tag, x, y, animated },
  });
}

/** 销毁悬浮窗 */
export async function destroy(tag?: string): Promise<void> {
  await invoke("plugin:floating-window|destroy", { payload: { tag } });
}

/** 监听悬浮窗点击事件 */
export async function onFloatingWindowClick(
  callback: (tag: string) => void,
): Promise<UnlistenFn> {
  return listenForTag("floating-window-click", callback);
}

/** 监听悬浮窗通过 AndroidFloatingWindow.close() 关闭的事件 */
export async function onFloatingWindowClosed(
  callback: (tag: string) => void,
): Promise<UnlistenFn> {
  return listenForTag("floating-window-closed", callback);
}

async function listenForTag(
  event: string,
  callback: (tag: string) => void,
): Promise<UnlistenFn> {
  const listener = await addPluginListener<{ tag: string }>(
    "floating-window",
    event,
    (data) => callback(data.tag),
  );
  return () => listener.unregister();
}
