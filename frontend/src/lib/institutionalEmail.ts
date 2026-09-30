/**
 * Mirrors `InstitutionalAccessPolicy` on the server. Keep the two in step.
 *
 * This is NOT enforcement. It stops someone creating an account they could never use
 * and tells them why while they can still fix it; the account is still refused by the
 * API if it ever exists, and that refusal is the actual gate.
 */
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

/** Any well-formed address is accepted; the .edu-only rule was dropped. */
export function isInstitutionalEmail(email: string | undefined | null): boolean {
  return !!domainOf(email)
}

