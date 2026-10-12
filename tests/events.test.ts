import { afterEach, expect, test } from "bun:test";
import { clearMocks, mockIPC } from "@tauri-apps/api/mocks";
import { onFloatingWindowClick, onFloatingWindowClosed } from "../guest-js/index";

// Use the real Tauri API and mock only the native IPC boundary.
globalThis.window = globalThis as unknown as Window & typeof globalThis;
afterEach(clearMocks);

for (const [event, subscribe] of [
  ["floating-window-click", onFloatingWindowClick],
  ["floating-window-closed", onFloatingWindowClosed],
] as const) {
  test(`${event} receives native channel payloads and unregisters`, async () => {
    const calls: string[] = [];
    let channel: { id: number; onmessage: (data: { tag: string }) => void };
    mockIPC((command, args) => {
      calls.push(command);
      expect(args?.event).toBe(event);
      if (command === "plugin:floating-window|register_listener") {
        channel = args?.handler as typeof channel;
      } else if (command === "plugin:floating-window|remove_listener") {
        expect(args?.channelId).toBe(channel.id);
      } else {
        throw new Error(`Unexpected IPC command: ${command}`);
      }
    });
    const tags: string[] = [];
    const unlisten = await subscribe((tag) => tags.push(tag));
    channel!.onmessage({ tag: "second-window" });
    expect(tags).toEqual(["second-window"]);
    await unlisten();
    expect(calls).toEqual([
      "plugin:floating-window|register_listener",
      "plugin:floating-window|remove_listener",
    ]);
  });
}
