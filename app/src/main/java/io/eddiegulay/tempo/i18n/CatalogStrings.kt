package io.eddiegulay.tempo.i18n

/**
 * Seeded content from the database: exercise cues, routine names, programme names, step labels.
 *
 * **This file is owned by one migration unit.** Interface plus both implementations live together so
 * that adding a string is a single-file change and two people migrating different pages never touch
 * the same file. Add members here; never to `Strings.kt`, which only lists the namespaces.
 *
 * Rules for filling it, from `.planning/i18n/DECISIONS.md`:
 *
 * - A key names *what the string means*, never what it says.
 * - Japanese values are **transcribed, not authored** — the app ships them today and this move must be
 *   behaviour-neutral for Japanese.
 * - Anything built from a number or a date belongs on [Formats], not here.
 * - Do not fill a deliberate hole: several functions return null rather than invent a sentence the
 *   specs never wrote, and an English string in that position re-introduces the bug the null prevents.
 *
 * ## This namespace is different in kind, and the difference decides its shape
 *
 * Every string below is a **row in `exercise.db`**, not a literal in a composable. The seeds are
 * compiled into the APK, written into the tables on first open, and thereafter the *database* is what
 * the app reads. A language toggle cannot restart the app with a different table.
 *
 * So the resolution is **by stable seed id, at display time, with the stored row as the fallback**:
 *
 * ```
 * strings.catalog.routineName("r_murph") ?: snapshot.name
 * ```
 *
 * Three properties follow, and each one is a constraint this namespace had to satisfy:
 *
 * 1. **The seed never varies by locale.** `SeedCatalog` is untouched and stays Japanese. That matters
 *    far more than it looks: the built-in routine pass is *content-addressed* over the routine's name
 *    and every station note (`GymMath.structuralHashOf`), so a seed whose text depended on the selected
 *    language would insert a fresh `routine_version` on **every flip of the toggle**, forever. Keeping
 *    localisation on the read side makes that impossible rather than merely unlikely.
 *
 * 2. **Nothing is delivered by a migration.** SQLite 3.28 at `minSdk 29` has no `DROP COLUMN`, so a
 *    column per language is permanent on five tables; `onUpgrade` never seeds, so a column added there
 *    stays empty until a content bump follows it; and a content bump only reaches a row whose own
 *    `catalogVersion` moved too. All three hazards are avoided by not adding a column. A fresh install
 *    and a three-year-old install read the same Japanese rows and render the same English.
 *
 * 3. **User-authored text falls through untranslated, automatically.** A user routine's id is
 *    `u_<uuid>`; it matches nothing here, so the lookup misses and the stored string is what renders —
 *    in every language, which is correct (§L10). Editing a built-in *forks* it into a new id, so a
 *    tidied-up 七分間 keeps the user's words rather than reverting to ours.
 *
 * ## The exercise names are not here, and that is deliberate
 *
 * `exercise.name_en` has existed since schema v1, is seeded for all 23 rows, is projected by
 * `GymStore`'s exercise read and reaches `Exercise.nameEn` on every launch. Duplicating those 23
 * strings here would create a second source of truth for data that is already correct. They are served
 * by `Exercise.displayName(strings)` off the column instead. Cues are here because there is no
 * `cue_en` column and adding one would cost the migration this design exists to avoid.
 *
 * ## Keying is by id, so the ids are append-only
 *
 * A built-in whose numbers are ever corrected gets a **new id** rather than an edit
 * (`BuiltInCatalog.kt`, §A.5). The same rule now covers its text: renaming a built-in in place would
 * leave this table translating the *old* name under the *new* id, and would break the March/April
 * guarantee in English exactly as it would in Japanese. `SeedTranslationTest` asserts every Japanese
 * value here against the literal `SeedCatalog` actually seeds, so the two cannot drift silently.
 */
interface CatalogStrings {

    // ─── §F.1 exercise cues — `exercise.cue`, 17 of 23 rows ─────────────────────────────────────
    //
    // A form cue drawn under the movement name on the index, the detail page, the picker and two
    // player pages. It is also read aloud: PreparePage and RestPage put it in a `contentDescription`,
    // so these are heard as often as they are seen and are written short for both.
    //
    // 走る and the five ladder rungs carry **no** cue and get no member. §F.1 writes "—" for them,
    // which is the table's way of saying "none", and inventing six sentences here would re-introduce
    // exactly the hole `BuiltInCatalog.kt:188` and `:199-200` are careful to leave open.

