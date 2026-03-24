# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java implementation of the **GB/T 28181-2022** standard — China's national standard for public security video and audio surveillance networking systems. The full specification PDF is at `doc/file/GBT+28181-2022.pdf`.

## Environment

- **Language**: Java 21 (Azul JDK distribution)
- **IDE**: IntelliJ IDEA (single-module project)
- **Source root**: `src/`

## Build System

No build system is configured yet. Once one is added (Maven or Gradle recommended), update this section with:
- How to build: `mvn package` or `./gradlew build`
- How to run tests: `mvn test` or `./gradlew test`
- How to run a single test: `mvn -Dtest=ClassName#methodName test` or `./gradlew test --tests "ClassName.methodName"`

## Architecture

The project is in early initialization — no source code exists yet. When implementing, the GB/T 28181 standard defines:

- **SIP-based signaling** for device registration, session negotiation (based on RFC 3261)
- **RTP/RTSP media streaming** for audio/video transport
- **Device catalog management** — querying and managing surveillance devices
- **PTZ control** — pan/tilt/zoom commands over SIP MESSAGE
- **Alarm and event notification** — device-to-platform reporting
- **Recording playback** — historical video retrieval via SIP INVITE with time range
