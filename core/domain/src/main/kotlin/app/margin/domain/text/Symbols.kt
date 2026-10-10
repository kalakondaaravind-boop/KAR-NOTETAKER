package app.margin.domain.text

/** Symbol picker catalogue (inserted as plain Unicode text, so it survives every export). */
object Symbols {
    data class Category(val id: String, val name: String, val symbols: List<String>)

    private fun of(s: String) = s.split(' ').filter { it.isNotEmpty() }

    val categories: List<Category> = listOf(
        Category("common", "Common", of("✓ ✔ ✗ ✘ ☐ ☑ ☒ • ◦ ▪ ★ ☆ ♥ ♡ ※ † ‡ § ¶ © ® ™ ° … – — « » ‹ › “ ” ‘ ’ № ℃ ℉")),
        Category("arrows", "Arrows", of("→ ← ↑ ↓ ↔ ↕ ⇒ ⇐ ⇑ ⇓ ⇔ ↗ ↘ ↙ ↖ ➜ ➔ ➤ ➡ ⬅ ⬆ ⬇ ↩ ↪ ⟲ ⟳ ⤴ ⤵ ⇄ ⇆ ⟶ ⟵")),
        Category("math", "Maths", of("± × ÷ = ≠ ≈ ≡ < > ≤ ≥ ∞ √ ∛ ∑ ∏ ∫ ∂ ∆ ∇ ∈ ∉ ⊂ ⊃ ⊆ ⊇ ∪ ∩ ∅ ∀ ∃ ¬ ∧ ∨ ⊕ ∝ ∠ ⊥ ∥ ° ′ ″ ½ ⅓ ⅔ ¼ ¾ ⅛ ² ³ ⁿ ‰")),
        Category("greek", "Greek", of("α β γ δ ε ζ η θ ι κ λ μ ν ξ ο π ρ σ τ υ φ χ ψ ω Α Β Γ Δ Θ Λ Ξ Π Σ Φ Ψ Ω")),
        Category("currency", "Currency", of("₹ $ € £ ¥ ¢ ₩ ₽ ₺ ₪ ₫ ₦ ₱ ฿ ₴ ₸ ₿ ¤")),
        Category("shapes", "Shapes", of("● ○ ◉ ◎ ■ □ ▲ △ ▼ ▽ ◆ ◇ ◀ ▶ ⬤ ⬛ ⬜ ▰ ▱ ◐ ◑ ◒ ◓ ✦ ✧ ✪ ✩ ✶ ✷ ❖ ⬟ ⬢ ⬡")),
        Category("marks", "Ticks & marks", of("✅ ❌ ⭕ ❗ ❓ ⚠ ⛔ 🚫 ✳ ✴ ❇ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🏁 🚩 📌 📍 🔖")),
        Category("work", "Work & study", of("📝 📒 📓 📔 📕 📗 📘 📙 📚 📎 🖇 📐 📏 ✂ 🗂 📁 📂 🗃 🗄 📅 📆 🗓 ⏰ ⏳ ⌛ 💡 🔍 🔑 🔒 🔓 📊 📈 📉 💼 🖊 ✏ 🖍 📣 📢 ☎ 📞 ✉ 📧 💻 🖥 🖨 ⌨")),
        Category("faces", "Faces & hands", of("😀 😃 😄 😊 🙂 😉 😍 🤔 😐 😮 😢 😡 😴 🤩 🥳 👍 👎 👏 🙌 🙏 👌 ✌ 🤝 💪 👀 🧠")),
        Category("nature", "Nature & weather", of("☀ 🌤 ⛅ ☁ 🌧 ⛈ 🌩 ❄ ☃ 🌈 🌙 ⭐ 🌟 🔥 💧 🌊 🌱 🌿 🍀 🌸 🌻 🌳 🍎 ☕ 🍰")),
        Category("music", "Music", of("♩ ♪ ♫ ♬ ♭ ♮ ♯ 𝄞 𝄢")),
    )

    fun search(q: String): List<String> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        return categories.filter { it.name.lowercase().contains(t) || it.id.contains(t) }.flatMap { it.symbols }.distinct()
    }
}
