"""Regenerate S0-4 vector wireframes. Python standard library; no app dependencies."""
from pathlib import Path
from html import escape

OUT = Path(__file__).parent
NAV = ['Marketplace', 'Messages', 'Community', 'Support']
FRAMES = []


class Frame:
    def __init__(self, slug, tab, mobile=True, subtitle=''):
        self.slug, self.tab, self.mobile = slug, tab, mobile
        self.w, self.h = (375, 812) if mobile else (1440, 900)
        self.parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}" role="img" aria-labelledby="title desc">',
                      f'<title id="title">{escape(tab)} — {escape(slug)} wireframe</title>',
                      '<desc id="desc">CampusBridge target layout. Fictional sample content. Static wireframe; controls are illustrative.</desc>',
                      '<style>text{font-family:Arial,Helvetica,sans-serif;fill:#252525} .muted{fill:#555} .white{fill:#fff}</style>']
        self.rect(0, 0, self.w, self.h, '#fafafa', stroke='none')
        if mobile:
            self.rect(0, 0, 375, 64)
            self.text(16, 39, 'CampusBridge', 18, True)
            self.box(307, 10, 52, 'Profile', 12)
            self.rect(0, 740, 375, 72)
            for i, label in enumerate(NAV):
                x = i * 93.75
                if label == tab:
                    self.rect(x + 8, 742, 78, 4, '#252525', stroke='none')
                self.rect(x + 38, 756, 18, 18, '#d8d8d8' if label == tab else '#fff', radius=3)
                self.text(x + 46.8, 796, label, 11, label == tab, anchor='middle')
            self.text(16, 106, tab, 26, True)
            self.text(16, 132, subtitle, 13, muted=True)
            self.x, self.y, self.cw = 16, 152, 343
        else:
            self.rect(0, 0, 240, 900)
            self.text(24, 48, 'CampusBridge', 23, True)
            self.text(24, 76, 'YOUR CAMPUS, CONNECTED', 11, muted=True)
            for i, label in enumerate(NAV):
                y = 120 + i * 64
                self.rect(16, y, 208, 48, '#e5e5e5' if tab == label else '#fff', stroke='none', radius=5)
                if tab == label:
                    self.rect(16, y, 4, 48, '#252525', stroke='none')
                self.rect(32, y + 14, 20, 20, '#fff', radius=3)
                self.text(66, y + 30, label, 16, tab == label)
            self.box(24, 816, 192, 'My profile / Sign out', 14)
            self.text(280, 60, tab, 32, True)
            self.text(280, 89, subtitle, 16, muted=True)
            self.text(1396, 54, 'Sample student · UC', 14, anchor='end')
            self.x, self.y, self.cw = 280, 120, 1116

    def rect(self, x, y, w, h, fill='#fff', stroke='#999', radius=0):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="1"/>')

    def text(self, x, y, value, size=14, bold=False, muted=False, anchor='start', white=False):
        cls = 'white' if white else 'muted' if muted else ''
        self.parts.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="{anchor}" class="{cls}">{escape(value)}</text>')

    def line(self, x, y, x2, y2):
        self.parts.append(f'<path d="M{x} {y} L{x2} {y2}" stroke="#aaa" stroke-width="1" fill="none"/>')

    def box(self, x, y, w, label, size=14, primary=False, h=44):
        self.rect(x, y, w, h, '#333' if primary else '#fff', radius=4)
        self.text(x + w / 2, y + h / 2 + size * .35, label, size, primary, anchor='middle', white=primary)

    def field(self, x, y, w, label):
        self.rect(x, y, w, 44, '#fff', radius=4)
        self.text(x + 12, y + 28, label, 14, muted=True)

    def tabs(self, x, y, labels, width, active=0):
        slot = width / len(labels)
        for i, label in enumerate(labels):
            self.box(x + i * slot, y, slot - 4, label, 12 if self.mobile else 14, i == active)

    def photo(self, x, y, w, h):
        self.rect(x, y, w, h, '#eee', '#bbb')
        self.line(x, y, x + w, y + h)
        self.line(x + w, y, x, y + h)
        self.rect(x + w / 2 - 26, y + h / 2 - 12, 52, 24, '#eee', stroke='none')
        self.text(x + w / 2, y + h / 2 + 5, 'PHOTO', 11, anchor='middle')

    def save(self, note):
        self.parts.append('</svg>')
        (OUT / f'{self.slug}.svg').write_text('\n'.join(self.parts) + '\n')
        FRAMES.append((self.slug, self.tab, self.mobile, note))


