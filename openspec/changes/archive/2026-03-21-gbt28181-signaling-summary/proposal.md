## Why

GB/T 28181-2022 defines a complex set of signaling interactions distributed across 14+ sections of the standard. There is no consolidated reference document in the codebase, making it hard to understand which SIP methods, MANSCDP XML CmdTypes, and interaction flows apply to each feature. This creates risk when implementing new features or debugging issues (e.g., the alarm subscription used wrong SIP method until explicitly checked against the standard).

## What Changes

- New reference document summarizing all GB/T 28181-2022 signaling definitions
- Covers all SIP methods used (REGISTER, INVITE/ACK/BYE, MESSAGE, INFO, SUBSCRIBE, NOTIFY)
- Lists all MANSCDP XML CmdType values for each MESSAGE-based command
- Describes the interaction flow for each signaling category (Chapter 9.1–9.14)
- Documents which signaling is currently implemented vs. not implemented in this codebase

## Capabilities

### New Capabilities
- `gbt28181-signaling-reference`: Comprehensive reference covering all signaling flows defined in GB/T 28181-2022, organized by section (device registration, live video, device control, alarm, query, keepalive, recording, playback, subscribe/notify, voice broadcast, software upgrade, image capture)

### Modified Capabilities

## Impact

- No code changes — documentation only
- New spec file: `openspec/specs/gbt28181-signaling-reference/spec.md`
- Serves as reference for future feature implementation decisions
