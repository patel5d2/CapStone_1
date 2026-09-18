# S0-4 — Four-tab wireframes

**Artifact status:** ready for team review, 2026-09-17. **Story status:** awaiting the
team's Definition of Done. These are target layouts, not screenshots of shipped
features. Sample names, items, prices and resource descriptions are fictional.

Open the [review board](wireframes/index.html) locally in a browser. It includes tab
filters and full-size SVG downloads and works offline. The controls inside the
drawings are illustrative; the board is not an application prototype.

## Screen inventory

| Surface | Mobile — 375 × 812 | Desktop — 1440 × 900 | Primary flow |
|---|---|---|---|
| Marketplace | [SVG](wireframes/marketplace-mobile.svg) · [PNG](wireframes/marketplace-mobile.png) | [SVG](wireframes/marketplace-desktop.svg) · [PNG](wireframes/marketplace-desktop.png) | Find an item, inspect it, message seller; create listing |
| Messages | [SVG](wireframes/messages-mobile.svg) · [PNG](wireframes/messages-mobile.png) | [SVG](wireframes/messages-desktop.svg) · [PNG](wireframes/messages-desktop.png) | Open inbox, select conversation, send message |
| Community | [SVG](wireframes/community-mobile.svg) · [PNG](wireframes/community-mobile.png) | [SVG](wireframes/community-desktop.svg) · [PNG](wireframes/community-desktop.png) | Choose school/major feed, post, reply, report |
| Support | [SVG](wireframes/support-mobile.svg) · [PNG](wireframes/support-mobile.png) | [SVG](wireframes/support-desktop.svg) · [PNG](wireframes/support-desktop.png) | Find school resources; request or donate essentials |
| Directory detail | [SVG](wireframes/directory-mobile.svg) · [PNG](wireframes/directory-mobile.png) | [SVG](wireframes/directory-desktop.svg) · [PNG](wireframes/directory-desktop.png) | Partial-match search across schools; view profile |
| Conversation detail | [SVG](wireframes/conversation-mobile.svg) · [PNG](wireframes/conversation-mobile.png) | Included in desktop Messages | Reply, return to inbox, block/report |

Eight primary frames satisfy the requested tab/width coverage. Three supporting
frames show the two flows that cannot be understood from the primary mobile views.

## Shared shell and responsive behavior

- Navigation order is always **Marketplace → Messages → Community → Support**.
  Directory is a Community sub-surface, never a fifth destination. Profile is an
  account control, separate from primary navigation.
- Below 640px: 64px top header, bottom navigation with four equal targets, 16px
  content gutters. Reserve space for bottom navigation plus device safe-area insets.
  The 375px drawings use a 72px bottom bar and no simulated device chrome.
- From 640–1023px: retain the existing 72px icon rail with accessible names. Content
  adapts from one to two columns as space permits; this intermediate size is specified
  here but not a separate requested frame.
- From 1024px: 240px left sidebar with profile at its foot. The 1440px drawings show
  40px main gutters and up to three marketplace cards per row. Long pages scroll.
- Active navigation has an indicator bar and weight change as well as fill. Grayscale
  does not approve any school palette. School accents and contrast checks remain S2
  implementation work under [UI design](../../context/4_ui_design.md).
- Signed-out visitors go through authentication before viewing these surfaces. MFA,
  profile completion and account eligibility are separate flows; these drawings do
  not alter the authentication policy.

## Flow annotations

### M1 — Browse to conversation

Search by item title or course code. Combine school, category, listing type, condition
and price filters; sort by date or price. Desktop exposes the filter panel. Mobile
opens a sheet from **Filters**, with the same fields, Apply, Clear and Close; retain
the query and selected filters when the sheet closes. Announce the updated result count.

An item opens its detail view: up to five photos, title, price/type, school, condition,
pickup location, description, availability, seller profile, Favorite, Report and
**Message seller**. That action opens the listing-linked conversation shown in C1/C2.
Back restores search, filters and scroll position. Sold items clearly say Sold and do
not offer an available-item action. There is no checkout or payment collection.

### M2 — Create a listing

