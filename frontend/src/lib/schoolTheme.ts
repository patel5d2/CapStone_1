/**
 * Objective 8: a signed-in student's school recolours the app. This only stamps
 * `data-school` on <html>; the palettes themselves live in index.css, so components keep
 * using the same primary tokens and pick up the school automatically.
 *
 * Keyed by the school names the database seeds (data.sql). Cincinnati Christian University
 * closed in 2019 and has no theme; any school not listed falls back to the pine green.
 */
const SLUG_BY_SCHOOL: Record<string, string> = {
  'University of Cincinnati': 'uc',
  'Xavier University': 'xavier',
  'Northern Kentucky University': 'nku',
  'Miami University': 'miami',
  'Cincinnati State Technical and Community College': 'cincystate',
  'Mount St. Joseph University': 'msj',
  'Thomas More University': 'thomasmore',
}
const SLUGS = new Set(Object.values(SLUG_BY_SCHOOL))
const STORAGE_KEY = 'campusbridge-school'
const listeners = new Set<() => void>()

export function slugForSchool(name?: string | null): string | null {
  return (name && SLUG_BY_SCHOOL[name]) || null
}

/** Applies a school's palette, or the default one for null, and tells subscribers. */
export function applySchoolTheme(slug: string | null) {
  const root = document.documentElement
  const next = slug && SLUGS.has(slug) ? slug : null
  if ((root.dataset.school ?? null) === next) return
  if (next) root.dataset.school = next
  else delete root.dataset.school
  try {
    if (next) localStorage.setItem(STORAGE_KEY, next)
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage blocked (private window): the theme still applies, it just isn't remembered.
  }
  listeners.forEach((listener) => listener())
}

/** Re-applies the last school before first paint, so a reload does not flash green. */
export function restoreSchoolTheme() {
  try {
    applySchoolTheme(localStorage.getItem(STORAGE_KEY))
  } catch {
    // Nothing remembered; the default palette shows until the profile loads.
  }
}

export function subscribeSchoolTheme(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function currentSchool(): string | null {
  return document.documentElement.dataset.school ?? null
}

/** The active --color-primary-600 as a literal hex, which Clerk needs to derive its shades. */
export function activePrimaryHex(): string {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-primary-600').trim() || '#2c6a4d'
}
