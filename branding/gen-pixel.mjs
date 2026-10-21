// Generates a 32x32 pixel-art SkCine icon (Minecraft block style) as SVG.
const N = 32, S = 16;
let seed = 1337;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
const pick = (a) => a[Math.floor(rnd() * a.length)];

const face = ['#2b1d5c', '#33226b', '#3a2878', '#2f2063', '#41308a', '#271a52'];
const side = ['#6b1230', '#7a1738', '#5e0f2a', '#861b3f'];
const gold = ['#ffd257', '#ffc23a', '#ffdb75', '#f5b02c', '#ffe48f'];
const outline = '#12081f', shadow = '#170c2c', border = '#0c0612';

const S_ = ['.########', '#########', '###......', '###......', '###......', '########.', '.########', '......###', '......###', '......###', '#########', '########.'];
const C_ = ['.########', '#########', '###......', '###......', '###......', '###......', '###......', '###......', '###......', '###......', '#########', '.########'];

const grid = Array.from({ length: N }, () => Array(N).fill(null));
const letter = Array.from({ length: N }, () => Array(N).fill(false));
const stamp = (g, x0, y0) => g.forEach((row, y) => [...row].forEach((c, x) => {
  if (c !== '#') return;
  letter[y0 + y][x0 + x] = true;
}));
stamp(S_, 5, 5);
stamp(C_, 18, 5);

for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
  const corner = (x === 0 || x === N - 1) && (y === 0 || y === N - 1);
  if (corner) continue;
  if (x === 0 || y === 0 || x === N - 1 || y === N - 1) { grid[y][x] = border; continue; }
  grid[y][x] = y >= 23 ? pick(side) : pick(face);
}
// side band: highlight edge and film perforations
for (let x = 1; x < N - 1; x++) grid[23][x] = '#a3264f';
for (let x = 3; x < N - 3; x += 4) for (const y of [25, 26, 27, 28]) for (const dx of [0, 1]) {
  if (y === 25 || y === 28) continue;
  grid[y][x + dx] = '#e8d9ef';
}
// letter shadow, outline, fill
const inLetter = (x, y) => letter[y]?.[x];
for (let y = 1; y < 23; y++) for (let x = 1; x < N - 1; x++) {
  if (inLetter(x, y)) continue;
  if (inLetter(x - 1, y - 1) || inLetter(x, y - 1) && inLetter(x - 1, y - 2)) grid[y][x] = shadow;
}
for (let y = 1; y < 23; y++) for (let x = 1; x < N - 1; x++) {
  if (inLetter(x, y)) continue;
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (inLetter(x + dx, y + dy)) grid[y][x] = outline;
}
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (letter[y][x]) {
  const top = !inLetter(x, y - 1);
  grid[y][x] = top && rnd() < 0.6 ? '#fff0b0' : pick(gold);
}

let rects = '';
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++)
  if (grid[y][x]) rects += `<rect x="${x}" y="${y}" width="1" height="1" fill="${grid[y][x]}"/>`;
console.log(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${N} ${N}" width="${N * S}" height="${N * S}" shape-rendering="crispEdges">${rects}</svg>`);
