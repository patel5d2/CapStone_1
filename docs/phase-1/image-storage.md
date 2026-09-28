# S1-05 / D-IMAGES — provider evidence, upload design and the ballot

**Prepared 2026-09-18. No provider chosen, no dependency added, no code written.**

S1-05's first step is "settle D-IMAGES", which is [Open Question 1](../../context/5_progress.md#open-questions)
and a Rule 8 decision. The issue forbids inventing a storage provider, and
`2_architecture.md` lists image storage as **PLANNED**, which means no SDK is installed
or pre-approved. So this is the half that does not depend on the vote: what the codebase
actually does today, evidence on the two options, the parts of the upload design that are
identical either way, and the ballot.

---

## 1. What exists today (read from the checkout, 2026-09-18)

| Thing | State |
|---|---|
| Provider SDK | none — `2_architecture.md` §Tech stack: "Image storage · **Cloudinary or S3** · PLANNED" |
| Environment | `CLOUDINARY_URL` *or* `AWS_S3_BUCKET`/`AWS_REGION`, both marked *(planned)*, neither in `.env.example` |
| `listing_photo` | `listing_id` + `photo_url` only — **no primary key, no ordering column** (the schema fix is S3-02, not this story) |
| Upload endpoint | none |

**`photoUrls` is accepted verbatim from the request body and echoed to every viewer.**
`ListingService.create` and `.update` do
`listing.setPhotoUrls(request.getPhotoUrls() == null ? List.of() : request.getPhotoUrls())`
with no validation of any kind: not a URL check, not a host check, not a count check.

That is already the "constrain returned asset references so a caller cannot claim an
asset they do not own" problem in AC2, live today rather than hypothetical. A signed-in
student can point a listing at any URL anywhere — another student's asset, a tracking
pixel that harvests the IP of everyone who opens the listing, or an arbitrarily large
remote file. It needs the approved asset host to be fixed properly, so it is S1-05's to
close, but **it is exploitable now and does not depend on the vote** — see §6.

## 2. The two options, against this project's constraints

The constraints that actually discriminate: **no budget line** (the same constraint that
killed SMS in decision 015), a five-person team, one deployable, and objective 3 —
a listing with up to five photos created in **under two minutes on mobile**.

### Cost and account friction

