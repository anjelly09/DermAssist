package com.dermassist.app

/** Questionnaire observations remain separate from the image-only research model. */
data class Symptoms(
    val location: String = "",
    val duration: String = "",
    val itching: String = "",
    val pain: String = "",
    val spread: String = "",
) {
    val complete: Boolean get() = listOf(location, duration, itching, pain, spread).all { it.isNotBlank() }
    fun summary() = "$location · $duration\nItching: $itching · Pain: $pain · Spreading: $spread"
}

data class Assessment(val id: String, val createdAt: Long, val symptoms: Symptoms, val screening: ScreeningResult = ScreeningResult()) {
    fun report(): String = listOf(
        "DermAssist — research prototype record",
        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(createdAt)),
        "", symptoms.summary(), "", screening.reportText(),
        "A qualified health worker can assess your concern. Do not use this prototype to decide that care is unnecessary.",
        "No photo is included in this report.",
    ).joinToString("\n")

}

object PhotoRules {
    const val MIN_EDGE = 320
    fun tooSmall(width: Int, height: Int) = minOf(width, height) < MIN_EDGE
}
