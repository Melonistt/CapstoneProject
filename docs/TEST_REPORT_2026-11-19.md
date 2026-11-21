# Test report — 19 Nov 2026: client feedback round (branch `boss-fixes`)

**Tester:** Claude, driving Dylan's laptop (Android Studio "Medium Phone API 37.0" emulator, backend in PyCharm, `adb reverse` via the Gradle task). **Build:** `boss-fixes` @ `16c6e4c` (+ `38762e0` docs). **Backend:** same branch, restarted 14:17.
Screenshots are in `docs/screenshots/2026-11-19-signup-profile/` and referenced by number below.

## Scope
The three client requests received 19 Nov:
1. AI replies too wordy → concise.
2. HTTP 422 "Input should be 'user' or 'assistant'" in the AI coach.
3. "All the health data connected": profile captured when the account is made; meals, workouts and the plan reflect each other.

## Results

| # | Test | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| T1 | Concise coach reply | `POST /ai/chat` × 3 questions (post-workout food, lose weight faster, skip breakfast) | ≤ ~80 words, no headings/bold | 64, 86, 73 words; 4 sentences each; no markup | Pass |
| T2 | 422 on follow-up | Second message with history role `model` (as the old app sent) and `assistant` | 200 both | 200 both; reply 52 words, in context | Pass |
| T3 | Workout log API | tests 32–39 in `backend/TESTING.md` (POST estimate 212 kcal = 5 MET × 85 kg × 0.5 h; manual kcal kept; bad type 422; day view burned/net/remaining; history totals; summary; delete 204→404; 401 without token) | per TESTING.md | all as expected | Pass |
| T4 | Coach knows today's food + training | Log 650 kcal lunch + 30 min strength, ask "have I trained today, and what should dinner look like?" | Mentions the session and remaining kcal | "Yes, you completed a 30-minute Full Body Strength workout today burning about 212 calories. You have 1922 calories remaining, so aim for … 700 to 800 calories…" | Pass |
| T5 | Manual workout from the app | Training → Log a workout → "Walk", Walking, 40 min, kcal blank → Save | Row appears in This week with an estimate | "1 session • 40 min • ≈198 kcal burned" (3.5 × 85 × 0.667 = 198) | Pass |
| T6 | Dashboard reflects the workout | Open Home after T5 | eaten / burned / remaining; Workouts row shows the session | "Today: 2250 eaten • 198 burned • 808 kcal left"; card "2250 kcal (−198)"; Workouts "Walk • 40 min" (screenshot 01) | Pass |
| T7 | Food log reflects the workout | Open Log Meal after T5 | Net line and workouts under meals | "2250 eaten − 198 burned = 2052 net • 808 kcal left of 2860"; walk listed with −198 kcal and Delete | Pass |
| T8 | Sign-up → profile step | Log out → Create Account: tess / tess@d.comm / test123! (02) | Toast "Account created! One more step." then About you screen, no second login | As expected (03, 04) | Pass |
| T9 | Live target preview | Age 25, Female, 165 cm, 62 kg, Moderate, Weight loss | Preview updates as fields change | "Your daily target will be about 1620 kcal" — hand check: (10×62 + 6.25×165 − 5×25 − 161) × 1.55 − 500 = 1616 → 1620 (05) | Pass |
| T10 | Save and continue | Tap Save and continue | `PUT /profile 200`, toast with target, Home shows target | Toast "Profile saved — target 1620 kcal/day"; Home "Good evening, Tess! … 1620 kcal left", card 1,620 remaining (06); Profile tab shows the same values (07) | Pass |
| T11 | Skip path | Log out → Create Account: sam / sam@d.com → Skip for now | Home with a nudge, no crash | "No calorie target yet — tap here to finish your profile"; card shows "—" (08) | Pass |
| T12 | Nudge opens Profile | Tap the nudge line | Profile tab opens | Profile tab with empty form and "Fill in your profile and save to calculate" (09) | Pass |
| T13 | "Mark as done" on a plan day | Build 1-day plan → Mark as done | Logged with source `ai_plan` | Button renders under each day card; the API path is covered by T3 (test 32). Not clicked on-device this round because the button sits below the fold and the emulator cannot be scrolled from the automation tooling | Not run on device |

## Evening session — Progress tab and screenshot pass (build `89affa0` + backend `91629bf`)

Screenshots in `docs/screenshots/2026-11-19-progress-and-screens/`.

| # | Test | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| T14 | Progress tab, empty account | Open Progress as `sam@d.com` with no data | Tiles 0 / — / 0, empty-state captions, no crash | As expected | Pass |
| T15 | Progress tab with a week of data | Seeded 7 days of meals, 4 workouts and 3 weigh-ins for `sam@d.com` via the API; reopen Progress | Tiles, grouped bars with target line, weight line, start/current/change | "4 workouts • 1,825 avg kcal • 1,039 burned"; bars Fri–Thu with "target 2000" dashed line; note "7 days logged • average net 1,677 kcal, 323 under your target"; weight 86 → 85.1 kg, −0.9 kg, 3-point line (10) | Pass |
| T16 | Weigh-in updates the profile weight | After the seeded weigh-ins, open Profile | Weight field shows the latest weigh-in | 85.1 kg shown | Pass |
| T17 | Coach answers "this week" questions | Ask "Did I train enough this week?" then follow up "and this week" | Uses the 7-day history, follow-up keeps context | Before the fix: "I don't have your activity history for the rest of the week". After adding the 7-day context (`91629bf`): "You've logged 4 workouts totaling 150 minutes over the last 7 days…" (13) | Pass (after fix) |
| T18 | Profile header name for older accounts | Open Profile | Name from `/auth/me` | "sam" / sam@d.com shown (account created before the local cache existed) | Pass |
| T19 | Weigh-in entry from the app | Progress → Today's weight → Log | Row appears, chart updates | Not clicked on device — the field is below the fold and the emulator can't be scrolled from the tooling; `POST /log/weight` is covered by TESTING.md test 24–26 | Not run on device |

Screenshot set for the manual/poster now covers: Login (14), Sign-up (02–03), About you (04–05), Dashboard (01, 06, 08), Training (11), Food log (12), AI Coach (13), Progress (10), Profile (07, 09).

## Observations / minor issues
- Gemini free tier returned one 503 "high demand" during Tuesday's testing; the app shows a retry message. Not seen today.
- `Save and continue` with an incomplete form shows a toast telling the user which fields are needed, or to Skip — not separately screenshotted.
- The Profile tab header now shows the sign-up name ("tess") because the sign-up stores `user_name`; existing accounts created before this change still show "Username" until they re-register or we read the name from `/auth/me` (small follow-up).
- Test accounts created in the local `wellness.db` today: `tess@d.comm`, `sam@d.com` (now holds 7 seeded days of meals/workouts/weigh-ins for the Progress screenshots), `coachtest<timestamp>@test.com`. Demo accounts untouched. The emulator was left on the Login screen, logged out.

## Verdict
All three client items are implemented and verified end-to-end on the emulator against the live backend, and the Progress tab closes the last instructor UI item. T13 (Mark as done) and T19 (weigh-in entry) are verified at the API level only and are on Dylan's tap-test list before the `v1.0` tag. Ready for PR to `Melonistt/main`; the backend needs Stop + Rerun after merge (new `workout_logs` table).