| | Cloudinary | AWS S3 |
|---|---|---|
| Free allowance | **25 credits per rolling 30 days**; 1 credit = 1 GB storage **or** 1 GB delivered bandwidth **or** 1,000 transformations ([credits FAQ](https://cloudinary.com/documentation/developer_onboarding_faq_credits)) | New 2026 accounts get **$100 in credits, up to $200 over 6 months** ([AWS Free Tier](https://aws.amazon.com/free/)) |
| Card required | **No** — "No credit card or other financial details are required" ([free plan FAQ](https://cloudinary.com/documentation/developer_onboarding_faq_free_plan)) | Account signup requires payment details |
| Production use on the free plan | Explicitly allowed: "can be used without restrictions, even in production, as long as you don't exceed your 30-day free credit allowance" | Charged once credits or always-free limits are exceeded |
| Runs out before the Expo? | No — the allowance renews every 30 days | **Yes.** Credits cover 6 months; this project runs Sep 2026 → the Expo on **19–23 Apr 2027**, which is longer |

At this project's scale the Cloudinary allowance is not close to binding: five photos per
listing at ~2 MB each is ~10 MB, so 1 credit of storage holds ~100 listings and the free
allowance holds far more than a demo will ever contain.

**The AWS credit expiry is the sharpest fact here.** Whoever owns the AWS account is
personally liable for the bill from roughly March 2027 — during Sprint 12 and the final
presentation.

### Objective 3: five photos, under two minutes, on mobile

A phone camera photo is several megabytes. Uploading five of them raw over campus wifi is
the thing most likely to blow the two-minute target.

- **Cloudinary** transforms on delivery (`f_auto,q_auto,w_…`), so the listing grid and
  detail view fetch small derivatives without extra infrastructure, and incoming uploads
  can be capped and re-encoded server-side by the provider.
- **S3 stores bytes and nothing else.** Delivering a sensible thumbnail means either
  CloudFront plus a Lambda resize pipeline — a second and third AWS service, which is the
  kind of addition decision 010 rejects without a measured need — or resizing in the
  browser before upload, which puts the quality/rotation/EXIF problem on the client.

### Dependencies — neither option actually needs an SDK

This matters because the issue bans adding one here, and PLANNED is not pre-approval.

- **Cloudinary**: a signed upload is one hash. Sort the parameters by name, join as
  `name=value` with `&`, append the API secret, take the SHA-1 **or** SHA-256 hex digest;
  valid one hour ([authentication signatures](https://cloudinary.com/documentation/authentication_signatures)).
  That is `java.security.MessageDigest` and nothing else.
- **S3**: a presigned PUT is SigV4 — a canonical request, a credential scope, and a
  four-step HMAC key derivation. Also pure JDK, but materially more code to get right,
  and getting it subtly wrong fails closed in ways that are tedious to debug.

**So "which provider" is a decision about cost, delivery and operational burden, not about
which SDK to install.** If the team still wants an SDK for ergonomics, that remains its own
approved change, as the issue requires.

### Recommendation for the vote, not a decision

The evidence favours **Cloudinary**: it is the only one of the two with a free allowance
that outlives this project, it needs no payment details from a student, it answers
objective 3's delivery problem without adding two more services, and its upload signature
is one hash. **S3's case** is that it is the cheaper and more standard choice at real
scale, it is the more transferable skill, and it keeps media in infrastructure the team
controls. Neither is settled until the minutes say so.

## 3. The design that is identical either way

None of this depends on the vote, and all of it is required by AC2.

**Upload authorization is server-side.** The browser never holds a provider secret
(invariant 8). The flow is: the SPA asks the API to authorize an upload; the API checks
the caller's Clerk token, applies the per-listing photo limit, and returns a short-lived
credential — a Cloudinary signature plus timestamp, or an S3 presigned PUT — scoped to one
asset path it chose. The secret stays in an environment variable server-side. The
publishable/bucket identifier may reach the client; the API secret never does.

**Validate the type by content, not by extension or `Content-Type`.** Both are attacker
controlled. Read the leading bytes and match a known signature —
JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, GIF `47 49 46 38`, WebP `52 49 46 46`
+ `57 45 42 50` at offset 8 — and reject anything else. `javax.imageio.ImageIO` then gives
width and height for the dimension cap without a dependency.

**Constrain the stored reference.** After upload the client tells the API which asset to
attach. The API accepts it only if the reference is on the approved asset host **and**
matches the path the API itself issued for that caller and listing. Anything else is
refused, which closes §1's gap. Ownership is checked by loading the listing and comparing
its owner to the caller (invariant 2) — a missing listing and someone else's listing must
be indistinguishable in the response.

**Rejected uploads report per file**, so the S3-03 composer can mark the offending
thumbnail rather than failing the whole set: a stable machine-readable reason
(`TOO_LARGE`, `NOT_AN_IMAGE`, `TOO_MANY_PHOTOS`, `DIMENSIONS_TOO_LARGE`, `UPLOAD_FAILED`),
the file's name, and a sentence a student can act on. One rejected file must not discard
the four that succeeded.

**Deletion and orphans.** `listing_photo` is `ON DELETE CASCADE` on `listing`, so deleting
a listing already removes the rows — **and leaves the remote assets behind forever**,
because nothing tells the provider. Three cases need a recorded answer:

| Case | Proposed handling |
|---|---|
| Listing deleted | Delete its assets at the provider in the same operation; a provider failure must not block the listing delete, so the reference goes to a pending-deletion record and is retried. |
| Photo removed from a listing during an edit | Same as above for the removed asset only. |
| Upload authorized, never attached (abandoned composer) | The asset exists at the provider with no row pointing at it. Sweep assets older than a chosen age that no `listing_photo` row references. |

The sweep interval and the abandonment age are **values the team has to choose**, not ones
this document may invent.

## 4. Values this story cannot pick

The working agreement forbids inventing a threshold, so these go on the ballot with a
suggested starting point and the reasoning behind it, not as settled numbers:

| Value | Suggested | Why that |
|---|---|---|
| Max file size | 10 MB | Above a typical phone photo, so nothing legitimate is refused; five of them is one hundredth of a free credit |
| Max dimensions | 4000 × 4000 | Above any phone camera's long edge; bounds the decode cost of the dimension check |
| Photos per listing | 5 | Fixed by objective 3, not a free choice |
| Abandoned-upload age | 24 h | Longer than any plausible composer session |
| Accepted types | JPEG, PNG, WebP | What phones produce and browsers render; HEIC is deliberately excluded because it needs a transcode |

## 5. The ballot for #51 (D-IMAGES)

1. **Provider** — Cloudinary or S3. §2 is the evidence; the recommendation is Cloudinary.
2. **The dependency** — whether the chosen provider's SDK is added at all, given that both
   signing schemes are pure JDK. Either way it lands in its own change, never in S1-05.
3. **The limits in §4** — size, dimensions, abandonment age, accepted types.
4. **Deletion policy** in §3, including who owns the orphan sweep.

## 6. Found while auditing — needs its own issue

**`photoUrls` accepts any string from the request body** (§1). A student can attach a
remote URL that harvests the IP of everyone who opens the listing. It is reachable today,
it is independent of D-IMAGES, and a minimal fix — bound the list length and require each
entry to be a well-formed `https` URL — does not need the provider to be chosen. The full
fix, restricting to the approved asset host, is S1-05's.
