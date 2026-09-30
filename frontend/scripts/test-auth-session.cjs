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
      : name === 'react' ? { useEffect: () => {}, ...mocks[name] } : mocks[name] ?? {},
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

function findElement(node, type) {
  if (!node || typeof node !== 'object') return undefined
  if (node.type === type) return node
  return [node.props?.children].flat().map((child) => findElement(child, type)).find(Boolean)
}

for (const status of ['needs_second_factor', 'needs_new_password']) {
  test(`SignInPage: password verification continues into Clerk for ${status}`, async () => {
    const states = []
    let index = 0
    let activations = 0
    let pathname = '/sign-in'
    const signIn = {
      status: 'needs_first_factor',
      create: async () => ({ supportedFirstFactors: [{ strategy: 'password' }] }),
      attemptFirstFactor: async () => { signIn.status = status; return signIn },
    }
    const { default: Page } = load('pages/SignInPage.tsx', {
      react: {
        useState: (initial) => {
          const slot = index++
          if (!(slot in states)) states[slot] = slot === 0 ? 'student@school.edu' : slot === 1 ? 'password-example' : initial
          return [states[slot], (value) => { states[slot] = value }]
        },
      },
      '../lib/institutionalEmail': { isInstitutionalEmail: () => true },
      '@clerk/clerk-react': {
        SignIn: 'ClerkSignIn',
        useSignIn: () => ({
          isLoaded: true,
          signIn,
          setActive: async () => { activations++ },
        }),
      },
      'react-router-dom': {
        Navigate: 'Navigate',
        useLocation: () => ({ pathname }),
        useNavigate: () => () => assert.fail('Incomplete sign-in must not navigate to the app'),
      },
    })
    await findForm(Page()).props.onSubmit({ preventDefault() {} })
    index = 0
    const redirect = Page()
    const expectedPath = status === 'needs_second_factor' ? '/sign-in/factor-two' : '/sign-in/reset-password'
    assert.equal(redirect.type, 'Navigate')
    assert.equal(redirect.props.to, expectedPath)
    pathname = redirect.props.to
    index = 0
    const continuation = findElement(Page(), 'ClerkSignIn')
    assert.ok(continuation, 'Required verification must render instead of a contact-support error')
    assert.equal(continuation.props.forceRedirectUrl, '/marketplace')
    assert.equal(activations, 0, 'Never activate an incomplete sign-in')
    // Reload with no component state must still resume the unfinished Clerk attempt.
    states.length = 0
    index = 0
    pathname = '/sign-in'
    assert.equal(Page().props.to, expectedPath)
    // Clerk's start-over action must be able to return to its identifier form.
    signIn.status = 'needs_identifier'
    pathname = '/sign-in/'
    index = 0
    assert.ok(findElement(Page(), 'ClerkSignIn'))
  })
}

for (const [page, hook, method, destination] of [
  ['SignInPage', 'useSignIn', 'attemptFirstFactor', '/marketplace'],
  ['SignUpPage', 'useSignUp', 'attemptEmailAddressVerification', '/profile'],
]) {
  for (const pending of [false, true]) {
    test(`${page}: activation ${pending ? 'preserves MFA redirect' : 'continues normally'}`, async () => {
      const calls = []
      let stateIndex = 0
      const resource = { create: async () => ({ supportedFirstFactors: [{ strategy: 'password' }] }), [method]: async () => ({ status: 'complete', createdSessionId: 'session-test' }) }
      const pageModule = load(`pages/${page}.tsx`, {
        react: { useState: (initial) => {
          const slot = stateIndex++
          return [page === 'SignInPage'
            ? slot === 0 ? 'student@school.edu' : slot === 1 ? 'password-example' : initial
            : slot === 2 ? true : initial, () => {}]
        } },
        '../lib/institutionalEmail': { isInstitutionalEmail: () => true },
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
        'react-router-dom': { useLocation: () => ({ pathname: '/sign-in' }), useNavigate: () => (url) => calls.push(url) },
      })
      await findForm(pageModule.default()).props.onSubmit({ preventDefault() {} })
      assert.deepEqual(calls, [pending ? '/session-tasks/setup-mfa' : destination])
    })
  }
}

