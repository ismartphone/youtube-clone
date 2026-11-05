package com.youtubeclone.app.presentation.help

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.R
import com.youtubeclone.app.presentation.common.BackTopBar

/** Une question fréquente et sa réponse. */
private data class Faq(@StringRes val question: Int, @StringRes val answer: Int)

/** Un sujet de l'aide et ses questions. */
private data class HelpTopic(@StringRes val title: Int, val questions: List<Faq>)

// Textes dans les ressources : l'aide s'affiche sans réseau (ADR-019). Ils ne décrivent que ce
// qui existe ; à tenir à jour si le comportement de l'application change.
private val TOPICS = listOf(
    HelpTopic(
        R.string.help_topic_publish,
        listOf(
            Faq(R.string.help_publish_video_q, R.string.help_publish_video_a),
            Faq(R.string.help_publish_settings_q, R.string.help_publish_settings_a),
            Faq(R.string.help_publish_post_q, R.string.help_publish_post_a),
            Faq(R.string.help_customize_q, R.string.help_customize_a),
        ),
    ),
    HelpTopic(
        R.string.help_topic_premium,
        listOf(
            Faq(R.string.help_premium_what_q, R.string.help_premium_what_a),
            Faq(R.string.help_premium_renewal_q, R.string.help_premium_renewal_a),
            Faq(R.string.help_downloads_q, R.string.help_downloads_a),
        ),
    ),
    HelpTopic(
        R.string.help_topic_subscriptions,
        listOf(
            Faq(R.string.help_follow_vs_premium_q, R.string.help_follow_vs_premium_a),
            Faq(R.string.help_follow_where_q, R.string.help_follow_where_a),
            Faq(R.string.help_notifications_q, R.string.help_notifications_a),
        ),
    ),
    // Comptes enregistrés sur l'appareil (ADR-022).
    HelpTopic(
        R.string.help_topic_accounts,
        listOf(
            Faq(R.string.help_accounts_switch_q, R.string.help_accounts_switch_a),
            Faq(R.string.help_accounts_logout_q, R.string.help_accounts_logout_a),
        ),
    ),
    HelpTopic(
        R.string.help_topic_subtitles,
        listOf(
            Faq(R.string.help_subtitles_how_q, R.string.help_subtitles_how_a),
            Faq(R.string.help_subtitles_missing_q, R.string.help_subtitles_missing_a),
        ),
    ),
    HelpTopic(
        R.string.help_topic_player,
        listOf(
            Faq(R.string.help_mini_player_q, R.string.help_mini_player_a),
        ),
    ),
)

/** Aide, ouverte depuis Vous : questions fréquentes par sujet, la réponse se déplie au toucher. */
@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BackTopBar(title = stringResource(R.string.you_help), onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f)) {
            TOPICS.forEach { topic ->
                item(key = topic.title) {
                    Text(
                        text = stringResource(topic.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                    )
                }
                items(topic.questions, key = { it.question }) { faq -> FaqItem(faq) }
            }
        }
    }
}

@Composable
private fun FaqItem(faq: Faq) {
    var expanded by rememberSaveable(faq.question) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(faq.question),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Text(
                text = stringResource(faq.answer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