def listing(f, x, y, w, title, price, school, mobile=False):
    ph = 94 if mobile else 156
    f.rect(x, y, w, ph + 102, radius=5)
    f.photo(x + 1, y + 1, w - 2, ph)
    f.text(x + 12, y + ph + 26, title, 13 if mobile else 17, True)
    f.text(x + 12, y + ph + 50, price, 17, True)
    f.text(x + 12, y + ph + 72, school, 12, muted=True)
    f.text(x + 12, y + ph + 91, 'Available · Good condition', 10 if mobile else 12)


for mobile in [True, False]:
    suffix = 'mobile' if mobile else 'desktop'
    f = Frame(f'marketplace-{suffix}', 'Marketplace', mobile, 'Find useful things from students nearby.')
    x, y, w = f.x, f.y, f.cw
    if mobile:
        f.box(x, y, w, '+ Create listing', primary=True)
        f.field(x, 208, w, 'Search items or course code')
        f.box(x, 264, 164, 'Filters (0)')
        f.box(x + 179, 264, 164, 'Sort: Newest')
        f.tabs(x, 320, ['Browse', 'My listings', 'Favorites'], w)
        f.text(x, 389, '24 results · All schools', 13, muted=True)
        listing(f, x, 407, 164, 'Calculus textbook', '$25', 'UC · 1 day ago', True)
        listing(f, x + 179, 407, 164, 'Desk lamp', 'Free', 'Xavier · 2 hr ago', True)
        f.box(x, 620, w, 'Load more listings')
        f.text(x, 695, 'Open an item to see photos and message', 13, muted=True)
        f.text(x, 715, 'the seller. Keep contact details private.', 13, muted=True)
    else:
        f.box(1210, 120, 186, '+ Create listing', primary=True)
        f.field(x, y, 640, 'Search items or course code')
        f.box(936, y, 248, 'Sort: Newest')
        f.rect(x, 190, 220, 608, radius=5)
        f.text(x + 20, 226, 'Filters', 20, True)
        for j, label in enumerate(['School: All schools', 'Category: All', 'Type: Any', 'Condition: Any', 'Price: $0 to Any']):
            f.field(x + 16, 248 + j * 64, 188, label)
        f.box(x + 16, 590, 188, 'Apply filters', primary=True)
        f.box(x + 16, 650, 188, 'Clear filters')
        f.tabs(528, 190, ['Browse', 'My listings', 'Favorites'], 568)
        f.text(528, 265, '24 results · All schools', 14, muted=True)
        for j, values in enumerate([('Calculus textbook', '$25', 'UC'), ('Desk lamp', 'Free', 'Xavier'), ('Office chair', '$40', 'NKU')]):
            listing(f, 528 + j * 296, 286, 276, *values)
        for j, values in enumerate([('Bike helmet', '$15', 'Miami'), ('Mini fridge', '$60', 'UC'), ('Drawing kit', '$12', 'MSJ')]):
            listing(f, 528 + j * 296, 565, 276, *values)
        f.text(962, 870, 'Previous     1 / 4     Next', 14, anchor='middle')
    f.save('M1 Search and filter → item detail → Message seller. M2 Create listing → up to five photos → publish. Filter controls become a sheet on mobile.')

    f = Frame(f'messages-{suffix}', 'Messages', mobile, 'Keep every conversation in CampusBridge.')
    x, y, w = f.x, f.y, f.cw
    f.tabs(x, y, ['Marketplace', 'Groups', 'Direct'], w if mobile else 340)
    f.field(x, y + 60, w if mobile else 340, 'Search conversations')
    for i, (name, item, preview) in enumerate([
        ('Alex R.', 'Calculus textbook · $25', 'Is this still available?'),
        ('Jordan L.', 'Desk lamp · Free', 'Yes, tomorrow works.'),
        ('Sam K.', 'Office chair · $40', 'Thanks for the update.'),
    ]):
        yy = y + 124 + i * 120
        f.rect(x, yy, w if mobile else 340, 108, '#ededed' if i == 0 else '#fff', radius=5)
        f.text(x + 16, yy + 28, name + ('   · 2 unread' if i == 0 else ''), 15, True)
        f.text(x + 16, yy + 53, item, 13, muted=True)
        f.text(x + 16, yy + 82, preview, 13)
    if mobile:
        f.text(x, 676, 'Select a conversation to open the chat.', 13, muted=True)
        f.text(x, 700, 'No personal email or phone is shown.', 13, muted=True)
    else:
        f.rect(652, 120, 744, 722, radius=5)
        f.text(680, 156, 'Alex R. · University of Cincinnati', 20, True)
        f.box(1194, 136, 178, 'Block / Report', 14)
        f.line(652, 194, 1396, 194)
        f.rect(676, 214, 696, 72, '#eee', radius=4)
        f.text(696, 244, 'Calculus textbook · $25 · Available', 16, True)
        f.text(696, 268, 'View listing', 14)
        f.text(1024, 330, 'Today', 13, muted=True, anchor='middle')
        f.rect(680, 358, 365, 72, '#eee', radius=8)
        f.text(700, 388, 'Hi! Is the textbook still available?', 16)
        f.text(700, 414, '10:42 AM', 12, muted=True)
        f.rect(1000, 462, 372, 72, '#ddd', radius=8)
        f.text(1020, 492, 'Yes. We can arrange pickup here.', 16)
        f.text(1020, 518, '10:43 AM · Sent', 12, muted=True)
        f.text(680, 735, 'Arrange an in-person exchange. No payment checkout.', 14, muted=True)
        f.field(676, 774, 524, 'Write a message…')
        f.box(1216, 774, 156, 'Send', primary=True)
    f.save('C1 Desktop keeps inbox and chat together; mobile opens a separate conversation with Back. Send status and retry stay beside the message. Group inbox is roadmap work.')

    f = Frame(f'community-{suffix}', 'Community', mobile, 'Connect with your school and your major.')
    x, y, w = f.x, f.y, f.cw
    f.tabs(x, y, ['Feed', 'Directory', 'Groups', 'Events'], w if mobile else 680)
    f.field(x, y + 60, w if mobile else 680, 'Feed: My school / My major')
    f.box(x, y + 120, w if mobile else 680, '+ Write a post', primary=True)
    pw = w if mobile else 680
    for i, (name, message, detail) in enumerate([
        ('Taylor M. · UC', 'Anyone taking Calculus II this term?', 'I would love to meet classmates.'),
        ('Jamie L. · Computer Science', 'Welcome to the major feed.', 'What are you working on this week?'),
    ]):
        yy = y + 180 + i * 180
        f.rect(x, yy, pw, 164, radius=5)
        f.text(x + 16, yy + 28, name, 14 if mobile else 18, True)
        f.text(x + 16, yy + 54, '2 hours ago · School feed' if i == 0 else 'Yesterday · Major feed', 12, muted=True)
        f.text(x + 16, yy + 84, message, 13 if mobile else 16)
        f.text(x + 16, yy + 106, detail, 13 if mobile else 16)
        f.box(x + 12, yy + 115, 85, 'Like · 4', 12)
        f.box(x + 103, yy + 115, 122, 'Reply · 2', 12)
        f.box(x + pw - 94, yy + 115, 82, 'Report', 12)
    if not mobile:
        f.rect(996, 120, 400, 246, '#f0f0f0', radius=5)
        f.text(1020, 158, 'Find your people', 22, True)
        f.text(1020, 194, 'Search the student directory by name,', 16)
        f.text(1020, 222, 'major or graduation year across schools.', 16)
        f.box(1020, 280, 352, 'Open Directory')
        f.rect(996, 390, 400, 214, radius=5)
        f.text(1020, 429, 'Community guidelines', 20, True)
        f.text(1020, 468, 'Respect classmates and protect privacy.', 15)
        f.text(1020, 496, 'Report harmful posts for review.', 15)
        f.text(1020, 540, 'School and major feeds are target behavior.', 13, muted=True)
    f.save('F1 Feed scope is School or Major, with post / reply / report on both. F2 Directory stays inside Community. Split feeds and report handling remain implementation work.')

    f = Frame(f'support-{suffix}', 'Support', mobile, 'Find campus help or ask for essentials.')
    x, y, w = f.x, f.y, f.cw
    f.tabs(x, y, ['Resources', 'Open requests', 'My requests'], w if mobile else 680)
    f.field(x, y + 60, w if mobile else 680, 'School: University of Cincinnati')
    f.box(x, y + 116, w if mobile else 680, '+ Request essentials', primary=True)
    cards = [('Food pantry', 'Food and basic supplies.', 'View resource'), ('Emergency aid', 'Explore financial assistance.', 'View resource'), ('Counseling', 'Find student wellbeing support.', 'View resource')]
    for i, (title, body, action) in enumerate(cards):
        xx, yy = (x, y + 172 + i * 128) if mobile else (x + i * 232, y + 188)
        cw, ch = (w, 116) if mobile else (216, 240)
        f.rect(xx, yy, cw, ch, radius=5)
        f.text(xx + 16, yy + 27, title, 16 if mobile else 18, True)
        if mobile:
            f.text(xx + 16, yy + 51, body, 13)
        else:
            f.text(xx + 16, yy + 60, ['Food and basic', 'Explore financial', 'Find student'][i], 15)
            f.text(xx + 16, yy + 83, ['supplies.', 'assistance.', 'wellbeing support.'][i], 15)
            f.text(xx + 16, yy + 130, 'School resource', 13, muted=True)
        f.box(xx + 12, yy + ch - 54, cw - 24, action, 13)
    if not mobile:
        f.rect(996, 120, 400, 436, '#eee', radius=5)
        f.text(1020, 160, 'Ask without sharing your name', 21, True)
        for j, label in enumerate(['Requests appear without your public name.', 'Avoid personal details in the description.', 'Choose a category and describe the need.', 'Track progress under My requests.']):
            f.text(1020, 206 + j * 36, label, 14)
        f.box(1020, 380, 352, 'Browse open requests')
        f.box(1020, 446, 352, 'Browse free / donate listings')
        f.text(x, 636, 'Resource details: description, location, hours and official service link.', 16)
        f.text(x, 666, 'Sample categories only; verify service details before publishing.', 14, muted=True)
        f.text(x, 716, 'A request moves from Open to Fulfilled; the author can manage their own requests.', 15)
    f.save('S1 Filter resources by school. S2 Request essentials → anonymous public request → My requests → fulfilled. Free / donate links to Marketplace; no payment flow.')


