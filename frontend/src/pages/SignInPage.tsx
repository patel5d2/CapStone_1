import { SignIn } from '@clerk/clerk-react'
import { Link } from 'react-router-dom'
import { GraduationCap } from 'lucide-react'

export default function SignInPage() {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Link to="/" className="flex items-center gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-600 text-white">
          <GraduationCap className="h-5 w-5" />
        </span>
        <span className="text-base font-extrabold tracking-tight">
          Campus<span className="text-primary-600">Bridge</span>
        </span>
      </Link>
      <SignIn routing="path" path="/sign-in" signUpUrl="/sign-up" forceRedirectUrl="/marketplace" />
    </div>
  )
}