    val cuePushup: String
    val cueKneePushup: String
    val cuePushupRotation: String
    val cuePullup: String
    val cueRingRow: String
    val cueSquat: String

    /** 膝は九十度. The kanji numeral is a *stored* one; English spells the angle in digits (§L7). */
    val cueWallSit: String

    /** The longest cue in the catalogue — 14 Japanese characters, and roughly 32 in English. */
    val cueLunge: String

    val cueStepUp: String
    val cueSitup: String
    val cueCrunch: String
    val cuePlank: String

    /**
     * 腰を落とさない — **the same words as [cueKneePushup]**, in both languages.
     *
     * Two keys rather than one because they are two rows of the `exercise` table that happen to agree;
     * merging them would make a future correction to one silently change the other. The values are
     * identical on purpose and `SeedTranslationTest` reads them from the seed, so they stay that way.
     */
    val cueSidePlank: String

    val cueDip: String
    val cueBurpee: String
    val cueJumpingJack: String
    val cueHighKnees: String

    // ─── §F.5 built-in routine names — `routine_version.name`, 9 rows ───────────────────────────
    //
    // Six of the nine are proper nouns and translate to themselves: the Japanese is a transliteration
    // of an English name, so the English is the *source* rather than a re-translation of the katakana.

    /**
     * 七分間.
     *
     * "Seven Minute", not "7-minute" and emphatically not "8-minute": the routine's own stored estimate
     * is 475 s ≈ 約八分, and the name and the arithmetic disagree **by design** — seven minutes is
     * ACSM's branding, and `BuiltInCatalog.kt:384-387` refuses to arrange the sums to agree with it. A
     * translation does not get to settle that argument either.
     */
    val routineSevenMinute: String

    val routineTabata: String
    val routineReconRon: String
    val routineCindy: String

    /**
     * シンディ（やさしい）.
     *
     * Composed of a proper noun and `ScalingTier.EASY`'s word, in full-width parentheses. English uses
     * ASCII parentheses and must use the **same word** the player's easy tier uses, or the library and
     * the launch dialog will name the same thing two ways.
     */
    val routineCindyScaled: String

    val routineChelsea: String
    val routineBarbara: String
    val routineMurph: String

    /** デス・バイ. The `・` is a katakana compound separator with no English equivalent; it goes. */
    val routineDeathBy: String

    // ─── §F.2–F.4 progression programmes — `progression_program.name_ja` / `.note_ja` ───────────

    val programReconRon: String

    /**
     * 週に一日は三分の一の回数で — the light-day rule, which is a note rather than a table row because
     * the player applies it as a session modifier (`BuiltInCatalog.kt:255-257`).
     */
    val programReconRonNote: String

    val programArmstrong: String
    val programFighter: String

    // ─── §F.3 Armstrong's five days — `progression_step.label_ja` / `.note_ja` ──────────────────
    //
    // Five labels rather than a `fmt` day-ordinal, and the reason is [armstrongDayFiveNote]: day five's
    // note *names* day four. A formatter that renumbered the series would break the cross-reference
    // silently. These are five fixed rows of one shipped programme, not a generated sequence — the
    // generated one is リーコン・ロン, whose 18 steps carry null labels and are numbered by the UI.

    val armstrongDayOne: String

    /** 全力五組 — five sets to failure. */
    val armstrongDayOneNote: String

    val armstrongDayTwo: String

    /** 段を上げて限界まで — the pyramid, climbed until it fails. */
    val armstrongDayTwoNote: String

    val armstrongDayThree: String

    /**
     * 順手・狭手・逆手 — the three grips of the rotation day.
     *
     * The words are a *label*; what is stored per set is the `SetVariant` enum
     * (`OVERHAND`/`CLOSE`/`REVERSE`), so translating this changes nothing on disk.
     */
    val armstrongDayThreeNote: String

    val armstrongDayFour: String

    /** 一番きつい日. */
    val armstrongDayFourNote: String

    val armstrongDayFive: String

    /** 第四日をもう一度 — **cross-references [armstrongDayFour]**. Keep the two agreeing. */
    val armstrongDayFiveNote: String

    // ─── §F.5 station notes — `routine_station.note`, 4 rows / 2 distinct ───────────────────────
    //
    // The picker cannot author one of these; it only carries an existing note across an edit
    // (`StationPickerScreen.kt:225-236`), so every note in the database is one of ours. They are also
    // **drawn nowhere in the app today** — see the migration report; マーフ's distance is currently
    // invisible. Translated anyway, because the column is real and the day a call site appears it must
    // not be the day someone notices.

