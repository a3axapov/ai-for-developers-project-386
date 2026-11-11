# Domain docs

## Before exploring

This repository uses a single-context layout:

- `GLOSSARY.md` at the repository root defines domain vocabulary.
- `docs/adr/` holds architectural decision records.
  Read decisions relevant to the area being explored.

If these files do not exist, proceed silently. The domain-modeling
skill creates them lazily when terms or decisions are resolved.

## Use the glossary's vocabulary

Use glossary terms in issue titles, proposals, hypotheses, and tests.
If a needed concept is missing, reconsider the terminology or note
the gap for domain-modeling.

## Surface ADR conflicts

If a proposal contradicts an existing ADR, identify the ADR explicitly
and explain why the decision should be reconsidered.
