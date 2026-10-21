// Player-looking actors (Mannequin, Minecraft 1.21.9+). Usage: node skcine-player-actor-test.mjs <serverLogPath>
import fs from 'node:fs'
import path from 'node:path'
import net from 'node:net'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , logPath] = process.argv
const here = path.dirname(fileURLToPath(import.meta.url))
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
async function main () {
  const caps = await rcon('skcine capabilities')
  const available = /player-actors: available/.test(caps)
  const scripts = path.join(path.dirname(logPath), 'plugins', 'Skript', 'scripts')
  fs.copyFileSync(path.join(here, '..', 'scripts', 'optional', 'skcine-player-actor-test.sk'), path.join(scripts, 'skcine-player-actor-test.sk'))
  const out = await rcon('sk reload skcine-player-actor-test')
  try {
    if (!available) {
      check('player actors: unavailable server reports a clear parse error', /need Minecraft 1\.21\.9/.test(out), out.slice(0, 200))
      return
    }
    check('player actors: syntax loads', /Successfully reloaded/.test(out), out.slice(0, 200))
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25599, username: 'MannequinBot', version: process.env.MC_VERSION || '1.21.11', auth: 'offline' })
    await new Promise((r) => bot.once('spawn', r))
    await sleep(4000)
    await rcon('skcinetest-stopall')
    await rcon('tp MannequinBot 0 -60 0')
    await rcon('gamemode survival MannequinBot')
    await sleep(1500)
    const since = fs.statSync(logPath).size
    await rcon('skcinetest-playeractor MannequinBot')
    await sleep(2500)
    const seen = Object.values(bot.entities).filter((e) => e.name === 'mannequin')
    check('player actors: client receives three mannequin entities', seen.length === 3, Object.values(bot.entities).map((e) => e.name).join(','))
    const log = strip(fs.readFileSync(logPath, 'utf8').slice(since))
    check('player actors: registered as cine actors', /SKCINE-TEST EVENT player-actors 3/.test(log))
    check('player actors: no errors', !/\[SkCine\].*(could not|ERROR)|Exception/.test(log), (log.match(/.*(could not|Exception).*/) || [''])[0])
    await sleep(3500)
    const after = Object.values(bot.entities).filter((e) => e.name === 'mannequin')
    check('player actors: removed when the scene ends', after.length === 0, `remaining=${after.length}`)
    bot.quit()
  } finally {
    fs.rmSync(path.join(scripts, 'skcine-player-actor-test.sk'), { force: true })
    await rcon('sk reload scripts').catch(() => {})
    await sleep(500)
    console.log(`\n${results.filter(Boolean).length}/${results.length} checks passed`)
    process.exit(results.every(Boolean) ? 0 : 1)
  }
}
main()
