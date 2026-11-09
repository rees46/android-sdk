package com.personalization

import com.personalization.api.responses.initialization.SdkInitializationResponse
import com.personalization.popups.PopupsV3
import com.personalization.ui.widgets.PersonalizationStories
import com.personalization.ui.widgets.PersonalizationStoriesViewer
import com.personalization.ui.widgets.StoriesPollAnswer
import com.personalization.ui.widgets.StoriesPollResults
import com.personalization.ui.widgets.StoriesPollsResult
import com.personalization.ui.widgets.StoriesPollsTransport
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The v3 gate as Java sees it. Java ignores opt-in, so every way into v3 from the SDK, the stories
 * widget and its viewer is `@JvmSynthetic`: a public, non-synthetic method that names v3 or takes a
 * v3 type would let a Java host in. Internal members (mangled `name$module`) are not API.
 */
class V3GateTest {

    @Test
    fun `java sees no v3 member on the SDK, the stories widget and its viewer`() {
        val leaks = listOf(
            SDK::class.java,
            SdkInitializationResponse::class.java,
            PersonalizationStories::class.java,
            PersonalizationStories.Companion::class.java,
            PersonalizationStoriesViewer::class.java
        ).flatMap { type ->
            type.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && '$' !in it.name }
                .filter { method ->
                    V3_NAME.containsMatchIn(method.name) ||
                        method.returnType in V3_TYPES || method.parameterTypes.any { it in V3_TYPES }
                }
                .map { "${type.name}.${it.name}(${it.parameterCount})" }
        }
        assertEquals(emptyList<String>(), leaks)
    }

    @Test
    fun `the kit show is still there for java, without v3 parameters`() {
        val shows = PersonalizationStories::class.java.declaredMethods
            .filter { it.name == "show" && Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        // @JvmOverloads: context and code, then one more parameter at a time up to soundOffLabel.
        assertEquals((2..18).toList(), shows.map { it.parameterCount }.sorted())
    }

    private companion object {
        val V3_NAME = Regex("(?i)v3|poll|share|handleLink|popups$")

        val V3_TYPES = setOf(
            PopupsV3::class.java,
            StoriesPollsTransport::class.java,
            StoriesPollsResult::class.java,
            StoriesPollAnswer::class.java,
            StoriesPollResults::class.java
        )
    }
}
