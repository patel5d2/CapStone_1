const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { runInNewContext } = require('node:vm')
const { test } = require('node:test')
const assert = require('node:assert/strict')
const ts = require('typescript')

// Exercise the components with controlled Clerk session states, without credentials.
function load(file, mocks) {
  const source = readFileSync(resolve(__dirname, '../src', file), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  })
  const exports = {}
  const jsx = (type, props) => ({ type, props })
  runInNewContext(outputText, {
    exports,
    require: (name) => name === 'react/jsx-runtime'
      ? { jsx, jsxs: jsx, Fragment: 'Fragment' }
      : mocks[name] ?? {},
  })
  return exports
}

for (const [label, state, destination] of [
  ['loading', { isLoaded: false }, 'Spinner'],
  ['signed out', { isLoaded: true, session: null }, 'form'],
  ['active session', { isLoaded: true, session: { status: 'active' } }, '/marketplace'],
  ['pending MFA', { isLoaded: true, session: { status: 'pending', currentTask: { key: 'setup-mfa' } } }, '/session-tasks/setup-mfa'],
]) {
  test(`auth forms: ${label}`, () => {
    const { AuthSessionGate } = load('components/AuthSessionGate.tsx', {
      '@clerk/clerk-react': { useSession: () => state },
      'react-router-dom': { Navigate: 'Navigate' },
      './ui/Feedback': { Spinner: 'Spinner' },
    })
    const view = AuthSessionGate({ children: 'form' })
    assert.equal(view.props.to ?? view.props.children ?? view.type, destination)
    if (view.type === 'Navigate') assert.equal(view.props.replace, true)
  })
}

function findForm(node) {
  if (!node || typeof node !== 'object') return undefined
  if (node.type === 'form') return node
  return [node.props?.children].flat().map(findForm).find(Boolean)
}

for (const [page, hook, method, destination] of [
  ['SignInPage', 'useSignIn', 'attemptFirstFactor', '/marketplace'],
  ['SignUpPage', 'useSignUp', 'attemptEmailAddressVerification', '/profile'],
]) {
  for (const pending of [false, true]) {
    test(`${page}: activation ${pending ? 'preserves MFA redirect' : 'continues normally'}`, async () => {
      const calls = []
      let stateIndex = 0
      const resource = { [method]: async () => ({ status: 'complete', createdSessionId: 'session-test' }) }
      const pageModule = load(`pages/${page}.tsx`, {
        react: { useState: (initial) => [stateIndex++ === 2 ? true : initial, () => {}] },
        '@clerk/clerk-react': {
          [hook]: () => ({
            isLoaded: true,
            signIn: resource,
            signUp: resource,
            setActive: async (options) => {
              assert.equal(options.session, 'session-test')
              assert.equal(typeof options.navigate, 'function')
              // taskUrls handles pending sessions without invoking navigate.
              if (pending) calls.push('/session-tasks/setup-mfa')
              else await options.navigate({ session: { status: 'active' } })
            },
          }),
        },
        'react-router-dom': { useNavigate: () => (url) => calls.push(url) },
      })
      await findForm(pageModule.default()).props.onSubmit({ preventDefault() {} })
      assert.deepEqual(calls, [pending ? '/session-tasks/setup-mfa' : destination])
    })
  }
}
