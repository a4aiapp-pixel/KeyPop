import com.aetherkeys.keyboard.KeyPopAdaptiveLanguageModel

fun main() {
    fun assertTop(text: String, expected: String) {
        val m = KeyPopAdaptiveLanguageModel()
        val result = m.predict(text, personalLearningEnabled = false, sensitiveEditor = false)
        check(result.firstOrNull()?.replacement == expected) { "Expected $expected for '$text', got $result" }
        println("PASS: $text => $expected")
    }
    assertTop("You a", "are")
    assertTop("you are allow", "allowed")
    assertTop("I a", "am")
    assertTop("They a", "are")

    val model = KeyPopAdaptiveLanguageModel()
    val address = "house no 169, street no 9, J Sector"
    check(!model.learnPhrase(address, personalLearningEnabled = false, sensitiveEditor = false))
    check(model.learnPhrase(address, personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true))
    check(model.learnPhrase(address, personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true))
    for ((context, expected) in listOf(
        "house no" to "no 169, street no 9",
        "house no " to "169, street no 9",
        "house no 169, street no " to "9, J Sector"
    )) {
        val predictions = model.predict(context, personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true)
        println("QUERY: $context => $predictions")
        check(predictions.any { it.replacement.startsWith(expected) }) {
            "Expected prefix '$expected' for '$context', got $predictions"
        }
    }
    check(model.predict("house no", personalLearningEnabled=false, sensitiveEditor=false)
        .none { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_PHRASE })
    check(model.predict("house no", personalLearningEnabled=true, sensitiveEditor=true).isEmpty())
    check(model.snapshot().single().uses == 2)
    println("PASS: local phrase learning and privacy controls")
}
