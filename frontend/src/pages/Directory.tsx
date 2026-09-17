import { useCallback, useEffect, useState } from 'react'
import { Search, Mail, MapPin, Link2, GraduationCap, MessageCircle } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { api, toQueryString, ApiError } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { EmptyState, Spinner } from '../components/ui/Feedback'
import type { Student } from '../types'

const GRADES = ['Freshman', 'Sophomore', 'Junior', 'Senior']

export default function Directory() {
  const navigate = useNavigate()
  const { push } = useToast()
  const { schools } = useSchools()

  const [students, setStudents] = useState<Student[]>([])
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  const [filters, setFilters] = useState({
    firstName: '',
    lastName: '',
    universityName: '',
    grade: '',
    major: '',
    city: '',
  })

  const search = useCallback(async () => {
    setLoading(true)
    setSearched(true)
    try {
      setStudents(await api.get<Student[]>(`/student${toQueryString(filters)}`))
    } catch (e) {
      // The directory API answers an empty search with 404 rather than [].
      if (e instanceof ApiError && e.status === 404) setStudents([])
      else push(e instanceof Error ? e.message : 'Search failed', 'error')
    } finally {
      setLoading(false)
    }
  }, [filters, push])

  useEffect(() => {
    void search()
    // Run once on mount to show the full directory; later searches are explicit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const messageStudent = async (student: Student) => {
    try {
      await api.post('/api/messages/conversations', { recipientEmail: student.email })
      push('Conversation started', 'success')
      navigate('/messages')
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not start a conversation', 'error')
    }
  }

  const set = (key: keyof typeof filters) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setFilters({ ...filters, [key]: e.target.value })

  // Rendered as a sub-surface of Community, which owns the page heading.
  return (
    <>
      <div className="card mb-6 p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <input className="field" placeholder="First name" value={filters.firstName} onChange={set('firstName')} />
          <input className="field" placeholder="Last name" value={filters.lastName} onChange={set('lastName')} />
          <input className="field" placeholder="Major" value={filters.major} onChange={set('major')} />
          <select className="field" value={filters.universityName} onChange={set('universityName')}>
            <option value="">All schools</option>
            {schools.map((s) => (
              <option key={s.id} value={s.name}>
                {s.name}
              </option>
            ))}
          </select>
          <select className="field" value={filters.grade} onChange={set('grade')}>
            <option value="">Any year</option>
            {GRADES.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
          <input className="field" placeholder="City" value={filters.city} onChange={set('city')} />
        </div>
        <div className="mt-3 flex justify-end gap-2">
          <button
            className="btn-ghost"
            onClick={() =>
              setFilters({ firstName: '', lastName: '', universityName: '', grade: '', major: '', city: '' })
            }
          >
            Clear
          </button>
          <button className="btn-primary" onClick={() => void search()}>
            <Search className="h-4 w-4" /> Search
          </button>
        </div>
      </div>

      {loading ? (
        <Spinner label="Searching the directory…" />
      ) : students.length === 0 ? (
        <EmptyState
          icon={<Search className="h-6 w-6" />}
          title={searched ? 'No students matched' : 'Search the directory'}
          description={
            searched
              ? 'Try fewer filters, or search by just a last name or school.'
              : 'Use the filters above to find classmates across the tri-state area.'
          }
        />
      ) : (
        <>
          <p className="mb-4 text-sm text-[var(--color-ink-muted)]">
            {students.length} {students.length === 1 ? 'student' : 'students'}
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {students.map((student) => (
              <article key={student.email} className="card card-hover animate-fade-in-up flex flex-col p-5">
                <div className="flex items-center gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-full bg-primary-100 text-base font-bold text-primary-700 dark:bg-primary-900/50 dark:text-primary-200">
                    {student.firstName.charAt(0)}
                    {student.lastName.charAt(0)}
                  </span>
                  <div className="min-w-0">
                    <h3 className="truncate text-base font-bold">
                      {student.firstName} {student.lastName}
                    </h3>
                    <p className="truncate text-xs text-[var(--color-ink-faint)]">{student.universityName}</p>
                  </div>
                </div>

                <div className="mt-3 flex flex-wrap gap-2">
                  <span className="badge-primary">{student.grade}</span>
                  <span className="badge-neutral normal-case">{student.major}</span>
                </div>

                <div className="mt-4 space-y-1.5 border-t border-[var(--color-border)] pt-3 text-xs text-[var(--color-ink-muted)]">
                  <p className="flex items-center gap-1.5">
                    <MapPin className="h-3.5 w-3.5 shrink-0" />
                    {student.residentCity}, {student.residentState}
                  </p>
                  <p className="flex items-center gap-1.5">
                    <Mail className="h-3.5 w-3.5 shrink-0" />
                    <a href={`mailto:${student.email}`} className="truncate hover:text-primary-600 dark:hover:text-primary-400">
                      {student.email}
                    </a>
                  </p>
                  {student.socialMediaLink && (
                    <p className="flex items-center gap-1.5">
                      <Link2 className="h-3.5 w-3.5 shrink-0" />
                      <a
                        href={student.socialMediaLink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="truncate hover:text-primary-600 dark:hover:text-primary-400"
                      >
                        {student.socialMediaLink}
                      </a>
                    </p>
                  )}
                </div>

                <button className="btn-secondary btn-sm mt-4" onClick={() => messageStudent(student)}>
                  <MessageCircle className="h-3.5 w-3.5" /> Message
                </button>
              </article>
            ))}
          </div>
        </>
      )}

      <p className="mt-8 flex items-center justify-center gap-1.5 text-xs text-[var(--color-ink-faint)]">
        <GraduationCap className="h-3.5 w-3.5" />
        Only verified students appear in the directory.
      </p>
    </>
  )
}
