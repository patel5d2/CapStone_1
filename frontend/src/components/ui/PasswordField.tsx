import { useState, type ReactNode } from 'react'

/**
 * A password input with a Show/Hide toggle, shared by sign-in and sign-up so a mistyped
 * password can be seen instead of guessed at.
 *
 * The toggle comes after the input in the page, so Tab still goes from the field before
 * straight into the password, and it is named by its own text: an `aria-label` mentioning
 * "password" would make it a second control answering to the field's label.
 */
export function PasswordField({ id, label, value, onChange, autoComplete, minLength, invalid, children }: {
  id: string
  label: string
  value: string
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void
  autoComplete: 'current-password' | 'new-password'
  minLength?: number
  invalid?: boolean
  /** Hint or error shown under the field. */
  children?: ReactNode
}) {
  const [shown, setShown] = useState(false)
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-xs font-semibold">{label}</label>
      <div className="relative">
        <input
          id={id}
          className="field pr-16"
          type={shown ? 'text' : 'password'}
          autoComplete={autoComplete}
          minLength={minLength}
          value={value}
          onChange={onChange}
          aria-invalid={invalid || undefined}
          required
        />
        <button
          type="button"
          className="absolute inset-y-0 right-1.5 my-auto h-8 rounded-lg px-2.5 text-xs font-semibold text-primary-700 hover:bg-primary-50 dark:text-primary-300 dark:hover:bg-primary-900/40"
          aria-pressed={shown}
          aria-controls={id}
          onClick={() => setShown(!shown)}
        >
          {shown ? 'Hide' : 'Show'}<span className="sr-only"> password</span>
        </button>
      </div>
      {children}
    </div>
  )
}
