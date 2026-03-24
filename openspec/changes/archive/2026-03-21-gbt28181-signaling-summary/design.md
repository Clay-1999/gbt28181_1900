## Context

This is a documentation-only change. The GB/T 28181-2022 standard PDF (`doc/file/GBT28181_2022.pdf`) is the authoritative source but is 200+ pages and not searchable as code. Developers have repeatedly needed to consult the PDF to answer questions like "which SIP method does alarm subscription use?" — this caused a real bug where RFC 3265 SUBSCRIBE was used instead of the correct SIP MESSAGE with `<Query><CmdType>Alarm</CmdType>`.

The spec document will be generated from PDF analysis (pdfplumber extraction) covering all Chapter 9 signaling sections.

## Goals / Non-Goals

**Goals:**
- Single consolidated reference for all signaling defined in GB/T 28181-2022
- Map each signaling operation to: SIP method, direction, XML CmdType, response type
- Describe interaction flow for each category
- Note current implementation status in this codebase

**Non-Goals:**
- Not a replacement for the actual standard PDF
- No code implementation required
- Does not need to cover non-signaling chapters (coding standards, resolution tables, etc.)

## Decisions

**Single spec file vs. one-per-category**: Use a single `gbt28181-signaling-reference/spec.md` organized by category. The reference is intended to be read holistically, not per feature. A single file is easier to scan and search.

**Format**: Tabular format per category with interaction diagrams in ASCII art. Follows the existing spec.md conventions in `openspec/specs/`.

**Scope**: Cover Chapter 9 (sections 9.1–9.14) which contains all signaling definitions. Earlier chapters are architecture/terminology.

## Risks / Trade-offs

- PDF text extraction may have formatting issues in complex tables → Mitigation: Cross-reference multiple sections for each signaling type
- Standard may have ambiguous/conflicting sections → Mitigation: Note ambiguities explicitly in the spec
