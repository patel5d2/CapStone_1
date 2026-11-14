import { SCHOOL_NAMES } from './schoolTheme'

/** Years of study the profile accepts, in the order the pickers show them. */
export const GRADES = ['Freshman', 'Sophomore', 'Junior', 'Senior', 'Graduate']

/**
 * The state picker: the API stores the two-letter code, and picking it from a list is
 * what stops "Ohio" or "oh" being typed where "OH" is wanted.
 */
export const US_STATES: [code: string, name: string][] = [
  ['AL', 'Alabama'], ['AK', 'Alaska'], ['AZ', 'Arizona'], ['AR', 'Arkansas'], ['CA', 'California'],
  ['CO', 'Colorado'], ['CT', 'Connecticut'], ['DE', 'Delaware'], ['DC', 'District of Columbia'],
  ['FL', 'Florida'], ['GA', 'Georgia'], ['HI', 'Hawaii'], ['ID', 'Idaho'], ['IL', 'Illinois'],
  ['IN', 'Indiana'], ['IA', 'Iowa'], ['KS', 'Kansas'], ['KY', 'Kentucky'], ['LA', 'Louisiana'],
  ['ME', 'Maine'], ['MD', 'Maryland'], ['MA', 'Massachusetts'], ['MI', 'Michigan'], ['MN', 'Minnesota'],
  ['MS', 'Mississippi'], ['MO', 'Missouri'], ['MT', 'Montana'], ['NE', 'Nebraska'], ['NV', 'Nevada'],
  ['NH', 'New Hampshire'], ['NJ', 'New Jersey'], ['NM', 'New Mexico'], ['NY', 'New York'],
  ['NC', 'North Carolina'], ['ND', 'North Dakota'], ['OH', 'Ohio'], ['OK', 'Oklahoma'], ['OR', 'Oregon'],
  ['PA', 'Pennsylvania'], ['RI', 'Rhode Island'], ['SC', 'South Carolina'], ['SD', 'South Dakota'],
  ['TN', 'Tennessee'], ['TX', 'Texas'], ['UT', 'Utah'], ['VT', 'Vermont'], ['VA', 'Virginia'],
  ['WA', 'Washington'], ['WV', 'West Virginia'], ['WI', 'Wisconsin'], ['WY', 'Wyoming'],
]

/**
 * The required profile details, asked on the sign-up form itself (#114).
 *
 * They are asked before the Clerk account exists, but the API only creates a student
 * once the session has a verified email and second factor. So they ride on the new Clerk
 * user's `unsafeMetadata` through email verification and MFA setup, and the last sign-up
 * step (`/sign-up/profile`) turns them into the student profile. Riding on the user rather
 * than in browser storage means a reload, a new tab or another device still finishes the
 * same sign-up. "Unsafe" only means the browser may write it: nothing trusts these values,
 * and the API validates them like any other profile save.
 *
 * The school travels by name because the list with ids needs a signed-in session.
 */
export interface SignupProfile {
  universityName: string
  grade: string
  major: string
  residentCity: string
  residentState: string
}

export const SIGNUP_PROFILE_KEY = 'signupProfile'

/** The first problem with the details and the field it is in, or null. Mirrors the API's rules. */
export function signupProfileProblem(profile: SignupProfile): { field: keyof SignupProfile; message: string } | null {
  const problem = (field: keyof SignupProfile, message: string) => ({ field, message })
  if (!SCHOOL_NAMES.includes(profile.universityName)) return problem('universityName', 'Select your school.')
  if (!GRADES.includes(profile.grade)) return problem('grade', 'Select your year of study.')
  if (!profile.major.trim()) return problem('major', 'Enter your major.')
  if (profile.major.length > 255) return problem('major', 'Use 255 characters or fewer for your major.')
  if (!profile.residentCity.trim()) return problem('residentCity', 'Enter your city.')
  if (profile.residentCity.length > 100) return problem('residentCity', 'Use 100 characters or fewer for your city.')
  if (!US_STATES.some(([code]) => code === profile.residentState)) return problem('residentState', 'Select your state.')
  return null
}

/**
 * The details waiting on a Clerk user, or null when sign-up left none (an account made
 * before #114, or outside the sign-up form). Read loosely: the profile form shows whatever
 * is there and reports what is wrong with it.
 */
export function pendingSignupProfile(metadata: unknown): SignupProfile | null {
  const value = (metadata as Record<string, unknown> | null | undefined)?.[SIGNUP_PROFILE_KEY]
  if (!value || typeof value !== 'object') return null
  const read = (key: keyof SignupProfile) => {
    const field = (value as Record<string, unknown>)[key]
    return typeof field === 'string' ? field : ''
  }
  return {
    universityName: read('universityName'),
    grade: read('grade'),
    major: read('major'),
    residentCity: read('residentCity'),
    residentState: read('residentState'),
  }
}

/** The metadata without the waiting details, for once they have become the profile. */
export function withoutSignupProfile(metadata: Record<string, unknown>): Record<string, unknown> {
  const rest = { ...metadata }
  delete rest[SIGNUP_PROFILE_KEY]
  return rest
}
