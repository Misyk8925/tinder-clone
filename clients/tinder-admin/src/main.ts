import './styles.css';
import { generateCity, pickViewers } from './sim/city';
import { emptyArmStats, MatchingEngine, matchesPerHundred } from './sim/engine';
import { DEFAULT_CITY, type Agent, type Arm, type SimEvent } from './sim/types';
import { CityRenderer, type MapFocus } from './render/city-renderer';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) {
  throw new Error('#app missing');
}

app.innerHTML = `
  <div class="shell">
    <aside class="hud">
      <div>
        <div class="kicker">Lunari</div>
        <h1>Ночь в городе</h1>
        <p class="lede">Смотри не точки — карточки. Кто-то листает деку, как в приложении. Слева обычный порядок. Справа вверх лезут те, кого уже лайкают.</p>
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
      <p class="ticker" id="ticker">Старт — увидишь, как Mira листает Leo.</p>
      <p class="aws">AWS имеет смысл, когда эти же карты берут фото из S3 и колоду из Kafka. Здесь буквы, потому что стека нет. Стенд нужен, чтобы проверить, что живая дека ведёт себя так же.</p>
    </aside>
    <div class="stage">
      <div class="pane-labels">
        <div class="pane-label">
          <strong>Обычная дека</strong>
          <span>карточка — кого сейчас показывают</span>
        </div>
        <div class="pane-label">
          <strong>По лайкам</strong>
          <span>те же люди, другой порядок</span>
        </div>
      </div>
      <div class="maps">
        <canvas id="city"></canvas>
        <div class="phones">
          ${phone('baseline')}
          ${phone('popularity')}
        </div>
        <div class="boom" id="boom" hidden>
          <div class="boom-kicker">Это матч</div>
          <div class="boom-faces">
            <div class="face" id="boom-a"></div>
            <div class="heart">♥</div>
            <div class="face" id="boom-b"></div>
          </div>
          <p class="boom-names" id="boom-names"></p>
          <p class="boom-why">На AWS здесь были бы настоящие фото и колода с сервера. Сейчас — симулятор, чтобы было что включить.</p>
        </div>
      </div>
    </div>
  </div>
`;

function phone(arm: Arm): string {
  return `
    <article class="phone" data-arm="${arm}">
      <div class="viewer" id="viewer-${arm}">кто-то откроет деку</div>
      <div class="card" id="card-${arm}">
        <div class="face" id="face-${arm}">?</div>
        <div class="who" id="who-${arm}">—</div>
        <div class="where" id="where-${arm}">Вена</div>
        <div class="stamp" id="stamp-${arm}"></div>
      </div>
    </article>
  `;
}

const canvas = must(document.querySelector<HTMLCanvasElement>('#city'), '#city');
const playBtn = must(document.querySelector<HTMLButtonElement>('#play'), '#play');
const resetBtn = must(document.querySelector<HTMLButtonElement>('#reset'), '#reset');
const speedSel = must(document.querySelector<HTMLSelectElement>('#speed'), '#speed');
const ticker = must(document.querySelector<HTMLParagraphElement>('#ticker'), '#ticker');
const boom = must(document.querySelector<HTMLDivElement>('#boom'), '#boom');

function must<T>(el: T | null, name: string): T {
  if (!el) {
    throw new Error(`${name} missing`);
  }
  return el;
}

const renderer = new CityRenderer(canvas);
const CARD_MS = 420;
const MATCH_MS = 1600;
let playing = false;
let raf = 0;
let engine: MatchingEngine;
let stats = emptyArmStats();
let done = false;
let focus: MapFocus = {};
let cardDue = 0;
let boomUntil = 0;
const queued = new Map<Arm, SimEvent>();

function boot(): void {
  const agents = generateCity(DEFAULT_CITY);
  const viewers = pickViewers(agents, DEFAULT_CITY.viewers, DEFAULT_CITY.seed);
  engine = new MatchingEngine(agents, viewers, DEFAULT_CITY);
  stats = emptyArmStats();
  done = false;
  focus = {};
  queued.clear();
  boom.hidden = true;
  boomUntil = 0;
  renderer.reset();
  renderer.resize();
  renderer.draw(engine.agents, performance.now(), focus);
  ticker.textContent = 'Старт — увидишь, как кто-то листает деку.';
  ticker.classList.remove('match');
  for (const arm of ['baseline', 'popularity'] as Arm[]) {
    paintIdleCard(arm);
  }
  paintHud();
}

