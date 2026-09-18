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
  major: string
  email: string
  socialMediaLink: string | null
}

export interface StudentAccountDetails extends Student {}

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
  unreadCount: number
  createdAt: string
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
