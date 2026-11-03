package com.personalization.demo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalization.stories.models.Story
import com.personalization.ui.widgets.PersonalizationStoriesViewer
import com.personalization.ui.widgets.StoriesPollResults
import com.personalization.ui.widgets.StoriesPollsResult
import com.personalization.ui.widgets.StoriesPollsTransport
import org.json.JSONObject

/**
 * "Stories V2 polls (offline)": the campaign of `assets/uikit/stories_polls.json` — an API v3 feed
 * with every poll kind (single and multiple choice, text, NPS, CSAT, CES, an answered one and an
 * unknown kind that is not drawn) — in the V2 viewer over [OfflinePollsTransport]. No request leaves
 * the device: answers come back in 600 ms, mini-polls with `show_results` get percents, the first
 * answer of `demo_text` fails so the error and the retry can be seen.
 */
@Composable
internal fun StoriesV2PollsExhibit() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Stories V2 polls — every poll kind over a fake transport: 600 ms answers, results, " +
                "the first text answer fails",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        OutlinedButton(onClick = { openOfflinePolls(context) }) { Text("Stories V2 polls (offline)") }
    }
}

private fun openOfflinePolls(context: Context) {
    val json = context.assets.open("uikit/stories_polls.json").bufferedReader().use { it.readText() }
    val array = JSONObject(json).getJSONObject("payload").getJSONArray("stories")
    val stories = List(array.length()) { Story(array.getJSONObject(it)) }
    PersonalizationStoriesViewer(context, stories).apply {
        onClickListener = DemoStoriesClicks(context)
        // With its own transport the viewer draws polls without an API v3 host.
        pollsTransport = OfflinePollsTransport(OfflinePollsTransport.miniPolls(array))
        show()
    }
}

/**
 * The fake transport of the offline demo: every answer comes back in [DELAY_MS]. Mini-polls with
 * `show_results` ([miniPolls]: code → option ids) get results — the chosen options share 70%, the
 * rest 30%, 100 in all. The first answer of `demo_text` fails, the next ones succeed.
 */
internal class OfflinePollsTransport(private val miniPolls: Map<String, List<Int>>) : StoriesPollsTransport {

    private val handler = Handler(Looper.getMainLooper())
    private var textFailed = false

    override fun answerMiniPoll(code: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
        val result = if (code == FAILING_POLL && !textFailed) {
            textFailed = true
            StoriesPollsResult.Failure(500, "offline demo: the first answer of $code fails")
        } else {
            val chosen = (body["option_ids"] as? List<*>).orEmpty().filterIsInstance<Int>()
            StoriesPollsResult.Success(results = miniPolls[code]?.let { results(it, chosen) })
        }
        handler.postDelayed({ callback(result) }, DELAY_MS)
    }

    override fun review(kind: String, body: Map<String, Any>, callback: (StoriesPollsResult) -> Unit) {
        handler.postDelayed({ callback(StoriesPollsResult.Success()) }, DELAY_MS)
    }

    private fun results(options: List<Int>, chosen: List<Int>): StoriesPollResults {
        val picked = options.filter { it in chosen }
        val rest = options - picked.toSet()
        val percents = if (rest.isEmpty() || picked.isEmpty()) {
            split(options, 100)
        } else {
            split(picked, CHOSEN_SHARE) + split(rest, 100 - CHOSEN_SHARE)
        }
        return StoriesPollResults(
            total = 120 + chosen.size,
            options = options.map { StoriesPollResults.Option(it, percents.getValue(it).toDouble()) }
        )
    }

    /** [total] split between [ids] in whole percents, the remainder to the first ones. */
    private fun split(ids: List<Int>, total: Int): Map<Int, Int> =
        ids.mapIndexed { index, id -> id to total / ids.size + if (index < total % ids.size) 1 else 0 }.toMap()

    companion object {
        private const val DELAY_MS = 600L
        private const val CHOSEN_SHARE = 70
        private const val FAILING_POLL = "demo_text"

        /** Mini-polls with `show_results` of a v3 `stories` array: code → option ids. */
        fun miniPolls(stories: org.json.JSONArray): Map<String, List<Int>> {
            val polls = LinkedHashMap<String, List<Int>>()
            for (s in 0 until stories.length()) {
                val slides = stories.getJSONObject(s).optJSONArray("slides") ?: continue
                for (i in 0 until slides.length()) {
                    val elements = slides.getJSONObject(i).optJSONArray("elements") ?: continue
                    for (e in 0 until elements.length()) {
                        val element = elements.getJSONObject(e)
                        val poll = element.optJSONObject("poll") ?: continue
                        if (element.optString("poll_type") != "mini_poll" || !poll.optBoolean("show_results")) continue
                        val options = poll.optJSONArray("options") ?: continue
                        polls[poll.optString("code")] = List(options.length()) { options.getJSONObject(it).optInt("id") }
                    }
                }
            }
            return polls
        }
    }
}
