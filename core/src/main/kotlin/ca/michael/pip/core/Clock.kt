package ca.michael.pip.core

/** Source of the current time in Unix epoch milliseconds. Injectable for tests. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
