const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { runInNewContext } = require('node:vm')
const { test } = require('node:test')
const assert = require('node:assert/strict')
const { transformSync } = require('esbuild')

// A new sign-up enrols the second factor without verifying it, so the session's fva is
// [n, -1] and the API refuses every call. RequireAuth must ask for the factor first and
// must not run the profile check on a session the API will refuse.
function loadApp(mocks) {
  const source = readFileSync(resolve(__dirname, '../src/App.tsx'), 'utf8')
    .replace(/import\.meta\.env\.VITE_REQUIRE_TWO_FACTOR/g, 'undefined') // default: required
  // TypeScript 7 has no transpileModule; esbuild strips types and emits CommonJS + react-jsx.
  const { code: outputText } = transformSync(source, { loader: 'tsx', format: 'cjs', jsx: 'automatic' })
  const module = { exports: {} }
  const jsx = (type, props) => ({ type, props })
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => name === 'react/jsx-runtime' ? { jsx, jsxs: jsx, Fragment: 'Fragment' } : mocks[name] ?? {},
  })
  return module.exports
}

function render(factorVerificationAge, { profile = 'complete', signupStep = false, retryable = true } = {}) {
  const profileCheckedFor = []
  const { RequireAuth } = loadApp({
    react: { useEffect: () => {}, useRef: () => ({ current: false }), useState: (v) => [v, () => {}] },
    'react-router-dom': { Navigate: 'Navigate' },
    '@clerk/clerk-react': {
      useAuth: () => ({ isLoaded: true, isSignedIn: true }),
      useSession: () => ({ isLoaded: true, session: { status: 'active', factorVerificationAge } }),
      useUser: () => ({ user: { id: 'user_1', twoFactorEnabled: true, primaryEmailAddress: { emailAddress: 'a@school.edu' } } }),
    },
    './lib/institutionalEmail': { isInstitutionalEmail: () => true },
    './lib/profileGate': { useProfileCheck: (userId) => {
      profileCheckedFor.push(userId)
      return { status: profile, message: 'Service unavailable', retryable, retry: () => {} }
    } },
    './components/ui/Feedback': { Spinner: 'Spinner' },
  })
  return { view: RequireAuth({ children: 'page', signupStep }), profileCheckedFor }
}

test('just signed up: second factor enrolled but never verified -> confirm it, no profile check', () => {
  const { view, profileCheckedFor } = render([0, -1])
  assert.equal(view.type.name, 'ConfirmSecondFactor')
  assert.deepEqual(profileCheckedFor, [undefined])
})

test('signed in with the second factor -> profile check runs and the page renders', () => {
  const { view, profileCheckedFor } = render([0, 0])
  assert.equal(view.props.children, 'page')
  assert.deepEqual(profileCheckedFor, ['user_1'])
})

// #114: the student account/profile is created during sign-up, before the app is entered.
test('no profile yet -> every app page sends the student to the profile step of sign-up', () => {
  const { view } = render([0, 0], { profile: 'missing' })
  assert.equal(view.type, 'Navigate')
  assert.equal(view.props.to, '/sign-up/profile')
})

test('profile check still running -> neither the app nor the sign-up step is shown', () => {
  for (const signupStep of [false, true]) assert.equal(render([0, 0], { profile: 'checking', signupStep }).view.type, 'Spinner')
})

test('sign-up profile step: shown to a student with no profile', () => {
  assert.equal(render([0, 0], { profile: 'missing', signupStep: true }).view.props.children, 'page')
})

test('sign-up profile step: a registered student is sent into the app, not asked again', () => {
  const { view } = render([0, 0], { profile: 'complete', signupStep: true })
  assert.equal(view.type, 'Navigate')
  assert.equal(view.props.to, '/marketplace')
})

test('sign-up profile step: an unverified second factor is confirmed first', () => {
  assert.equal(render([0, -1], { profile: 'missing', signupStep: true }).view.type.name, 'ConfirmSecondFactor')
})

test('profile check failed -> the app stays closed and a retry is offered, on both gates', () => {
  for (const signupStep of [false, true]) {
    const { view } = render([0, 0], { profile: 'failed', signupStep })
    assert.equal(view.type.name, 'AccountCheckFailed')
    assert.equal(view.props.message, 'Service unavailable')
    assert.equal(typeof view.props.onRetry, 'function')
  }
})

test('profile bound to another Clerk account -> the refusal is shown without a pointless retry', () => {
  const { view } = render([0, 0], { profile: 'failed', retryable: false })
  assert.equal(view.type.name, 'AccountCheckFailed')
  assert.equal(view.props.onRetry, undefined)
  // Rendered: the title names the conflict and there is no "Try again".
  const card = view.type(view.props)
  const text = JSON.stringify(card)
  assert.match(text, /This email already has a profile/)
  assert.doesNotMatch(text, /Try again/)
})
