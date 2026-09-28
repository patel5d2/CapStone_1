import { Link } from 'react-router-dom'
import { SignedIn, SignedOut } from '@clerk/clerk-react'
import { Store, Users, LifeBuoy, MessageCircle, ShieldCheck, MapPin, ArrowRight } from 'lucide-react'

const FEATURES = [
  {
    icon: Store,
    title: 'Marketplace',
    body: 'Buy, sell, rent, and give away textbooks, dorm gear, and tickets — only with verified students.',
    to: '/marketplace',
  },
  {
    icon: Users,
    title: 'Community',
    body: 'Groups by major, graduation year, and course. Post, comment, and find your people across schools.',
    to: '/community',
  },
  {
    icon: LifeBuoy,
    title: 'Support',
    body: "Every school's food pantry, emergency aid, and counseling — plus anonymous requests for help.",
    to: '/support',
  },
  {
    icon: MessageCircle,
    title: 'Messages',
    body: 'One inbox for marketplace deals and direct chats, with blocking and reporting built in.',
    to: '/messages',
  },
]

const SCHOOLS = [
  'University of Cincinnati',
  'Xavier University',
  'Northern Kentucky University',
  'Miami University',
  'Cincinnati State',
  'Mount St. Joseph',
]

export default function Landing() {
  return (
    <div className="space-y-16 py-4">
      <section className="relative overflow-hidden rounded-3xl border border-[var(--color-border)] bg-gradient-to-br from-primary-600 via-primary-700 to-primary-900 px-6 py-16 text-center text-white sm:px-12 sm:py-20">
        <div className="pointer-events-none absolute -top-24 -right-16 h-72 w-72 rounded-full bg-accent-400/20 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-10 h-72 w-72 rounded-full bg-primary-400/25 blur-3xl" />
        <div className="relative mx-auto max-w-3xl">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-xs font-bold tracking-wide uppercase backdrop-blur">
            <ShieldCheck className="h-3.5 w-3.5" /> Verified students only
          </span>
          <h1 className="mt-5 text-4xl leading-tight font-extrabold tracking-tight sm:text-5xl">
            Everything a Cincinnati student needs, in one app.
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-base text-primary-100 sm:text-lg">
            Buy what you need, connect with students at six area schools, and find campus support — without
            juggling five different group chats.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <SignedOut>
              <Link to="/sign-up" className="btn bg-white px-5 py-3 text-primary-700 hover:bg-primary-50">
                Join with your .edu email <ArrowRight className="h-4 w-4" />
              </Link>
              <Link to="/sign-in" className="btn border border-white/40 px-5 py-3 text-white hover:bg-white/10">
                Sign in
              </Link>
            </SignedOut>
            <SignedIn>
              <Link to="/marketplace" className="btn bg-white px-5 py-3 text-primary-700 hover:bg-primary-50">
                Go to Marketplace <ArrowRight className="h-4 w-4" />
              </Link>
              <Link to="/community" className="btn border border-white/40 px-5 py-3 text-white hover:bg-white/10">
                Browse Community
              </Link>
            </SignedIn>
          </div>
        </div>
      </section>

      <section>
        <div className="grid gap-4 sm:grid-cols-2">
          {FEATURES.map(({ icon: Icon, title, body, to }) => (
            <Link key={title} to={to} className="card card-hover group flex gap-4 p-6">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600 transition-colors group-hover:bg-primary-600 group-hover:text-white dark:bg-primary-900/40 dark:text-primary-200">
                <Icon className="h-5 w-5" />
              </span>
              <div>
                <h3 className="flex items-center gap-1.5 text-base font-bold">
                  {title}
                  <ArrowRight className="h-4 w-4 opacity-0 transition-opacity group-hover:opacity-100" />
                </h3>
                <p className="mt-1 text-sm text-[var(--color-ink-muted)]">{body}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="card px-6 py-10 text-center">
        <h2 className="text-xl font-extrabold tracking-tight">Built for six Cincinnati-area schools</h2>
        <p className="mt-2 text-sm text-[var(--color-ink-muted)]">
          Listings and groups stay local, so you are trading and meeting with people a short drive away.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          {SCHOOLS.map((school) => (
            <span key={school} className="badge-neutral gap-1.5 normal-case">
              <MapPin className="h-3.5 w-3.5" />
              {school}
            </span>
          ))}
        </div>
      </section>
    </div>
  )
}