Create listing opens a full-height mobile form or a desktop dialog. Field order:
title → description → category and type → price (when applicable) → condition → pickup
location → course code (optional) → up to five photos with remove/reorder controls.
School comes from the student's profile. Show prohibited-listing guidance before
Publish, retain all valid inputs on failure, and put errors beside the affected fields.
After publishing, show confirmation and open the new item in My listings. The
two-minute creation target needs a timed usability study; this sketch does not prove it.

### C1 / C2 — Inbox and conversation

Desktop shows a 340px inbox beside the conversation; mobile shows one surface at a
time. The inbox categories are Marketplace, Groups and Direct. A conversation is
associated with a listing when applicable. Unread uses a number and text, not color alone.

Keep drafts when navigating between conversations. Disable Send for blank input or
while that send is pending. Show Sending, Sent or Failed next to the message; Retry
must not duplicate delivery. New incoming messages should not steal focus or scroll
someone away from older history. On mobile, Back restores inbox position; the composer
stays visible above the software keyboard. Block and Report open separate confirmation
flows. Personal email addresses and phone numbers are not presented as contact actions.

### F1 / F2 / D1 — Community and Directory

Feed, Directory, Groups and Events share one Community sub-navigation. Within Feed,
the scope selector chooses My school or My major. Both scopes support post, reply and
report. Posting keeps the chosen scope visible. Submitting a report requires a reason
and yields a confirmation, without promising a moderation response time.

