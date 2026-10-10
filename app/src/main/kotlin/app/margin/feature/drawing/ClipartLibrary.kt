package app.margin.feature.drawing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Clipart for drawings and notes: single-colour vector pictures (Material Symbols, Apache 2.0, bundled with
 * the app, no network). Stored in drawings by [key], recoloured and resized freely.
 */
object ClipartLibrary {
    data class Item(val key: String, val label: String, val icon: ImageVector)
    data class Category(val name: String, val items: List<Item>)

    private fun i(key: String, label: String, icon: ImageVector) = Item(key, label, icon)

    val categories: List<Category> = listOf(
        Category("Shapes & marks", listOf(
            i("star", "Star", Icons.Filled.Star), i("heart", "Heart", Icons.Filled.Favorite), i("check", "Tick", Icons.Filled.Check),
            i("check-circle", "Done", Icons.Filled.CheckCircle), i("cross", "Cross", Icons.Filled.Close), i("cancel", "Cancel", Icons.Filled.Cancel),
            i("warning", "Warning", Icons.Filled.Warning), i("info", "Info", Icons.Filled.Info), i("flag", "Flag", Icons.Filled.Flag),
            i("pin", "Pin", Icons.Filled.PushPin), i("bookmark", "Bookmark", Icons.Filled.Bookmark), i("diamond", "Diamond", Icons.Filled.Diamond),
            i("arrow-right", "Arrow right", Icons.AutoMirrored.Filled.ArrowForward), i("arrow-left", "Arrow left", Icons.AutoMirrored.Filled.ArrowBack),
            i("arrow-up", "Arrow up", Icons.Filled.ArrowUpward), i("arrow-down", "Arrow down", Icons.Filled.ArrowDownward),
        )),
        Category("Work & study", listOf(
            i("lightbulb", "Idea", Icons.Filled.Lightbulb), i("event", "Calendar", Icons.Filled.Event), i("schedule", "Clock", Icons.Filled.Schedule),
            i("alarm", "Alarm", Icons.Filled.Alarm), i("timer", "Timer", Icons.Filled.Timer), i("hourglass", "Hourglass", Icons.Filled.HourglassEmpty),
            i("work", "Briefcase", Icons.Filled.Work), i("school", "School", Icons.Filled.School), i("book", "Book", Icons.AutoMirrored.Filled.MenuBook),
            i("assignment", "Clipboard", Icons.Filled.Assignment), i("description", "Document", Icons.Filled.Description), i("edit", "Pencil", Icons.Filled.Edit),
            i("draw", "Pen", Icons.Filled.Draw), i("brush", "Brush", Icons.Filled.Brush), i("palette", "Palette", Icons.Filled.Palette),
            i("scissors", "Scissors", Icons.Filled.ContentCut), i("calculate", "Calculator", Icons.Filled.Calculate), i("science", "Science", Icons.Filled.Science),
            i("psychology", "Brain", Icons.Filled.Psychology), i("search", "Search", Icons.Filled.Search), i("lock", "Lock", Icons.Filled.Lock),
            i("key", "Key", Icons.Filled.Key), i("gavel", "Gavel", Icons.Filled.Gavel), i("inventory", "Box", Icons.Filled.Inventory),
        )),
        Category("Business", listOf(
            i("chart-bar", "Bar chart", Icons.Filled.BarChart), i("chart-pie", "Pie chart", Icons.Filled.PieChart), i("trending-up", "Trending up", Icons.AutoMirrored.Filled.TrendingUp),
            i("money", "Money", Icons.Filled.AttachMoney), i("rupee", "Rupee", Icons.Filled.CurrencyRupee), i("savings", "Savings", Icons.Filled.Savings),
            i("cart", "Shopping cart", Icons.Filled.ShoppingCart), i("store", "Shop", Icons.Filled.Store), i("handshake", "Handshake", Icons.Filled.Handshake),
            i("trophy", "Trophy", Icons.Filled.EmojiEvents), i("rocket", "Rocket", Icons.Filled.RocketLaunch), i("factory", "Factory", Icons.Filled.Factory),
            i("apartment", "Building", Icons.Filled.Apartment), i("shipping", "Delivery", Icons.Filled.LocalShipping), i("group", "Team", Icons.Filled.Groups),
            i("person", "Person", Icons.Filled.Person),
        )),
        Category("Communication", listOf(
            i("phone", "Phone", Icons.Filled.Phone), i("email", "Email", Icons.Filled.Email), i("notifications", "Bell", Icons.Filled.Notifications),
            i("computer", "Computer", Icons.Filled.Computer), i("mobile", "Mobile", Icons.Filled.PhoneAndroid), i("camera", "Camera", Icons.Filled.PhotoCamera),
            i("music", "Music", Icons.Filled.MusicNote), i("headphones", "Headphones", Icons.Filled.Headphones), i("globe", "Globe", Icons.Filled.Public),
            i("language", "Language", Icons.Filled.Language),
        )),
        Category("Feelings", listOf(
            i("smile", "Smile", Icons.Filled.SentimentVerySatisfied), i("happy", "Happy", Icons.Filled.EmojiEmotions), i("neutral", "Neutral", Icons.Filled.SentimentNeutral),
            i("sad", "Sad", Icons.Filled.SentimentVeryDissatisfied), i("thumb-up", "Thumbs up", Icons.Filled.ThumbUp), i("thumb-down", "Thumbs down", Icons.Filled.ThumbDown),
            i("celebration", "Party", Icons.Filled.Celebration), i("cake", "Cake", Icons.Filled.Cake),
        )),
        Category("Nature & travel", listOf(
            i("sun", "Sun", Icons.Filled.WbSunny), i("moon", "Moon", Icons.Filled.NightsStay), i("cloud", "Cloud", Icons.Filled.Cloud),
            i("umbrella", "Umbrella", Icons.Filled.Umbrella), i("snow", "Snowflake", Icons.Filled.AcUnit), i("bolt", "Lightning", Icons.Filled.Bolt),
            i("fire", "Fire", Icons.Filled.Whatshot), i("water", "Water drop", Icons.Filled.WaterDrop), i("flower", "Flower", Icons.Filled.LocalFlorist),
            i("tree", "Tree", Icons.Filled.Park), i("forest", "Forest", Icons.Filled.Forest), i("grass", "Grass", Icons.Filled.Grass),
            i("spa", "Leaf", Icons.Filled.Spa), i("pets", "Paw", Icons.Filled.Pets), i("recycle", "Recycle", Icons.Filled.Recycling),
            i("beach", "Beach", Icons.Filled.BeachAccess), i("flight", "Plane", Icons.Filled.Flight), i("car", "Car", Icons.Filled.DirectionsCar),
            i("bike", "Bicycle", Icons.Filled.DirectionsBike), i("train", "Train", Icons.Filled.Train), i("map", "Map", Icons.Filled.Map),
            i("place", "Location", Icons.Filled.Place), i("home", "Home", Icons.Filled.Home),
        )),
        Category("Food & health", listOf(
            i("coffee", "Coffee", Icons.Filled.LocalCafe), i("restaurant", "Meal", Icons.Filled.Restaurant), i("fitness", "Fitness", Icons.Filled.FitnessCenter),
            i("hospital", "Hospital", Icons.Filled.LocalHospital), i("medication", "Medicine", Icons.Filled.Medication), i("sports", "Football", Icons.Filled.SportsSoccer),
            i("settings", "Gear", Icons.Filled.Settings), i("build", "Tools", Icons.Filled.Build), i("shield", "Shield", Icons.Filled.Shield),
        )),
    )

