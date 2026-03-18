# HITA_Angent Login + Session Lifecycle Design

**Date:** 2026-03-14

## Goal
Add a HITA_L-style login card in Workbench and implement per-campus session lifecycle for Shenzhen (direct API login) and Main (CAS WebView login).

## Scope
- Shenzhen login: username/password form, direct API login, bearer + cookies saved.
- Main login: CAS WebView login only, cookies saved.
- Session lifecycle: LoggedOut → LoggingIn → LoggedIn → Stale, per campus.
- No server involvement; no credentials or sessions leave device.

## UX / IA
- Workbench contains a "教务账号" card (HITA_L style: rounded card, two inputs, primary button).
- Campus selector: Shenzhen / Main.
- Shenzhen: show account + password + “登录”.
- Main: show “WebView 登录” button only (no auto-fill).
- Status line: 未登录 / 已登录 / 会话过期（需重新登录）.
- Logout button visible when LoggedIn or Stale.

## Session Lifecycle
**States**
- LoggedOut: no stored session
- LoggingIn: login in progress
- LoggedIn: session exists and validate=true
- Stale: session exists but validate=false

**Transitions**
- LoggedOut → LoggingIn on user action
- LoggingIn → LoggedIn on success; → LoggedOut on failure
- LoggedIn → Stale on validate failure or 401/403
- Stale → LoggingIn on re-login; → LoggedOut on logout

**Validation**
- Shenzhen: call adapter validateSession(session)
- Main: treat cookies present as LoggedIn (placeholder), no auto-validate

## Data Flow
**Shenzhen**
1) User enters credentials → login API
2) Receive bearer + cookies → create CampusSession
3) Save session to FileSessionStore (per campus)
4) Update UI state

**Main (CAS WebView)**
1) User opens WebView login → completes CAS/MFA
2) On success redirect, extract cookies by host
3) Create CampusSession with cookies only
4) Save session and update UI state

## Privacy
- Credentials entered locally only
- Sessions stored locally only
- No credentials/session tokens are sent to backend

## Notes
- Main campus login uses iVPN/CAS flow; start URL should be configurable via intent, with a sensible default.
- Auto-fill for Main WebView is disabled by design.