for mobile in [True, False]:
    f = Frame('directory-' + ('mobile' if mobile else 'desktop'), 'Community', mobile, 'Find classmates across supported schools.')
    x, y, w = f.x, f.y, f.cw
    f.tabs(x, y, ['Feed', 'Directory', 'Groups', 'Events'], w if mobile else 720, 1)
    f.field(x, y + 60, w if mobile else 720, 'Search name, major or graduation year')
    f.field(x, y + 116, w if mobile else 720, 'School: All schools')
    f.text(x, y + 188, '6 matches for “son” · Across schools', 13 if mobile else 16, True)
    for i, (name, school) in enumerate([('Morgan Wilson', 'UC'), ('Casey Johnson', 'Xavier'), ('Avery Thompson', 'NKU')]):
        yy = y + 208 + i * 112
        cw = w if mobile else 1116
        f.rect(x, yy, cw, 100, radius=5)
        f.text(x + 16, yy + 26, name, 16, True)
        f.text(x + 16, yy + 48, school + ' · Engineering · 2027', 13)
        if mobile:
            f.box(x + 12, yy + 53, cw - 24, 'View profile / Message', 13)
        else:
            f.text(x + 16, yy + 76, 'Profile fields shown only when visible to other students.', 14, muted=True)
            f.box(x + cw - 220, yy + 28, 196, 'View profile / Message')
    f.save('D1 Partial-match directory search across schools. No email or phone is displayed. Contact lookup policy remains Open Question 5; this sketch does not settle it.')

