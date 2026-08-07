<script>
  import Greet from './lib/Greet.svelte'
  import {
    checkPermission,
    requestPermission,
    create,
    show,
    hide,
    moveWindow,
    destroy,
    onFloatingWindowClick,
  } from 'tauri-plugin-floating-window-api'

  let response = $state('')
  let permissionGranted = $state(false)

  function log(msg) {
    response += `[${new Date().toLocaleTimeString()}] ${msg}\n`
  }

  async function _checkPermission() {
    try {
      permissionGranted = await checkPermission()
      log(`Permission: ${permissionGranted}`)
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _requestPermission() {
    try {
      permissionGranted = await requestPermission()
      log(`Permission requested, granted: ${permissionGranted}`)
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _create() {
    try {
      const tag = await create()
      log(`Created floating window: ${tag}`)
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _show() {
    try {
      await show()
      log('Showed')
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _hide() {
    try {
      await hide()
      log('Hidden')
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _move() {
    try {
      await moveWindow('default', 200, 400, true)
      log('Moved to (200, 400)')
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  async function _destroy() {
    try {
      await destroy()
      log('Destroyed')
    } catch (e) {
      log(`Error: ${e}`)
    }
  }

  onFloatingWindowClick((tag) => {
    log(`Floating window clicked: ${tag}`)
  }).then(() => log('Click listener registered'))
</script>

<main class="container">
  <h1>Floating Window Demo</h1>

  <div class="row">
    <a href="https://vite.dev" target="_blank">
      <img src="/vite.svg" class="logo vite" alt="Vite Logo" />
    </a>
    <a href="https://tauri.app" target="_blank">
      <img src="/tauri.svg" class="logo tauri" alt="Tauri Logo" />
    </a>
    <a href="https://svelte.dev" target="_blank">
      <img src="/svelte.svg" class="logo svelte" alt="Svelte Logo" />
    </a>
  </div>

  <div class="controls">
    <button onclick={_checkPermission}>Check Permission</button>
    <button onclick={_requestPermission}>Request Permission</button>
    <hr />
    <button onclick={_create}>Create Floating Window</button>
    <button onclick={_show}>Show</button>
    <button onclick={_hide}>Hide</button>
    <button onclick={_move}>Move (200,400)</button>
    <button onclick={_destroy}>Destroy</button>
  </div>

  <pre class="log">{response}</pre>
</main>

<style>
  .logo.vite:hover {
    filter: drop-shadow(0 0 2em #747bff);
  }

  .logo.svelte:hover {
    filter: drop-shadow(0 0 2em #ff3e00);
  }

  .controls {
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
    margin: 20px 0;
  }

  .controls button {
    padding: 8px 16px;
    font-size: 14px;
    cursor: pointer;
  }

  .log {
    text-align: left;
    font-size: 12px;
    max-height: 300px;
    overflow-y: auto;
    background: #1e1e1e;
    color: #4ec9b0;
    padding: 12px;
    border-radius: 6px;
    white-space: pre-wrap;
  }
</style>
