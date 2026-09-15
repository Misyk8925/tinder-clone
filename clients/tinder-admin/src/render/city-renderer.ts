import type { Agent, Arm, SimEvent } from '../sim/types';

interface Arc {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  arm: Arm;
  born: number;
  match: boolean;
}

export class CityRenderer {
  private readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private arcs: Arc[] = [];
  private pulse = 0;

  constructor(canvas: HTMLCanvasElement) {
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('canvas unsupported');
    }
    this.canvas = canvas;
    this.ctx = ctx;
  }

  resize(): void {
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    this.canvas.width = Math.max(1, Math.floor(rect.width * dpr));
    this.canvas.height = Math.max(1, Math.floor(rect.height * dpr));
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  ingest(agents: Agent[], events: SimEvent[], now: number): void {
    const project = this.projector(agents);
    for (const event of events) {
      if (event.type === 'pass') continue;
      const from = agents[event.from];
      const to = agents[event.to];
      if (!from || !to) continue;
      const a = project(from);
      const b = project(to);
      this.arcs.push({
        x1: a.x,
        y1: a.y,
        x2: b.x,
        y2: b.y,
        arm: event.arm,
        born: now,
        match: event.type === 'match',
      });
    }
    if (this.arcs.length > 240) {
      this.arcs.splice(0, this.arcs.length - 240);
    }
  }

  draw(agents: Agent[], now: number): void {
    const { ctx, canvas } = this;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);
    this.drawGrid(w, h);
    const project = this.projector(agents);

    for (const agent of agents) {
      const p = project(agent);
      const heat = Math.min(1, Math.log1p(agent.likesReceived) / 3);
      const r = 1.2 + heat * 3.4;
      ctx.beginPath();
      ctx.fillStyle = agent.arm === 'popularity'
        ? `rgba(94, 224, 255, ${0.18 + heat * 0.7})`
        : `rgba(246, 181, 63, ${0.16 + heat * 0.7})`;
      ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    this.pulse += 1;
    const remaining: Arc[] = [];
    for (const arc of this.arcs) {
      const age = now - arc.born;
      if (age > 1400) continue;
      remaining.push(arc);
      const t = 1 - age / 1400;
      ctx.beginPath();
      ctx.strokeStyle = arc.match
        ? `rgba(156, 206, 43, ${0.15 + t * 0.75})`
        : arc.arm === 'popularity'
          ? `rgba(94, 224, 255, ${0.08 + t * 0.45})`
          : `rgba(246, 181, 63, ${0.08 + t * 0.45})`;
      ctx.lineWidth = arc.match ? 2.2 : 1.1;
      ctx.moveTo(arc.x1, arc.y1);
      ctx.quadraticCurveTo((arc.x1 + arc.x2) / 2, Math.min(arc.y1, arc.y2) - 18, arc.x2, arc.y2);
      ctx.stroke();
      if (arc.match) {
        const q = 0.5 + 0.5 * Math.sin((this.pulse + age) / 80);
        ctx.beginPath();
        ctx.fillStyle = `rgba(156, 206, 43, ${0.25 + q * 0.4})`;
        ctx.arc(arc.x2, arc.y2, 5 + q * 4, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    this.arcs = remaining;
  }

  private projector(agents: Agent[]): (agent: Agent) => { x: number; y: number } {
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
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
    const pad = 28;
    const spanX = Math.max(0.001, maxX - minX);
    const spanY = Math.max(0.001, maxY - minY);
    return (agent: Agent) => ({
      x: pad + ((agent.x - minX) / spanX) * (w - pad * 2),
      y: pad + ((maxY - agent.y) / spanY) * (h - pad * 2),
    });
  }

  private drawGrid(w: number, h: number): void {
    const ctx = this.ctx;
    ctx.fillStyle = '#0b0d10';
    ctx.fillRect(0, 0, w, h);
    ctx.strokeStyle = 'rgba(255,255,255,0.035)';
    ctx.lineWidth = 1;
    for (let x = 40; x < w; x += 48) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }
    for (let y = 40; y < h; y += 48) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }
  }
}
