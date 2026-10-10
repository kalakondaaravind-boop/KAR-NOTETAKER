package app.margin.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.margin.R
import app.margin.domain.text.NoteFont

/** Bundled fonts (SIL Open Font License, texts in docs/licenses/fonts) and system families. Offline, no downloads. */
object NoteFonts {
    private val caveat = FontFamily(Font(R.font.caveat_regular), Font(R.font.caveat_bold, FontWeight.Bold))
    private val patrickHand = FontFamily(Font(R.font.patrick_hand_regular))
    private val dancingScript = FontFamily(Font(R.font.dancing_script_regular), Font(R.font.dancing_script_bold, FontWeight.Bold))
    private val lora = FontFamily(
        Font(R.font.lora_regular), Font(R.font.lora_bold, FontWeight.Bold),
        Font(R.font.lora_italic, style = FontStyle.Italic), Font(R.font.lora_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    private val montserrat = FontFamily(
        Font(R.font.montserrat_regular), Font(R.font.montserrat_bold, FontWeight.Bold),
        Font(R.font.montserrat_italic, style = FontStyle.Italic), Font(R.font.montserrat_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    private val playfair = FontFamily(
        Font(R.font.playfair_display_regular), Font(R.font.playfair_display_bold, FontWeight.Bold),
        Font(R.font.playfair_display_italic, style = FontStyle.Italic), Font(R.font.playfair_display_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

    fun family(font: NoteFont?): FontFamily = when (font) {
        null, NoteFont.SANS -> FontFamily.SansSerif
        NoteFont.SERIF -> FontFamily.Serif
        NoteFont.MONO -> FontFamily.Monospace
        NoteFont.CAVEAT -> caveat
        NoteFont.PATRICK_HAND -> patrickHand
        NoteFont.DANCING_SCRIPT -> dancingScript
        NoteFont.LORA -> lora
        NoteFont.MONTSERRAT -> montserrat
        NoteFont.PLAYFAIR -> playfair
    }

    fun family(id: String?): FontFamily = family(NoteFont.from(id))
}
