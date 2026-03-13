# HITA_Angent Refactor Progress

**Date:** 2026-03-13

## Completed
- Wrote design doc: `docs/plans/2026-03-13-hita-agent-refactor-design.md`
- Defined layered architecture and Compose-first migration strategy
- Captured data flow, privacy constraints, orchestration model
- Added campus-aware public skill requirement
- Updated design with Shenzhen v0 scope (login/session + timetable + scores + empty rooms)
- Added unified local schemas + TTL rules + Shenzhen data flows

## Next
- Create implementation plan (writing-plans)
- Implement Shenzhen adapter skeleton in `core-data`
- Wire timetable/scores/empty rooms pipelines to unified schema
