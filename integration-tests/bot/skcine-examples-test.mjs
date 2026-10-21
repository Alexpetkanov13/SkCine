// Runs every shipped example in game and fails on runtime errors or players left unrestored.
// Usage: node skcine-examples-test.mjs <serverLogPath> [host] [port] [rconPort] [rconPassword]
import fs from 'node:fs'
import net from 'node:net'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , logPath, host = '127.0.0.1', port = '25599', rconPort = '25598', rconPass = 'skcinetest'] = process.argv
const VERSION = process.env.MC_VERSION || '1.21.11'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const strip = (s) => s.replace(/\x1b\[[0-9;]*m/g, '')

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
        if (!authed) { if (id === -1) { reject(new Error('auth')); return } authed = true; send(2, 2, command) } else { resolve(body); sock.end(); return }
      }
    })
    sock.on('error', reject)
  })
}

function logSince (offset) {
  const size = fs.statSync(logPath).size
  const fd = fs.openSync(logPath, 'r')
  const b = Buffer.alloc(Math.max(0, size - offset))
  fs.readSync(fd, b, 0, b.length, offset)
  fs.closeSync(fd)
  return strip(b.toString('utf8'))
}

const NOISE = /No key layers|oshi|SystemReport|Win32Exception/
function problems (text) {
  return text.split(/\r?\n/).filter((l) => !NOISE.test(l) && (/ERROR\]|SEVERE|Exception|\[Skript\].*(error|Error)|SKCINE-TEST FAIL/.test(l)))
}

let statusSeq = 0
async function watching (name) {
  // Unique token per request: Paper writes its log asynchronously, so never trust an untagged "latest" line.
  const tok = `t${++statusSeq}x${Date.now()}`
  const since = Math.max(0, fs.statSync(logPath).size - 4096)
  await rcon(`skcinetest-status ${name} ${tok}`)
  const re = new RegExp('SKCINE-STATUS tok=' + tok + ' watching=([a-z]+)[^\r\n]*gamemode=([a-z]+)')
  for (let i = 0; i < 80; i++) {
    const m = logSince(since).match(re)
    if (m) return { watching: m[1] === 'true', gamemode: m[2], known: true }
    await sleep(100)
  }
  return { watching: true, gamemode: '?', known: false }
}

// [command, max seconds to wait for the scene to end, optional chat reply when a choice appears]
const CASES = [
  ['/welcome', 12], ['/flyover', 16], ['/orbitme', 11], ['/quake', 9], ['/displays', 15], ['/actors', 17],
  ['/testboss zombie', 14], ['/warpto 40 -60 40', 8], ['/testreveal', 10], ['/quest', 20, '1'], ['/tour', 20],
  ['/testvictory', 12], ['/groupscene', 13], ['/solo', 15], ['/together', 15], ['/story', 25, '2'],
  ['/tl play', 45], ['/reveal', 11], ['/testprogress rank', 8], ['/testprogress rebirth', 10], ['/testprogress hatch', 11],
  ['/teststory quest', 9], ['/teststory dungeon', 12], ['/teststory horror', 13], ['/teststory cutscene', 12],
  ['/testprogress level', 0], ['/vfx legendary-reveal', 0], ['/vfxshapes', 0], ['/aura', 0], ['/auraoff', 0],
]

async function main () {
  const bot = mineflayer.createBot({ host, port: Number(port), username: 'ExampleBot', version: VERSION, auth: 'offline' })
  await new Promise((r) => bot.once('spawn', r))
  bot.chatLog = []
  bot.on('messagestr', (m) => bot.chatLog.push(m))
  await rcon('op ExampleBot')
  // player-welcome.sk plays a tour on first join; let it start, then stop it so cases start clean.
  await sleep(3500)
  await rcon('skcinetest-stopall')
  let failed = 0
  for (const [cmd, maxSeconds, reply] of CASES) {
    await rcon('gamemode survival ExampleBot')
    await rcon('tp ExampleBot 0 -60 0')
    await sleep(1200)
    const since = fs.statSync(logPath).size
    bot.chatLog.length = 0
    bot.chat(cmd)
    let ended = maxSeconds === 0
    let replied = false
    const start = Date.now()
    await sleep(1500)
    while (!ended && Date.now() - start < (maxSeconds + 15) * 1000) {
      if (reply && !replied && bot.chatLog.some((l) => l.includes('[1]'))) {
        await sleep(500)
        bot.chat(reply)
        replied = true
      }
      const s = await watching('ExampleBot')
      if (!s.watching) ended = true
      else await sleep(1000)
    }
    if (!ended) await rcon('skcinetest-control stop ExampleBot')
    await sleep(1500)
    const s = await watching('ExampleBot')
    const issues = problems(logSince(since))
    const ok = ended && !s.watching && s.gamemode === 'survival' && issues.length === 0 && (!reply || replied)
    if (!ok) failed++
    console.log(`${ok ? 'PASS' : 'FAIL'} ${cmd}${ok ? '' : ` — ended=${ended} gamemode=${s.gamemode} replied=${replied} ${issues.slice(0, 4).join(' | ')}`}`)
  }
  console.log(`\n${CASES.length - failed}/${CASES.length} examples ran cleanly`)
  bot.quit()
  await sleep(300)
  process.exit(failed ? 1 : 0)
}

main()
