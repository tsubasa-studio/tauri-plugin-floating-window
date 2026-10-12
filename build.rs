const COMMANDS: &[&str] = &[
  "check_permission",
  "request_permission",
  "create",
  "show",
  "hide",
  "move_window",
  "destroy",
  // Implemented by Tauri's native Plugin base class; ACL runs before dispatch.
  "register_listener",
  "remove_listener",
];

fn main() {
  tauri_plugin::Builder::new(COMMANDS)
    .android_path("android")
    .ios_path("ios")
    .build();
}
