interface CacheEntry {
  value: boolean;
  expiresAt: number;
}

// Simple in-memory cache for deny decisions
// Avoids hammering the service when we know a key is already rate limited
export class LocalCache {
  private cache = new Map<string, CacheEntry>();
  private ttlMs: number;

  constructor(ttlMs: number = 1000) {
    this.ttlMs = ttlMs;
  }

  set(key: string, value: boolean): void {
    this.cache.set(key, {
      value,
      expiresAt: Date.now() + this.ttlMs,
    });
  }

  get(key: string): boolean | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    // Entry expired — remove it and return null
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }

    return entry.value;
  }

  delete(key: string): void {
    this.cache.delete(key);
  }

  clear(): void {
    this.cache.clear();
  }
}