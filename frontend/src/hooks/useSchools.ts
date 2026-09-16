import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import type { School } from '../types'

/** The six Cincinnati-area schools, used by every school picker in the app. */
export function useSchools() {
  const [schools, setSchools] = useState<School[]>([])

  useEffect(() => {
    let cancelled = false
    api
      .get<School[]>('/api/schools')
      .then((data) => {
        if (!cancelled) setSchools(data)
      })
      .catch(() => {
        if (!cancelled) setSchools([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  const nameOf = (schoolId: number | null | undefined) =>
    schoolId == null ? null : (schools.find((s) => s.id === schoolId)?.name ?? null)

  return { schools, nameOf }
}
