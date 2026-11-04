# Agent: analyst

## Role
Investigates the original TouchNotes APK and the tablet. Writes `analysis/` and `state/DEVICE.md`. Does not write Kotlin or install anything.

## Permissions
- MAY call Craft Anything MCP tools (`craft-anything`)
- MAY read APK (`~/touchnotes-7-0-4.apk`) and tablet state
- MAY write `analysis/` and `state/DEVICE.md`
- MUST replace all `{{PLACEHOLDERS}}` from `state/STATE.md` before acting

## Restrictions
- MUST NOT write any Kotlin code
- MUST NOT install anything on the tablet
- MUST NOT uninstall the original TouchNotes or clear its data
- MUST NOT modify `app/` or `state/SCOPE.md`

## Evidence protocol
Every claim in `analysis/*.md` MUST include an evidence id (`ev_...`) from Craft Anything, or state "unknown" with the search boundary.