    /** 種目は自由に — on タバタ and on デス・バイ, deliberately the same words on both. */
    val stationAnyMovement: String

    /** 一マイル — マーフ's two run legs. Distance is a note rather than a column (§F.5, §Q15). */
    val stationOneMile: String

    // ─── Resolution ─────────────────────────────────────────────────────────────────────────────
    //
    // Default implementations, so the id → member mapping exists exactly once and both languages
    // dispatch identically. A miss returns null and the caller renders the stored row, which is what
    // makes user-authored text pass through untouched.

    /** The cue for a seeded movement, or null for a movement this table does not know. */
    fun exerciseCue(exerciseId: String): String? = when (exerciseId) {
        "pushup" -> cuePushup
        "knee_pushup" -> cueKneePushup
        "pushup_rotation" -> cuePushupRotation
        "pullup" -> cuePullup
        "ring_row" -> cueRingRow
        "squat" -> cueSquat
        "wall_sit" -> cueWallSit
        "lunge" -> cueLunge
        "step_up" -> cueStepUp
        "situp" -> cueSitup
        "crunch" -> cueCrunch
        "plank" -> cuePlank
        "side_plank" -> cueSidePlank
        "dip" -> cueDip
        "burpee" -> cueBurpee
        "jumping_jack" -> cueJumpingJack
        "high_knees" -> cueHighKnees
        else -> null
    }

    /** The name of a **built-in** routine. Null for a user routine, whose name is the user's. */
    fun routineName(routineId: String): String? = when (routineId) {
        "r_seven_minute" -> routineSevenMinute
        "r_tabata" -> routineTabata
        "r_recon_ron" -> routineReconRon
        "r_cindy" -> routineCindy
        "r_cindy_scaled" -> routineCindyScaled
        "r_chelsea" -> routineChelsea
        "r_barbara" -> routineBarbara
        "r_murph" -> routineMurph
        "r_death_by" -> routineDeathBy
        else -> null
    }

    fun programName(programId: String): String? = when (programId) {
        "p_recon_ron" -> programReconRon
        "p_armstrong" -> programArmstrong
        "p_fighter" -> programFighter
        else -> null
    }

    fun programNote(programId: String): String? = when (programId) {
        "p_recon_ron" -> programReconRonNote
        else -> null
    }

    fun stepLabel(programId: String, stepIndex: Int): String? =
        if (programId != "p_armstrong") null else when (stepIndex) {
            1 -> armstrongDayOne
            2 -> armstrongDayTwo
            3 -> armstrongDayThree
            4 -> armstrongDayFour
            5 -> armstrongDayFive
            else -> null
        }

    fun stepNote(programId: String, stepIndex: Int): String? =
        if (programId != "p_armstrong") null else when (stepIndex) {
            1 -> armstrongDayOneNote
            2 -> armstrongDayTwoNote
            3 -> armstrongDayThreeNote
            4 -> armstrongDayFourNote
            5 -> armstrongDayFiveNote
            else -> null
        }

    /**
     * A station's note, by the routine it belongs to and the station's position.
     *
     * Keyed on the pair rather than on the note's text, because a note carried onto a user's forked
     * routine is the user's row and must render as they inherited it (§L10).
     */
    fun stationNote(routineId: String, position: Int): String? = when {
        routineId == "r_tabata" && position == 0 -> stationAnyMovement
        routineId == "r_death_by" && position == 0 -> stationAnyMovement
        routineId == "r_murph" && (position == 0 || position == 4) -> stationOneMile
        else -> null
    }
}

/**
 * Japanese — transcribed from `SeedCatalog`, character for character.
 *
 * Nothing here is authored. Every value is the literal `BuiltInCatalog.kt` writes into the row, so a
 * Japanese reader sees byte-identical text whether the lookup hits or falls through to the stored
 * column. `SeedTranslationTest` asserts that equality against the seed itself rather than against a
 * copy of it, which is what stops this file drifting the day someone corrects a cue.
 */
internal object JaCatalog : CatalogStrings {

    override val cuePushup = "体は一直線に"
    override val cueKneePushup = "腰を落とさない"
    override val cuePushupRotation = "上げた手を目で追う"
    override val cuePullup = "肩を下げてから引く"
    override val cueRingRow = "体は板のまま"
    override val cueSquat = "膝は爪先の向きに"
    override val cueWallSit = "膝は九十度"
    override val cueLunge = "前膝を爪先より前に出さない"
    override val cueStepUp = "足の裏全体で乗る"
    override val cueSitup = "反動を使わない"
    override val cueCrunch = "腰は床につけたまま"
    override val cuePlank = "肘は肩の真下に"
    override val cueSidePlank = "腰を落とさない"
    override val cueDip = "肘は後ろへ"
    override val cueBurpee = "着地は柔らかく"
    override val cueJumpingJack = "肩の力を抜く"
    override val cueHighKnees = "腿は腰の高さまで"

