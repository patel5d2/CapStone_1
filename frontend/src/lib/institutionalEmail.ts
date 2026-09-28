/**
 * Mirrors `InstitutionalAccessPolicy` on the server. Keep the two in step.
 *
 * This is NOT enforcement. It stops someone creating an account they could never use
 * and tells them why while they can still fix it; the account is still refused by the
 * API if it ever exists, and that refusal is the actual gate.
 */
const SUFFIX = '.edu'

/** The domain part of an address, lower-cased, or null if there isn't one. */
export function domainOf(email: string | undefined | null): string | null {
  if (!email) return null
  const trimmed = email.trim()
  const at = trimmed.lastIndexOf('@')
  // Same as the server: split on the LAST '@', so a quoted local part cannot spoof
  // the domain, and require something before it.
  if (at < 1 || at === trimmed.length - 1) return null
  const domain = trimmed.slice(at + 1).toLowerCase().replace(/\.$/, '')
  return domain && !/\s/.test(domain) ? domain : null
}

export function isInstitutionalEmail(email: string | undefined | null): boolean {
  const domain = domainOf(email)
  return !!domain && domain.length > SUFFIX.length && domain.endsWith(SUFFIX)
}

/**
 * Clerk requires a username on this instance, but CampusBridge never shows one. Rather
 * than make every student invent one, derive it from the address they already typed.
 */
export function suggestUsername(email: string): string {
  const local = email.trim().toLowerCase().split('@')[0] ?? ''
  const base = local.replace(/[^a-z0-9_]/g, '').slice(0, 20)
  // Clerk rejects very short usernames; pad rather than fail on someone called "jo".
  return base.length >= 4 ? base : `${base}${Math.floor(1000 + Math.random() * 9000)}`
}