    private val byKey: Map<String, Item> = categories.flatMap { it.items }.associateBy { it.key }

    fun item(key: String): Item? = byKey[key]

    private val pathCache = HashMap<String, Path>()

    /** The clipart as a Compose path in its 24×24 viewport (cached). */
    fun path(key: String): Path? = synchronized(pathCache) {
        pathCache[key] ?: byKey[key]?.let { item ->
            val out = Path()
            fun walk(n: VectorNode) {
                when (n) {
                    is VectorPath -> out.addPath(PathParser().addPathNodes(n.pathData).toPath())
                    is VectorGroup -> n.forEach { walk(it) }
                }
            }
            walk(item.icon.root)
            out.also { pathCache[key] = it }
        }
    }

    /** SVG path data for export (same 24×24 viewport). */
    fun svgPath(key: String): String? {
        val item = byKey[key] ?: return null
        val sb = StringBuilder()
        fun f(v: Float) = if (v == v.toLong().toFloat()) v.toLong().toString() else "%.3f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
        fun walk(n: VectorNode) {
            when (n) {
                is VectorPath -> n.pathData.forEach { sb.append(svg(it, ::f)).append(' ') }
                is VectorGroup -> n.forEach { walk(it) }
            }
        }
        walk(item.icon.root)
        return sb.toString().trim()
    }

