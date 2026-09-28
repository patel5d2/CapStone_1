// No Clerk credentials or network access are needed: the key is publishable.
import assert from 'node:assert/strict'

const key = process.env.VITE_CLERK_PUBLISHABLE_KEY ?? ''
assert.match(key, /^pk_(test|live)_[A-Za-z0-9_-]+$/, 'Set the VITE_CLERK_PUBLISHABLE_KEY repository variable')
const keyHost = Buffer.from(key.replace(/^pk_(test|live)_/, ''), 'base64').toString('utf8')
assert.ok(keyHost.endsWith('$'), 'Clerk publishable key has an invalid host encoding')
const issuer = new URL(process.env.CLERK_ISSUER ?? '')
const jwks = new URL(process.env.CLERK_JWKS_URI ?? '')
assert.equal(issuer.protocol, 'https:', 'Clerk issuer must use HTTPS')
assert.equal(issuer.hostname, keyHost.slice(0, -1), 'Frontend key and backend issuer must use the same Clerk instance')
assert.equal(jwks.origin, issuer.origin, 'Clerk JWKS must use the configured issuer origin')
assert.equal(jwks.pathname, '/.well-known/jwks.json', 'Set the Clerk JWKS endpoint')
assert.match(process.env.VITE_REQUIRE_TWO_FACTOR ?? '', /^(true|false)$/, 'Set CAMPUSBRIDGE_AUTH_REQUIRE_TWO_FACTOR to true or false')
console.log('Release Clerk publishable key, issuer, JWKS, and two-factor setting are consistent.')
