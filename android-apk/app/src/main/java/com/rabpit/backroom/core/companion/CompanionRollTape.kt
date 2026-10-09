package com.rabpit.backroom.core.companion

/** Bounded native roll-tape seam. Unimplemented methods fail closed. */
object CompanionRollTape {
  enum class Purpose { EXIT_STREAK, SURVIVOR, ENTITY_ENCOUNTER, LEVEL_BOUND_ENTITY }
  data class Route(val sourceLevel: Int, val sourceStop: String,
                   val targetLevel: Int, val targetStop: String, val completed: Boolean)
  data class Draw(val purpose: Purpose, val bound: Int, val value: Int)
  class Tape internal constructor(val draws: List<Draw>, val route: Route) {
    fun encode(): String = throw UnsupportedOperationException("tape_not_implemented")
    fun replay(): Replay = throw UnsupportedOperationException("tape_not_implemented")
  }
  class Capture(private val nativeNextInt: (Int) -> Int) {
    fun next(purpose: Purpose, bound: Int): Int =
      throw UnsupportedOperationException("tape_not_implemented")
    fun seal(route: Route): Tape = throw UnsupportedOperationException("tape_not_implemented")
  }
  class Replay internal constructor(private val tape: Tape) {
    fun next(purpose: Purpose, bound: Int): Int =
      throw UnsupportedOperationException("tape_not_implemented")
    fun finish() { throw UnsupportedOperationException("tape_not_implemented") }
  }
  fun decode(encoded: String): Tape = throw UnsupportedOperationException("tape_not_implemented")
}
