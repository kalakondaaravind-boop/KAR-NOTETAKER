package app.margin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.margin.R

/** Company name as it appears in the logo. A brand name, so deliberately not a translatable string. */
private const val BRAND_PRIMARY = "KAR"
private const val BRAND_SECONDARY = "GROUP"

/**
 * KAR Group logo lockup: the mark and "KAR GROUP" wordmark on one line, with the "Note taker" tagline below.
 * Announced to TalkBack as a single element ("KAR Group, Note taker").
 *
 * @param markSize height of the mark; the wordmark and tagline scale with it.
 * @param centered centre the tagline under the whole lockup (onboarding, drawer) instead of aligning it with the wordmark.
 */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, markSize: Dp = 40.dp, centered: Boolean = false) {
    val description = stringResource(R.string.brand_logo_desc)
    val gap = markSize * 0.25f
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(markSize * 0.08f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.kar_logo_mark), contentDescription = null, modifier = Modifier.size(markSize))
            Spacer(Modifier.width(gap))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 0.03.em)) { append(BRAND_PRIMARY) }
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.09.em)) { append(BRAND_SECONDARY) }
                },
                fontSize = (markSize.value * 0.6f).sp,
                maxLines = 1,
            )
        }
        Text(
            stringResource(R.string.brand_tagline),
            modifier = if (centered) Modifier else Modifier.padding(start = markSize + gap),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = (markSize.value * 0.32f).coerceAtLeast(12f).sp, letterSpacing = 0.12.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
