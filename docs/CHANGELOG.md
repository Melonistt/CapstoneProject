# Change log — what was changed, when, and by whom

Every entry below corresponds to a git commit on the named branch; `git log` has the full message.
Entries marked (Claude) were written by Claude with Dylan directing and reviewing; all were applied to
Dylan's laptop (`C:\Users\Dylan.UniLaptop\Documents\Uni\CapstoneProject`) and nothing outside that folder
was touched. Local-only, git-ignored files that were modified are listed separately.

## 21 Nov 2026 — branch `boss-fixes` (team integration)

| Commit | Change | Files |
|---|---|---|
| `cb4c421` (Ali) | History screen (Progress → "View food & workout history"): Food / Workouts tabs, 7/30/90 days; `GET /log/meals/history`; first pytest suite (`backend/tests/`, 9 tests) + `requirements-dev.txt` | `ui/HistoryFragment.kt`, `res/layout/fragment_history.xml`, `nav_graph.xml`, `ProgressFragment.kt`, `backend/app/routers/log.py`, `backend/app/schemas.py`, `backend/tests/*`, `backend/README.md` |
| `9a99adf` (Jacqueline, cherry-picked from the orphan branch `auth-validator-unit-tests`) | `AuthValidator` (login/sign-up rules, name split) + 17 JUnit tests | `app/.../testing/AuthValidator.kt`, `app/src/test/.../AuthValidatorTest.kt` |
| `43c5a61` | Moved `AuthValidator.kt` into the `testing/` folder its package declares; Login and Sign-up now call it instead of inline copies of the same rules | `LoginActivity.kt`, `SignupActivity.kt` |
| (this) | Changelog + test evidence: integrated build OK (3 m 32 s), pytest 9/9, JUnit 17/17 | `docs/CHANGELOG.md`, `docs/screenshots/2026-11-21-integration/` |

Integration done by fast-forwarding `boss-fixes` onto Ali's branch (he had branched from it) and cherry-picking
Jacqueline's commit (her branch had no shared history, so it could not be merged by PR). All four team members now
appear in the branch history; PR #5 carries everything.

## 19 Nov 2026 — branch `boss-fixes` (client feedback round)

| Commit | Change | Files |
|---|---|---|
| `abd0d1a` | AI coach: concise replies (≤4 sentences / ~80 words, 350-token cap); fix 422 on follow-up messages (app sent role `model`, backend expected `assistant`) | `backend/app/ai_service.py`, `backend/app/schemas.py`, `app/.../ui/CoachFragment.kt` |
| `1c0cec9` | Backend workout log (`/log/workouts`), MET-based kcal estimate, food + training combined in `GET /log/meals` and `/log/summary`, "today so far" context for the coach; tests 32–41 | `backend/app/{models,nutrition,schemas,ai_service}.py`, `backend/app/routers/{log,ai}.py`, `backend/README.md`, `backend/TESTING.md` |
| `acf2779` | App: Training "This week" + manual workout log + "Mark as done" on plan days; Home and Food log show eaten / burned / remaining | `app/.../network/{ApiModels,WellnessApi}.kt`, `app/.../ui/{TrainFragment,HomeFragment,MealFragment}.kt`, `app/.../res/layout/{fragment_train,fragment_home}.xml` |
| `16c6e4c` | App: health profile collected as step 2 of sign-up (`ProfileSetupActivity`), auto-login after register, Home nudge when no target | `app/.../ProfileSetupActivity.kt` (new), `app/.../res/layout/activity_profile_setup.xml` (new), `AndroidManifest.xml`, `SignupActivity.kt`, `ui/HomeFragment.kt` |
| `38762e0` | Added this change log | `docs/CHANGELOG.md` |
| `bd75b87` | Test report for the client-feedback round + 9 emulator screenshots | `docs/TEST_REPORT_2026-11-19.md`, `docs/screenshots/2026-11-19-signup-profile/*.jpg` |
| `89affa0` | Progress tab: 7-day eaten vs burned bar chart with target line, weight trend line, weigh-in entry, summary tiles (two Canvas chart views, no new dependency); Profile header name from `/auth/me` | `app/.../ui/ProgressFragment.kt`, `app/.../ui/charts/{WeekBarChartView,WeightLineChartView}.kt` (new), `app/.../res/layout/fragment_progress.xml`, `app/.../ui/ProfileFragment.kt` |
| `91629bf` | Coach context now includes the last 7 days (average kcal eaten, workouts) | `backend/app/ai_service.py`, `backend/app/routers/ai.py` |
| `a4d9eda` | Test report tests T14–T19 + 5 more screenshots (Progress, Training, Food log, Coach, Login) | `docs/TEST_REPORT_2026-11-19.md`, `docs/screenshots/2026-11-19-progress-and-screens/*.jpg` |
| `cec8400` | Backend `POST /ai/analyse-meal`: meal photo → identification, nutrition estimate, coaching verdict, rest-of-day and training adjustment (Gemini inline image; Groq returns 503 for photos) | `backend/app/ai_service.py`, `backend/app/schemas.py`, `backend/app/routers/ai.py` |
| `d56dc92` | App: Take photo / From gallery on the Food log tab, result card with verdict chip, "Log this meal", "Plan a N-min session" (hands off to Training), "Adjust the rest of today's meal plan"; CAMERA permission | `AndroidManifest.xml`, `network/{ApiModels,WellnessApi}.kt`, `ui/{MealFragment,TrainFragment}.kt`, `res/layout/fragment_meal.xml` |
| (this) | TESTING.md tests 43–47; photo-analysis screenshots | `backend/TESTING.md`, `docs/screenshots/2026-11-19-photo-analysis/*.jpg` |

Local-only files touched on 19 Nov (not committed): `backend/wellness.db` — throwaway test users
`coachtest<timestamp>@test.com` (meal + workout), `tess@d.comm` (profile) and `sam@d.com` (no profile), created
while testing; `sam@d.com` was then seeded with 7 days of meals, 4 workouts and 3 weigh-ins through the API for the
Progress-tab screenshots. No demo accounts changed. The emulator was left on the Login screen.
Temporary `*.patch` files were copied into the repo root to apply each commit and deleted straight after.

## 17 Nov 2026 — branch `batch2-screens` (merged as PR #4)

| Commit | Change |
|---|---|
| `78cec09` | Profile, AI Coach, Train and Meal screens wired to the backend |
| `c623cc3` | Keyboard insets fix, `adbReverseBackend` Gradle task, Home card wired, markdown stripped from coach replies |

Local-only: `backend/wellness.db` — profile set for `dylan_demo@hotmail.com`; `dylan@demo.com`'s profile was
briefly overwritten by mistake and restored to its original values within a minute.

## 15 Nov 2026 — branch `food-log` (merged as PR #3)

`f20566f` app connects via `adb reverse` (127.0.0.1), request bodies no longer logged · `577b9d7` README/TESTING
updates · `508a988` logout clears token · `b6dbb18` app network layer for `/log` and `/ai/estimate`, docs drafts.

## Earlier

`PR #1` FastAPI backend (auth, profile, Gemini coach) · `PR #2` Login/Sign Up wired to the backend, Groq
provider, Gemini over REST, `/ai/models`.
