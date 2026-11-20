package com.personalization.features.search

import com.personalization.api.models.search.AgentDialogResponse
import com.personalization.api.models.search.AgentMessage
import com.personalization.api.models.search.AgentMessageRole
import com.personalization.api.models.search.AgentProduct
import com.personalization.api.models.search.AgentResponse
import com.personalization.api.models.search.AgentSession
import com.personalization.core.internal.arrayOrNull
import com.personalization.core.internal.doubleOrNull
import com.personalization.core.internal.longOrNull
import com.personalization.core.internal.mapObjects
import com.personalization.core.internal.nonBlankStringOrNull
import com.personalization.core.internal.objectOrNull
import com.personalization.core.internal.stringOrNull
import org.json.JSONObject

/**
 * Reading the search agent's answers (`api/v3/storefront/search/agent`) into the `Agent*` models,
 * tolerantly: a session or a product without its id is left out, a message without one is kept with a null
 * id, a product without a reason has an empty one, and a role the SDK does not know reads as it is.
 */
internal object AgentJson {

    /** The sessions and the chosen one; an answer that is not an object is null (`decoding`). */
    fun dialog(body: Any?): AgentDialogResponse? {
        if (body !is JSONObject) return null
        return AgentDialogResponse(
            sessions = body.arrayOrNull("sessions")?.mapObjects(::session).orEmpty(),
            session = body.objectOrNull("session")?.let(::session),
        )
    }

    /** The session and the agent's answer; null (`decoding`) without either object, or a session without its id. */
    fun answer(body: Any?): AgentResponse? {
        if (body !is JSONObject) return null
        val session = body.objectOrNull("session")?.let(::session) ?: return null
        val message = body.objectOrNull("message")?.let(::message) ?: return null
        return AgentResponse(session = session, message = message)
    }

    private fun session(json: JSONObject): AgentSession? {
        val id = json.longOrNull("id") ?: return null
        return AgentSession(
            id = id,
            title = json.stringOrNull("title"),
            updatedAt = json.stringOrNull("updated_at"),
            messages = json.arrayOrNull("messages")?.mapObjects(::message).orEmpty(),
        )
    }

    /** Never null: a message without its id is still the agent's answer, kept with a null id. */
    private fun message(json: JSONObject): AgentMessage =
        AgentMessage(
            id = json.longOrNull("id"),
            role = json.nonBlankStringOrNull("role")?.let(::AgentMessageRole),
            content = json.stringOrNull("content"),
            createdAt = json.stringOrNull("created_at"),
            products = json.arrayOrNull("products")?.mapObjects(::product).orEmpty(),
        )

    private fun product(json: JSONObject): AgentProduct? {
        val id = json.nonBlankStringOrNull("id") ?: return null
        return AgentProduct(
            id = id,
            name = json.stringOrNull("name"),
            price = json.doubleOrNull("price"),
            // The server sends 0.0 for a product without an old price.
            oldPrice = json.doubleOrNull("oldprice")?.takeIf { it != 0.0 },
            priceFormatted = json.stringOrNull("price_formatted"),
            oldPriceFormatted = json.stringOrNull("oldprice_formatted"),
            picture = json.stringOrNull("picture"),
            url = json.stringOrNull("url"),
            reason = json.stringOrNull("reason").orEmpty(),
        )
    }
}
