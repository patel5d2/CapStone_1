// Mirrors the Spring Boot DTOs field-for-field. Keep in sync with the
// corresponding record/class under src/main/java/.../{marketplace,messages,community,support,school}.

export interface School {
  id: number
  name: string
}

// ---- Directory (existing /student endpoints) ----
export interface Student {
  /** Stable directory identifier. Use this to key or refer to a student, never `email`. */
  id: number
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityName: string
  grade: string
  major?: string | null
  graduationYear?: number | null
  bio?: string | null
  photoUrl?: string | null
}

/** Owner-only fields are kept separate from the directory response. */
export interface StudentAccountDetails extends Omit<Student, 'id'> {
  email: string
  major: string
  socialMediaLink: string | null
  universityId: number
  graduationYear: number | null
  bio: string | null
  photoUrl: string | null
}

export interface ProfileSaveRequest {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: number
  grade: string
  major: string
  email: string
  socialMediaLink: string | null
  graduationYear: number | null
  bio: string | null
  photoUrl: string | null
}

export interface ProfileImageResponse {
  photoUrl: string
}

export interface StudentSignupRequest {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: number
  grade: string
  major: string
  email: string
  socialMediaLink?: string | null
}

export interface EditStudentDetailsRequest {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: number
  grade: string
  major: string
  email: string
  socialMediaLink?: string | null
}

// ---- Marketplace ----
export type ListingCategory = 'BOOKS' | 'CLOTHES' | 'EVENT_TICKETS' | 'FURNITURE' | 'ELECTRONICS' | 'OTHER'
export type ListingType = 'SELL' | 'RENT' | 'FREE' | 'LOOKING_FOR'
export type ListingStatus = 'AVAILABLE' | 'PENDING' | 'SOLD'

export interface Listing {
  id: number
  sellerEmail: string
  title: string
  description: string | null
  category: ListingCategory
  listingType: ListingType
  status: ListingStatus
  price: number | null
  courseCode: string | null
  schoolId: number | null
  photoUrls: string[]
  favorited: boolean
  createdAt: string
  updatedAt: string
}

export interface ListingRequest {
  title: string
  description?: string
  category: ListingCategory
  listingType: ListingType
  price?: number | null
  courseCode?: string | null
  schoolId?: number | null
  photoUrls?: string[]
}

// ---- Messages ----
export type ConversationType = 'MARKETPLACE' | 'DIRECT'

export interface ConversationSummary {
  id: number
  type: ConversationType
  listingId: number | null
  participantEmails: string[]
  participantNames: string[]
  unreadCount: number
  createdAt: string
  // The newest message, for the inbox preview; null before the first message.
  lastMessage: string | null
  lastMessageSenderEmail: string | null
  lastMessageAt: string | null
}

export interface ChatMessage {
  id: number
  conversationId: number
  senderEmail: string
  content: string
  imageUrl: string | null
  createdAt: string
}

// ---- Community ----
export type GroupType = 'MAJOR' | 'GRADUATION_YEAR' | 'COURSE_STUDY' | 'GENERAL'

export interface CommunityGroup {
  id: number
  name: string
  description: string | null
  type: GroupType
  relatedValue: string | null
  schoolId: number | null
  createdByEmail: string
  memberCount: number
  joined: boolean
  createdAt: string
}

export interface Post {
  id: number
  authorEmail: string
  groupId: number | null
  content: string
  imageUrl: string | null
  pinned: boolean
  likeCount: number
  commentCount: number
  likedByMe: boolean
  createdAt: string
}

export interface Comment {
  id: number
  postId: number
  authorEmail: string
  content: string
  createdAt: string
}

export interface CampusEvent {
  id: number
  title: string
  description: string | null
  startsAt: string
  location: string | null
  schoolId: number | null
  listingId: number | null
  createdByEmail: string
  createdAt: string
}

// ---- Support ----
export type SupportCategory = 'FOOD_PANTRY' | 'EMERGENCY_AID' | 'COUNSELING' | 'OTHER'
export type RequestStatus = 'OPEN' | 'FULFILLED' | 'CLOSED'

export interface SupportResource {
  id: number
  schoolId: number
  category: SupportCategory
  name: string
  description: string | null
  contactInfo: string | null
  address: string | null
  latitude: number | null
  longitude: number | null
}

export interface AnonymousRequest {
  id: number
  category: SupportCategory
  description: string
  schoolId: number | null
  status: RequestStatus
  createdAt: string
}

// ── Admin account management (#113) ──

/** What the signed-in caller may do. The server decides; this only shapes the UI. */
export interface AdminAccess {
  admin: boolean
  /** False for a dedicated administrator account, which sees account management only. */
  hasStudentProfile: boolean
}

/** A student account as an administrator sees it. Any optional value may be missing. */
export interface AdminStudent {
  id: number
  firstName: string
  lastName: string
  email: string
  universityId: number | null
  /** Null when the account points at a school that no longer exists. */
  universityName: string | null
  grade: string
  major: string
  residentCity: string
  residentState: string
  socialMediaLink: string | null
  graduationYear: number | null
  bio: string | null
  /** False for a directory row nobody has signed in to. */
  hasSignIn: boolean
  /** Administrator accounts cannot be banned or deleted from account management. */
  administrator: boolean
  /** Banned accounts are refused by every endpoint and hidden from the directory. */
  banned: boolean
}

/** One bounded page of anything an administrator lists. `page` is zero-based. */
export interface AdminPage<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface AdminStudentUpdateRequest {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: number
  grade: string
  major: string
  socialMediaLink: string | null
  graduationYear: number | null
  bio: string | null
}

/** A banned email address. `studentId` is null when the account itself was deleted. */
export interface AdminBan {
  email: string
  studentId: number | null
  name: string | null
}

export interface AdminUserReport {
  id: number
  reportedEmail: string
  reporterEmail: string
  reason: string
  createdAt: string
}

export interface AdminListing {
  id: number
  title: string
  description: string | null
  category: ListingCategory
  listingType: ListingType
  status: ListingStatus
  price: number | null
  sellerEmail: string
  schoolName: string | null
  photoUrl: string | null
  reportCount: number
  createdAt: string
}

export interface AdminListingReport {
  id: number
  reason: string
  reporterEmail: string
  createdAt: string
}

export interface AdminPost {
  id: number
  content: string
  imageUrl: string | null
  authorEmail: string
  groupId: number | null
  /** Null for the main feed. */
  groupName: string | null
  commentCount: number
  likeCount: number
  createdAt: string
}

export interface AdminComment {
  id: number
  postId: number
  content: string
  authorEmail: string
  createdAt: string
}

export interface AdminGroup {
  id: number
  name: string
  description: string | null
  type: GroupType
  createdByEmail: string
  memberCount: number
  /** Posts that are deleted along with the group. */
  postCount: number
  createdAt: string
}

export interface AdminEvent {
  id: number
  title: string
  description: string | null
  location: string | null
  startsAt: string
  schoolName: string | null
  createdByEmail: string
  createdAt: string
}

/** A request for help. Deliberately carries nothing about who asked. */
export interface AdminSupportRequest {
  id: number
  category: SupportCategory
  description: string
  status: RequestStatus
  schoolName: string | null
  createdAt: string
}

export interface AdminSupportResourceRequest {
  schoolId: number
  category: SupportCategory
  name: string
  description: string | null
  contactInfo: string | null
  address: string | null
}
