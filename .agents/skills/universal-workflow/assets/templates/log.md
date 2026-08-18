# Log: bio-max-length

## 2026-08-18 — slice 1
Failed: unit, 500-char bio was accepted (off-by-one).
Cause: `length <= 500` used as the reject path.
Change: reject when `length > 500`; 500 still saves.
