package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings

/*
 * How seeded content is read in the user's language.
 *
 * Everything the catalogue ships is a **row**, and a row does not change when a toggle does: the seeds
 * are compiled into the APK, written into `exercise.db` on first open, and thereafter the database is
 * what the app reads. So localisation happens **here**, on the read side, and the tables stay exactly
 * as they were seeded.
 *
 * That is not merely convenient. The built-in routine pass is content-addressed over the routine's name
 * and every station note (`GymMath.structuralHashOf`, `Seeder.upsertBuiltInRoutine`), so a seed whose
 * text varied by selected language would insert a new `routine_version` on **every flip of the
 * toggle** — permanently, and on every install. Resolving at display time makes that unrepresentable.
 *
 * Two mechanisms, and the split is decided by what already exists on disk:
 *
 *  - **Exercise names** come from the `exercise` table's own second column. `name_en` has been NOT NULL
 *    and correctly seeded for all 23 movements since schema v1 and is already read into [Exercise]; it
 *    was simply never drawn. [displayName] is the whole of that fix.
 *  - **Everything else** — cues, routine names, programme names, step labels, station notes — has no
 *    English column, and adding one would mean an irreversible migration on five tables plus a
 *    two-counter content release that `onUpgrade` cannot deliver on its own. Those resolve through
 *    `strings.catalog`, keyed by the row's stable seed id, **falling back to the stored string**.
 *
 * The fallback is what makes user-authored text correct for free. A user routine's id is `u_<uuid>` and
 * matches nothing in the table, so its name renders as the user typed it — in every language, which is
 * the ruling in `.planning/i18n/DECISIONS.md` §L10 rather than a gap. Editing a built-in *forks* it into
 * a new id, so a preset someone tidied up keeps their words and not ours.
 *
 * **Never translate `session.routine_name`.** It is a snapshot frozen at `startSession` so the resume
 * banner survives the routine's deletion, and whatever the routine was called that day, in whatever
 * language, is what that session performed. There is deliberately no selector for it.
 */

/**
 * The movement's name — `name_ja` or `name_en`, straight off the row.
 *
 * A `when` on [Strings.lang] rather than a table lookup, because both languages are already in memory
 * and a second copy of 23 names would be a second thing to keep correct. The pair is not always a
 * literal translation and is not meant to be: `squat` is スクワット and "Air squat", the generic
 * movement word against CrossFit's vocabulary, and both are right for their reader.
 */
fun Exercise.displayName(strings: Strings): String = when (strings.lang) {
    Lang.Ja -> nameJa
    Lang.En -> nameEn
}

/**
 * The form cue, or null when the movement has none.
 *
 * **The null is load-bearing.** 走る and the five push-up ladder rungs carry no cue because §F.1 writes
 * "—" for them, meaning *none* rather than *unknown*; six invented English sentences would put unsourced
 * content in the catalogue, which is the one thing this feature is not allowed to do.
 */
fun Exercise.displayCue(strings: Strings): String? =
    cue?.let { stored -> strings.catalog.exerciseCue(id) ?: stored }

/** The routine's name as the library lists it. A user routine falls through to what they typed. */
fun RoutineSummary.displayName(strings: Strings): String =
    strings.catalog.routineName(routineId) ?: name

/**
 * The name on a version snapshot.
 *
 * This is the routine's name *now*, which for a built-in is the same string it has always been — seed
 * text is never rewritten, and a built-in whose content is ever corrected takes a new id rather than an
 * edit (§A.5). Historical naming is `session.routine_name`, which is frozen and never translated.
 */
fun RoutineSnapshot.displayName(strings: Strings): String =
    strings.catalog.routineName(routineId) ?: name

fun RoutineDetail.displayName(strings: Strings): String = snapshot.displayName(strings)

fun RoutineCard.displayName(strings: Strings): String =
    strings.catalog.routineName(routineId) ?: name

/**
 * A station's note — マーフ's 一マイル, タバタ's 種目は自由に.
 *
 * Takes the owning routine's id because [RoutineStation] does not carry one; the note is keyed on
 * (routine, position) so that the same words on a user's forked copy stay theirs.
 */
fun RoutineStation.displayNote(routineId: String, strings: Strings): String? =
    note?.let { stored -> strings.catalog.stationNote(routineId, position) ?: stored }

fun ProgressionState.displayName(strings: Strings): String =
    strings.catalog.programName(programId) ?: nameJa

fun ProgressionState.displayNote(strings: Strings): String? =
    noteJa?.let { stored -> strings.catalog.programNote(programId) ?: stored }

/**
 * A step's own label — アームストロング's 第一日..第五日, and null for every リーコン・ロン step.
 *
 * The null is again the point: Recon Ron's eighteen steps carry no label and the UI numbers them, so a
 * caller must keep its `?: "第" + count(n) + unit` fallback rather than expecting a string here.
 */
fun ProgressionStep.displayLabel(programId: String, strings: Strings): String? =
    labelJa?.let { stored -> strings.catalog.stepLabel(programId, stepIndex) ?: stored }

fun ProgressionStep.displayNote(programId: String, strings: Strings): String? =
    noteJa?.let { stored -> strings.catalog.stepNote(programId, stepIndex) ?: stored }
