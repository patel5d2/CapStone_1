const { readFileSync } = require('node:fs')
const { resolve } = require('node:path')
const { runInNewContext } = require('node:vm')
const { test } = require('node:test')
const assert = require('node:assert/strict')
const { transformSync } = require('esbuild')

// Exercise the components with controlled Clerk session states, without credentials.
function load(file, mocks) {
  const source = readFileSync(resolve(__dirname, '../src', file), 'utf8')
  // TypeScript 7 has no transpileModule; esbuild strips types and emits CommonJS + react-jsx.
  const { code: outputText } = transformSync(source, { loader: 'tsx', format: 'cjs', jsx: 'automatic' })
  const module = { exports: {} }
  const jsx = (type, props) => ({ type, props })
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => name === 'react/jsx-runtime'
      ? { jsx, jsxs: jsx, Fragment: 'Fragment' }
      : name === 'react' ? { useEffect: () => {}, ...mocks[name] } : mocks[name] ?? {},
  })
  return module.exports
}

// The sign-up form's own modules, real except for the school list.
const SCHOOL_THEME = { SCHOOL_NAMES: ['University of Cincinnati', 'Xavier University'] }
const SCHOOL_DETAILS = {
  universityName: 'Xavier University', grade: 'Junior', major: 'Nursing', residentCity: 'Norwood', residentState: 'OH',
}
const SIGNUP_LIBS = {
  '../lib/schoolTheme': SCHOOL_THEME,
  '../lib/signupProfile': load('lib/signupProfile.ts', { './schoolTheme': SCHOOL_THEME }),
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

test('auth forms: pending on a task the app does not host offers sign-out, not the form', () => {
  const { AuthSessionGate } = load('components/AuthSessionGate.tsx', {
    '@clerk/clerk-react': {
      useSession: () => ({ isLoaded: true, session: { status: 'pending', currentTask: { key: 'choose-organization' } } }),
      SignOutButton: 'SignOutButton',
    },
    'react-router-dom': { Navigate: 'Navigate' },
    './ui/Feedback': { Spinner: 'Spinner' },
  })
  const view = AuthSessionGate({ children: 'form' })
  assert.ok(findElement(view, 'SignOutButton'))
  assert.notEqual(view, 'form')
})

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
  ['SignUpPage', 'useSignUp', 'attemptEmailAddressVerification', '/sign-up/profile'],
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
        ...SIGNUP_LIBS,
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
        ? { firstName: ' Jane ', lastName: ' Doe ', email, password, ...SCHOOL_DETAILS, major: ' Nursing ' } : initial, () => {}] },
      '../lib/institutionalEmail': institutional,
      ...SIGNUP_LIBS,
      '@clerk/clerk-react': { useSignUp: () => ({
        isLoaded: true,
        signUp: {
          create: async (details) => {
            calls.push('create')
            assert.equal(details.firstName, 'Jane')
            assert.equal(details.lastName, 'Doe')
            // The profile details ride on the new Clerk user until the last step saves them.
            assert.deepEqual({ ...details.unsafeMetadata.signupProfile }, { ...SCHOOL_DETAILS, major: 'Nursing' })
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
      ...SIGNUP_LIBS,
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

for (const [label, outcome, expected] of [
  ['no profile yet (404) is missing', { status: 404 }, { status: 'missing' }],
  ['a saved profile is complete', 'ok', { status: 'complete' }],
  ['any other failure is failed, never registered', { status: 500 }, { status: 'failed', message: 'x', retryable: true }],
  ['a profile bound to another account (409) is failed for good', { status: 409 }, { status: 'failed', message: 'x', retryable: false }],
]) {
  test(`profile check: ${label}`, async () => {
    class ApiError extends Error { constructor(status) { super('x'); this.status = status } }
    const states = []
    let slot = 0
    const { useProfileCheck } = load('lib/profileGate.ts', {
      react: {
        useState: (initial) => {
          const index = slot++
          if (!(index in states)) states[index] = initial
          return [states[index], (v) => { states[index] = typeof v === 'function' ? v(states[index]) : v }]
        },
        useEffect: (fn) => { fn() },
      },
      './api': {
        ApiError,
        api: { get: () => outcome === 'ok' ? Promise.resolve({}) : Promise.reject(new ApiError(outcome.status)) },
      },
    })
    const userId = `user_${label}`
    assert.equal(useProfileCheck(userId).status, 'checking')
    await new Promise((r) => setTimeout(r, 0))
    slot = 0
    const { retry, ...check } = useProfileCheck(userId)
    assert.deepEqual({ ...check }, expected)
    // Another student never reads this one's answer.
    slot = 0
    assert.equal(useProfileCheck('user_someone_else').status, 'checking')
    // Asking again starts over.
    retry()
    slot = 0
    assert.equal(outcome === 'ok' ? 'complete' : useProfileCheck(userId).status, outcome === 'ok' ? 'complete' : 'checking')
  })
}

for (const [label, saved] of [['a profile the server holds', true], ['no profile, or a failed check', false]]) {
  test(`profileExists: ${label} -> ${saved}`, async () => {
    const { profileExists } = load('lib/profileGate.ts', {
      './api': { api: { get: () => saved ? Promise.resolve({}) : Promise.reject(new Error('x')) } },
    })
    assert.equal(await profileExists(), saved)
  })
}

function mfaPage(session, hadTask = false) {
  return load('pages/SetupMfaPage.tsx', {
    react: { useState: () => [hadTask, () => {}] },
    '@clerk/clerk-react': { TaskSetupMFA: 'TaskSetupMFA', useSession: () => ({ isLoaded: true, session }) },
    'react-router-dom': { Navigate: 'Navigate' },
  }).default()
}

test('MFA setup continues to the profile step of sign-up', () => {
  const page = mfaPage({ status: 'pending', currentTask: { key: 'setup-mfa' } })
  assert.equal(findElement(page, 'TaskSetupMFA').props.redirectUrlComplete, '/sign-up/profile')
})

test('MFA setup with nothing to set up is not left as an empty page', () => {
  const page = mfaPage({ status: 'active', currentTask: null })
  assert.equal(page.type, 'Navigate')
  assert.equal(page.props.to, '/marketplace')
})

test('MFA setup finished on this visit stays put, so Clerk can show its last screen', () => {
  assert.ok(findElement(mfaPage({ status: 'active', currentTask: null }, true), 'TaskSetupMFA'))
})

for (const [field, value, problem] of [
  ['universityName', '', 'Select your school.'],
  ['universityName', 'Hogwarts', 'Select your school.'],
  ['grade', 'Wizard', 'Select your year of study.'],
  ['major', '   ', 'Enter your major.'],
  ['residentCity', '', 'Enter your city.'],
  ['residentState', '', 'Select your state.'],
  ['residentState', 'Ohio', 'Select your state.'],
]) {
  test(`signup refuses ${field} "${value}" before anything reaches Clerk`, async () => {
    let stateIndex = 0
    let shown
    const { default: Page } = load('pages/SignUpPage.tsx', {
      react: { useState: (initial) => {
        const slot = stateIndex++
        return [slot === 0
          ? { firstName: 'Jane', lastName: 'Doe', email: 'jane@school.edu', password: 'Abcd1234!', ...SCHOOL_DETAILS, [field]: value }
          : initial, (next) => { if (slot === 3) shown = next }]
      } },
      '../lib/institutionalEmail': { isInstitutionalEmail: () => true },
      ...SIGNUP_LIBS,
      '@clerk/clerk-react': { useSignUp: () => ({
        isLoaded: true,
        signUp: { create: () => assert.fail('Refused details must not create a Clerk account') },
      }) },
      'react-router-dom': { useNavigate: () => () => assert.fail('Refused details must not navigate') },
    })
    await findForm(Page()).props.onSubmit({ preventDefault() {} })
    assert.equal(shown, problem)
  })
}

test('a reload while verifying resumes at the code step instead of the empty form', () => {
  const page = (signUp) => load('pages/SignUpPage.tsx', {
    react: { useState: (initial) => [initial, () => {}] },
    ...SIGNUP_LIBS,
    '@clerk/clerk-react': { useSignUp: () => ({ isLoaded: true, signUp }) },
    'react-router-dom': { useNavigate: () => () => {} },
  }).default()
  const waiting = { status: 'missing_requirements', unverifiedFields: ['email_address'], emailAddress: 'jane@school.edu' }
  assert.ok(findButton(page(waiting), 'Resend code'))
  assert.equal(findButton(page({ status: null }), 'Resend code'), undefined)
})

test('signup profile details: read from Clerk metadata, and removed without touching the rest', () => {
  const { pendingSignupProfile, withoutSignupProfile } = load('lib/signupProfile.ts', { './schoolTheme': SCHOOL_THEME })
  assert.equal(pendingSignupProfile({}), null)
  assert.equal(pendingSignupProfile(undefined), null)
  assert.deepEqual({ ...pendingSignupProfile({ signupProfile: SCHOOL_DETAILS }) }, SCHOOL_DETAILS)
  // Anything that is not text is dropped, so the profile form reports it as missing.
  assert.equal(pendingSignupProfile({ signupProfile: { major: 7 } }).major, '')
  assert.deepEqual({ ...withoutSignupProfile({ signupProfile: SCHOOL_DETAILS, other: 1 }) }, { other: 1 })
})

test('Clerk sends its own redirects to this app, not to the hosted Account Portal', () => {
  const { ThemedClerkProvider } = load('components/ThemedClerkProvider.tsx', {
    react: { useMemo: (fn) => fn(), useSyncExternalStore: () => null },
    '@clerk/clerk-react': { ClerkProvider: 'ClerkProvider' },
    '../lib/clerkAppearance': { clerkAppearanceWith: () => ({}) },
    '../lib/schoolTheme': { activePrimaryHex: () => '#000000' },
  })
  const { props } = ThemedClerkProvider({ publishableKey: 'pk_test_x', children: 'app' })
  assert.equal(props.signInUrl, '/sign-in')
  assert.equal(props.signUpUrl, '/sign-up')
  assert.equal(props.signInFallbackRedirectUrl, '/marketplace')
  assert.equal(props.signUpFallbackRedirectUrl, '/sign-up/profile')
  assert.equal(props.taskUrls['setup-mfa'], '/session-tasks/setup-mfa')
})

for (const [label, clerkError, shown] of [
  ['an email with no account', { code: 'form_identifier_not_found', longMessage: "Couldn't find your account." }, /No account uses that email.*emailed code/],
  ['a wrong password', { code: 'form_password_incorrect', longMessage: 'Password is incorrect.' }, /^Password is incorrect\.$/],
]) {
  test(`SignInPage: ${label} is explained, and nothing is activated`, async () => {
    const states = []
    let index = 0
    const { default: Page } = load('pages/SignInPage.tsx', {
      react: { useState: (initial) => {
        const slot = index++
        if (!(slot in states)) states[slot] = slot === 0 ? 'student@school.edu' : slot === 1 ? 'password-example' : initial
        return [states[slot], (value) => { states[slot] = value }]
      } },
      '../lib/institutionalEmail': { isInstitutionalEmail: () => true },
      '@clerk/clerk-react': { useSignIn: () => ({
        isLoaded: true,
        signIn: { create: async () => { throw { errors: [clerkError] } } },
        setActive: () => assert.fail('A failed sign-in must not activate a session'),
      }) },
      'react-router-dom': { useLocation: () => ({ pathname: '/sign-in' }), useNavigate: () => () => assert.fail('A failed sign-in must not navigate') },
    })
    await findForm(Page()).props.onSubmit({ preventDefault() {} })
    assert.match(states[2], shown)
    assert.equal(states[3], false, 'Always release busy state')
  })
}

for (const [label, clerkError, shown, field] of [
  ['an email already in use', { code: 'form_identifier_exists', longMessage: 'That email address is taken. Please try another.', meta: { paramName: 'email_address' } },
    'An account already uses that email. Sign in instead, or use a different email.', 'email'],
  ['a password Clerk refuses', { code: 'form_password_pwned', longMessage: 'This password has been found in a breach.', meta: { paramName: 'password' } },
    'This password has been found in a breach.', 'password'],
  ['a failure with no field', { code: 'captcha_invalid', longMessage: 'Security check failed.' }, 'Security check failed.', null],
]) {
  test(`signup shows ${label} beside its field`, async () => {
    const states = []
    let index = 0
    const { default: Page } = load('pages/SignUpPage.tsx', {
      react: { useState: (initial) => {
        const slot = index++
        if (!(slot in states)) states[slot] = slot === 0
          ? { firstName: 'Jane', lastName: 'Doe', email: 'jane@school.edu', password: 'Abcd1234!', ...SCHOOL_DETAILS } : initial
        return [states[slot], (value) => { states[slot] = value }]
      } },
      '../lib/institutionalEmail': { isInstitutionalEmail: () => true },
      ...SIGNUP_LIBS,
      '@clerk/clerk-react': { useSignUp: () => ({ isLoaded: true, signUp: { create: async () => { throw { errors: [clerkError] } } } }) },
      'react-router-dom': { useNavigate: () => () => assert.fail('A refused sign-up must not navigate') },
    })
    await findForm(Page()).props.onSubmit({ preventDefault() {} })
    assert.equal(states[3], shown)
    assert.equal(states[8], field, 'The field the message belongs to')
    assert.equal(states[2], false, 'Not moved on to the code step')
    assert.equal(states[4], false, 'Always release busy state')
  })
}

test('a complete emailed code is submitted without pressing the button', async () => {
  const attempts = []
  let index = 0
  const { default: Page } = load('pages/SignUpPage.tsx', {
    react: { useState: (initial) => [index++ === 2 ? true : initial, () => {}] },
    ...SIGNUP_LIBS,
    '@clerk/clerk-react': { useSignUp: () => ({
      isLoaded: true,
      signUp: { attemptEmailAddressVerification: async ({ code }) => { attempts.push(code); return { status: 'complete', createdSessionId: 's' } } },
      setActive: async () => {},
    }) },
    'react-router-dom': { useNavigate: () => () => {} },
  })
  const input = findElement(Page(), 'input')
  input.props.onChange({ target: { value: '424' } })
  assert.deepEqual(attempts, [], 'A partial code is not sent')
  input.props.onChange({ target: { value: '424 242' } })
  await new Promise((r) => setTimeout(r, 0))
  assert.deepEqual(attempts, ['424242'], 'Spaces from a pasted code are dropped')
})
