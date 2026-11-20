package com.personalization.api.models.search

/**
 * The customer's dialog with the search agent (`sdk.search.agentDialog`).
 *
 * @property sessions the customer's last 20 sessions, the latest first, without their messages.
 * @property session the chosen session with its messages; null while the customer has none.
 */
data class AgentDialogResponse(
    val sessions: List<AgentSession> = emptyList(),
    val session: AgentSession? = null,
)

/**
 * The agent's answer to the customer's message (`sdk.search.agent`).
 *
 * @property session the session the message went to (a new one without a session id), without its messages.
 * @property message the agent's answer.
 */
data class AgentResponse(
    val session: AgentSession,
    val message: AgentMessage,
)

/**
 * A session of the dialog; [id] is 64-bit, as the server's ids of sessions, and is what `agentDialog` and
 * `agent` take as `sessionId`.
 *
 * @property title the start of the customer's first message.
 * @property updatedAt when the last message came, ISO 8601.
 * @property messages empty in the list of sessions and in [AgentResponse].
 */
data class AgentSession(
    val id: Long,
    val title: String? = null,
    val updatedAt: String? = null,
    val messages: List<AgentMessage> = emptyList(),
)

/**
 * A message of the dialog; [id] is 64-bit, as the server's ids of messages.
 *
 * @property id null for a message the server sent without its id: the message is kept, not left out.
 * @property createdAt ISO 8601.
 * @property products the products the agent offered; empty in the customer's messages.
 */
data class AgentMessage(
    val id: Long?,
    val role: AgentMessageRole? = null,
    val content: String? = null,
    val createdAt: String? = null,
    val products: List<AgentProduct> = emptyList(),
)

/**
 * Who wrote a message. An open set: a role the server adds later reads as it is, `AgentMessageRole("…")`.
 */
@JvmInline
value class AgentMessageRole(val value: String) {
    companion object {
        /** The customer. */
        val USER = AgentMessageRole("user")

        /** The agent. */
        val ASSISTANT = AgentMessageRole("assistant")
    }
}

/**
 * A product the agent offered: not a `Product`, a short set of fields and why it fits.
 *
 * @property price in the customer's location, when the server knows it.
 * @property oldPrice null, not 0, without an old price.
 * @property url the product's link with the agent's attribution (`recommended_by=agent_search`); null for a
 * product without an absolute http(s) link.
 * @property reason why the product fits the request; empty without an explanation.
 */
data class AgentProduct(
    val id: String,
    val name: String? = null,
    val price: Double? = null,
    val oldPrice: Double? = null,
    val priceFormatted: String? = null,
    val oldPriceFormatted: String? = null,
    val picture: String? = null,
    val url: String? = null,
    val reason: String = "",
)