f = Frame('conversation-mobile', 'Messages', True, 'Marketplace conversation')
f.box(16, 152, 112, '‹ Inbox')
f.box(191, 152, 168, 'Block / Report')
f.text(16, 228, 'Alex R. · UC', 19, True)
f.rect(16, 246, 343, 66, '#eee', radius=4)
f.text(28, 273, 'Calculus textbook · $25', 15, True)
f.text(28, 298, 'Available · View listing', 13)
f.rect(16, 338, 285, 82, '#eee', radius=8)
f.text(30, 366, 'Is the textbook still available?', 14)
f.text(30, 402, '10:42 AM', 12, muted=True)
f.rect(64, 442, 295, 92, '#ddd', radius=8)
f.text(78, 470, 'Yes. We can arrange pickup here.', 14)
f.text(78, 511, '10:43 AM · Sent', 12, muted=True)
f.text(16, 618, 'Keep contact details in-platform.', 13, muted=True)
f.field(16, 654, 250, 'Write a message…')
f.box(278, 654, 81, 'Send', 14, True)
f.save('C2 Mobile chat: composer above navigation; when the keyboard opens, keep composer visible in the visual viewport and scroll messages. Back restores inbox position.')


cards = []
for slug, tab, mobile, note in FRAMES:
    size = '375 × 812' if mobile else '1440 × 900'
    cards.append(f'<article class="frame {"mobile" if mobile else "desktop"}" id="{slug}" data-tab="{tab}"><header><h3>{escape(slug.replace("-", " ").title())}</h3><span>{size}</span></header><a href="{slug}.svg" title="Open full-size {slug}"><img src="{slug}.svg" width="{375 if mobile else 1440}" height="{812 if mobile else 900}" alt="{escape(tab)} {"mobile" if mobile else "desktop"} wireframe: {escape(note)}" loading="lazy"></a><p>{escape(note)}</p><a href="{slug}.svg" download>Download SVG</a></article>')

