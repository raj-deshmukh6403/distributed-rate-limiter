// Circuit breaker prevents hammering a down service
// States: CLOSED (normal) → OPEN (failing, reject fast) → HALF_OPEN (testing recovery)
export class CircuitBreaker {
  private failures = 0;
  private lastFailureTime = 0;
  private state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';

  private readonly threshold: number;      // failures before opening
  private readonly recoveryMs: number;     // how long to wait before retrying

  constructor(threshold: number = 5, recoveryMs: number = 30000) {
    this.threshold   = threshold;
    this.recoveryMs  = recoveryMs;
  }

  // Returns true if request should be allowed through
  isAllowed(): boolean {
    if (this.state === 'CLOSED') return true;

    if (this.state === 'OPEN') {
      // Check if enough time has passed to try again
      if (Date.now() - this.lastFailureTime > this.recoveryMs) {
        this.state = 'HALF_OPEN';
        return true;
      }
      return false; // still open, reject fast
    }

    // HALF_OPEN — allow one request through to test recovery
    return true;
  }

  // Call when a request succeeds
  onSuccess(): void {
    this.failures = 0;
    this.state    = 'CLOSED';
  }

  // Call when a request fails
  onFailure(): void {
    this.failures++;
    this.lastFailureTime = Date.now();

    if (this.failures >= this.threshold) {
      this.state = 'OPEN';
      console.warn(`[RateLimiter] Circuit breaker OPEN after ${this.failures} failures`);
    }
  }

  getState(): string {
    return this.state;
  }
}