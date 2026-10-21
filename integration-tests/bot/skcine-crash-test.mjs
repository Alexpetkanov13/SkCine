// Crash-recovery test. Phase "before": join and start a scene, then the harness hard-kills the server.
// Phase "after": rejoin and verify the recovery journal restored game mode and location.
// Usage: node skcine-crash-test.mjs before|after [host] [port]
import fs from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , phase, host = '127.0.0.1', port = '25599'] = process.argv
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const bot = mineflayer.createBot({ host, port: Number(port), username: 'CrashBot', version: process.env.MC_VERSION || '1.21.11', auth: 'offline' })
bot.on('error', (e) => console.log('error', e.message))
bot.once('spawn', async () => {
  if (phase === 'before') {
    // Record the state immediately on spawn, before any scene (e.g. a first-join tour) can start.
    await sleep(300)
    const p = bot.entity.position
    fs.writeFileSync('crash-start.json', JSON.stringify({ x: p.x, y: p.y, z: p.z, gm: bot.game.gameMode }))
    await sleep(1700)
    bot.chat('/welcome')
    await sleep(3000)
    console.log(`BEFORE gamemode=${bot.game.gameMode}`)
    // Leave the connection open; the harness kills the server now.
    fs.writeFileSync('crash-ready', '1')
    await sleep(60000)
  } else {
    await sleep(2000)
    const start = JSON.parse(fs.readFileSync('crash-start.json', 'utf8'))
    const p = bot.entity.position
    const dist = Math.hypot(p.x - start.x, p.y - start.y, p.z - start.z)
    const ok = bot.game.gameMode === start.gm && dist < 1.5
    console.log(`${ok ? 'PASS' : 'FAIL'} crash: restored gamemode=${bot.game.gameMode} (expected ${start.gm}), distance=${dist.toFixed(2)}`)
    bot.quit()
    await sleep(500)
    process.exit(ok ? 0 : 1)
  }
})
setTimeout(() => { console.log('timeout'); process.exit(3) }, 90000)
