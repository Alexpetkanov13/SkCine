// SkCine in-game integration test.
// Joins a real Paper server with headless mineflayer players (offline mode), triggers scenes defined in
// integration-tests/scripts/*.sk and examples, and asserts behaviour from both sides:
//   - server log lines printed by the test script (SKCINE-TEST EVENT/STATUS ...)
//   - what the client actually receives (game mode, camera packets, chat, position restoration)
//
// Usage: node skcine-bot-test.mjs <serverLogPath> [host] [port] [rconPort] [rconPassword]
import fs from 'node:fs'
import net from 'node:net'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')

const [, , logPath, host = '127.0.0.1', port = '25599', rconPort = '25598', rconPass = 'skcinetest'] = process.argv
if (!logPath) {
  console.error('usage: node skcine-bot-test.mjs <serverLogPath> [host] [port] [rconPort] [rconPassword]')
  process.exit(2)
}

const VERSION = process.env.MC_VERSION || '1.21.11'
const results = []
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function check (name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`)
}

// ── RCON ────────────────────────────────────────────────────────────────────
function rcon (command) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(Number(rconPort), host)
    let buf = Buffer.alloc(0)
    let authed = false
    const send = (id, type, body) => {
      const b = Buffer.from(body, 'utf8')
      const p = Buffer.alloc(14 + b.length)
      p.writeInt32LE(10 + b.length, 0); p.writeInt32LE(id, 4); p.writeInt32LE(type, 8); b.copy(p, 12)
      sock.write(p)
    }
    sock.on('connect', () => send(1, 3, rconPass))
    sock.on('data', (d) => {
      buf = Buffer.concat([buf, d])
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0)
        if (buf.length < 4 + len) return
        const id = buf.readInt32LE(4)
        const body = buf.slice(12, 4 + len - 2).toString('utf8')
        buf = buf.slice(4 + len)
        if (!authed) {
          if (id === -1) { reject(new Error('rcon auth failed')); sock.end(); return }
          authed = true
          send(2, 2, command)
        } else { resolve(body); sock.end(); return }
      }
    })
    sock.on('error', reject)
    setTimeout(() => { reject(new Error('rcon timeout: ' + command)); sock.destroy() }, 20000)
  })
}

// ── Server log ──────────────────────────────────────────────────────────────
let logOffset = fs.statSync(logPath).size
const strip = (s) => s.replace(/\x1b\[[0-9;]*m/g, '')
function markLog () { logOffset = fs.statSync(logPath).size }
function readNew () {
  const size = fs.statSync(logPath).size
  if (size <= logOffset) return ''
  const fd = fs.openSync(logPath, 'r')
  const b = Buffer.alloc(size - logOffset)
  fs.readSync(fd, b, 0, b.length, logOffset)
  fs.closeSync(fd)
  return strip(b.toString('utf8'))
}
async function waitForLog (re, timeoutMs = 10000, since = logOffset) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const size = fs.statSync(logPath).size
    const fd = fs.openSync(logPath, 'r')
    const b = Buffer.alloc(Math.max(0, size - since))
    fs.readSync(fd, b, 0, b.length, since)
    fs.closeSync(fd)
    const m = strip(b.toString('utf8')).match(re)
    if (m) return m
    await sleep(100)
  }
  return null
}
let statusSeq = 0
async function status (name) {
  // Each request carries a unique token so a stale line from Paper's asynchronous log can never be mistaken for it.
  const tok = `t${++statusSeq}x${Date.now()}`
  const since = Math.max(0, fs.statSync(logPath).size - 4096)
  await rcon(`skcinetest-status ${name} ${tok}`)
  const m = await waitForLog(new RegExp(`SKCINE-STATUS tok=${tok} ([^\n\r]*)`), 8000, since)
  const out = {}
  if (m) for (const kv of m[1].trim().split(' ')) { const i = kv.indexOf('='); if (i > 0) out[kv.slice(0, i)] = kv.slice(i + 1) }
  return out
}
async function entityCount () {
  const since = fs.statSync(logPath).size
  await rcon('skcinetest-entities')
  const m = await waitForLog(/SKCINE-ENTITIES (\d+)/, 5000, since)
  return m ? Number(m[1]) : -1
}

// ── Bots ────────────────────────────────────────────────────────────────────
function spawnBot (username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username, version: VERSION, auth: 'offline' })
    bot.chatLog = []
    bot.cameraIds = []
    bot.on('messagestr', (m) => bot.chatLog.push(m))
    bot.once('spawn', () => resolve(bot))
    bot.on('kicked', (r) => console.log(`[${username}] kicked: ${JSON.stringify(r)}`))
    bot.on('error', (e) => console.log(`[${username}] error: ${e.message}`))
    bot._client.on('camera', (p) => bot.cameraIds.push(p.cameraId))
    setTimeout(() => reject(new Error('spawn timeout ' + username)), 60000)
  })
}
const pos = (bot) => bot.entity.position.clone()
async function sneak (bot, ms = 400) { bot.setControlState('sneak', true); await sleep(ms); bot.setControlState('sneak', false) }
const chatHas = (bot, text) => bot.chatLog.some((l) => l.includes(text))

async function prepare (bot) {
  await rcon(`gamemode survival ${bot.username}`)
  await rcon(`tp ${bot.username} 0 -60 0`)
  await sleep(1500)
}

// ── Scenarios ───────────────────────────────────────────────────────────────
async function scenarioWelcome (bot) {
  await prepare(bot)
  const start = pos(bot)
  const s0 = await status(bot.username)
  check('welcome: not watching before', s0.watching === 'false' && s0.gamemode === 'survival', JSON.stringify(s0))
  markLog()
  bot.chatLog.length = 0
  bot.chat('/welcome')
  check('welcome: camera attach event', await waitForLog(/SKCINE-TEST EVENT camera-attach CineBot/, 5000))
  await sleep(1500)
  const s1 = await status(bot.username)
  check('welcome: watching + spectator + camera', s1.watching === 'true' && s1.gamemode === 'spectator' && s1.camera === 'true', JSON.stringify(s1))
  check('welcome: client is in spectator', bot.game.gameMode === 'spectator', bot.game.gameMode)
  const camId = bot.cameraIds.at(-1)
  const camEntity = camId != null ? bot.entities[camId] : null
  check('welcome: client received camera packet for a display entity', camEntity && camEntity.name === 'item_display', `id=${camId} name=${camEntity?.name}`)
  const p1 = camEntity ? camEntity.position.clone() : null
  await sleep(1500)
  const p2 = camEntity ? bot.entities[camId]?.position?.clone() : null
  check('welcome: camera moves along its path', p1 && p2 && p1.distanceTo(p2) > 1, p1 && p2 ? `moved ${p1.distanceTo(p2).toFixed(2)} blocks in 1.5s` : 'no camera entity')
  check('welcome: title/dialogue reached client', await waitForLog(/SKCINE-TEST EVENT dialogue-start/, 6000))
  check('welcome: scene finished', await waitForLog(/SKCINE-TEST EVENT global-finish examples:welcome/, 12000))
  await sleep(1000)
  const s2 = await status(bot.username)
  check('welcome: restored to survival, not watching', s2.watching === 'false' && s2.gamemode === 'survival' && s2.camera === 'false', JSON.stringify(s2))
  check('welcome: client back in survival', bot.game.gameMode === 'survival', bot.game.gameMode)
  check('welcome: location restored', pos(bot).distanceTo(start) < 1.5, `${pos(bot)} vs ${start}`)
  check('welcome: on finish callback chat', chatHas(bot, 'Welcome aboard!'), bot.chatLog.slice(-5).join(' | '))
}

async function scenarioSkip (bot) {
  await prepare(bot)
  markLog()
  bot.chatLog.length = 0
  bot.chat('/welcome')
  await waitForLog(/SKCINE-TEST EVENT camera-attach CineBot/, 5000)
  await sleep(1000)
  await sneak(bot)
  check('skip: sneak skips skippable scene', await waitForLog(/SKCINE-TEST EVENT global-skip examples:welcome/, 5000))
  await sleep(800)
  const s = await status(bot.username)
  check('skip: restored after skip', s.watching === 'false' && s.gamemode === 'survival', JSON.stringify(s))
  check('skip: on skip callback chat', chatHas(bot, 'Intro skipped.'), bot.chatLog.slice(-5).join(' | '))
}

async function scenarioFullSyntax (bot) {
  await prepare(bot)
  const base = await entityCount()
  markLog()
  const since = logOffset
  await rcon('skcinetest-group')
  check('full: prepare event', await waitForLog(/SKCINE-TEST EVENT prepare test:all-syntax/, 5000, since))
  check('full: start event with player + id', await waitForLog(/SKCINE-TEST EVENT start CineBot test:all-syntax/, 5000, since))
  await sleep(1200)
  const s = await status(bot.username)
  check('full: timeline built (variable set in timeline:)', s.built === 'true', JSON.stringify(s))
  check('full: on start callback ran', s.started === 'true')
  check('full: sequence inlined', s.seq === 'true')
  check('full: actors spawned', Number(s.actors) >= 4, `actors=${s.actors}`)
  await rcon(`skcinetest-control pause ${bot.username}`)
  check('full: pause event', await waitForLog(/SKCINE-TEST EVENT pause/, 3000, since))
  const a = await status(bot.username)
  await sleep(1000)
  const b = await status(bot.username)
  check('full: clock frozen while paused', a.elapsed === b.elapsed && a.state === 'paused', `${a.elapsed} -> ${b.elapsed} (${a.state})`)
  await rcon(`skcinetest-control resume ${bot.username}`)
  check('full: resume event', await waitForLog(/SKCINE-TEST EVENT resume/, 3000, since))
  check('full: marker event', await waitForLog(/SKCINE-TEST EVENT marker middle/, 6000, since))
  check('full: dialogue start/finish', (await waitForLog(/SKCINE-TEST EVENT dialogue-start Hello there!/, 6000, since)) && (await waitForLog(/SKCINE-TEST EVENT dialogue-finish/, 6000, since)))
  check('full: actor spawn event', await waitForLog(/SKCINE-TEST EVENT actor-spawn npc/, 1000, since))
  check('full: on finish structure callback', await waitForLog(/SKCINE-TEST EVENT on-finish test:all-syntax/, 15000, since))
  check('full: on end structure callback', await waitForLog(/SKCINE-TEST EVENT on-end/, 3000, since))
  check('full: actor despawn event', await waitForLog(/SKCINE-TEST EVENT actor-despawn npc/, 3000, since))
  await sleep(1000)
  const after = await entityCount()
  check('full: all temporary actors removed', after === base, `before=${base} after=${after}`)
  const errors = readSince(since).split('\n').filter((l) => /\[SkCine\].*(ERROR|SEVERE)|ERROR\]: \[SkCine\]|Exception/.test(l))
  check('full: no SkCine errors during playback', errors.length === 0, errors.slice(0, 3).join(' | '))
}

function readSince (since) {
  const size = fs.statSync(logPath).size
  const fd = fs.openSync(logPath, 'r')
  const b = Buffer.alloc(Math.max(0, size - since))
  fs.readSync(fd, b, 0, b.length, since)
  fs.closeSync(fd)
  return strip(b.toString('utf8'))
}

async function scenarioDynamicAndControls (bot) {
  await prepare(bot)
  markLog()
  const since = logOffset
  await rcon(`skcinetest-dynamic ${bot.username}`)
  check('dynamic: section added to runtime definition fired', await waitForLog(/SKCINE-TEST EVENT dynamic-at/, 4000, since))
  await sneak(bot)
  await sleep(500)
  const s = await status(bot.username)
  check('dynamic: not skippable ignores sneak', s.watching === 'true', JSON.stringify(s))
  check('dynamic: finished', await waitForLog(/SKCINE-TEST EVENT global-finish test:dynamic/, 6000, since))

  markLog()
  const since2 = logOffset
  bot.chat('/welcome')
  await waitForLog(/camera-attach CineBot/, 5000, since2)
  await rcon(`skcinetest-control seek ${bot.username}`)
  const st = await status(bot.username)
  check('control: seek moves the playhead', parseFloat(st.elapsed) >= 3, `elapsed=${st.elapsed}`)
  await rcon(`skcinetest-control stop ${bot.username}`)
  check('control: stop cancels', await waitForLog(/SKCINE-TEST EVENT global-cancel examples:welcome/, 3000, since2))
  await sleep(600)
  const s2 = await status(bot.username)
  check('control: restored after stop', s2.watching === 'false' && s2.gamemode === 'survival', JSON.stringify(s2))
}

async function scenarioChoice (bot) {
  await prepare(bot)
  markLog()
  const since = logOffset
  bot.chatLog.length = 0
  await rcon(`skcinetest-choice ${bot.username}`)
  const start = Date.now()
  while (!chatHas(bot, 'Pick one') && Date.now() - start < 5000) await sleep(100)
  check('choice: prompt delivered to client', chatHas(bot, 'Pick one'))
  check('choice: clickable options delivered', chatHas(bot, '[1]') && chatHas(bot, '[2]'), bot.chatLog.slice(-4).join(' | '))
  await sleep(600)
  const waiting = await status(bot.username)
  check('choice: scene waits for input', (waiting.state || '').startsWith('waiting'), `state=${waiting.state}`)
  bot.chat('2')
  check('choice: typed reply resolves section with option', await waitForLog(/SKCINE-TEST EVENT choice-section Decline CineBot timedout=false/, 5000, since))
  check('choice: global choice event', await waitForLog(/SKCINE-TEST EVENT choice Decline/, 2000, since))
  check('choice: scene resumes and ends with variable', await waitForLog(/SKCINE-TEST EVENT choice-scene-end picked=Decline/, 8000, since))
}

async function scenarioReloadDuringPlayback (bot) {
  await prepare(bot)
  markLog()
  const since = logOffset
  bot.chat('/welcome')
  await waitForLog(/camera-attach CineBot/, 5000, since)
  await sleep(1000)
  const out = await rcon('sk reload first-cinematic')
  check('reload: script reloads while its scene plays', /reloaded first-cinematic/i.test(out.replace(/§./g, '')), out.slice(0, 120))
  check('reload: playing scene cancelled by unload', await waitForLog(/SKCINE-TEST EVENT global-cancel examples:welcome/, 4000, since))
  await sleep(800)
  const s = await status(bot.username)
  check('reload: viewer restored', s.watching === 'false' && s.gamemode === 'survival', JSON.stringify(s))
  markLog()
  bot.chat('/welcome')
  check('reload: reloaded scene plays again', await waitForLog(/SKCINE-TEST EVENT camera-attach CineBot/, 5000))
  await rcon(`skcinetest-control stop ${bot.username}`)
  await sleep(600)
}

async function scenarioLateJoin (bot) {
  // Connect first so any first-join scene (e.g. the welcome-tour example) can be stopped beforehand.
  const late = await spawnBot('LateBot')
  await sleep(3500)
  await rcon('skcinetest-control stop LateBot')
  await prepare(late)
  await prepare(bot)
  markLog()
  const since = logOffset
  await rcon('skcinetest-groupof CineBot')
  await waitForLog(/SKCINE-TEST EVENT start CineBot/, 5000, since)
  await sleep(1500)
  await rcon('skcinetest-join LateBot CineBot')
  await sleep(1500)
  const a = await status(bot.username)
  const b = await status(late.username)
  check('late join: joins the running shared playback', b.watching === 'true' && b.id === a.id && b.gamemode === 'spectator', `${a.id}/${b.id} ${b.gamemode}`)
  const villager = Object.values(late.entities).find((e) => e.name === 'villager')
  check('late join: private scene actors become visible to the late joiner', !!villager, Object.values(late.entities).map((e) => e.name).join(','))
  await rcon('skcinetest-stopall')
  await sleep(1000)
  const b2 = await status(late.username)
  check('late join: late joiner restored', b2.watching === 'false' && b2.gamemode === 'survival', JSON.stringify(b2))
  late.quit()
}

async function scenarioMenuChoice (bot) {
  await prepare(bot)
  markLog()
  const since = logOffset
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  await rcon(`skcinetest-menuchoice ${bot.username}`)
  const win = await Promise.race([opened, sleep(6000).then(() => null)])
  check('menu choice: inventory menu opens for a spectating viewer', !!win, win ? `${win.type} slots=${win.slots.length}` : 'no window')
  if (!win) return
  const items = win.slots.slice(0, 9).map((it, i) => (it ? `${i}:${it.name}` : null)).filter(Boolean)
  check('menu choice: one item per option', items.length === 2, items.join(','))
  const second = win.slots.slice(0, 9).map((it, i) => (it ? i : -1)).filter((i) => i >= 0)[1]
  await bot.clickWindow(second, 0, 0).catch((e) => console.log('click error', e.message))
  check('menu choice: clicking an option resolves it (server-validated)', await waitForLog(/SKCINE-TEST EVENT menu-choice Decline CineBot/, 5000, since))
  await sleep(500)
  check('menu choice: menu closed after choosing', !bot.currentWindow, bot.currentWindow ? 'still open' : '')
  check('menu choice: scene resumes and finishes', await waitForLog(/SKCINE-TEST EVENT global-finish test:menu-choice/, 8000, since))
}

async function scenarioDisconnect () {
  let bot = await spawnBot('CineBot3')
  await prepare(bot)
  const start = pos(bot)
  markLog()
  const since = logOffset
  bot.chat('/welcome')
  await waitForLog(/camera-attach CineBot3/, 5000, since)
  await sleep(1000)
  bot.quit()
  check('disconnect: scene ends when its only viewer leaves', await waitForLog(/SKCINE-TEST EVENT global-cancel examples:welcome/, 5000, since))
  await sleep(1500)
  bot = await spawnBot('CineBot3')
  await sleep(1500)
  check('disconnect: rejoins in survival', bot.game.gameMode === 'survival', bot.game.gameMode)
  check('disconnect: rejoins at original location', pos(bot).distanceTo(start) < 1.5, `${pos(bot)} vs ${start}`)
  bot.quit()
}

async function scenarioMultiplayer (bot) {
  const bot2 = await spawnBot('CineBot2')
  await prepare(bot)
  await prepare(bot2)
  markLog()
  const since = logOffset
  bot.chat('/welcome')
  bot2.chat('/welcome')
  await waitForLog(/camera-attach CineBot2/, 5000, since)
  await sleep(1200)
  const a = await status(bot.username)
  const b = await status(bot2.username)
  check('multi: two independent playbacks', a.watching === 'true' && b.watching === 'true', `${a.id}/${b.id}`)
  await sneak(bot2)
  await waitForLog(/global-skip examples:welcome/, 4000, since)
  await sleep(600)
  const a2 = await status(bot.username)
  const b2 = await status(bot2.username)
  check('multi: one viewer skipping does not end the other scene', a2.watching === 'true' && b2.watching === 'false', `${a2.watching}/${b2.watching}`)
  await rcon(`skcinetest-control stop ${bot.username}`)
  await sleep(800)

  // Shared group scene with skip mode "vote": needs a majority.
  markLog()
  const since2 = logOffset
  await rcon('skcinetest-group')
  await waitForLog(/SKCINE-TEST EVENT start/, 5000, since2)
  await sleep(1500)
  await sneak(bot)
  await sleep(700)
  const g1 = await status(bot.username)
  check('group: one vote of two does not skip', g1.watching === 'true', JSON.stringify(g1))
  check('group: vote message shown', chatHas(bot2, 'wants to skip'), bot2.chatLog.slice(-3).join(' | '))
  await sneak(bot2)
  check('group: majority vote skips for everyone', await waitForLog(/SKCINE-TEST EVENT global-skip test:all-syntax/, 4000, since2))
  await sleep(1000)
  const g2 = await status(bot.username)
  const g3 = await status(bot2.username)
  check('group: both viewers restored', g2.watching === 'false' && g3.watching === 'false' && g2.gamemode === 'survival' && g3.gamemode === 'survival', `${g2.gamemode}/${g3.gamemode}`)
  bot2.quit()
}

async function main () {
  const bot = await spawnBot('CineBot')
  await rcon('op CineBot')
  // Servers with the examples installed play a tour on first join; let it start, then stop it.
  await sleep(3500)
  await rcon('skcinetest-stopall')
  await sleep(500)
  try {
    await scenarioWelcome(bot)
    await scenarioSkip(bot)
    await scenarioFullSyntax(bot)
    await scenarioDynamicAndControls(bot)
    await scenarioChoice(bot)
    await scenarioMultiplayer(bot)
    await scenarioReloadDuringPlayback(bot)
    await scenarioLateJoin(bot)
    await scenarioMenuChoice(bot)
    await scenarioDisconnect()
  } catch (e) {
    check('harness exception', false, e.stack)
  }
  bot.quit()
  const failed = results.filter((r) => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
  fs.writeFileSync('bot-test-results.json', JSON.stringify(results, null, 2))
  await sleep(500)
  process.exit(failed.length ? 1 : 0)
}

main()
