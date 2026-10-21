// Server-side load benchmark: N connected players each watching their own busy scene.
// Usage: node skcine-bench.mjs <serverLogPath> <counts e.g. 1,10,25> [host] [port] [rconPort] [rconPassword]
import { createRequire } from 'node:module'
import net from 'node:net'

const require = createRequire(import.meta.url)
const mineflayer = require('mineflayer')
const [, , logPath, countsArg = '1,10,25', host = '127.0.0.1', port = '25599', rconPort = '25598', rconPass = 'skcinetest'] = process.argv
const VERSION = process.env.MC_VERSION || '1.21.11'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

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
        if (!authed) { if (id === -1) { reject(new Error('auth')); return } authed = true; send(2, 2, command) } else { resolve(body.replace(/§./g, '')); sock.end(); return }
      }
    })
    sock.on('error', reject)
  })
}

async function spawn (name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username: name, version: VERSION, auth: 'offline', viewDistance: 'tiny' })
    bot.once('spawn', () => resolve(bot))
    bot.on('error', () => {})
    bot.on('kicked', (r) => console.log(name, 'kicked', JSON.stringify(r).slice(0, 120)))
  })
}

function parseProfiler (text) {
  const m = text.match(/avg\/p50\/p95\/p99\/max \(µs\): ([\d.]+) \/ ([\d.]+) \/ ([\d.]+) \/ ([\d.]+) \/ ([\d.]+)/)
  const p = text.match(/Particles sent \/ dropped \(last second\): (\d+) \/ (\d+)/)
  const sc = text.match(/Scenes \/ cameras \/ actors: (\d+) \/ (\d+) \/ (\d+)/)
  return m ? { avg: +m[1], p50: +m[2], p95: +m[3], p99: +m[4], max: +m[5], particles: p ? +p[1] : 0, dropped: p ? +p[2] : 0, scenes: sc ? +sc[1] : 0, actors: sc ? +sc[3] : 0 } : null
}

async function main () {
  const counts = countsArg.split(',').map(Number)
  const bots = []
  const rows = []
  console.log('baseline mspt:', (await rcon('mspt')).replace(/\n/g, ' '))
  for (const n of counts) {
    while (bots.length < n) {
      const b = await spawn(`Bench${bots.length}`)
      bots.push(b)
      await rcon(`gamemode survival ${b.username}`)
      await rcon(`tp ${b.username} ${(bots.length % 10) * 40} -60 ${Math.floor(bots.length / 10) * 40}`)
      await sleep(150)
    }
    await sleep(2000)
    for (const b of bots) await rcon(`skcinebench-play ${b.username}`)
    await sleep(8000) // warm up, then measure inside the scenes' 30 s window
    const samples = []
    for (let i = 0; i < 3; i++) {
      samples.push(parseProfiler(await rcon('skcine profiler')))
      await sleep(4000)
    }
    const mspt = (await rcon('mspt')).replace(/\n/g, ' ')
    const s = samples.filter(Boolean)
    const avg = (k) => (s.reduce((a, x) => a + x[k], 0) / s.length).toFixed(1)
    rows.push(`| ${n} | ${s[0]?.scenes} | ${s[0]?.actors} | ${avg('avg')} | ${avg('p95')} | ${avg('p99')} | ${avg('max')} | ${avg('particles')} | ${avg('dropped')} | ${mspt} |`)
    console.log(rows.at(-1))
    await rcon('stop all cine scenes').catch(() => {})
    await rcon('skcinetest-stopall').catch(() => {})
    await sleep(3000)
  }
  console.log('\n| viewers | scenes | actors | SkCine avg µs/tick | p95 | p99 | max | particles/s | dropped/s | Paper MSPT (5s, 10s, 1m) |')
  console.log('|---|---|---|---|---|---|---|---|---|---|')
  rows.forEach((r) => console.log(r))
  bots.forEach((b) => b.quit())
  await sleep(1000)
  process.exit(0)
}

main()
