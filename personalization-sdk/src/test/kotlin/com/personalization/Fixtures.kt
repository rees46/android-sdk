package com.personalization

/** Live API answers saved under `src/test/resources/fixtures`. */
internal object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource("fixtures/$name")) { "no fixture $name" }
            .readText()
}
