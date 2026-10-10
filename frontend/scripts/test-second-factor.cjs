const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { runInNewContext } = require('node:vm')
const { test } = require('node:test')
const assert = require('node:assert/strict')
const ts = require('typescript')

// A new sign-up enrols the second factor without verifying it, so the session's fva is
// [n, -1] and the API refuses every call. RequireAuth must ask for the factor first and
// must not run the profile check on a session the API will refuse.
function loadApp(mocks) {
  const source = readFileSync(resolve(__dirname, '../src/App.tsx'), 'utf8')
    .replace(/import\.meta\.env\.VITE_REQUIRE_TWO_FACTOR/g, 'undefined') // default: required
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  })
  const exports = {}
  const jsx = (type, props) => ({ type, props })
  runInNewContext(outputText, {
    exports,
    require: (name) => name === 'react/jsx-runtime' ? { jsx, jsxs: jsx, Fragment: 'Fragment' } : mocks[name] ?? {},
  })
  return exports
}

function render(factorVerificationAge) {
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
    './lib/profileGate': { useProfileMissing: (userId) => { profileCheckedFor.push(userId); return false } },
    './components/ui/Feedback': { Spinner: 'Spinner' },
  })
  return { view: RequireAuth({ children: 'page' }), profileCheckedFor }
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