for (const [email, password, allowed] of [
  ['student@school.edu', 'Abcd1234!', true],
  ['student@school.edu', 'Abcd123!', false],
  ['student@gmail.com', 'Abcd1234!', true],
  ['no-at-sign', 'Abcd1234!', false],
]) {
  test(`signup validates email address and password: ${email}, length ${password.length}`, async () => {
    let stateIndex = 0
    const calls = []
    const institutional = load('lib/institutionalEmail.ts', {})
    const { default: Page } = load('pages/SignUpPage.tsx', {
      react: { useState: (initial) => [stateIndex++ === 0
        ? { firstName: ' Jane ', lastName: ' Doe ', email, password } : initial, () => {}] },
      '../lib/institutionalEmail': institutional,
      '@clerk/clerk-react': { useSignUp: () => ({
        isLoaded: true,
        signUp: {
          create: async (details) => {
            calls.push('create')
            assert.equal(details.firstName, 'Jane')
            assert.equal(details.lastName, 'Doe')
            return { status: 'missing_requirements' }
          },
          prepareEmailAddressVerification: async ({ strategy }) => calls.push(strategy),
        },
        setActive: () => assert.fail('Must verify email before activating'),
      }) },
      'react-router-dom': { useNavigate: () => () => assert.fail('Must verify email before navigating') },
    })
    await findForm(Page()).props.onSubmit({ preventDefault() {} })
    assert.deepEqual(calls, allowed ? ['create', 'email_code'] : [])
  })
}

function findButton(node, label) {
  if (!node || typeof node !== 'object') return undefined
  if (node.type === 'button' && node.props.children === label) return node
  return [node.props?.children].flat().map((child) => findButton(child, label)).find(Boolean)
}

for (const rejected of [false, true]) {
  test(`verification resend ${rejected ? 'shows provider error' : 'requests email code and starts cooldown'}`, async () => {
    const states = [{ firstName: 'Jane', lastName: 'Doe', email: 'student@school.edu', password: '' }, 'oldcode', true, null, false, 0, null]
    let index = 0
    let requests = 0
    const { default: Page } = load('pages/SignUpPage.tsx', {
      react: { useState: () => {
        const slot = index++
        return [states[slot], (value) => { states[slot] = value }]
      } },
      '@clerk/clerk-react': { useSignUp: () => ({
        isLoaded: true,
        signUp: { prepareEmailAddressVerification: async ({ strategy }) => {
          assert.equal(strategy, 'email_code')
          requests++
          if (rejected) throw { errors: [{ longMessage: 'Too many requests. Try again later.' }] }
        } },
      }) },
      'react-router-dom': { useNavigate: () => () => assert.fail('Resend must stay on verification') },
    })
    const button = findButton(Page(), 'Resend code')
    assert.ok(button, 'Verification screen needs a resend action')
    assert.equal(button.props.disabled, false)
    await button.props.onClick()
    assert.equal(requests, 1)
    assert.equal(states[4], false, 'Always release busy state')
    assert.equal(states[5], 30)
    if (rejected) {
      assert.equal(states[3], 'Too many requests. Try again later.')
      assert.equal(states[6], null, 'Do not claim success after failure')
    } else {
      assert.equal(states[1], '', 'Clear the previous code')
      assert.match(states[6], /Another code was requested/)
    }
    index = 0
    const coolingDown = findButton(Page(), 'Resend code in 30s')
    assert.equal(coolingDown.props.disabled, true)
    await coolingDown.props.onClick()
    assert.equal(requests, 1, 'Do not resend during cooldown')
  })
}
