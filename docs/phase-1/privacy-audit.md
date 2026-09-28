# S1-07 / D-DIRECTORY — contact-exposure audit and the ballot

**Prepared 2026-09-18. Nothing was implemented, on purpose.**

S1-07's accuracy note says: *"Implement whichever answer is recorded, and if none is
recorded yet, stop."* [Open Question 5](../../context/5_progress.md#open-questions) is
still open, so this is the audit and the ballot for #52, not the change.

---

## 1. Where personal email addresses are exposed today

Eight response shapes carry an address. The column that matters is **who receives it**.

| Surface | Shape · field | Reaches | Invariant 4 |
|---|---|---|---|
| **Directory** | `Student.email` — `GET /student` | **every signed-in student, for every student matched** | **breached** |
| Marketplace | `ListingResponse.sellerEmail` | every signed-in student, for every listing | **breached** |
| Messages | `MessageResponse.senderEmail` | every participant in the conversation | **breached** |
| Messages | `ConversationResponse.participantEmails` | every participant | **breached** |
| Community | `PostResponse.authorEmail` | every signed-in student | **breached** |
| Community | `CommentResponse.authorEmail` | every signed-in student | **breached** |
| Community | `GroupResponse.createdByEmail` | every signed-in student | **breached** |
| Community | `EventResponse.createdByEmail` | every signed-in student | **breached** |
| Self profile | `StudentAccountDetails.email` — `GET /student/profile` | **the owner only** | fine — this is the "separate authorized response" the story asks for, and it already exists |

The SPA consumes four of them, so this is visible behaviour and not only payload shape:

- `Directory.tsx:149` renders a **`mailto:` link** with another student's address.
- `Directory.tsx:52` starts a chat by posting `{ recipientEmail: student.email }`.
- `Directory.tsx:123` uses `student.email` as the React list **key**.
- `Marketplace.tsx:494` prints `listing.sellerEmail` on the listing.

**There is no `GET /student/{id}`.** The directory is a filtered list, so the "fetch another
student by ID directly" test in AC3 has no endpoint to hit for students today; it applies
to listings and conversations instead. Worth recording so nobody later reports its absence
as a missing test.

## 2. The finding that reframes Open Question 5

Open Question 5 is written as a conflict between invariant 4 and *"the directory's stated
purpose is finding peers by email"*. **That purpose is not implemented.**

`GET /student` accepts `firstName`, `lastName`, `city`, `state`, `universityName`, `grade`
and `major`. **There is no email parameter**, and `StudentRequest` has no email field.
`AND_EMAIL` exists in `StudentRepository` but is used only by `findByEmail`, which serves
the caller's *own* profile.

So the directory does not *look up* by email. It *discloses* email in results. Removing
the field from the payload costs **no implemented search capability** — objective 5 is
partial-name search, which is untouched by it.

That does not settle the question, because something else does depend on the disclosure:

## 3. What actually breaks if the field is removed

Two things, and both point at work this story does not own:

1. **Chat has no other way to address a recipient.** `StartConversationRequest` takes
   `recipientEmail`, and the SPA fills it from the directory payload. Remove the address
   and "Message this student" stops working until conversations can be started by a stable
   identifier — **S1-11**.
2. **The directory payload has no identifier at all.** `Student` carries name, city, state,
   university, grade, major, email and social link — **no id**. The SPA is reduced to using
   the address as a React key. Anything that replaces email needs an id to exist in the
   payload first — **S1-02's subject or S1-10/11/12's student id**.

**So S1-07 cannot fully land before the ownership cutover**, whatever #52 decides. The
dependency map lists #15 and #52; this one is not on it and should be. It is not a reason
to leave the addresses exposed — it is a reason to sequence the removal behind an
identifier, and to do the surfaces that already have one first.

## 4. `profile_privacy` — and the line invariant 5 does not cross

The planned ERD (S0-5) proposes one row per profile with `show_major`,
`show_graduation_year`, `show_bio`, `show_photo`. Those four are marked "proposed field
policy" and the ERD's own team acceptance is not recorded, so AC1's *"reviewed"*
preferences are not reviewed yet.

**Email must not be one of the toggles.** Invariant 5 makes *field visibility* the user's
choice; invariant 4 says contact details are *never* exposed to another user. A
`show_email` preference would turn a rule into a setting, and would let a student opt into
a state the contract forbids. Defaults fail closed, and email is not on the list at all.

The values still needing a decision: which fields are togglable, and what each default is.

## 5. The ballot for #52 (D-DIRECTORY)

1. **Does the directory expose email at all?** The evidence in §2 is that removing it costs
   no implemented capability. If the team wants peer contact, the in-platform route is a
   conversation, which is what invariant 4 exists to force.
2. **If not, what replaces it** as the directory's identifier and as chat's addressing —
   and therefore what S1-11 must deliver before the removal can complete.
3. **`profile_privacy` fields and defaults** (§4), excluding email.
4. **The order of removal.** The community and marketplace surfaces can drop their address
   fields as soon as a caller can be identified another way; the directory and chat are
   coupled to §3.

## 6. What was not done, and why

No migration, no `profile_privacy` table, no filtering, no DTO changes. The issue's
accuracy note is explicit that the directory conflict is decided in #52 and that this story
stops if no answer is recorded. Implementing "remove every address" would have decided
Open Question 5 by writing code, which is the one thing the guardrail forbids.

Objective 6 does not move, and the invariant-4 gap is unchanged from what the scoreboard
already records — this document only measures it.
