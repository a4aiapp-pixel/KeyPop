import com.aetherkeys.keyboard.KeyPopAdaptiveLanguageModel
import com.aetherkeys.keyboard.KeyPopAdaptiveCandidatePlanner
import com.aetherkeys.keyboard.KeyPopAdaptiveEditorPolicy
import com.aetherkeys.keyboard.KeyPopUrlEditingPolicy

private var checks = 0
private fun verify(condition: Boolean, message: String) {
    checks++
    check(condition) { "FAILED: $message" }
}

fun main() {
    val model = KeyPopAdaptiveLanguageModel()
    for ((prefix, completion) in listOf(
        "You a" to "are", "I a" to "am", "he go" to "goes",
        "you are allow" to "allowed", "you are going" to "going")) {
        val result = model.predict(prefix, personalLearningEnabled = false,
            sensitiveEditor = false)
        verify(result.firstOrNull()?.replacement == completion, "$prefix -> $completion: $result")
    }
    val address = "house no 169, street no 9, J Sector"
    verify(!model.learnPhrase(address, true, false), "address not learned without special consent")
    verify(model.learnPhrase(address, true, false, allowAddressMemory = true), "learn permitted address")
    verify(model.learnPhrase(address, true, false, allowAddressMemory = true), "learn repeat address")
    verify(model.snapshot().single().uses == 2, "usage counter")
    verify(model.predict("house no ", personalLearningEnabled = true, sensitiveEditor = false)
        .none { it.kind.name.startsWith("PERSONAL") }, "no address suggestion without consent")
    for ((query, startsWith) in listOf(
        "house no" to "no 169", "house no " to "169, street no 9",
        "house no 169, street no " to "9, J Sector")) {
        verify(model.predict(query, personalLearningEnabled = true, sensitiveEditor = false,
            allowAddressMemory = true).any { it.replacement.startsWith(startsWith) },
            "address recall for $query")
    }
    verify(model.predict("house no ", personalLearningEnabled = true, sensitiveEditor = false,
        allowAddressMemory = true, allowPersonalWords = true, allowPersonalPhrases = false)
        .none { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_PHRASE },
        "phrase-specific opt-out")
    verify(model.predict("inter", personalLearningEnabled = true, sensitiveEditor = false,
        allowPersonalWords = false).none { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_WORD },
        "word-specific opt-out")
    verify(model.predict("house no ", personalLearningEnabled = false, sensitiveEditor = false,
        allowAddressMemory = true).none { it.kind.name.startsWith("PERSONAL") }, "consent off")
    verify(model.predict("house no ", personalLearningEnabled = true, sensitiveEditor = true,
        allowAddressMemory = true).isEmpty(), "sensitive editor empty")
    val restored = KeyPopAdaptiveLanguageModel()
    restored.restore(model.snapshot())
    verify(restored.predict("house no ", personalLearningEnabled = true,
        sensitiveEditor = false, allowAddressMemory = true).any { it.replacement.startsWith("169,") },
        "snapshot restore")
    verify(!model.learnWord("secret@work.com", true, false), "no email word learning")
    verify(model.learnWord("internationalization", true, false), "learn accepted word")
    verify(model.predict("inter", personalLearningEnabled = true, sensitiveEditor = false)
        .any { it.replacement == "internationalization" }, "personal word completion")
    val candidate = KeyPopAdaptiveCandidatePlanner.makePlan("you are allow", "", "allowed")
    verify(candidate?.replaceBeforeChars == 5, "replace fragment length")
    verify(candidate?.insert == "allowed", "replacement text")
    verify(KeyPopAdaptiveCandidatePlanner.makePlan("You a", "rest", "are") == null,
        "cannot insert inside word")
    verify(KeyPopAdaptiveCandidatePlanner.makePlan("house no ", "", "169, street no 9")
        ?.replaceBeforeChars == 0, "boundary insertion")
    verify(KeyPopAdaptiveCandidatePlanner.makePlan("you a", "", "are\nsomething") == null,
        "no multiline candidate injection")
    val merged = KeyPopAdaptiveCandidatePlanner.combine(
        KeyPopAdaptiveLanguageModel().predict("You a", personalLearningEnabled = false,
            sensitiveEditor = false),
        listOf(KeyPopAdaptiveCandidatePlanner.RankedCandidate("ARE", "ARE", 10, "legacy")))
    verify(merged.count { it.replacement.equals("are", true) } == 1, "deduplicate")
    val consent = KeyPopAdaptiveEditorPolicy.Consent(true, true, true)
    for (ctx in listOf(KeyPopAdaptiveEditorPolicy.Context(incognito = true),
        KeyPopAdaptiveEditorPolicy.Context(appExcluded = true))) {
        val dec = KeyPopAdaptiveEditorPolicy.decide(ctx, consent)
        verify(dec.mayPredictGeneric, "generic suggestions still work in non-secret fields")
        verify(!dec.maySuggestPersonal, "private suggestions blocked")
        verify(!dec.mayRememberAddresses, "address memory disabled")
    }
    val uriField = KeyPopAdaptiveEditorPolicy.decide(
        KeyPopAdaptiveEditorPolicy.Context(urlOrEmail = true), consent)
    verify(!uriField.mayPredictGeneric && !uriField.maySuggestPersonal,
        "URL fields must not receive grammar or personal replacements")
    val urlCases = listOf(
        "https://example.com/very/long/path?query=abc" to "",
        "https://exa" to "mple.com/path?x=123",
        "www.example.com" to "/very/long/part",
        "email@example.com" to "",
        "example.co" to "m/path",
        "127.0.0.1" to "/admin",
        "https://" to "",
        "repeated/path/to/file?x=1" to "&mode=2",
        "long%2Fencoded/path?q=1" to ""
    )
    for ((beforeUrl, afterUrl) in urlCases) {
        val policy = KeyPopUrlEditingPolicy.decide(false, false, beforeUrl, afterUrl)
        verify(policy.protected && !policy.allowSuggestions, "URL shield $beforeUrl")
        verify(!policy.allowAutomaticSpacing && !policy.allowAutomaticCorrection,
            "URL spacing and correction off $beforeUrl")
        verify(KeyPopAdaptiveCandidatePlanner.makePlan(beforeUrl, afterUrl, "anything") == null,
            "No candidate replacement in URL $beforeUrl")
    }
    for (normal in listOf("You a", "please find", "house no", "what is " , "example.")) {
        val policy = KeyPopUrlEditingPolicy.decide(false, false, normal)
        verify(!policy.protected && policy.allowSuggestions, "Normal phrase remains suggestible: $normal")
    }
    verify(KeyPopUrlEditingPolicy.decide(true, false, "").protected, "URI field protected even when blank")
    verify(KeyPopUrlEditingPolicy.decide(false, true, "me").protected, "Email field protected before @")
    verify(!KeyPopUrlEditingPolicy.touchesUrlOrEmail("Hi visit example.com ", "tomorrow"),
        "Earlier URL in a completed token cannot disable subsequent normal typing")
    val secure = KeyPopAdaptiveEditorPolicy.decide(
        KeyPopAdaptiveEditorPolicy.Context(sensitive = true), consent)
    verify(!secure.mayPredictGeneric && !secure.mayRememberPhrases, "sensitive fields fully excluded")
    verify(model.deletePhrase(address), "delete personal phrase")
    verify(model.snapshot().isEmpty(), "phrase deletion persisted in model")
    model.clear()
    verify(model.wordSnapshot().isEmpty(), "complete erase")
    println("PASS: $checks RC42 local regression assertions")
}
