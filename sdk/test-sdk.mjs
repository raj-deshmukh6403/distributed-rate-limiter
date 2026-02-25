import { RateLimiter } from './dist/index.mjs';

const limiter = new RateLimiter({
  serviceUrl: 'http://localhost:8080',
  apiKey: '452b12ffc5ae404f95867040',  // your registered key
  failOpen: true,
});

console.log('Testing rate limiter SDK...\n');

// Hit it 6 times
for (let i = 1; i <= 6; i++) {
  const result = await limiter.check({ identifier: 'sdk-test-user' });
  console.log(`Request ${i}: allowed=${result.allowed} remaining=${result.remaining}`);
}