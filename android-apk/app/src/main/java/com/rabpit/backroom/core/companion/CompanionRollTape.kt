package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Native, versioned, bounded ordered tape. The caller MUST inject the existing
 * production bounded draw function; this class NEVER constructs or seeds an RNG.
 *
 * Replay has no generator parameter. Absent, extra, reordered, mistyped or
 * different-bound draws fail closed. The route record is checked against the
 * existing FeaturedJourneyRoutes graph; Core snapshot, lock and SQLite provenance
 * remain external mandatory checks. This seam is not the full live-game capture.
 */
object CompanionRollTape {
  private const val VERSION = "companion_roll_tape.v1"
  private const val MAX_DRAWS = 128
  private const val MAX_BYTES = 131072
  private val stopId = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

  enum class Purpose {
    EXIT_STREAK,
    AN_NHIEN_ENCOUNTER, SURVIVOR, IRIS_REUNION, SYVIAL_REUNION,
    LUCIA_ENCOUNTER, LUC_TRAM_ENCOUNTER, AN_NHIEN_HAZARD_CHECK,
    HAZARD, DIEP_MINH_ENCOUNTER, ENTITY_ENCOUNTER, ROAMING_ENTITY_KEY,
    LOOT, MAD_GOD_SET, ALMOND_WATER, LEVEL_BOUND_ENTITY, COMBAT_INITIAL
  }

  data class Route(
    val sourceLevel: Int, val sourceStop: String,
    val targetLevel: Int, val targetStop: String, val completed: Boolean
  )

  data class Draw(val purpose: Purpose, val bound: Int, val value: Int)

  class Tape internal constructor(draws: List<Draw>, val route: Route) {
    val draws: List<Draw> = Collections.unmodifiableList(ArrayList(draws))

    fun encode(): String {
      val lines = ArrayList<String>(draws.size + 3)
      lines.add(VERSION)
      lines.add(draws.size.toString())
      draws.forEach { lines.add(it.purpose.name + "|" + it.bound + "|" + it.value) }
      lines.add("route|" + route.sourceLevel + "|" + route.sourceStop + "|" +
        route.targetLevel + "|" + route.targetStop + "|" + if (route.completed) "1" else "0")
      val result = lines.joinToString("\n")
      require(result.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "tape_size_exceeded" }
      return result
    }

    fun replay() = Replay(this)
  }

  class Capture(private val nativeNextInt: (Int) -> Int) {
    private val recorded = ArrayList<Draw>()
    private var sealed = false

    fun next(purpose: Purpose, bound: Int): Int {
      check(!sealed) { "capture_already_sealed" }
      require(bound > 0) { "draw_bound_invalid" }
      require(recorded.size < MAX_DRAWS) { "tape_draw_limit" }
      val value = nativeNextInt(bound)
      require(value in 0 until bound) { "native_draw_out_of_range" }
      record(purpose, bound, value)
      return value
    }

    /** Native capture adapter records an outcome from an existing non-GAME_RNG scope. */
    internal fun record(purpose: Purpose, bound: Int, value: Int) {
      check(!sealed) { "capture_already_sealed" }
      require(bound > 0 && value in 0 until bound) { "native_draw_out_of_range" }
      require(recorded.size < MAX_DRAWS) { "tape_draw_limit" }
      recorded.add(Draw(purpose, bound, value))
    }

    fun seal(route: Route): Tape {
      check(!sealed) { "capture_already_sealed" }
      verifyRoute(route)
      val tape = Tape(recorded, route)
      tape.encode() // Check the serialized storage bound before sealing/exposing.
      sealed = true
      return tape
    }
  }

  class Replay internal constructor(private val tape: Tape) {
    private var position = 0

    fun next(purpose: Purpose, bound: Int): Int {
      require(bound > 0) { "draw_bound_invalid" }
      check(position < tape.draws.size) { "missing_reserved_draw" }
      val stored = tape.draws[position]
      check(stored.purpose == purpose) { "draw_purpose_or_order_mismatch" }
      check(stored.bound == bound) { "draw_bound_mismatch" }
      position++
      return stored.value
    }

    fun finish() {
      check(position == tape.draws.size) { "unused_reserved_draws" }
    }
  }

  fun decode(encoded: String): Tape {
    require(encoded.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "tape_size_exceeded" }
    val lines = encoded.split('\n')
    require(lines.size >= 3 && lines[0] == VERSION) { "tape_version_invalid" }
    val count = lines[1].toIntOrNull() ?: throw IllegalArgumentException("tape_count_invalid")
    require(count in 0..MAX_DRAWS && lines.size == count + 3) { "tape_length_invalid" }
    val draws = ArrayList<Draw>(count)
    for (index in 0 until count) {
      val fields = lines[index + 2].split('|')
      require(fields.size == 3) { "draw_fields_invalid" }
      val purpose = enumValues<Purpose>().firstOrNull { it.name == fields[0] }
        ?: throw IllegalArgumentException("draw_purpose_invalid")
      val bound = fields[1].toIntOrNull() ?: throw IllegalArgumentException("draw_bound_invalid")
      val value = fields[2].toIntOrNull() ?: throw IllegalArgumentException("draw_value_invalid")
      require(bound > 0 && value in 0 until bound) { "draw_value_out_of_range" }
      draws.add(Draw(purpose, bound, value))
    }
    val routeFields = lines[count + 2].split('|')
    require(routeFields.size == 6 && routeFields[0] == "route" &&
      routeFields[5] in listOf("0", "1")) { "tape_route_invalid" }
    val route = Route(
      routeFields[1].toIntOrNull() ?: throw IllegalArgumentException("route_level_invalid"),
      routeFields[2],
      routeFields[3].toIntOrNull() ?: throw IllegalArgumentException("route_level_invalid"),
      routeFields[4],
      routeFields[5] == "1"
    )
    verifyRoute(route)
    val result = Tape(draws, route)
    require(result.encode() == encoded) { "tape_noncanonical" }
    return result
  }

  private fun verifyRoute(route: Route) {
    require(route.sourceStop.matches(stopId) && route.targetStop.matches(stopId)) {
      "route_stop_invalid"
    }
    val nativeLevel = FeaturedJourneyRoutes.stopLevelNumber(route.sourceStop)
    require(nativeLevel != null && nativeLevel == route.sourceLevel) { "route_source_invalid" }
    if (route.completed) {
      val native = FeaturedJourneyRoutes.next(route.sourceStop)
      require(native != null && native.targetStopKey == route.targetStop &&
        native.targetLevelNumber == route.targetLevel) { "route_target_invalid" }
    } else {
      require(route.targetLevel == route.sourceLevel && route.targetStop == route.sourceStop) {
        "route_uncompleted_transition_invalid"
      }
    }
  }
}
