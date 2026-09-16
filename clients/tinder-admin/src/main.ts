import Keycloak from 'keycloak-js';
import { ApiError, ExperimentApi, type Experiment, type Results, type VariantOutcome } from './api';

interface AppConfig {
  keycloak: { url: string; realm: string; clientId: string };
}

const ADMIN_ROLE = 'ADMIN';
const root = document.getElementById('app') as HTMLElement;

void start();

async function start(): Promise<void> {
  const config: AppConfig = await (await fetch('/config.json')).json();
  const keycloak = new Keycloak(config.keycloak);

  // This app has nothing to show a signed-out visitor, so it demands a session up front
  // rather than rendering a shell first.
  await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });

  if (!keycloak.realmAccess?.roles?.includes(ADMIN_ROLE)) {
    // The gateway would refuse the calls anyway; saying so here avoids a screen of 403s.
    renderFatal(`У пользователя нет роли ${ADMIN_ROLE}. Экспериментами управляют только операторы.`);
    return;
  }

  const api = new ExperimentApi(async () => {
    await keycloak.updateToken(30);
    return keycloak.token ?? '';
  });

  await render(api);
}

async function render(api: ExperimentApi, message?: string): Promise<void> {
  root.classList.remove('loading');
  let experiments: Experiment[];
  try {
    experiments = await api.list();
  } catch (error) {
    renderFatal(describe(error));
    return;
  }

  root.replaceChildren();
  root.append(
    element('h1', {}, 'Эксперименты ранжирования'),
    element('p', { class: 'subtitle' }, 'Одновременно может идти только один эксперимент.'),
  );

  if (message) {
    root.append(element('p', { class: 'error' }, message));
  }

  root.append(element('h2', {}, 'Существующие'));
  if (experiments.length === 0) {
    root.append(element('p', { class: 'empty' }, 'Пока ни одного. Создайте черновик ниже.'));
  }
  for (const experiment of experiments) {
    root.append(experimentPanel(api, experiment, experiments));
  }

  root.append(element('h2', {}, 'Новый черновик'), createForm(api));
}

function experimentPanel(api: ExperimentApi, experiment: Experiment, all: Experiment[]): HTMLElement {
  const split = Object.entries(experiment.split)
    .map(([variant, weight]) => `${variant} ${weight}`)
    .join(' · ');

  const details = element(
    'div',
    {},
    element('h3', {}, experiment.name),
    element('p', { class: 'meta' }, `${split} · по умолчанию ${experiment.defaultVariant}`),
    metaWithCode('соль ', experiment.salt),
    element('p', { class: 'meta' }, timeline(experiment)),
  );

  const actions = element('div', {}, element('p', { class: `status ${experiment.status}` }, experiment.status));

  if (experiment.status === 'DRAFT') {
    const blocked = all.some((other) => other.status === 'RUNNING');
    const start = element('button', { class: 'primary' }, 'Запустить') as HTMLButtonElement;
    start.disabled = blocked;
    if (blocked) start.title = 'Сначала остановите работающий эксперимент';
    start.addEventListener('click', () => act(api, () => api.start(experiment.id)));
    actions.append(start);
  }

  if (experiment.status === 'RUNNING') {
    const stop = element('button', {}, 'Остановить');
    stop.addEventListener('click', () => act(api, () => api.stop(experiment.id)));
    actions.append(stop);
  }

  const panel = element('section', { class: 'panel' }, element('div', { class: 'experiment' }, details, actions));

  if (experiment.status !== 'DRAFT') {
    const show = element('button', {}, 'Показать исходы');
    const slot = element('div', {});
    show.addEventListener('click', async () => {
      show.setAttribute('disabled', 'true');
      slot.replaceChildren(element('p', { class: 'meta' }, 'Считаю…'));
      try {
        slot.replaceChildren(resultsTable(await api.results(experiment.id)));
      } catch (error) {
        slot.replaceChildren(element('p', { class: 'error' }, describe(error)));
      } finally {
        show.removeAttribute('disabled');
      }
    });
    panel.append(show, slot);
  }

  return panel;
}

