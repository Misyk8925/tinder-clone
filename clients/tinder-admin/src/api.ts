export type ExperimentStatus = 'DRAFT' | 'RUNNING' | 'STOPPED';

export interface Experiment {
  id: string;
  name: string;
  salt: string;
  defaultVariant: string;
  status: ExperimentStatus;
  split: Record<string, number>;
  createdAt: string;
  startedAt: string | null;
  stoppedAt: string | null;
}

export interface VariantOutcome {
  variant: string;
  viewers: number;
  swipes: number;
  likes: number;
  matches: number;
  likesPer100Swipes: number;
  matchesPer100Swipes: number;
  matchesPer100SwipesLower: number;
  matchesPer100SwipesUpper: number;
}

export interface Results {
  experiment: string;
  from: string;
  to: string;
  variants: VariantOutcome[];
}

export interface ExperimentRequest {
  name: string;
  salt: string;
  defaultVariant: string;
  split: Record<string, number>;
}

/** Thrown for any non-2xx response, carrying the status so 409 can be shown as a rule. */
export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const BASE = '/api/v1/admin/experiments';

export class ExperimentApi {
  constructor(private readonly token: () => Promise<string>) {}

  list(): Promise<Experiment[]> {
    return this.send('GET', BASE);
  }

  create(request: ExperimentRequest): Promise<Experiment> {
    return this.send('POST', BASE, request);
  }

  start(id: string): Promise<Experiment> {
    return this.send('POST', `${BASE}/${id}/start`);
  }

  stop(id: string): Promise<Experiment> {
    return this.send('POST', `${BASE}/${id}/stop`);
  }

  results(id: string): Promise<Results> {
    return this.send('GET', `${BASE}/${id}/results`);
  }

  private async send<T>(method: string, url: string, body?: unknown): Promise<T> {
    const response = await fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${await this.token()}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (!response.ok) {
      throw new ApiError(response.status, await describe(response));
    }
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  }
}

async function describe(response: Response): Promise<string> {
  if (response.status === 403) return 'Нужна роль ADMIN';
  try {
    const payload = await response.json();
    if (payload && typeof payload.error === 'string') return payload.error;
    if (payload && typeof payload.message === 'string') return payload.message;
  } catch {
    // A gateway error page is not JSON; the status alone is the message.
  }
  return `Запрос не прошёл: ${response.status}`;
}
