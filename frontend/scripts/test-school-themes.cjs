const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { test } = require('node:test')
const assert = require('node:assert/strict')

// Invariant 11: every school palette meets the contrast ledger in context/4_ui_design.md,
// in both modes. Reads the real index.css, so a hand-edited hex cannot slip through.
const css = readFileSync(resolve(__dirname, '../src/index.css'), 'utf8')
const themeTs = readFileSync(resolve(__dirname, '../src/lib/schoolTheme.ts'), 'utf8')

const rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
const lin = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }
const lum = (h) => { const [r, g, b] = rgb(h); return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) }
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05) }
const over = (fg, bg, alpha) => '#' + rgb(fg).map((v, i) => Math.round(rgb(bg)[i] + (v - rgb(bg)[i]) * alpha).toString(16).padStart(2, '0')).join('')

const SURFACE = '#ffffff', MUTED = '#f7f5f1', DARK = '#1c1f1d', WHITE = '#ffffff'

const palettes = {}
for (const [, slug, body] of css.matchAll(/:root\[data-school="([a-z]+)"\]\s*\{([^}]*)\}/g)) {
  palettes[slug] = Object.fromEntries([...body.matchAll(/--color-primary-(\d+):\s*(#[0-9a-f]{6})/gi)].map(([, k, v]) => [k, v.toLowerCase()]))
}
const slugs = [...themeTs.matchAll(/:\s*'([a-z]+)',/g)].map(([, slug]) => slug)

test('every school the app can select has a palette, and none is orphaned', () => {
  assert.ok(slugs.length >= 7, `expected the seeded schools in schoolTheme.ts, found ${slugs}`)
  assert.deepEqual([...slugs].sort(), Object.keys(palettes).sort())
})

for (const slug of slugs) {
  test(`${slug}: palette meets the contrast ledger in light and dark mode`, () => {
    const p = palettes[slug]
    for (const step of ['50', '100', '200', '400', '500', '600', '700', '900']) assert.ok(p[step], `missing primary-${step}`)
    const checks = [
      ['white on 600 (primary button)', ratio(WHITE, p[600]), 4.5],
      ['600 on surface (link)', ratio(p[600], SURFACE), 4.5],
      ['600 on surface-muted (link)', ratio(p[600], MUTED), 4.5],
      ['700 on 50 (secondary button, badge)', ratio(p[700], p[50]), 4.5],
      ['100 on 700 (landing hero)', ratio(p[100], p[700]), 4.5],
      ['100 on 600 (tab count)', ratio(p[100], p[600]), 4.5],
      ['500 on surface (focus border)', ratio(p[500], SURFACE), 3],
      ['500 on dark surface (focus border)', ratio(p[500], DARK), 3],
      ['400 on dark surface (dark-mode text)', ratio(p[400], DARK), 4.5],
      ['200 on 900 at 40% over dark surface (dark active nav)', ratio(p[200], over(p[900], DARK, 0.4)), 4.5],
    ]
    for (const [label, value, floor] of checks) {
      assert.ok(value >= floor, `${slug} ${label}: ${value.toFixed(2)}:1, needs ${floor}:1`)
    }
  })
}
