import { useState } from 'react'
import { Ban, Flag, Pencil, RotateCcw, Trash2, Users } from 'lucide-react'
import { api } from '../../lib/api'
import { useSchools } from '../../hooks/useSchools'
import { useToast } from '../../components/ui/Toast'
import { Tabs } from '../../components/ui/Tabs'
import type { AdminBan, AdminStudent, AdminUserReport } from '../../types'
import { nameOf, usePagedList } from './adminList'
import { Byline, Check, ConfirmDialog, ListFrame, Row, SearchBar, Strong } from './shared'
import { EditAccountDialog } from './EditAccountDialog'

type View = 'accounts' | 'banned' | 'reports'

const REMOVE_CONTENT = 'Also remove their listings, posts, comments, events and support requests'

function AccountCard({ account }: { account: AdminStudent }) {
  return (
    <>
      <p className="text-base font-bold break-words">{nameOf(account)}</p>
      <p className="break-all text-[var(--color-ink-muted)]">{account.email || 'No email on record'}</p>
      <p className="mt-0.5 text-xs text-[var(--color-ink-faint)]">
        {[account.universityName ?? 'School unavailable', account.grade, account.major].filter(Boolean).join(' · ')}
      </p>
    </>
  )
}

/**
 * Student accounts: find, edit, ban and delete; plus who is banned, and what students have
 * reported about each other.
 */
