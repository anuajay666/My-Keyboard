# Bugs

Format per bug: ID, severity (S1 crash/data loss, S2 feature broken, S3 annoying, S4 cosmetic), steps, what happened, what should happen, evidence (screenshot or Diagnostics log).

| ID | Sev | Steps | Actual | Expected | Evidence | Status |
|----|-----|-------|--------|----------|----------|--------|
| B1 | S1 | Open AI panel, run Fix grammar | Panel closed by itself (Oct 23 build) | Result shown | no log yet | fix attempted (own process, keep rules), needs phone retest |
| B2 | S2 | Tap Speak | No text appeared (Oct 23 build) | Text appears | none | rewritten, needs phone retest |
| B3 | S1 | Install new build over old | "App not installed" | Installs | none | fixed key in code, needs fresh install after uninstall |