function resultsTable(results: Results): HTMLElement {
  const rows: (Node | string)[] = [
    element(
      'tr',
      {},
      element('th', {}, 'Вариант'),
      element('th', {}, 'Зрители'),
      element('th', {}, 'Свайпы'),
      element('th', {}, 'Лайки'),
      element('th', {}, 'Матчи'),
      element('th', {}, 'Матчей на 100 свайпов'),
    ),
  ];

  for (const variant of results.variants) {
    rows.push(
      element(
        'tr',
        {},
        element('td', {}, variant.variant),
        element('td', {}, String(variant.viewers)),
        element('td', {}, String(variant.swipes)),
        element('td', {}, String(variant.likes)),
        element('td', {}, String(variant.matches)),
        element('td', {}, rateWithInterval(variant)),
      ),
    );
  }

  const overlap = intervalsOverlap(results.variants);
  return element(
    'div',
    { class: 'results' },
    element('table', {}, ...rows),
    element(
      'p',
      { class: overlap ? 'meta warn' : 'meta' },
      overlap
        ? 'Интервалы пересекаются — варианты не различены, какими бы разными ни выглядели средние.'
        : 'Интервалы не пересекаются.',
    ),
    element(
      'p',
      { class: 'meta' },
      '95% интервал получен пересэмплированием зрителей, не свайпов: свайпы одного человека ' +
        'связаны между собой, поэтому единица наблюдения здесь — человек.',
    ),
  );
}

function rateWithInterval(variant: VariantOutcome): string {
  const point = variant.matchesPer100Swipes.toFixed(2);
  const low = variant.matchesPer100SwipesLower.toFixed(2);
  const high = variant.matchesPer100SwipesUpper.toFixed(2);
  return `${point}  (${low}–${high})`;
}

// Two arms whose 95% intervals overlap have not been told apart. Saying so on the screen
// keeps a promising-looking average from being read as a result.
function intervalsOverlap(variants: VariantOutcome[]): boolean {
  for (let i = 0; i < variants.length; i++) {
    for (let j = i + 1; j < variants.length; j++) {
      const a = variants[i];
      const b = variants[j];
      if (a.matchesPer100SwipesLower <= b.matchesPer100SwipesUpper &&
          b.matchesPer100SwipesLower <= a.matchesPer100SwipesUpper) {
        return true;
      }
    }
  }
  return false;
}

function createForm(api: ExperimentApi): HTMLElement {
  const name = input('name', 'reciprocity');
  const salt = input('salt', String(Date.now()));
  const baseline = input('baseline', '50', 'number');
  const reciprocal = input('reciprocal', '50', 'number');

  const submit = element('button', { class: 'primary' }, 'Создать черновик');
  submit.addEventListener('click', () =>
    act(api, () =>
      api.create({
        name: name.value.trim(),
        salt: salt.value.trim(),
        defaultVariant: 'baseline',
        split: { baseline: Number(baseline.value), reciprocal: Number(reciprocal.value) },
      }),
    ),
  );

  return element(
    'section',
    { class: 'panel' },
    element(
      'div',
      { class: 'fields' },
      labelled('Название', name),
      labelled('Соль (после запуска не меняется)', salt),
      labelled('Доля baseline', baseline),
      labelled('Доля reciprocal', reciprocal),
    ),
    submit,
  );
}

async function act(api: ExperimentApi, operation: () => Promise<unknown>): Promise<void> {
  try {
    await operation();
    await render(api);
  } catch (error) {
    await render(api, describe(error));
  }
}

function timeline(experiment: Experiment): string {
  const parts = [`создан ${short(experiment.createdAt)}`];
  if (experiment.startedAt) parts.push(`запущен ${short(experiment.startedAt)}`);
  if (experiment.stoppedAt) parts.push(`остановлен ${short(experiment.stoppedAt)}`);
  return parts.join(' · ');
}

function short(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

function describe(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : String(error);
}

function renderFatal(message: string): void {
  root.classList.remove('loading');
  root.replaceChildren(element('h1', {}, 'Эксперименты ранжирования'), element('p', { class: 'error' }, message));
}

function element(tag: string, attributes: Record<string, string>, ...children: (Node | string)[]): HTMLElement {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
  node.append(...children);
  return node;
}

function metaWithCode(prefix: string, value: string): HTMLElement {
  return element('p', { class: 'meta' }, prefix, element('code', {}, value));
}

function labelled(text: string, field: HTMLInputElement): HTMLElement {
  return element('div', {}, element('label', {}, text), field);
}

function input(name: string, value: string, type = 'text'): HTMLInputElement {
  const field = document.createElement('input');
  field.name = name;
  field.type = type;
  field.value = value;
  if (type === 'number') field.min = '0';
  return field;
}
