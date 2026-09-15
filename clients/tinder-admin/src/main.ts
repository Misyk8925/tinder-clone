import './styles.css';
import { generateCity, pickViewers } from './sim/city';
import { emptyArmStats, MatchingEngine, matchesPerHundred } from './sim/engine';
import { DEFAULT_CITY, type Arm } from './sim/types';
import { CityRenderer } from './render/city-renderer';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) {
  throw new Error('#app missing');
}

app.innerHTML = `
  <div class="shell">
    <aside class="hud">
      <div>
        <div class="kicker">Lunari</div>
        <h1>Matching observatory</h1>
        <p class="lede">A local city of agents. Amber is today's deck score (age + distance). Cyan multiplies that by log(1+likes) and keeps 12% newcomers in reach.</p>
      </div>
      <div class="arms">
        <div class="arm baseline">
          <div class="name">Baseline</div>
          <div class="rate" id="baseline-rate">0.0</div>
          <div class="meta" id="baseline-meta">0 matches · 0 swipes</div>
        </div>
        <div class="arm popularity">
          <div class="name">Popularity</div>
          <div class="rate" id="popularity-rate">0.0</div>
          <div class="meta" id="popularity-meta">0 matches · 0 swipes</div>
        </div>
      </div>
      <div class="controls">
        <button class="primary" id="play" type="button">Play</button>
        <button id="reset" type="button">Reset</button>
        <select id="speed" aria-label="Speed">
          <option value="8">slow</option>
          <option value="28" selected>live</option>
          <option value="90">fast</option>
          <option value="400">night</option>
        </select>
      </div>
      <p class="note">No AWS, no Kafka, no Compose. Scoring copies <code>AgeCompatibilityStrategy</code> and <code>LocationProximityStrategy</code>. Production ranking is unchanged.</p>
    </aside>
    <div class="stage">
      <canvas id="city"></canvas>
      <div class="legend">
        <span><i class="swatch" style="background:#f6b53f"></i>baseline</span>
        <span><i class="swatch" style="background:#5ee0ff"></i>popularity</span>
        <span><i class="swatch" style="background:#9cce2b"></i>match</span>
      </div>
    </div>
  </div>
`;

const canvas = must(document.querySelector<HTMLCanvasElement>('#city'), '#city');
const playBtn = must(document.querySelector<HTMLButtonElement>('#play'), '#play');
const resetBtn = must(document.querySelector<HTMLButtonElement>('#reset'), '#reset');
const speedSel = must(document.querySelector<HTMLSelectElement>('#speed'), '#speed');

function must<T>(el: T | null, name: string): T {
  if (!el) {
    throw new Error(`${name} missing`);
  }
  return el;
}

const renderer = new CityRenderer(canvas);
let playing = false;
let raf = 0;
let engine: MatchingEngine;
let stats = emptyArmStats();
let done = false;

function boot(): void {
  const agents = generateCity(DEFAULT_CITY);
  const viewers = pickViewers(agents, DEFAULT_CITY.viewers, DEFAULT_CITY.seed);
  engine = new MatchingEngine(agents, viewers, DEFAULT_CITY);
  stats = emptyArmStats();
  done = false;
  renderer.resize();
  renderer.draw(engine.agents, performance.now());
  paintHud();
}

function paintHud(): void {
  (['baseline', 'popularity'] as Arm[]).forEach((arm) => {
    const rate = matchesPerHundred(stats[arm].swipes, stats[arm].matches);
    const rateEl = document.getElementById(`${arm}-rate`);
    const metaEl = document.getElementById(`${arm}-meta`);
    if (rateEl) rateEl.textContent = rate.toFixed(1);
    if (metaEl) metaEl.textContent = `${stats[arm].matches} matches · ${stats[arm].swipes} swipes`;
  });
}

function applyEvents(now: number): boolean {
  const { events, done: finished } = engine.tick();
  for (const event of events) {
    if (event.type === 'pass' || event.type === 'like') {
      stats[event.arm].swipes += 1;
    }
    if (event.type === 'like') {
      stats[event.arm].likes += 1;
    }
    if (event.type === 'match') {
      stats[event.arm].matches += 1;
    }
  }
  renderer.ingest(engine.agents, events, now);
  return finished;
}

function frame(now: number): void {
  const burst = Number(speedSel.value);
  if (playing && !done) {
    for (let i = 0; i < burst; i++) {
      done = applyEvents(now);
      if (done) break;
    }
    if (done) {
      playing = false;
      playBtn.textContent = 'Done';
    }
  }
  renderer.draw(engine.agents, now);
  paintHud();
  raf = requestAnimationFrame(frame);
}

playBtn.addEventListener('click', () => {
  if (done) {
    boot();
  }
  playing = !playing;
  playBtn.textContent = playing ? 'Pause' : 'Play';
});

resetBtn.addEventListener('click', () => {
  playing = false;
  playBtn.textContent = 'Play';
  boot();
});

window.addEventListener('resize', () => {
  renderer.resize();
  renderer.draw(engine.agents, performance.now());
});

boot();
raf = requestAnimationFrame(frame);
