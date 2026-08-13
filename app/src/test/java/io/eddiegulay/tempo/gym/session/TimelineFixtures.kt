package io.eddiegulay.tempo.gym.session

import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation

/**
 * Routines the compiler tests are written against.
 *
 * They are shaped like the seeded ones — 七分間's thirty-on / ten-off, Barbara's three minutes, an
 * EMOM's minute — because a timeline that is only ever tested against round numbers hides exactly the
 * off-by-one the real catalogue would find. No fixture invents a *prescription*; the durations here
 * are the ones the built-in routines carry.
 */
internal fun exercise(
    id: String,
    secondsPerRep: Double = 2.0,
    isometric: Boolean = false,
): Exercise = Exercise(
    id = id,
    nameJa = id,
    nameEn = id,
    pattern = Pattern.CORE,
    secondsPerRep = secondsPerRep,
    difficulty = 1.0,
    isIsometric = isometric,
    cue = null,
    ladderId = null,
    catalogVersion = 1,
    archived = false,
)

internal fun lib(vararg ex: Exercise): Map<String, Exercise> = ex.associateBy { it.id }

internal fun station(
    position: Int,
    exerciseId: String,
    seconds: Int? = null,
    reps: Int? = null,
): RoutineStation = RoutineStation(
    position = position,
    exerciseId = exerciseId,
    exercise = null,
    measure = when {
        seconds != null -> Measure.DURATION
        reps != null -> Measure.REPS
        else -> Measure.MAX_EFFORT
    },
    prescribedReps = reps,
    prescribedSeconds = seconds,
    note = null,
)

internal fun snapshot(
    engine: Engine,
    stations: List<RoutineStation>,
    rounds: Int? = 1,
    prepareSeconds: Int = 5,
    restBetweenStations: Int = 0,
    restBetweenRounds: Int = 0,
    timeCapSeconds: Int? = null,
    intervalSeconds: Int? = null,
    routineId: String = "r1",
): RoutineSnapshot = RoutineSnapshot(
    versionId = 1L,
    routineId = routineId,
    versionNumber = 1,
    name = "型",
    engine = engine,
    rounds = rounds,
    timeCapSeconds = timeCapSeconds,
    intervalSeconds = intervalSeconds,
    restBetweenStations = restBetweenStations,
    restBetweenRounds = restBetweenRounds,
    prepareSeconds = prepareSeconds,
    progressionProgramId = null,
    primaryMetric = BestMetric.BEST_TIME,
    stationCount = stations.size,
    estimatedDurationSeconds = 0,
    estimatedTotalReps = 0,
    structuralHash = 0,
    createdAt = 0L,
    stations = stations,
)

/** 七分間's shape at two stations: thirty seconds on, ten off, five to get ready. */
internal fun circuit(rounds: Int = 1, prepareSeconds: Int = 5): RoutineSnapshot = snapshot(
    engine = Engine.INTERVAL_CIRCUIT,
    stations = listOf(station(0, "a", seconds = 30), station(1, "b", seconds = 30)),
    rounds = rounds,
    prepareSeconds = prepareSeconds,
    restBetweenStations = 10,
    restBetweenRounds = 60,
)

internal val circuitLib = lib(exercise("a"), exercise("b"))

internal fun compiledCircuit(rounds: Int = 1, prepareSeconds: Int = 5): Timeline =
    compile(circuit(rounds, prepareSeconds), ScalingTier.RX, circuitLib)

/** One station, one minute, three rounds — the smallest thing that can prove a grid does not slide. */
internal fun emom(rounds: Int = 3, ascending: Boolean = false, stations: Int = 1): Timeline = compile(
    snapshot(
        engine = if (ascending) Engine.EMOM_ASCENDING else Engine.EMOM,
        stations = (0 until stations).map { station(it, "a", reps = 10) },
        rounds = rounds,
        prepareSeconds = 0,
        intervalSeconds = 60,
    ),
    ScalingTier.RX,
    circuitLib,
)
