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
        <h1>Кто набирает матчи</h1>
        <p class="lede">Один город, две колоды. Слева людям показывают ближних. Справа поднимают тех, кого уже лайкают. Точка крупнее — больше лайков. Зелёная вспышка — матч.</p>
      </div>
      <div class="arms">
        <div class="arm baseline">
          <div class="name">Обычная дека</div>
          <div class="hint">возраст и расстояние</div>
          <div class="rate" id="baseline-rate">0</div>
          <div class="unit">матчей на 100 свайпов</div>
          <div class="meta" id="baseline-meta">0 матчей · 0 свайпов</div>
        </div>
        <div class="arm popularity">
          <div class="name">По лайкам</div>
          <div class="hint">кого уже выбирают</div>
          <div class="rate" id="popularity-rate">0</div>
          <div class="unit">матчей на 100 свайпов</div>
          <div class="meta" id="popularity-meta">0 матчей · 0 свайпов</div>
        </div>
      </div>
      <div class="controls">
        <button class="primary" id="play" type="button">Старт</button>
        <button id="reset" type="button">Сначала</button>
        <select id="speed" aria-label="Скорость">
          <option value="8">медленно</option>
          <option value="28" selected>живо</option>
          <option value="90">быстро</option>
        </select>
      </div>
      <p class="ticker" id="ticker">Нажми Старт — люди начнут свайпать.</p>
    </aside>
    <div class="stage">
      <div class="pane-labels">
        <div class="pane-label">
          <strong>Обычная дека</strong>
          <span>крупные точки = кому повезло в этой колоде</span>
        </div>
        <div class="pane-label">
          <strong>По лайкам</strong>
          <span>те же люди, другой порядок в ленте</span>
        </div>
      </div>
      <canvas id="city"></canvas>
    </div>
  </div>
`;

const canvas = must(document.querySelector<HTMLCanvasElement>('#city'), '#city');
const playBtn = must(document.querySelector<HTMLButtonElement>('#play'), '#play');
const resetBtn = must(document.querySelector<HTMLButtonElement>('#reset'), '#reset');
const speedSel = must(document.querySelector<HTMLSelectElement>('#speed'), '#speed');
const ticker = must(document.querySelector<HTMLParagraphElement>('#ticker'), '#ticker');

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
  renderer.reset();
  renderer.resize();
  renderer.draw(engine.agents, performance.now());
  ticker.textContent = 'Нажми Старт — люди начнут свайпать.';
  ticker.classList.remove('match');
  paintHud();
}

function paintHud(): void {
  (['baseline', 'popularity'] as Arm[]).forEach((arm) => {
    const rate = matchesPerHundred(stats[arm].swipes, stats[arm].matches);
    const rateEl = document.getElementById(`${arm}-rate`);
    const metaEl = document.getElementById(`${arm}-meta`);
    if (rateEl) rateEl.textContent = rate.toFixed(1);
    if (metaEl) metaEl.textContent = `${stats[arm].matches} матчей · ${stats[arm].swipes} свайпов`;
  });
}

function narrate(arm: Arm, kind: 'like' | 'match'): void {
  const side = arm === 'popularity' ? 'справа' : 'слева';
  if (kind === 'match') {
    ticker.textContent = `Матч ${side}. Оба лайкнули друг друга.`;
    ticker.classList.add('match');
    return;
  }
  ticker.textContent = `Лайк ${side}. Точка стала крупнее.`;
  ticker.classList.remove('match');
}

function applyEvents(now: number): boolean {
  const { events, done: finished } = engine.tick();
  for (const event of events) {
    if (event.type === 'pass' || event.type === 'like') {
      stats[event.arm].swipes += 1;
    }
    if (event.type === 'like') {
      stats[event.arm].likes += 1;
      narrate(event.arm, 'like');
    }
    if (event.type === 'match') {
      stats[event.arm].matches += 1;
      narrate(event.arm, 'match');
    }
  }
  renderer.ingest(events, now);
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
      playBtn.textContent = 'Готово';
      ticker.textContent = 'Ночь закончилась. Сравни, где точки крупнее и где больше матчей.';
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
  playBtn.textContent = playing ? 'Пауза' : 'Старт';
});

resetBtn.addEventListener('click', () => {
  playing = false;
  playBtn.textContent = 'Старт';
  boot();
});

window.addEventListener('resize', () => {
  renderer.resize();
  renderer.draw(engine.agents, performance.now());
});

boot();
raf = requestAnimationFrame(frame);