    override val routineSevenMinute = "七分間"
    override val routineTabata = "タバタ"
    override val routineReconRon = "リーコン・ロン"
    override val routineCindy = "シンディ"
    override val routineCindyScaled = "シンディ（やさしい）"
    override val routineChelsea = "チェルシー"
    override val routineBarbara = "バーバラ"
    override val routineMurph = "マーフ"
    override val routineDeathBy = "デス・バイ"

    override val programReconRon = "リーコン・ロン"
    override val programReconRonNote = "週に一日は三分の一の回数で"
    override val programArmstrong = "アームストロング"
    override val programFighter = "ファイター懸垂"

    override val armstrongDayOne = "第一日"
    override val armstrongDayOneNote = "全力五組"
    override val armstrongDayTwo = "第二日"
    override val armstrongDayTwoNote = "段を上げて限界まで"
    override val armstrongDayThree = "第三日"
    override val armstrongDayThreeNote = "順手・狭手・逆手"
    override val armstrongDayFour = "第四日"
    override val armstrongDayFourNote = "一番きつい日"
    override val armstrongDayFive = "第五日"
    override val armstrongDayFiveNote = "第四日をもう一度"

    override val stationAnyMovement = "種目は自由に"
    override val stationOneMile = "一マイル"
}

/**
 * English.
 *
 * Short, and imperative where the Japanese is. A form cue is glanced at between reps and spoken over a
 * phone on the floor; a sentence that explains twice is a worse cue in either language. Numerals are
 * arabic (§L7), including the ones the Japanese spells in kanji — 九十度 is `90 degrees`, 一マイル is
 * `One mile`.
 *
 * The six proper nouns are **not translations**. シンディ is a transliteration of Cindy, so Cindy is
 * the source text coming home rather than a rendering of the katakana; the same holds for Tabata,
 * Chelsea, Barbara, Murph and Recon Ron.
 */
internal object EnCatalog : CatalogStrings {

    override val cuePushup = "Body in a straight line"
    override val cueKneePushup = "Keep the hips up"
    override val cuePushupRotation = "Follow the raised hand with your eyes"
    override val cuePullup = "Set the shoulders down, then pull"
    override val cueRingRow = "Hold the body flat"
    override val cueSquat = "Knees track the toes"
    override val cueWallSit = "Knees at 90 degrees"
    override val cueLunge = "Front knee stays behind the toes"
    override val cueStepUp = "Step up with the whole foot"
    override val cueSitup = "No momentum"
    override val cueCrunch = "Keep the lower back on the floor"
    override val cuePlank = "Elbows under the shoulders"
    override val cueSidePlank = "Keep the hips up"
    override val cueDip = "Elbows point back"
    override val cueBurpee = "Land softly"
    override val cueJumpingJack = "Loose shoulders"
    override val cueHighKnees = "Thighs to hip height"

    override val routineSevenMinute = "Seven Minute"
    override val routineTabata = "Tabata"
    override val routineReconRon = "Recon Ron"
    override val routineCindy = "Cindy"
    override val routineCindyScaled = "Cindy (Easy)"
    override val routineChelsea = "Chelsea"
    override val routineBarbara = "Barbara"
    override val routineMurph = "Murph"
    override val routineDeathBy = "Death By"

    override val programReconRon = "Recon Ron"
    override val programReconRonNote = "One day a week, a third of the reps"
    override val programArmstrong = "Armstrong"
    override val programFighter = "Fighter Pull-Up"

    override val armstrongDayOne = "Day 1"
    override val armstrongDayOneNote = "Five all-out sets"
    override val armstrongDayTwo = "Day 2"
    override val armstrongDayTwoNote = "Climb the ladder to failure"
    override val armstrongDayThree = "Day 3"
    override val armstrongDayThreeNote = "Overhand, close, reverse"
    override val armstrongDayFour = "Day 4"
    override val armstrongDayFourNote = "The hardest day"
    override val armstrongDayFive = "Day 5"
    override val armstrongDayFiveNote = "Day 4 again"

    override val stationAnyMovement = "Any movement"
    override val stationOneMile = "One mile"
}