function paintIdleCard(arm: Arm): void {
  const who = must(document.getElementById(`who-${arm}`), `who-${arm}`);
  const where = must(document.getElementById(`where-${arm}`), `where-${arm}`);
  const face = must(document.getElementById(`face-${arm}`), `face-${arm}`);
  const stamp = must(document.getElementById(`stamp-${arm}`), `stamp-${arm}`);
  const viewer = must(document.getElementById(`viewer-${arm}`), `viewer-${arm}`);
  const card = must(document.getElementById(`card-${arm}`), `card-${arm}`);
  who.textContent = '—';
  where.textContent = 'Вена';
  face.textContent = '?';
  face.style.setProperty('--hue', '200');
  stamp.textContent = '';
  viewer.textContent = arm === 'popularity' ? 'ждёт колоду по лайкам' : 'ждёт обычную колоду';
  card.classList.remove('liked', 'passed');
}

function paintCard(arm: Arm, event: SimEvent): void {
  const viewer = engine.byId.get(event.from);
  const candidate = engine.byId.get(event.to);
  if (!viewer || !candidate) return;
  const card = must(document.getElementById(`card-${arm}`), `card-${arm}`);
  card.classList.remove('liked', 'passed');
  void card.offsetWidth;
  must(document.getElementById(`viewer-${arm}`), `viewer-${arm}`).textContent =
    `${viewer.name}, ${viewer.age} смотрит`;
  const face = must(document.getElementById(`face-${arm}`), `face-${arm}`);
  face.textContent = candidate.name.slice(0, 1);
  face.style.setProperty('--hue', String(candidate.hue));
  must(document.getElementById(`who-${arm}`), `who-${arm}`).textContent =
    `${candidate.name}, ${candidate.age}`;
  must(document.getElementById(`where-${arm}`), `where-${arm}`).textContent = candidate.district;
  const stamp = must(document.getElementById(`stamp-${arm}`), `stamp-${arm}`);
  stamp.textContent = event.type === 'pass' ? 'нет' : 'лайк';
  card.classList.add(event.type === 'pass' ? 'passed' : 'liked');
  focus = { ...focus, [arm]: { viewer: viewer.id, candidate: candidate.id } };
}

function showMatch(event: SimEvent): void {
  const a = engine.byId.get(event.from);
  const b = engine.byId.get(event.to);
  if (!a || !b) return;
  paintFace(must(document.getElementById('boom-a'), 'boom-a'), a);
  paintFace(must(document.getElementById('boom-b'), 'boom-b'), b);
  must(document.getElementById('boom-names'), 'boom-names').textContent =
    `${a.name}, ${a.age}  ×  ${b.name}, ${b.age}`;
  boom.hidden = false;
  boomUntil = performance.now() + MATCH_MS;
  ticker.textContent = `Матч: ${a.name} и ${b.name}.`;
  ticker.classList.add('match');
}

function paintFace(el: HTMLElement, agent: Agent): void {
  el.textContent = agent.name.slice(0, 1);
  el.style.setProperty('--hue', String(agent.hue));
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

function applyEvents(now: number): boolean {
  const { events, done: finished } = engine.tick();
  for (const event of events) {
    if (event.type === 'pass' || event.type === 'like') {
      stats[event.arm].swipes += 1;
      queued.set(event.arm, event);
    }
    if (event.type === 'like') {
      stats[event.arm].likes += 1;
    }
    if (event.type === 'match') {
      stats[event.arm].matches += 1;
      showMatch(event);
    }
  }
  renderer.ingest(events, now);
  return finished;
}

function flushCards(now: number): void {
  if (now < cardDue) return;
  for (const arm of ['baseline', 'popularity'] as Arm[]) {
    const event = queued.get(arm);
    if (!event) continue;
    queued.delete(arm);
    paintCard(arm, event);
    cardDue = now + CARD_MS;
  }
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
      ticker.textContent = 'Ночь закончилась. Сравни карточки и то, где больше матчей.';
    }
  }
  flushCards(now);
  if (!boom.hidden && now >= boomUntil) {
    boom.hidden = true;
  }
  renderer.draw(engine.agents, now, focus);
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
  renderer.draw(engine.agents, performance.now(), focus);
});

boot();
raf = requestAnimationFrame(frame);
