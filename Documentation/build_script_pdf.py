"""Renders the SmartStock+ MCO 2 presentation script as a polished PDF
with member role assignments. Blue theme matched to the app/deck.

Run:  python Documentation/build_script_pdf.py
Out:  SmartStock+_MCO2_Presentation_Script.pdf in the project root.
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table,
    TableStyle, HRFlowable, KeepTogether, PageBreak
)

OUT = r"C:\Users\Admin\Downloads\SmartStock+\SmartStock\SmartStock+_MCO2_Presentation_Script.pdf"

DEEP   = colors.HexColor("#0A235B")
PRIMARY= colors.HexColor("#1D4ED8")
ACCENT = colors.HexColor("#38BDF8")
SAYBG  = colors.HexColor("#EAF1FB")
SAYBD  = colors.HexColor("#1D4ED8")
GREYBG = colors.HexColor("#F4F7FB")
BODY   = colors.HexColor("#1F2A37")
MUTED  = colors.HexColor("#55637A")
LINE   = colors.HexColor("#DDE6F2")

styles = getSampleStyleSheet()


def S(name, **kw):
    kw.setdefault("fontName", "Helvetica")
    kw.setdefault("fontSize", 10)
    kw.setdefault("leading", 14)
    kw.setdefault("textColor", BODY)
    return ParagraphStyle(name, parent=styles["Normal"], **kw)


st_title   = S("t", fontName="Helvetica-Bold", fontSize=22, leading=26,
                textColor=DEEP, alignment=TA_CENTER)
st_sub     = S("s", fontSize=11, leading=15, textColor=MUTED, alignment=TA_CENTER)
st_h2      = S("h2", fontName="Helvetica-Bold", fontSize=14, leading=18,
                textColor=colors.white, spaceBefore=2, spaceAfter=2)
st_h2sub   = S("h2s", fontSize=9.5, leading=12, textColor=colors.HexColor("#DBEAFE"))
st_say     = S("say", fontSize=10.5, leading=15, textColor=DEEP,
                leftIndent=8, rightIndent=8, spaceBefore=3, spaceAfter=3)
st_do      = S("do", fontSize=9.5, leading=13, textColor=BODY, leftIndent=2)
st_body    = S("b", fontSize=10, leading=14)
st_sech    = S("sec", fontName="Helvetica-Bold", fontSize=13, leading=16,
                textColor=DEEP, spaceBefore=6, spaceAfter=4)
st_cell    = S("c", fontSize=9, leading=12)
st_cellb   = S("cb", fontSize=9, leading=12, fontName="Helvetica-Bold",
                textColor=DEEP)
st_foot    = S("f", fontSize=7.5, textColor=MUTED)


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(DEEP)
    canvas.rect(0, A4[1] - 16 * mm, A4[0], 16 * mm, fill=1, stroke=0)
    canvas.setFillColor(ACCENT)
    canvas.rect(0, A4[1] - 17 * mm, A4[0], 1 * mm, fill=1, stroke=0)
    canvas.setFillColor(colors.white)
    canvas.setFont("Helvetica-Bold", 11)
    canvas.drawString(18 * mm, A4[1] - 11 * mm, "SmartStock+  ·  MCO 2 Presentation Script")
    canvas.setFont("Helvetica", 8.5)
    canvas.setFillColor(colors.HexColor("#93C5FD"))
    canvas.drawRightString(A4[0] - 18 * mm, A4[1] - 11 * mm, "Team 2")
    canvas.setStrokeColor(LINE); canvas.setLineWidth(0.5)
    canvas.line(18 * mm, 13 * mm, A4[0] - 18 * mm, 13 * mm)
    canvas.setFont("Helvetica", 7.5); canvas.setFillColor(MUTED)
    canvas.drawString(18 * mm, 9 * mm, "Mobile Programming 2  ·  Instructor: Mr. Aaron Jude Pael")
    canvas.drawRightString(A4[0] - 18 * mm, 9 * mm, f"Page {doc.page}")
    canvas.restoreState()


doc = BaseDocTemplate(OUT, pagesize=A4,
                      leftMargin=18 * mm, rightMargin=18 * mm,
                      topMargin=22 * mm, bottomMargin=16 * mm)
frame = Frame(doc.leftMargin, doc.bottomMargin,
              doc.width, doc.height, id="main")
doc.addPageTemplates([PageTemplate(id="t", frames=[frame],
                                    onPage=header_footer)])

E = []


def slide_band(num, title, sub, speaker, time):
    """Coloured slide header band as a 1-row table."""
    left = Paragraph(f"{title}", st_h2)
    subp = Paragraph(sub, st_h2sub)
    inner = Table([[left], [subp]], colWidths=[120 * mm])
    inner.setStyle(TableStyle([
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    meta = Paragraph(
        f'<font color="#DBEAFE"><b>{speaker}</b><br/>{time}</font>',
        S("m", fontSize=9, leading=12, alignment=TA_LEFT))
    t = Table([[inner, meta]], colWidths=[120 * mm, 54 * mm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PRIMARY),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("ROUNDEDCORNERS", [5, 5, 5, 5]),
    ]))
    return t


def say(text):
    p = Paragraph(text, st_say)
    t = Table([[p]], colWidths=[174 * mm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), SAYBG),
        ("LINEBEFORE", (0, 0), (0, -1), 3, SAYBD),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ]))
    return t


def do(label, text):
    return Paragraph(
        f'<font color="#1D4ED8"><b>[{label}]</b></font> {text}', st_do)


# ---- COVER ----
E.append(Spacer(1, 14 * mm))
E.append(Paragraph("SmartStock+", st_title))
E.append(Spacer(1, 2 * mm))
E.append(Paragraph("MCO 2 Final Presentation — Speaking Script &amp; Role Assignments",
                   st_sub))
E.append(Spacer(1, 2 * mm))
E.append(Paragraph("Team 2  ·  Mobile Programming 2  ·  Instructor: Mr. Aaron Jude Pael  ·  May 19, 2026",
                   st_sub))
E.append(Spacer(1, 4 * mm))
E.append(HRFlowable(width="100%", thickness=1.2, color=ACCENT))
E.append(Spacer(1, 5 * mm))
E.append(Paragraph(
    "Target length: ~11–12 minutes (≈6 min slides + ≈4 min live demo + Q&amp;A). "
    "Deck: <b>SmartStock+_MCO2_Presentation.pptx</b> (9 slides). "
    "<b>[SAY]</b> = spoken (keep it natural, don't read robotically). "
    "<b>[DO]</b> = action. <b>[TIME]</b> = running budget.", st_body))
E.append(Spacer(1, 6 * mm))

# ---- ROLE ASSIGNMENTS ----
E.append(Paragraph("Role Assignments — Team 2 (13 members)", st_sech))
rows = [
    ["#", "Member", "Role in Presentation"],
    ["1", "Jhon Clarence B. Rulona", "Lead — Slide 1 (Title/Intro) + Q&A lead (technical)"],
    ["2", "Cris Gerard O. Carpon", "Speaker — Slide 2 (Project Overview)"],
    ["3", "Jasper Keith P. Teorica", "Speaker — Slide 3 (Architecture) + Q&A support"],
    ["4", "Mark Jacob S. Allero", "Speaker — Slide 4 (Cloud / API Integration)"],
    ["5", "Leopoldo C. Villaflores III", "Demo Narrator — Slide 5 (speaks the live demo)"],
    ["6", "John Dennis Y. Chan", "Demo Driver — Slide 5 (operates the device)"],
    ["7", "Dustin Aaron D. Tocayon", "Speaker — Slide 8 (Challenges & Learnings)"],
    ["8", "Angel Mae S. Dura", "Speaker — Slide 9 (Conclusion & acknowledgment)"],
    ["9", "Heizel D. Panugaling", "Deck operator — advances slides, screenshot fallback"],
    ["10", "Justin Jamaica P. Julaton", "Supabase operator — 2nd screen / dashboard proof"],
    ["11", "Roselyn B. Deguino", "Timekeeper — cue cards, keeps the 12-min budget"],
    ["12", "Lou Ariane Mae B. Delos Reyes", "Backup device + connectivity (airplane-mode demo)"],
    ["13", "Rynel Jay V. Vallejos", "Q&A support — notes questions, assists answers"],
]
data = [[Paragraph(c, st_cellb if i == 0 else st_cell) for c in r]
        for i, r in enumerate(rows)]
tbl = Table(data, colWidths=[10 * mm, 52 * mm, 112 * mm], repeatRows=1)
tbl.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), DEEP),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, 0), 9),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, GREYBG]),
    ("GRID", (0, 0), (-1, -1), 0.4, LINE),
    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ("LEFTPADDING", (0, 0), (-1, -1), 6),
    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
]))
E.append(tbl)
E.append(Spacer(1, 3 * mm))
E.append(Paragraph(
    "<i>Everyone faces the audience. Non-speaking members stand with the "
    "team, ready for their support role and Q&amp;A.</i>",
    S("i", fontSize=9, textColor=MUTED)))
E.append(PageBreak())


def block(num, title, sub, speaker, time, flows):
    seq = [slide_band(num, title, sub, speaker, time), Spacer(1, 4 * mm)]
    seq += flows
    seq.append(Spacer(1, 5 * mm))
    E.append(KeepTogether(seq))


# ---- SLIDE 1 ----
block(1, "Slide 1 — Title", "Opening", "Jhon Clarence B. Rulona", "0:00–0:45", [
    do("DO", "App already open on the device, mirrored to the screen."),
    Spacer(1, 2 * mm),
    say('"Good morning, Mr. Pael. We\'re Team 2, and this is our MCO 2 final '
        'presentation for <b>SmartStock+</b> — an offline-first inventory and '
        'asset management system for Android. In MCO 1 we built the offline '
        'foundation. For MCO 2 we added a full cloud backend with secure '
        'multi-tenant sync. I\'ll start with an overview, then my teammates '
        'will walk through the architecture, the cloud integration, a live '
        'demo, and what we learned."'),
    Spacer(1, 2 * mm),
    do("DO", "Advance to Slide 2."),
])

# ---- SLIDE 2 ----
block(2, "Slide 2 — Project Overview", "Introduction",
      "Cris Gerard O. Carpon", "0:45–2:00", [
    say('"The problem: small businesses, school labs, and field teams still '
        'track equipment on paper or spreadsheets — and those break the moment '
        'the network is unreliable. SmartStock+ replaces that. The key design '
        'decision is <b>offline-first</b>: the app is fully usable with no '
        'connection, and syncs to the cloud automatically when it\'s back '
        'online. Our users are small businesses, school and IT labs, field '
        'teams, and asset custodians. The core modules: inventory CRUD with '
        'asset codes, barcode and QR scanning with per-unit checkout, usage '
        'reports with CSV and PDF export, dashboard charts and low-stock '
        'alerts, a stock-take flow, and — new in MCO 2 — multi-tenant cloud '
        'sync and authentication."'),
    Spacer(1, 2 * mm),
    do("DO", "Advance to Slide 3."),
])

# ---- SLIDE 3 ----
block(3, "Slide 3 — Architecture", "How it's built",
      "Jasper Keith P. Teorica", "2:00–3:30", [
    say('"Architecturally we follow <b>MVVM with the Repository pattern</b>. '
        'The Compose UI talks only to a ViewModel, which exposes state with '
        'StateFlow. The ViewModel talks to a single Repository — the only '
        'thing that touches data: Room locally, and the cloud sync source '
        'remotely. The important part is the flow: every write hits '
        '<b>Room instantly</b>, so the UI never blocks on the network. A '
        'WorkManager job — SyncWorker — reconciles with Supabase in the '
        'background, with last-write-wins, soft-deletes, and '
        '<b>natural-key matching</b> on asset code and name, which keeps sync '
        'duplicate-free even after a reinstall or on a second device. '
        'Everything is wired with Hilt, keeping data, domain, and UI cleanly '
        'separated."'),
    Spacer(1, 2 * mm),
    do("DO", "Advance to Slide 4."),
])

# ---- SLIDE 4 ----
block(4, "Slide 4 — Cloud / API Integration", "Supabase backend",
      "Mark Jacob S. Allero", "3:30–5:00", [
    say('"For the cloud we use <b>Supabase</b> — a REST-API backend: '
        'PostgreSQL through PostgREST, plus GoTrue auth, object Storage, and '
        'Edge Functions, consumed from Kotlin with supabase-kt over Ktor. '
        'Auth supports email/password with session persistence, password '
        'reset, and biometric unlock; an Admin provisions Staff through a '
        'secure Edge Function. Sync is bidirectional across six tables with '
        'natural-key reconciliation so we never get duplicates, and item '
        'photos sync across devices through <b>Supabase Storage</b>, scoped '
        'per team. The security highlight is <b>multi-tenant isolation</b>: '
        'every row carries a team_id, and PostgreSQL Row-Level Security — '
        'backed by a SECURITY DEFINER my_team() function — guarantees each '
        'account only ever reads its own team\'s data."'),
    Spacer(1, 2 * mm),
    do("DO", "Point to the Supabase screenshot, then advance to Slide 5 and "
             "switch the screen to the DEVICE."),
])

# ---- SLIDE 5 ----
block(5, "Slide 5 — UI Demo (LIVE)", "Live walkthrough",
      "Narrator: Villaflores · Driver: Chan", "5:00–9:00", [
    Paragraph("<b>Core of the presentation. Narrate every tap. If anything "
              "stalls, switch to Slides 6–7 screenshots and keep talking — "
              "never debug live.</b>", S("warn", fontSize=9.5, leading=13,
              textColor=colors.HexColor("#B91C1C"))),
    Spacer(1, 2 * mm),
    Paragraph("1 · Auth &amp; multi-tenant isolation (~45s)", st_sech),
    do("DO", "Show redesigned login; tap password field to show it scrolls "
             "above the keyboard. Register a fresh account."),
    say('"Notice the inventory is <b>empty</b> — and in Profile, the team '
        'list shows <b>only this account</b>. That proves our multi-tenant '
        'isolation: a new account never sees another team\'s data."'),
    Paragraph("2 · Create with photo + barcode (~60s)", st_sech),
    do("DO", "Add an item with name/category/quantity, attach a camera "
             "photo, scan/link a barcode, save."),
    say('"That write went to the local Room database instantly — no spinner, '
        'works even offline."'),
    Paragraph("3 · Cloud sync proof (~45s)", st_sech),
    do("DO", "Show the sync badge / pull-to-refresh. Switch to the Supabase "
             "dashboard (Justin), refresh inventory_items."),
    say('"There\'s the row we just created, with its team_id — and the photo '
        'is in the Supabase Storage bucket. It synced across the network."'),
    Paragraph("4 · Notification + Undo (~45s)", st_sech),
    do("DO", "Edit quantity below threshold → low-stock notification. Delete "
             "an item → tap UNDO to restore."),
    say('"Low-stock alerts, and a safe undo on destructive actions."'),
    Paragraph("5 · Profile depth (~45s)", st_sech),
    do("DO", "Profile → toggle theme; Export Data → CSV share sheet; open "
             "Security Settings dialog."),
    say('"The Profile screen is fully functional — theme, CSV export, '
        'security posture, and team management for admins."'),
    Spacer(1, 2 * mm),
    do("DO", "Switch back to the deck; advance to Slide 8. (Slides 6–7 are "
             "screenshot fallbacks — present only if the live demo failed.)"),
])

# ---- SLIDE 8 ----
block(8, "Slide 8 — Challenges & Learnings", "What we learned",
      "Dustin Aaron D. Tocayon", "9:00–10:30", [
    say('"A few real challenges we solved. <b>One — Room-to-REST sync:</b> '
        'the database stores time as epoch-millis, the API uses ISO-8601; we '
        'built a mapping layer and last-write-wins resolution. <b>Two — our '
        'biggest learning, multi-tenant isolation:</b> in testing a new '
        'account could see another admin\'s data; we root-caused it to an '
        'over-broad is_admin() RLS policy plus a cache not wiped on logout, '
        'and fixed it with strict per-team RLS and a cache wipe on account '
        'switch. <b>Three — duplicate-free sync:</b> client-UUID-only keying '
        'duplicated rows on reinstall; fixed by reconciling by natural key. '
        '<b>Four — cross-device images:</b> local file paths are meaningless '
        'elsewhere, so photos moved to per-team Supabase Storage. Best '
        'practices throughout: MVVM, Repository, StateFlow, Hilt, '
        'lifecycle-aware APIs, WorkManager, and RLS-backed security."'),
    Spacer(1, 2 * mm),
    do("DO", "Advance to Slide 9."),
])

# ---- SLIDE 9 ----
block(9, "Slide 9 — Conclusion & Future", "Wrap-up",
      "Angel Mae S. Dura", "10:30–11:30", [
    say('"To summarize: SmartStock+ is a complete offline-first inventory '
        'system — full CRUD, scanning, reporting, charts, notifications, '
        'biometric auth, and secure multi-tenant Supabase sync with '
        'cross-device image storage. It stays fully usable with zero '
        'connectivity and reconciles automatically once back online. That '
        'was our core goal, and we achieved it. Future improvements: '
        'hardware-backed biometrics with the Android Keystore, an admin web '
        'panel, real-time updates, FCM push notifications, and an audit log. '
        'Thank you, Mr. Pael, for your guidance throughout Mobile '
        'Programming 2. We\'d be happy to answer any questions."'),
    Spacer(1, 2 * mm),
    do("DO", "Stop. Stay on Slide 9 for Q&A."),
])

# ---- Q&A ----
E.append(KeepTogether([
    Paragraph("Q&amp;A — Anticipated Questions  ·  (Jhon Clarence Rulona & "
              "Jasper Teorica lead; Rynel Jay assists)", st_sech),
    Paragraph('<b>Q: Why Supabase and not Firebase?</b> — "The requirement '
              'allows Firebase or a REST API. Supabase IS a REST-API backend '
              '(PostgREST over PostgreSQL) plus managed auth and storage. We '
              'chose it because PostgreSQL Row-Level Security gives '
              'database-enforced multi-tenant isolation — stronger than '
              'app-level checks."', st_body),
    Spacer(1, 2 * mm),
    Paragraph('<b>Q: How does it work offline?</b> — "Room is the single '
              'source of truth. Every read and write is local and instant. A '
              'WorkManager job syncs opportunistically when connected, so the '
              'network is never on the critical path."', st_body),
    Spacer(1, 2 * mm),
    Paragraph('<b>Q: Two devices edit the same item?</b> — "Last-write-wins '
              'on an updatedAt timestamp, with natural-key reconciliation so '
              'the same logical item never duplicates across devices."',
              st_body),
    Spacer(1, 2 * mm),
    Paragraph('<b>Q: Is biometric login real authentication?</b> — "It\'s a '
              'local unlock for an already-established session — biometrics '
              'can\'t authenticate against the server alone. The '
              'production-grade version, in our future improvements, binds '
              'the session token to a hardware Keystore key."', st_body),
    Spacer(1, 2 * mm),
    Paragraph('<b>Q: How is one team\'s data kept private?</b> — "Every row '
              'has a team_id. PostgreSQL RLS policies, backed by a SECURITY '
              'DEFINER my_team() function, make it physically impossible for '
              'a query to return another team\'s rows — enforced at the '
              'database, not the app."', st_body),
]))
E.append(Spacer(1, 6 * mm))

# ---- CHECKLIST ----
E.append(KeepTogether([
    Paragraph("Pre-Demo Checklist (before presenting)", st_sech),
    Paragraph(
        "☐ <b>supabase/01_schema.sql</b> re-run (RLS + dedup + Storage)<br/>"
        "☐ Release APK rebuilt &amp; installed; old app uninstalled first "
        "(new icon shows)<br/>"
        "☐ Test account ready; one item with a photo pre-synced<br/>"
        "☐ Screenshots pasted into Slides 4, 6, 7 (placeholder boxes "
        "deleted)<br/>"
        "☐ Device on Wi-Fi; screen mirroring tested<br/>"
        "☐ Supabase dashboard logged in on the 2nd screen (Justin)<br/>"
        "☐ Airplane-mode toggle rehearsed (offline → sync)<br/>"
        "☐ One full timed dry run completed with all speakers", st_body),
]))

doc.build(E)
print("Saved:", OUT)
