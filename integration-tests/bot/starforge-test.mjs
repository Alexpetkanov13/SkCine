// Plays examples/showcase/starforge.sk with a real client: claim path, seal (timeout) path, and skip.
// Usage: node starforge-test.mjs <serverLogPath>
import fs from 'node:fs'
import net from 'node:net'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , logPath] = process.argv
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const strip = (s) => s.replace(/\x1b\[[0-9;]*m/g, '')
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
const check = (n, ok, d = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${n}${d ? ' — ' + d : ''}`) }
const chat = []
const waitChat = async (re, ms) => { const end = Date.now() + ms; while (Date.now() < end) { if (chat.some((m) => re.test(m))) return true; await sleep(250) } return false }
const mannequins = (bot) => Object.values(bot.entities).filter((e) => e.name === 'mannequin').length
const swords = (bot) => bot.inventory.items().filter((i) => i.name === 'netherite_sword').length

async function reset (bot) {
  await rcon('skcinetest-stopall').catch(() => {})
  await rcon(`clear ${bot.username}`)
  await rcon(`tp ${bot.username} 0 -60 0`)
  await rcon(`gamemode survival ${bot.username}`)
  await sleep(6000) // command cooldown
  chat.length = 0
}

async function main () {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25599, username: 'ForgeBot', version: '1.21.11', auth: 'offline' })
  bot.on('message', (m) => chat.push(m.toString()))
  await new Promise((r) => bot.once('spawn', r))
  await sleep(4000)
  const logStart = fs.statSync(logPath).size

  // 1. Claim path: click "Claim it" in the menu.
  await reset(bot)
  bot.chat('/starforge')
  await sleep(3000)
  check('claim: camera takes over (spectator)', bot.game.gameMode === 'spectator', bot.game.gameMode)
  await sleep(4500)
  check('claim: echo player actor visible by 7 s', mannequins(bot) === 1, `mannequins=${mannequins(bot)}`)
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve))
  const win = await Promise.race([opened, sleep(16000).then(() => null)])
  check('claim: choice menu opens at 20 s', !!win)
  if (win) {
    const slots = win.slots.slice(0, 9).map((it, i) => (it ? i : -1)).filter((i) => i >= 0)
    check('claim: two options in the menu', slots.length === 2, String(slots.length))
    await bot.clickWindow(slots[0], 0, 0).catch((e) => console.log('click error', e.message))
  }
  check('claim: reward message', await waitChat(/Starblade hums/, 14000), chat.slice(-3).join(' | '))
  await sleep(1500)
  check('claim: Starblade given exactly once', swords(bot) === 1, `swords=${swords(bot)}`)
  await sleep(2500)
  check('claim: player restored to survival', bot.game.gameMode === 'survival', bot.game.gameMode)
  check('claim: actors cleaned up', mannequins(bot) === 0, `mannequins=${mannequins(bot)}`)

  // 2. Seal path: let the choice time out (default "Seal it away").
  await reset(bot)
  bot.chat('/starforge')
  const opened2 = new Promise((resolve) => bot.once('windowOpen', resolve))
  const win2 = await Promise.race([opened2, sleep(24000).then(() => null)])
  check('seal: choice menu opens', !!win2)
  check('seal: timeout falls to "Seal it away" and the seal branch ends the scene', await waitChat(/forge falls silent/, 34000), chat.slice(-3).join(' | '))
  await sleep(2000)
  check('seal: no Starblade given', swords(bot) === 0, `swords=${swords(bot)}`)
  check('seal: player restored to survival', bot.game.gameMode === 'survival', bot.game.gameMode)

  // 3. Skip: sneak during the intro.
  await reset(bot)
  bot.chat('/starforge')
  await sleep(4000)
  bot.setControlState('sneak', true); await sleep(400); bot.setControlState('sneak', false)
  check('skip: skip hook message', await waitChat(/You skipped the Starforge/, 5000), chat.slice(-3).join(' | '))
  check('skip: even-if-skipped ending still runs once', await waitChat(/forge falls silent/, 3000))
  await sleep(1500)
  check('skip: ending message sent exactly once', chat.filter((m) => /forge falls silent/.test(m)).length === 1)
  check('skip: player restored to survival', bot.game.gameMode === 'survival', bot.game.gameMode)

  const log = strip(fs.readFileSync(logPath, 'utf8').slice(logStart))
  const bad = log.split('\n').filter((l) => /\[SkCine\].*(could not|error|ERROR|WARN)|Exception|starforge\.sk/i.test(l))
  check('no SkCine/Skript errors in the server log', bad.length === 0, bad.slice(0, 5).join(' || '))
  bot.quit()
  console.log(`\n${results.filter(Boolean).length}/${results.length} checks passed`)
  process.exit(results.every(Boolean) ? 0 : 1)
}
main().catch((e) => { console.error(e); process.exit(2) })
