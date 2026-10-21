// Plays "Echoes of the Shattered Crown" (showcase/shattered-crown) with a real client.
// Usage: node crown-test.mjs <serverLogPath> [quick]
// Needs the campaign loaded, RCON on 25598 (password skcinetest), server on 25599.
import fs from 'node:fs'
import net from 'node:net'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , logPath, mode] = process.argv
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const strip = (s) => s.replace(/\x1b\[[0-9;]*m/g, '')
const NAME = 'CrownBot'
function rcon (command) {
  return new Promise((resolve, reject) => {
    const sock = net.connect(25598, '127.0.0.1'); let buf = Buffer.alloc(0); let authed = false
    const send = (id, type, body) => { const b = Buffer.from(body, 'utf8'); const p = Buffer.alloc(14 + b.length); p.writeInt32LE(10 + b.length, 0); p.writeInt32LE(id, 4); p.writeInt32LE(type, 8); b.copy(p, 12); sock.write(p) }
    sock.on('connect', () => send(1, 3, 'skcinetest'))
    sock.on('data', (d) => { buf = Buffer.concat([buf, d]); while (buf.length >= 4) { const len = buf.readInt32LE(0); if (buf.length < 4 + len) return; const id = buf.readInt32LE(4); const body = buf.slice(12, 4 + len - 2).toString('utf8'); buf = buf.slice(4 + len); if (!authed) { if (id === -1) { reject(new Error('auth')); return } authed = true; send(2, 2, command) } else { resolve(body.replace(/§./g, '')); sock.end(); return } } })
    sock.on('error', reject)
  })
}
const results = []
const check = (n, ok, d = '') => { results.push([n, ok]); console.log(`${ok ? 'PASS' : 'FAIL'} ${n}${d ? ' — ' + d : ''}`) }
const chat = []
let bot
const waitFor = async (pred, ms, step = 200) => { const end = Date.now() + ms; while (Date.now() < end) { if (pred()) return true; await sleep(step) } return false }
const chatHas = (re) => chat.some((m) => re.test(m))
const spectating = () => bot.game.gameMode === 'spectator'

// Clicks the menu option whose display name matches `want` (or index).
async function clickOption (win, want) {
  const items = win.slots.slice(0, 9).map((it, i) => ({ it, i })).filter((x) => x.it)
  let target = items[0]
  if (typeof want === 'number') target = items[Math.min(want, items.length - 1)]
  else if (want) {
    const hit = items.find((x) => {
      const n = x.it.customName ? JSON.stringify(x.it.customName) : (x.it.displayName || '')
      return n.toLowerCase().includes(want.toLowerCase())
    })
    if (hit) target = hit
  }
  await bot.clickWindow(target.i, 0, 0).catch((e) => console.log('  click error', e.message))
  return items.length
}

// Plays whatever scene starts next. Answers every menu with `answers` in order.
// Returns once the bot is back out of spectator for `settleMs`.
async function watch (label, { start, answers = [], speed = 5, maxMs = 120000, chain = false } = {}) {
  const queue = [...answers]
  let menus = 0
  const onWin = async (win) => {
    menus++
    await sleep(400)
    const want = queue.length ? queue.shift() : 0
    const n = await clickOption(win, want)
    console.log(`  [${label}] menu #${menus} (${n} options) → ${want}`)
  }
  bot.on('windowOpen', onWin)
  try {
    if (start) await start()
    const began = await waitFor(spectating, 15000)
    check(`${label}: scene starts (spectator camera)`, began, bot.game.gameMode)
    if (!began) return { menus }
    await sleep(600)
    if (speed !== 1) await rcon(`crown admin speed ${NAME} ${speed}`)
    const end = Date.now() + maxMs
    // A scene can chain into another (ending → credits); wait until we stay out.
    while (Date.now() < end) {
      const out = await waitFor(() => !spectating(), end - Date.now())
      if (!out) break
      const stayed = !(await waitFor(spectating, chain ? 4000 : 1500))
      if (stayed) break
      await sleep(600)
      if (speed !== 1) await rcon(`crown admin speed ${NAME} ${speed}`)
    }
    check(`${label}: scene ends and the player is restored`, bot.game.gameMode === 'survival', bot.game.gameMode)
    return { menus }
  } finally {
    bot.removeListener('windowOpen', onWin)
  }
}

// Clears trial waves and the guardian with the admin "win" tool.
async function winTrial (label) {
  const started = await waitFor(() => chatHas(/Wave 1|trial is skipped/) || bot.game.gameMode === 'survival', 12000)
  check(`${label}: trial begins`, started)
  if (chatHas(/trial is skipped/)) return
  let waves = 0
  for (let i = 0; i < 40; i++) {
    await sleep(2500)
    if (spectating()) break // boss intro
    const r = await rcon(`crown admin win ${NAME}`)
    console.log('  win →', r.trim())
    if (/cleared/.test(r)) waves++
    if (/not in a trial/.test(r)) break
  }
  check(`${label}: waves cleared, guardian intro plays`, spectating(), `waves=${waves}`)
  await waitFor(() => !spectating(), 30000)
  await sleep(1500)
  const mobs = Object.values(bot.entities).filter((e) => e.type === 'hostile' || e.type === 'mob')
  check(`${label}: guardian spawned in the arena`, mobs.length > 0, mobs.map((m) => m.name).join(','))
  let killed = false
  for (let i = 0; i < 10 && !killed; i++) {
    const r = await rcon(`crown admin win ${NAME}`)
    console.log('  win →', r.trim())
    killed = /guardian falls/.test(r)
    if (!killed) { console.log('  info →', (await rcon('crown admin info')).split(String.fromCharCode(10)).join(' / ')); await sleep(1500) }
  }
  check(`${label}: guardian defeated`, killed)
}

async function main () {
  const logStart = fs.statSync(logPath).size
  await rcon('difficulty normal')
  await rcon('gamerule doMobSpawning false')
  await rcon('time set day')
  await rcon('kill @e[type=!player]')
  await rcon(`crown admin reset ${NAME}`).catch(() => {})
  bot = mineflayer.createBot({ host: '127.0.0.1', port: 25599, username: NAME, version: process.env.MC_VERSION || '1.21.11', auth: 'offline' })
  bot.on('message', (m) => { const s = m.toString(); chat.push(s) })
  await new Promise((r) => bot.once('spawn', r))
  await sleep(3000)
  await rcon(`skcinetest-stopall`).catch(() => {})
  await rcon(`crown admin reset ${NAME}`)
  await rcon(`tp ${NAME} 0 -60 0`)
  await rcon(`gamemode survival ${NAME}`)
  await rcon(`effect give ${NAME} minecraft:resistance 99999 4 true`)
  await sleep(1500)

  // ── Hub menu ──
  const hubOpen = new Promise((r) => bot.once('windowOpen', r))
  bot.chat('/crown')
  const hub = await Promise.race([hubOpen, sleep(5000).then(() => null)])
  check('hub: menu opens', !!hub)
  if (hub) {
    const n = hub.slots.slice(0, 9).filter(Boolean).length
    check('hub: nine options', n === 9, String(n))
    bot.closeWindow(hub)
    await sleep(800)
  }

  // ── Prologue ──
  chat.length = 0
  await watch('prologue', { start: async () => bot.chat('/crown play'), answers: ['Valor'], speed: 5 })
  check('prologue: chapter complete message', chatHas(/Prologue · The Fall of Aurelion.*complete/), chat.slice(-4).join(' | '))
  check('prologue: oath remembered (karma +5)', chatHas(/\+5 Light/))
  check('prologue: codex unlocked', chatHas(/Codex: .*The City of Aurelion/))

  // ── Chapter I: intro → trial → guardian → outro ──
  chat.length = 0
  await watch('interlude + ember intro', { start: async () => bot.chat('/crown play'), speed: 5, chain: true, maxMs: 180000 })
  await winTrial('ember')
  await watch('ember outro', { answers: ['Spare'], speed: 5, maxMs: 150000 })
  check('ember: shard recovered', chatHas(/Ember Shard recovered/), chat.slice(-4).join(' | '))
  check('ember: chapter complete', chatHas(/Chapter I · The Ember.*complete/))
  check('ember: mercy recorded (+15)', chatHas(/\+15 Light.*Spared Hadrek/))

  if (mode !== 'quick') {
    // Remaining chapters: intros and outros directly (trials verified above).
    const outro = { 3: ['Free'], 4: ['Trust'], 5: ['Let it walk'], 6: ['Forgive'] }
    for (const n of [3, 4, 5, 6]) {
      chat.length = 0
      await watch(`chapter ${n} intro`, { start: async () => rcon(`crown admin run ${NAME} ${n}`), speed: 6 })
      await sleep(2500)
      await rcon(`crown admin win ${NAME}`).catch(() => {})
      // End the trial so the outro can be played on its own.
      await rcon(`crown admin endtrial ${NAME}`)
      await sleep(1500)
      chat.length = 0
      await watch(`chapter ${n} outro`, { start: async () => rcon(`crown admin outro ${NAME} ${n}`), answers: outro[n], speed: 6, maxMs: 150000 })
      check(`chapter ${n}: complete`, chatHas(/complete/), chat.slice(-3).join(' | '))
    }
    // Finale → ending → credits (five mercies: the Echo option appears)
    chat.length = 0
    await rcon(`crown admin choose ${NAME} ember spare`)
    await rcon(`crown admin choose ${NAME} tide free`)
    await rcon(`crown admin choose ${NAME} gale trust`)
    await rcon(`crown admin choose ${NAME} stone wake`)
    await rcon(`crown admin choose ${NAME} void forgive`)
    let finaleOptions = 0
    const countFinale = (win) => { finaleOptions = Math.max(finaleOptions, win.slots.slice(0, 9).filter(Boolean).length) }
    bot.on('windowOpen', countFinale)
    await watch('finale', { start: async () => rcon(`crown admin run ${NAME} 7`), answers: ['Echo'], speed: 6, maxMs: 240000, chain: true })
    bot.removeListener('windowOpen', countFinale)
    check('finale: secret option offered after five mercies', finaleOptions === 3, String(finaleOptions))
    check('finale: secret ending recorded', chatHas(/Ending unlocked: Two Who Were One/), chat.slice(-5).join(' | '))
    check('credits: thank-you message', chatHas(/Thank you for playing/))
    // The other three endings, directly
    for (const e of ['radiant', 'tyrant', 'sacrifice']) {
      chat.length = 0
      await watch(`ending ${e}`, { start: async () => rcon(`crown admin ending ${NAME} ${e}`), speed: 8, maxMs: 200000, chain: true })
      check(`ending ${e}: recorded`, chatHas(/Ending unlocked/), chat.slice(-3).join(' | '))
    }
    check('achievements: all four endings', chatHas(/Every Version Is True/))

    // ── Tales ──
    for (const t of ['first-crown', 'last-verse', 'two-hundred-riders', 'first-thought', 'brother-and-sister', 'aurel']) {
      await watch(`tale ${t}`, { start: async () => bot.chat(`/crown tales ${t}`), speed: 8 })
    }

    // ── Trial of Echoes (post-game gauntlet) ──
    chat.length = 0
    bot.chat('/crown gauntlet')
    let felled = 0
    for (let round = 0; round < 5; round++) {
      const intro = await waitFor(spectating, 30000)
      if (!intro) break
      await waitFor(() => !spectating(), 40000)
      await sleep(1500)
      let ok = false
      for (let i = 0; i < 12 && !ok; i++) {
        const r = await rcon(`crown admin win ${NAME}`)
        ok = /guardian falls/.test(r)
        if (!ok) await sleep(1500)
      }
      if (ok) felled++
      await waitFor(spectating, 10000)
      await waitFor(() => !spectating(), 40000)
    }
    check('gauntlet: five guardians defeated', felled === 5, `felled=${felled}`)
    check('gauntlet: achievement', await waitFor(() => chatHas(/Echo of Every Guardian/), 15000), chat.slice(-3).join(' | '))
    chat.length = 0
    bot.chat('/crown leaderboard')
    await sleep(1500)
    check('leaderboard: lists the run', chatHas(/#1 .*CrownBot/), chat.slice(-3).join(' | '))

    // ── Recap (runtime-built scene) ──
    await watch('recap', { start: async () => bot.chat('/crown recap'), speed: 6 })

    // ── Cosmetics menu ──
    chat.length = 0
    const cos = new Promise((r) => bot.once('windowOpen', r))
    bot.chat('/crown cosmetics')
    const cw = await Promise.race([cos, sleep(5000).then(() => null)])
    check('cosmetics: menu offers earned auras', !!cw && cw.slots.slice(0, 9).filter(Boolean).length >= 5, cw ? String(cw.slots.slice(0, 9).filter(Boolean).length) : 'none')
    if (cw) { await clickOption(cw, 'Halo'); await sleep(1000) }
    check('cosmetics: aura equipped', chatHas(/Now wearing: Radiant Halo/), chat.slice(-2).join(' | '))
  }

  // ── Gallery replay grants nothing ──
  chat.length = 0
  await watch('gallery replay', { start: async () => bot.chat('/crown gallery 1'), answers: ['Wisdom'], speed: 8 })
  check('gallery: replay is announced', chatHas(/Replaying/))
  check('gallery: replay grants nothing', !chatHas(/Light|Shadow|complete/), chat.join(' | ').slice(0, 300))

  // ── Photo mode ──
  await watch('photo mode', { start: async () => bot.chat('/crown photo sky'), speed: 4 })

  // ── Codex read aloud ──
  await watch('codex reader', { start: async () => bot.chat('/crown codex read crown'), speed: 4 })

  // ── Settings menu + status ──
  chat.length = 0
  bot.chat('/crown settings speed fast')
  bot.chat('/crown settings channel title')
  bot.chat('/crown status')
  bot.chat('/crown journal')
  bot.chat('/crown achievements')
  bot.chat('/crown tips void')
  await sleep(2500)
  check('settings: speed saved', chatHas(/Text speed: fast/))
  check('settings: channel saved', chatHas(/shown in the title/))
  check('status: shows shards', chatHas(/Shards:/))
  check('journal: shows the ember choice', chatHas(/spared Hadrek/i))
  check('tips: guardian briefing', chatHas(/Hollow Nova/))

  // ── Server log ──
  const log = strip(fs.readFileSync(logPath, 'utf8').slice(logStart))
  const bad = log.split('\n').filter((l) => /(WARN|ERROR)\]: \[(SkCine|Skript)\]|Exception|shattered-crown/.test(l) && !/Thread RCON/.test(l))
  check('no SkCine/Skript warnings or errors in the server log', bad.length === 0, bad.slice(0, 8).join('\n   '))
  bot.quit()
  const passed = results.filter(([, ok]) => ok).length
  console.log(`\n${passed}/${results.length} checks passed`)
  process.exit(passed === results.length ? 0 : 1)
}
main().catch((e) => { console.error(e); process.exit(2) })