export default function AccountsPanel() {
  const { push } = useToast()
  const { schools } = useSchools()
  const [view, setView] = useState<View>('accounts')
  const [query, setQuery] = useState('')
  const accounts = usePagedList<AdminStudent>('/api/admin/students', { query })
  const bans = usePagedList<AdminBan>('/api/admin/bans')
  const reports = usePagedList<AdminUserReport>('/api/admin/user-reports')

  const [editing, setEditing] = useState<AdminStudent | null>(null)
  const [banning, setBanning] = useState<AdminStudent | null>(null)
  const [deleting, setDeleting] = useState<AdminStudent | null>(null)
  const [lifting, setLifting] = useState<{ email: string; name: string | null } | null>(null)
  const [dismissing, setDismissing] = useState<AdminUserReport | null>(null)
  // Options chosen inside the ban and delete dialogs.
  const [removeContent, setRemoveContent] = useState(false)
  const [alsoBan, setAlsoBan] = useState(false)

  const search = (next: string) => {
    accounts.setPage(0)
    setQuery(next)
  }
  const refreshAll = () => {
    accounts.reload()
    bans.reload()
  }
  /** Someone else (or Clerk) already removed the account this dialog was about. */
  const alreadyGone = (account: AdminStudent) => {
    setEditing(null)
    setBanning(null)
    setDeleting(null)
    push(`${nameOf(account)}'s account no longer exists. The list has been refreshed.`, 'error')
    refreshAll()
  }
  const open = (dialog: (account: AdminStudent) => void, account: AdminStudent) => {
    setRemoveContent(false)
    setAlsoBan(false)
    dialog(account)
  }

  return (
    <div>
      <div className="mb-4">
        <Tabs<View>
          value={view}
          onChange={setView}
          options={[
            { value: 'accounts', label: 'Accounts' },
            { value: 'banned', label: 'Banned', count: bans.loading ? undefined : bans.totalItems },
            { value: 'reports', label: 'Reports', count: reports.loading ? undefined : reports.totalItems },
          ]}
        />
      </div>

      {view === 'accounts' && (
        <>
          <SearchBar label="Search accounts by name or email" placeholder="Search by name or email" applied={query} onSearch={search} />
          <ListFrame list={accounts} noun="account" icon={<Users className="h-6 w-6" />}
            emptyTitle={query ? 'No accounts matched' : 'No student accounts yet'}
            emptyDescription={query ? `Nothing matches “${query}”. Try part of a name or an email address.` : 'Student accounts appear here once someone signs up.'}>
            {(account) => (
              <Row key={account.id}
                lead={
                  <span aria-hidden="true" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-100 text-base font-bold text-primary-700 dark:bg-primary-900/50 dark:text-primary-200">
                    {`${account.firstName?.charAt(0) ?? ''}${account.lastName?.charAt(0) ?? ''}`.toUpperCase() || '?'}
                  </span>
                }
                actions={
                  <>
                    <button className="btn-secondary btn-sm" onClick={() => setEditing(account)} aria-label={`Edit ${nameOf(account)}`}>
                      <Pencil className="h-3.5 w-3.5" /> Edit
                    </button>
                    {account.administrator ? (
                      <span className="max-w-40 text-xs text-[var(--color-ink-faint)]">Administrators can&rsquo;t be banned or deleted here</span>
                    ) : (
                      <>
                        {account.banned ? (
                          <button className="btn-ghost btn-sm" onClick={() => setLifting({ email: account.email, name: nameOf(account) })} aria-label={`Lift the ban on ${nameOf(account)}`}>
                            <RotateCcw className="h-3.5 w-3.5" /> Unban
                          </button>
                        ) : (
                          <button className="btn-ghost btn-sm" onClick={() => open(setBanning, account)} aria-label={`Ban ${nameOf(account)}`}>
                            <Ban className="h-3.5 w-3.5" /> Ban
                          </button>
                        )}
                        <button className="btn-danger btn-sm" onClick={() => open(setDeleting, account)} aria-label={`Delete ${nameOf(account)}`}>
                          <Trash2 className="h-3.5 w-3.5" /> Delete
                        </button>
                      </>
                    )}
                  </>
                }>
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-bold break-words">{nameOf(account)}</span>
                  {account.administrator && <span className="badge-warning">Administrator</span>}
                  {account.banned && <span className="badge-danger">Banned</span>}
                  {!account.hasSignIn && <span className="badge-neutral">No sign-in</span>}
                </p>
                <p className="text-sm break-all text-[var(--color-ink-muted)]">{account.email || 'No email on record'}</p>
                <p className="mt-0.5 text-xs text-[var(--color-ink-faint)]">
                  {[account.universityName ?? 'School unavailable', account.grade, account.major].filter(Boolean).join(' · ')}
                </p>
              </Row>
            )}
          </ListFrame>
        </>
      )}

      {view === 'banned' && (
        <ListFrame list={bans} noun="banned email" icon={<Ban className="h-6 w-6" />}
          emptyTitle="Nobody is banned"
          emptyDescription="Ban a student from the Accounts list. A banned email cannot use CampusBridge, even by signing up again.">
          {(ban) => (
            <Row key={ban.email}
              actions={
                <button className="btn-ghost btn-sm" onClick={() => setLifting({ email: ban.email, name: ban.name })} aria-label={`Lift the ban on ${ban.email}`}>
                  <RotateCcw className="h-3.5 w-3.5" /> Unban
                </button>
              }>
              <p className="font-bold break-all">{ban.email}</p>
              <p className="text-sm text-[var(--color-ink-muted)]">
                {ban.studentId == null ? 'Account deleted. This email cannot sign up again.' : `${ban.name || 'Unnamed account'} — profile kept, hidden from the directory`}
              </p>
            </Row>
          )}
        </ListFrame>
      )}

      {view === 'reports' && (
        <ListFrame list={reports} noun="report" icon={<Flag className="h-6 w-6" />}
          emptyTitle="No reports about students"
          emptyDescription="When a student reports someone from Messages, it appears here.">
          {(report) => (
            <Row key={report.id}
              actions={
                <>
                  <button className="btn-secondary btn-sm" onClick={() => { search(report.reportedEmail); setView('accounts') }}>
                    <Users className="h-3.5 w-3.5" /> Find account
                  </button>
                  <button className="btn-ghost btn-sm" onClick={() => setDismissing(report)}>Dismiss</button>
                </>
              }>
              <p className="font-bold break-all">{report.reportedEmail}</p>
              <p className="mt-1 text-sm break-words whitespace-pre-wrap">{report.reason}</p>
              <Byline email={`Reported by ${report.reporterEmail}`} date={report.createdAt} />
            </Row>
          )}
        </ListFrame>
      )}

      {editing && (
        <EditAccountDialog key={editing.id} account={editing} schools={schools}
          onClose={() => setEditing(null)}
          onMissing={() => alreadyGone(editing)}
          onSaved={(saved) => {
            setEditing(null)
            // Show what the server stored, not what was typed.
            accounts.replace((item) => item.id === saved.id, saved)
            push(`Saved changes to ${nameOf(saved)}'s account.`, 'success')
          }} />
      )}

      {banning && (
        <ConfirmDialog key={banning.id}
          title="Ban this student?"
          target={<AccountCard account={banning} />}
          consequences={[
            'They are refused by CampusBridge from their next click, and cannot get back in by signing up again with this email.',
            'Their profile is kept but hidden from the student directory.',
            <>You can lift the ban at any time, and they get their account back as it was. <Strong>Removed content does not come back.</Strong></>,
          ]}
          options={<Check checked={removeContent} onChange={setRemoveContent}>{REMOVE_CONTENT}</Check>}
          confirmLabel="Ban student" busyLabel="Banning…" failurePrefix="The student was not banned."
          onClose={() => setBanning(null)}
          onMissing={() => alreadyGone(banning)}
          onConfirm={async () => {
            await api.post(`/api/admin/students/${banning.id}/ban`, { removeContent })
            setBanning(null)
            push(`Banned ${nameOf(banning)}.`, 'success')
            refreshAll()
          }} />
      )}

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this student account?"
          target={<AccountCard account={deleting} />}
          consequences={[
            deleting.hasSignIn
              ? 'Their sign-in is removed. They are signed out and can no longer sign in to CampusBridge.'
              : 'Nobody has signed in to this profile. If a sign-in is registered to its email, that is removed too.',
            'Their profile is deleted, and they disappear from the student directory.',
            alsoBan
              ? 'This email is banned, so signing up again will not bring them back.'
              : 'They can sign up again with the same email as a new student, unless you also ban it below.',
            removeContent
              ? 'Everything they posted is removed.'
              : 'Listings, messages and posts they made stay on CampusBridge but no longer belong to an account.',
            <Strong key="undone">This cannot be undone.</Strong>,
          ]}
          options={
            <>
              <Check checked={alsoBan} onChange={setAlsoBan}>Also ban this email, so they cannot sign up again as a new student</Check>
              <Check checked={removeContent} onChange={setRemoveContent}>{REMOVE_CONTENT}</Check>
            </>
          }
          acknowledgement={`I want to permanently delete ${nameOf(deleting)}'s account.`}
          confirmLabel="Delete account" busyLabel="Deleting…" failurePrefix="The account was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={() => alreadyGone(deleting)}
          onConfirm={async () => {
            await api.del(`/api/admin/students/${deleting.id}?ban=${alsoBan}&removeContent=${removeContent}`)
            setDeleting(null)
            push(`Deleted ${nameOf(deleting)}'s account${alsoBan ? ' and banned the email' : ''}.`, 'success')
            refreshAll()
          }} />
      )}

      {lifting && (
        <ConfirmDialog key={lifting.email}
          title="Lift this ban?"
          tone="primary"
          target={<><p className="text-base font-bold break-all">{lifting.email}</p>{lifting.name && <p className="text-[var(--color-ink-muted)]">{lifting.name}</p>}</>}
          consequences={[
            'This email can use CampusBridge again straight away.',
            'If their profile was kept, it returns to the directory. If the account was deleted, they can sign up as a new student.',
            'Content removed when they were banned is not restored.',
          ]}
          confirmLabel="Lift ban" busyLabel="Lifting…" failurePrefix="The ban was not lifted."
          onClose={() => setLifting(null)}
          onMissing={() => { setLifting(null); push('That email is no longer banned. The lists have been refreshed.', 'error'); refreshAll() }}
          onConfirm={async () => {
            await api.post('/api/admin/bans/lift', { email: lifting.email })
            setLifting(null)
            push(`Lifted the ban on ${lifting.email}.`, 'success')
            refreshAll()
          }} />
      )}

      {dismissing && (
        <ConfirmDialog key={dismissing.id}
          title="Dismiss this report?"
          tone="primary"
          target={<><p className="font-bold break-all">{dismissing.reportedEmail}</p><p className="mt-1 break-words whitespace-pre-wrap">{dismissing.reason}</p></>}
          consequences={['The report is removed from this list. Nobody is notified, and the reported account is not changed.']}
          confirmLabel="Dismiss report" busyLabel="Dismissing…" failurePrefix="The report was not dismissed."
          onClose={() => setDismissing(null)}
          onMissing={() => { setDismissing(null); push('That report was already dismissed.', 'error'); reports.reload() }}
          onConfirm={async () => {
            await api.del(`/api/admin/user-reports/${dismissing.id}`)
            setDismissing(null)
            push('Report dismissed.', 'success')
            reports.reload()
          }} />
      )}
    </div>
  )
}
