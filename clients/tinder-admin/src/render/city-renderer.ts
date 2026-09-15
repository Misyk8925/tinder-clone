import type { Agent, Arm, SimEvent } from '../sim/types';

export type MapFocus = Partial<Record<Arm, { viewer: number; candidate: number }>>;

interface Pulse {
  ids: number[];
  arm: Arm;
  kind: 'like' | 'match';
  born: number;
}

interface Pane {
  x: number;
  y: number;
  w: number;
  h: number;
}

const GAP = 16;

export class CityRenderer {
  private readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private pulses: Pulse[] = [];
  private pulseClock = 0;

  constructor(canvas: HTMLCanvasElement) {
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('canvas unsupported');
    }
    this.canvas = canvas;
    this.ctx = ctx;
  }

  reset(): void {
    this.pulses = [];
  }

  resize(): void {
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    this.canvas.width = Math.max(1, Math.floor(rect.width * dpr));
    this.canvas.height = Math.max(1, Math.floor(rect.height * dpr));
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  ingest(events: SimEvent[], now: number): void {
    for (const event of events) {
      if (event.type === 'pass') continue;
      this.pulses.push({
        ids: event.type === 'match' ? [event.from, event.to] : [event.to],
        arm: event.arm,
        kind: event.type,
        born: now,
      });
    }
    if (this.pulses.length > 48) {
      this.pulses.splice(0, this.pulses.length - 48);
    }
  }

  draw(agents: Agent[], now: number, focus: MapFocus = {}): void {
    const { ctx } = this;
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = '#0b0d10';
    ctx.fillRect(0, 0, w, h);

    const left: Pane = { x: 0, y: 0, w: (w - GAP) / 2, h };
    const right: Pane = { x: left.w + GAP, y: 0, w: w - left.w - GAP, h };
    this.drawPane(agents, left, 'baseline', '#f6b53f', now, focus);
    this.drawPane(agents, right, 'popularity', '#5ee0ff', now, focus);

    ctx.fillStyle = 'rgba(255,255,255,0.08)';
    ctx.fillRect(left.w + GAP / 2 - 0.5, 12, 1, h - 24);

    this.pulses = this.pulses.filter((pulse) => {
      const life = pulse.kind === 'match' ? 1600 : 700;
      return now - pulse.born <= life;
    });
  }

  private drawPane(
    agents: Agent[],
    pane: Pane,
    arm: Arm,
    color: string,
    now: number,
    focus: MapFocus,
  ): void {
    const { ctx } = this;
    ctx.save();
    ctx.beginPath();
    ctx.rect(pane.x, pane.y, pane.w, pane.h);
    ctx.clip();
    this.drawGrid(pane);
    const project = this.projector(agents, pane);

    for (const agent of agents) {
      const p = project(agent);
      const heat = Math.min(1, Math.log1p(agent.likesFrom[arm]) / 2.4);
      const r = 1.4 + heat * 5.5;
      ctx.beginPath();
      ctx.fillStyle = withAlpha(color, 0.16 + heat * 0.78);
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    this.pulseClock += 1;
    for (const pulse of this.pulses) {
      if (pulse.arm !== arm) continue;
      const age = now - pulse.born;
      const life = pulse.kind === 'match' ? 1600 : 700;
      if (age > life) continue;
      const t = 1 - age / life;
      for (const id of pulse.ids) {
        const agent = agents[id];
        if (!agent) continue;
        const p = project(agent);
        ctx.beginPath();
        if (pulse.kind === 'match') {
          const q = 0.55 + 0.45 * Math.sin((this.pulseClock + age) / 70);
          ctx.fillStyle = `rgba(156, 206, 43, ${0.35 + t * 0.55})`;
          ctx.arc(p.x, p.y, 6 + q * 5, 0, Math.PI * 2);
        } else {
          ctx.fillStyle = `rgba(255, 255, 255, ${0.18 + t * 0.45})`;
          ctx.arc(p.x, p.y, 3.2 + t * 2, 0, Math.PI * 2);
        }
        ctx.fill();
      }
    }

    const live = focus[arm];
    if (live) {
      const viewer = agents[live.viewer];
      const candidate = agents[live.candidate];
      if (viewer) {
        const p = project(viewer);
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(255,255,255,0.85)';
        ctx.lineWidth = 2;
        ctx.arc(p.x, p.y, 8, 0, Math.PI * 2);
        ctx.stroke();
      }
      if (candidate) {
        const p = project(candidate);
        ctx.beginPath();
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.4;
        ctx.arc(p.x, p.y, 10, 0, Math.PI * 2);
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  private projector(agents: Agent[], pane: Pane): (agent: Agent) => { x: number; y: number } {
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const agent of agents) {
      minX = Math.min(minX, agent.x);
      maxX = Math.max(maxX, agent.x);
      minY = Math.min(minY, agent.y);
      maxY = Math.max(maxY, agent.y);
    }
    const pad = 22;
    const spanX = Math.max(0.001, maxX - minX);
    const spanY = Math.max(0.001, maxY - minY);
    return (agent: Agent) => ({
      x: pane.x + pad + ((agent.x - minX) / spanX) * (pane.w - pad * 2),
      y: pane.y + pad + ((maxY - agent.y) / spanY) * (pane.h - pad * 2),
    });
  }

  private drawGrid(pane: Pane): void {
    const ctx = this.ctx;
    ctx.strokeStyle = 'rgba(255,255,255,0.035)';
    ctx.lineWidth = 1;
    for (let x = pane.x + 36; x < pane.x + pane.w; x += 44) {
      ctx.beginPath();
      ctx.moveTo(x, pane.y);
      ctx.lineTo(x, pane.y + pane.h);
      ctx.stroke();
    }
    for (let y = pane.y + 36; y < pane.y + pane.h; y += 44) {
      ctx.beginPath();
      ctx.moveTo(pane.x, y);
      ctx.lineTo(pane.x + pane.w, y);
      ctx.stroke();
    }
  }
}

function withAlpha(hex: string, alpha: number): string {
  const r = Number.parseInt(hex.slice(1, 3), 16);
  const g = Number.parseInt(hex.slice(3, 5), 16);
  const b = Number.parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