    private fun svg(p: PathNode, f: (Float) -> String): String = when (p) {
        is PathNode.Close -> "Z"
        is PathNode.MoveTo -> "M${f(p.x)} ${f(p.y)}"
        is PathNode.RelativeMoveTo -> "m${f(p.dx)} ${f(p.dy)}"
        is PathNode.LineTo -> "L${f(p.x)} ${f(p.y)}"
        is PathNode.RelativeLineTo -> "l${f(p.dx)} ${f(p.dy)}"
        is PathNode.HorizontalTo -> "H${f(p.x)}"
        is PathNode.RelativeHorizontalTo -> "h${f(p.dx)}"
        is PathNode.VerticalTo -> "V${f(p.y)}"
        is PathNode.RelativeVerticalTo -> "v${f(p.dy)}"
        is PathNode.CurveTo -> "C${f(p.x1)} ${f(p.y1)} ${f(p.x2)} ${f(p.y2)} ${f(p.x3)} ${f(p.y3)}"
        is PathNode.RelativeCurveTo -> "c${f(p.dx1)} ${f(p.dy1)} ${f(p.dx2)} ${f(p.dy2)} ${f(p.dx3)} ${f(p.dy3)}"
        is PathNode.ReflectiveCurveTo -> "S${f(p.x1)} ${f(p.y1)} ${f(p.x2)} ${f(p.y2)}"
        is PathNode.RelativeReflectiveCurveTo -> "s${f(p.dx1)} ${f(p.dy1)} ${f(p.dx2)} ${f(p.dy2)}"
        is PathNode.QuadTo -> "Q${f(p.x1)} ${f(p.y1)} ${f(p.x2)} ${f(p.y2)}"
        is PathNode.RelativeQuadTo -> "q${f(p.dx1)} ${f(p.dy1)} ${f(p.dx2)} ${f(p.dy2)}"
        is PathNode.ReflectiveQuadTo -> "T${f(p.x)} ${f(p.y)}"
        is PathNode.RelativeReflectiveQuadTo -> "t${f(p.dx)} ${f(p.dy)}"
        is PathNode.ArcTo -> "A${f(p.horizontalEllipseRadius)} ${f(p.verticalEllipseRadius)} ${f(p.theta)} ${if (p.isMoreThanHalf) 1 else 0} ${if (p.isPositiveArc) 1 else 0} ${f(p.arcStartX)} ${f(p.arcStartY)}"
        is PathNode.RelativeArcTo -> "a${f(p.horizontalEllipseRadius)} ${f(p.verticalEllipseRadius)} ${f(p.theta)} ${if (p.isMoreThanHalf) 1 else 0} ${if (p.isPositiveArc) 1 else 0} ${f(p.arcStartDx)} ${f(p.arcStartDy)}"
    }
}