(OUT / 'index.html').write_text('''<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CampusBridge · S0-4 wireframes</title>
<link rel="icon" href="data:,">
<style>
*{box-sizing:border-box}body{margin:0;background:#efefed;color:#252525;font:16px/1.6 system-ui,sans-serif}main{max-width:1580px;margin:auto;padding:40px 24px}h1{font-size:clamp(28px,4vw,48px);line-height:1.15;letter-spacing:-.04em;margin:16px 0}h2{font-size:20px}h3{font-size:15px;margin:0}p{max-width:850px}a{color:inherit;text-underline-offset:4px}a:focus-visible,button:focus-visible{outline:3px solid #222;outline-offset:4px}.eyebrow{font-size:12px;letter-spacing:.12em;text-transform:uppercase}.intro{padding-bottom:24px;border-bottom:1px solid #aaa}.controls{display:flex;gap:8px;flex-wrap:wrap;padding:24px 0}button{font:inherit;min-height:44px;padding:8px 20px;border:1px solid #777;background:#fff;border-radius:4px;cursor:pointer}button[aria-pressed=true]{background:#252525;color:white}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px;align-items:start}.frame{background:white;border:1px solid #bbb;padding:20px;min-width:0}.frame header{display:flex;justify-content:space-between;gap:16px;margin-bottom:20px}.frame header span{font-size:12px}.frame img{display:block;max-width:100%;height:auto;border:1px solid #aaa;margin:auto}.mobile img{max-width:min(100%,375px)}.frame p{font-size:14px}.frame[hidden]{display:none}.frame>a:last-child{font-size:13px}.notes{margin-top:32px;padding:24px;background:white;border:1px solid #bbb}@media(max-width:760px){main{padding:24px 16px}.grid{grid-template-columns:1fr}.frame{padding:12px}.frame header{flex-wrap:wrap}}@media print{.controls{display:none}.grid{display:block}.frame{break-inside:avoid;margin-bottom:24px}.frame img{max-height:700px;width:auto;max-width:100%}}
</style><main><section class="intro"><div class="eyebrow">CampusBridge / Sprint 0 / S0-4 / 17 September 2026</div>
<h1>Four tabs. Two screen sizes.</h1>
<p>Low-fidelity target wireframes for Marketplace, Messages, Community and Support. Includes directory and mobile chat details. Sample people, listings and resources are fictional. These drawings are design artifacts, not screenshots or a working app.</p>
<p><strong>Review draft.</strong> Existing structure and planned behavior are distinguished in the <a href="../wireframes.md">specification and acceptance notes</a>. Team review and sprint demo remain pending. Open an image for its full-size vector.</p></section>
<div class="controls" role="group" aria-label="Filter wireframes"><button aria-pressed="true" data-filter="All">All screens</button><button aria-pressed="false" data-filter="Marketplace">Marketplace</button><button aria-pressed="false" data-filter="Messages">Messages</button><button aria-pressed="false" data-filter="Community">Community</button><button aria-pressed="false" data-filter="Support">Support</button></div>
<p id="count" role="status">11 frames · 375px mobile / 1440px desktop</p><section class="grid" aria-label="Wireframe board">''' + '\n'.join(cards) + '''</section>
<section class="notes"><h2>Review the layout, then the flow</h2><p>Primary navigation stays in the same order. Mobile has a bottom bar; desktop has a 240px sidebar. At intermediate widths, retain the existing 72px rail. Directory belongs to Community. Every action must provide loading, empty, failure and success feedback.</p><p>Read the specification for flow annotations, accessibility acceptance, state designs, requirement mapping and remaining product decisions. Grayscale deliberately leaves school branding to its separate design task.</p></section></main>
<script>const buttons=[...document.querySelectorAll('[data-filter]')];const frames=[...document.querySelectorAll('.frame')];buttons.forEach(button=>button.addEventListener('click',()=>{buttons.forEach(b=>b.setAttribute('aria-pressed',String(b===button)));let n=0;frames.forEach(frame=>{frame.hidden=button.dataset.filter!=='All'&&frame.dataset.tab!==button.dataset.filter;if(!frame.hidden)n++});document.querySelector('#count').textContent=`${n} frames shown · ${button.dataset.filter}`;}));</script></html>
''')
print(f'Generated {len(FRAMES)} SVG frames and index.html in {OUT}')