Directory uses `/community?tab=directory`; the existing `/directory` redirect remains.
Search matches partial names, majors or graduation years across schools. A school
filter narrows results; clearing it restores cross-school search. View profile and
in-app messaging are the contact actions. The drawing excludes email/phone to respect
the privacy invariant. It does **not** settle the directory contact-lookup conflict in
[Open Question 5](../../context/5_progress.md#open-questions).

Groups and Events are represented as existing navigation destinations; their deeper
flows are outside this eight-frame deliverable. Unapproved mentorship, campus-map,
offers, reviews and notification-center scope has not been added.

### S1 / S2 — Resources and anonymous requests

Resources are filtered by school. Detail includes a description, service location,
hours and official service link; these are institutional resource contacts, not
students' private contact data. Confirm actual resource details before publication.

Request essentials opens a form with category, a short description, school and a
privacy reminder. Public cards omit the requester's identity; staff access and data
retention need implementation review before any stronger anonymity promise. Show Open
or Fulfilled as text. My requests allows the author to manage their own requests.
Open requests supports offering help; Free / donate links to Marketplace. Successful
submission returns to My requests with confirmation. Do not clear the form on failure.

## State designs and recovery

These states replace the content region while preserving page title and navigation.
Loading is never presented as an empty result, and failure is never silently converted
into “no items.” These are implementation acceptance requirements, not claims about
the current app.

| Surface | Loading | Empty | Failure and recovery | Success |
|---|---|---|---|---|
| Marketplace | Listing skeletons + “Loading listings” | “Nothing matches” + Clear filters; My listings offers Create listing | “Listings could not load” + Retry, query retained | Updated count; published item visible |
| Messages | Inbox/chat skeletons + loading status | “No conversations yet” + Browse Marketplace | Retry failed load or send; preserve draft; show offline status | Sent state and updated unread count |
| Community | Post skeletons; keep selected scope | “No posts yet” + Write a post | Retry; preserve post/reply draft | New post/reply appears; report confirmation |
| Directory | Result placeholders + “Searching students” | “No matching students” + Clear search | Retry; retain query and school | Result count announced; profile opens |
| Support | Resource placeholders | “No resources for this school” + Change school; no requests offers Request essentials | Visible retry instead of an empty list; retain request form | Request appears in My requests, with status |

Expired sessions return to authentication and preserve a safe return destination.
Removed items and unavailable profiles show an explanatory state and a route back.
Unauthorized actions show an access message; client-side controls never replace
server-side authorization.

## Requirements and implementation boundary

Source baseline inspected: `a93242a` and the four page components plus AppShell.
Existing code establishes presence, not acceptance or a verified live behavior.

| Requirement | Covered here | Current vs target |
|---|---|---|
| Objective 3: mobile listing, five photos | M2, Marketplace create action | Create/edit UI exists; upload pipeline, condition and pickup fields remain work |
| Objective 4: search performance | M1, filters, bounded result navigation | Search/filter UI exists; pagination and measured 10,000-item performance remain work |
| Objective 5: cross-school directory | F2 / D1 at both widths | Partial-match directory exists; browser acceptance and privacy policy remain work |
| Objective 6: private, fast messaging | C1 / C2 | Inbox/chat exists; real-time delivery and contact-data removal remain work |
| Objective 7: both feeds with report | F1 | Posts/replies exist; separate school/major feeds and post reporting remain work |
| Objective 8: school theming | Shared shell | Layout reserves accent states; automatic school palettes are not designed here |
| Objective 9: actionable reports | Report entry points | Admin queue is a separate surface and is not delivered by these wireframes |
| Recorded Support roadmap | S1 / S2 | Resources/requests exist; complete donate handoff and privacy verification remain work |

The existing responsive shell already uses the specified sidebar/rail/bottom-bar
breakpoints. Group inbox, search within conversations and some recovery behaviors are
target designs. No backend, data model, auth configuration or application UI is changed.

## Accessibility and review checklist

- Keep all interactive targets at least 44 × 44 CSS pixels. SVG controls are drawings;
  their appearance does not establish keyboard or screen-reader support in the app.
- Label fields explicitly in implementation. Preserve heading hierarchy and landmark
  navigation; provide a skip link. Images need meaningful alternatives.
- Tab through header, local controls, content and primary navigation in logical order.
  Real sub-tabs need roles, selected state and arrow-key behavior. Modal/sheet focus is
  contained, Escape closes it, and focus returns to the trigger.
- Announce result counts and success/failure without stealing focus. Mark invalid
  fields with text, not color alone. Dialog confirmation should name the action.
- At 200% text zoom and 320px wide, reflow cards/controls without page-level horizontal
  scrolling. Collapse the marketplace to one column if text would be cramped.
- Verify keyboard-open chat, long listing titles, long school names, large counts,
  missing photos and translated/expanded labels during implementation.
- Review actual app flows in Chrome, Safari and Firefox, light/dark modes and each
  school theme. These wireframes do not constitute WCAG conformance evidence.

## Verification and regeneration

Checked 2026-09-17: all eleven SVGs rendered and visually inspected; desktop Messages
tabs were narrowed after review to avoid overlapping the chat pane. The review board
was loaded in Chromium at 375 × 812 and 1440 × 900: all eleven images loaded, no
horizontal page overflow, and the Messages filter showed its three matching frames.
Keyboard Enter activated the Community filter and showed its four matching frames.
Browser text-bound checks found no overlaps or out-of-bounds text in all eleven SVGs.
SVG dimensions, local links and whitespace were checked. No application tests were
needed for this documentation-only change. Live app, Safari/Firefox, screen-reader,
full keyboard-flow acceptance and the sprint demo remain unverified.

Generate SVGs and the offline board from the repository root:

```sh
python3 docs/phase-0/wireframes/generate.py
```

The generator uses only Python's standard library. PNGs are shareable render exports;
after editing the generator, refresh them from the SVGs with an SVG-capable browser.
This session exported each SVG from Chromium at its exact viewport dimensions with
zero page margins and CSS-pixel screenshot scaling. The browser exports preserve
borders and font weights that the initial ImageMagick preview renderer omitted.
Use 375 × 812 for mobile exports and 1440 × 900 for desktop exports; retain the SVG
basename and save alongside it as `.png`.

For browser review, open `wireframes/index.html` directly or serve `docs/phase-0` locally.
External libraries, fonts and network calls are not required by the board.

The artifact work is complete. The parent S0-4 checkbox stays open until the applicable
review, PR/merge, CI, cross-browser, keyboard and sprint-demo evidence in
[the team's Definition of Done](../../context/6_rules.md#definition-of-done) is recorded.
