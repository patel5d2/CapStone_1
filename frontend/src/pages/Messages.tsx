import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useUser } from '@clerk/clerk-react'
import { MessageCircle, Send, ArrowLeft, Ban, Flag, Plus, Tag } from 'lucide-react'
import { api } from '../lib/api'
import { useToast } from '../components/ui/Toast'
import { Modal } from '../components/ui/Modal'
import { EmptyState, Spinner } from '../components/ui/Feedback'
import type { ChatMessage, ConversationSummary } from '../types'

export default function Messages() {
  const { user } = useUser()
  const { push } = useToast()
  const myEmail = user?.primaryEmailAddress?.emailAddress?.toLowerCase() ?? ''

  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [activeId, setActiveId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [newChatOpen, setNewChatOpen] = useState(false)
  const [recipient, setRecipient] = useState('')
  const [blockedOpen, setBlockedOpen] = useState(false)
  const [blocked, setBlocked] = useState<string[]>([])
  const threadRef = useRef<HTMLDivElement>(null)

/**
 * Fetches the current user's message conversations from the server and loads them into component state.
 *
 * Behavior:
 * - Performs a GET request to `/api/messages/conversations` and expects an array of ConversationSummary objects.
 * - Stores the returned array into the `conversations` state via `setConversations`.
 * - Errors are treated as transient and swallowed (the poll/retry will attempt again).
 * - Always sets the `loading` flag to false in the finally block so the UI exits the loading state whether the request succeeded or failed.
 *
 * @returns {Promise<void>} Resolves once the conversations state and loading flag have been updated.
 */
  const loadConversations = useCallback(async () => {
    try {
      // data from our server is stored in 'data' variable for better debugging session. Then we set setConversation with our data variable
      const data = await api.get<ConversationSummary[]>('/api/messages/conversations')
      setConversations(data)
    } catch {
      /* transient — the poll will retry */
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMessages = useCallback(async (conversationId: number) => {
    try {
      setMessages(await api.get<ChatMessage[]>(`/api/messages/conversations/${conversationId}/messages`))
    } catch {
      /* transient — the poll will retry */
    }
  }, [])

  useEffect(() => {
    void loadConversations()
    // Real-time delivery over WebSockets is planned; until then the inbox
    // refreshes on a timer, which is enough for a chat this size.
    const timer = setInterval(() => void loadConversations(), 8000)
    return () => clearInterval(timer)
  }, [loadConversations])

  useEffect(() => {
    if (activeId == null) return
    void loadMessages(activeId)
    const timer = setInterval(() => void loadMessages(activeId), 5000)
    return () => clearInterval(timer)
  }, [activeId, loadMessages])

  // Scroll to the bottom only when a new message arrives, not on every poll, so reading
  // older messages is not yanked back down every few seconds.
  const lastMessageId = messages.at(-1)?.id
  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight })
  }, [lastMessageId])

  const activeConversation = useMemo(
    () => conversations.find((c) => c.id === activeId) ?? null,
    [conversations, activeId],
  )

  const partnerOf = useCallback(
    (conversation: ConversationSummary) =>
      conversation.participantEmails.find((e) => e.toLowerCase() !== myEmail) ?? 'Conversation',
    [myEmail],
  )
  // The partner's profile name. participantNames lines up with participantEmails, so find the
  // partner by address (which the caller's Clerk session gives exactly) and take the name at
  // the same position; comparing names would pick yourself whenever your Clerk name differs
  // from your profile name.
  const partnerNameOf = useCallback(
    (conversation: ConversationSummary) => {
      const i = conversation.participantEmails.findIndex((e) => e.toLowerCase() !== myEmail)
      if (i === -1) return 'Just you' // older threads could be started with yourself
      return conversation.participantNames?.[i] ?? partnerOf(conversation)
    },
    [myEmail, partnerOf],
  )

  // A message that arrives while its thread is open has been seen: the open thread never shows
  // a badge (unreadOf), and the server's count is cleared instead of waiting for another click.
  const unreadOf = (conversation: ConversationSummary) =>
    conversation.id === activeId ? 0 : conversation.unreadCount
  const activeUnread = activeConversation?.unreadCount ?? 0
  useEffect(() => {
    if (activeId == null || activeUnread === 0) return
    api.post(`/api/messages/conversations/${activeId}/read`).catch(() => {
      /* marking read is best-effort */
    })
  }, [activeId, activeUnread])

  // Marking it read is the effect above, which also covers messages arriving while it is open.
  const openConversation = (conversationId: number) => {
    setActiveId(conversationId)
    setMessages([])
  }

  const send = async () => {
    const content = draft.trim()
    if (!content || activeId == null) return
    setDraft('')
    try {
      const sent = await api.post<ChatMessage>(`/api/messages/conversations/${activeId}/messages`, { content })
      setMessages((prev) => [...prev, sent])
      void loadConversations() // move this thread to the top with its new preview
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not send message', 'error')
      setDraft(content)
    }
  }

  const startDirect = async () => {
    const email = recipient.trim()
    if (!email) return
    try {
      const conversation = await api.post<ConversationSummary>('/api/messages/conversations', {
        recipientEmail: email,
      })
      setNewChatOpen(false)
      setRecipient('')
      await loadConversations()
      openConversation(conversation.id)
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not start conversation', 'error')
    }
  }

  const blockPartner = async () => {
    if (!activeConversation) return
    const email = partnerOf(activeConversation)
    if (!confirm(`Block ${email}? They will not be able to message you.`)) return
    try {
      await api.post('/api/users/block', { email })
      push(`${email} blocked`, 'success')
    } catch {
      push('Could not block that user', 'error')
    }
  }

  const openBlocked = async () => {
    setBlockedOpen(true)
    try {
      setBlocked(await api.get<string[]>('/api/users/block'))
    } catch {
      push('Could not load your blocked list', 'error')
    }
  }

  const unblock = async (email: string) => {
    try {
      await api.del(`/api/users/block/${encodeURIComponent(email)}`)
      setBlocked((prev) => prev.filter((e) => e !== email))
      push(`${email} unblocked`, 'success')
    } catch {
      push('Could not unblock that user', 'error')
    }
  }

  const reportPartner = async () => {
    if (!activeConversation) return
    const email = partnerOf(activeConversation)
    const reason = prompt(`Why are you reporting ${email}?`)
    if (!reason) return
    try {
      await api.post('/api/users/report', { email, reason })
      push('Reported to moderators — thank you', 'success')
    } catch {
      push('Could not send report', 'error')
    }
  }

  if (loading) return <Spinner label="Loading your inbox…" />

  return (
    <>
      <div className="card flex h-[calc(100vh-13rem)] overflow-hidden md:h-[calc(100vh-11rem)]">
        {/* Conversation list — full width on mobile until a chat is opened */}
        <aside
          className={`thin-scrollbar w-full shrink-0 overflow-y-auto border-r border-[var(--color-border)] md:block md:w-80 ${
            activeId != null ? 'hidden' : 'block'
          }`}
        >
          <div className="flex items-center justify-between gap-2 border-b border-[var(--color-border)] px-4 py-3">
            <h2 className="text-sm font-bold">Inbox</h2>
            <div className="flex gap-1.5">
              <button className="btn-ghost btn-sm" onClick={openBlocked} title="Manage blocked students">
                <Ban className="h-3.5 w-3.5" />
              </button>
              <button className="btn-secondary btn-sm" onClick={() => setNewChatOpen(true)}>
                <Plus className="h-3.5 w-3.5" /> New
              </button>
            </div>
          </div>

          {conversations.length === 0 ? (
            <div className="p-6 text-center text-sm text-[var(--color-ink-muted)]">
              No conversations yet.
            </div>
          ) : (
            conversations.map((conversation) => (
              <button
                key={conversation.id}
                onClick={() => openConversation(conversation.id)}
                className={`flex w-full flex-col gap-1 border-b border-[var(--color-border)] px-4 py-3 text-left transition-colors hover:bg-[var(--color-surface-muted)] ${
                  conversation.id === activeId ? 'bg-primary-50 dark:bg-primary-900/30' : ''
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-semibold">{partnerNameOf(conversation)}</span>
                  <time
                    className="shrink-0 text-[11px] text-[var(--color-ink-faint)]"
                    dateTime={conversation.lastMessageAt ?? conversation.createdAt}
                  >
                    {inboxTime(conversation.lastMessageAt ?? conversation.createdAt)}
                  </time>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span
                    className={`flex min-w-0 items-center gap-1 text-xs ${
                      unreadOf(conversation) > 0
                        ? 'font-semibold text-[var(--color-ink)]'
                        : 'text-[var(--color-ink-faint)]'
                    }`}
                  >
                    {conversation.type === 'MARKETPLACE' && <Tag className="h-3 w-3 shrink-0" aria-label="Listing chat" />}
                    <span className="truncate">
                      {conversation.lastMessage == null
                        ? conversation.type === 'MARKETPLACE' ? 'Listing chat' : 'No messages yet'
                        : `${conversation.lastMessageSenderEmail?.toLowerCase() === myEmail ? 'You: ' : ''}${conversation.lastMessage}`}
                    </span>
                  </span>
                  {unreadOf(conversation) > 0 && (
                    <span className="shrink-0 rounded-full bg-primary-600 px-2 py-0.5 text-[11px] font-bold text-white">
                      {unreadOf(conversation)}
                    </span>
                  )}
                </div>
              </button>
            ))
          )}
        </aside>

        {/* Thread */}
        <section className={`flex flex-1 flex-col ${activeId == null ? 'hidden md:flex' : 'flex'}`}>
          {activeConversation ? (
            <>
              <header className="flex items-center justify-between gap-2 border-b border-[var(--color-border)] px-4 py-3">
                <div className="flex min-w-0 items-center gap-2">
                  <button className="btn-ghost btn-sm md:hidden" onClick={() => setActiveId(null)} aria-label="Back">
                    <ArrowLeft className="h-4 w-4" />
                  </button>
                  <span className="truncate text-sm font-bold">{partnerNameOf(activeConversation)}</span>
                </div>
                <div className="flex gap-2">
                  <button className="btn-ghost btn-sm" onClick={blockPartner}>
                    <Ban className="h-3.5 w-3.5" /> Block
                  </button>
                  <button className="btn-ghost btn-sm" onClick={reportPartner}>
                    <Flag className="h-3.5 w-3.5" /> Report
                  </button>
                </div>
              </header>

              <div ref={threadRef} className="thin-scrollbar flex flex-1 flex-col gap-2 overflow-y-auto p-4">
                {messages.length === 0 ? (
                  <p className="m-auto text-sm text-[var(--color-ink-faint)]">
                    No messages yet — say hello.
                  </p>
                ) : (
                  messages.map((message, index) => {
                    const mine = message.senderEmail.toLowerCase() === myEmail
                    const sent = new Date(message.createdAt)
                    const newDay =
                      index === 0 || new Date(messages[index - 1].createdAt).toDateString() !== sent.toDateString()
                    return (
                      <Fragment key={message.id}>
                        {newDay && (
                          <p className="my-1 self-center text-[11px] font-medium text-[var(--color-ink-faint)]">
                            {dayLabel(sent)}
                          </p>
                        )}
                        <div
                          className={`max-w-[75%] rounded-2xl px-3.5 py-2 text-sm ${
                            mine
                              ? 'self-end rounded-br-sm bg-primary-600 text-white'
                              : 'self-start rounded-bl-sm border border-[var(--color-border)] bg-[var(--color-surface-muted)]'
                          }`}
                        >
                          <p className="whitespace-pre-wrap break-words">{message.content}</p>
                          <time
                            dateTime={message.createdAt}
                            className={`mt-0.5 block text-right text-[10px] ${mine ? 'text-white/75' : 'text-[var(--color-ink-faint)]'}`}
                          >
                            {sent.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}
                          </time>
                        </div>
                      </Fragment>
                    )
                  })
                )}
              </div>

              <div className="flex gap-2 border-t border-[var(--color-border)] p-3">
                <input aria-label="Message"
                  className="field"
                  placeholder="Type a message…"
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && send()}
                />
                <button className="btn-primary" onClick={send} aria-label="Send">
                  <Send className="h-4 w-4" />
                </button>
              </div>
            </>
          ) : (
            <div className="m-auto p-6">
              <EmptyState
                icon={<MessageCircle className="h-6 w-6" />}
                title="Select a conversation"
                description="Pick a chat from the list, or start one from a marketplace listing."
              />
            </div>
          )}
        </section>
      </div>

      <Modal
        open={newChatOpen}
        onClose={() => setNewChatOpen(false)}
        title="New direct message"
        description="Enter the email of the student you want to reach."
        footer={
          <>
            <button className="btn-ghost" onClick={() => setNewChatOpen(false)}>
              Cancel
            </button>
            <button className="btn-primary" onClick={startDirect}>
              Start chat
            </button>
          </>
        }
      >
        <input aria-label="Recipient email"
          className="field"
          placeholder="student@example.com"
          value={recipient}
          onChange={(e) => setRecipient(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && startDirect()}
        />
      </Modal>

      <Modal
        open={blockedOpen}
        onClose={() => setBlockedOpen(false)}
        title="Blocked students"
        description="Blocked students cannot message you, and you cannot start a chat with them."
        footer={
          <button className="btn-ghost" onClick={() => setBlockedOpen(false)}>
            Done
          </button>
        }
      >
        {blocked.length === 0 ? (
          <p className="py-4 text-center text-sm text-[var(--color-ink-muted)]">
            You have not blocked anyone.
          </p>
        ) : (
          <ul className="space-y-2">
            {blocked.map((email) => (
              <li
                key={email}
                className="flex items-center justify-between gap-3 rounded-xl border border-[var(--color-border)] px-3 py-2"
              >
                <span className="truncate text-sm">{email}</span>
                <button className="btn-ghost btn-sm" onClick={() => unblock(email)}>
                  Unblock
                </button>
              </li>
            ))}
          </ul>
        )}
      </Modal>
    </>
  )
}

const isSameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString()

// Inbox timestamp: the time today, the weekday within a week, otherwise the date.
function inboxTime(iso: string) {
  const date = new Date(iso)
  const now = new Date()
  if (isSameDay(date, now)) return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  if (now.getTime() - date.getTime() < 6 * 86_400_000) return date.toLocaleDateString(undefined, { weekday: 'short' })
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

// Divider between days in a thread.
function dayLabel(date: Date) {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  if (isSameDay(date, new Date())) return 'Today'
  if (isSameDay(date, yesterday)) return 'Yesterday'
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })
}
