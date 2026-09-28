import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
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

  const loadConversations = useCallback(async () => {
    try {
      setConversations(await api.get<ConversationSummary[]>('/api/messages/conversations'))
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

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight })
  }, [messages])

  const activeConversation = useMemo(
    () => conversations.find((c) => c.id === activeId) ?? null,
    [conversations, activeId],
  )

  const partnerOf = useCallback(
    (conversation: ConversationSummary) =>
      conversation.participantEmails.find((e) => e.toLowerCase() !== myEmail) ?? 'Conversation',
    [myEmail],
  )

  const openConversation = async (conversationId: number) => {
    setActiveId(conversationId)
    setMessages([])
    try {
      await api.post(`/api/messages/conversations/${conversationId}/read`)
      setConversations((prev) => prev.map((c) => (c.id === conversationId ? { ...c, unreadCount: 0 } : c)))
    } catch {
      /* marking read is best-effort */
    }
  }

  const send = async () => {
    const content = draft.trim()
    if (!content || activeId == null) return
    setDraft('')
    try {
      const sent = await api.post<ChatMessage>(`/api/messages/conversations/${activeId}/messages`, { content })
      setMessages((prev) => [...prev, sent])
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
      void openConversation(conversation.id)
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
              No conversations yet. Message a seller from the Marketplace to start one.
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
                  <span className="truncate text-sm font-semibold">{partnerOf(conversation)}</span>
                  {conversation.unreadCount > 0 && (
                    <span className="rounded-full bg-primary-600 px-2 py-0.5 text-[11px] font-bold text-white">
                      {conversation.unreadCount}
                    </span>
                  )}
                </div>
                <span className="flex items-center gap-1 text-xs text-[var(--color-ink-faint)]">
                  {conversation.type === 'MARKETPLACE' ? (
                    <>
                      <Tag className="h-3 w-3" /> Listing chat
                    </>
                  ) : (
                    'Direct message'
                  )}
                </span>
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
                  <span className="truncate text-sm font-bold">{partnerOf(activeConversation)}</span>
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
                  messages.map((message) => {
                    const mine = message.senderEmail.toLowerCase() === myEmail
                    return (
                      <div
                        key={message.id}
                        className={`max-w-[75%] rounded-2xl px-3.5 py-2 text-sm ${
                          mine
                            ? 'self-end rounded-br-sm bg-primary-600 text-white'
                            : 'self-start rounded-bl-sm border border-[var(--color-border)] bg-[var(--color-surface-muted)]'
                        }`}
                      >
                        {message.content}
                      </div>
                    )
                  })
                )}
              </div>

              <div className="flex gap-2 border-t border-[var(--color-border)] p-3">
                <input
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
        description="Enter the school email of the student you want to reach."
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
        <input
          className="field"
          placeholder="student@mail.uc.edu"
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
