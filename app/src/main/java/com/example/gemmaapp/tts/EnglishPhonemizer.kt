package com.example.gemmaapp.tts

import android.content.res.AssetManager

// Converts English text to Kokoro token ID sequences.
// Primary lookup: CMU Pronouncing Dictionary (134k words, loaded from assets at init).
// Fallback: hardcoded common-word dict, then rule-based letter-by-letter.
object EnglishPhonemizer {

    // ARPABET phone → Kokoro token IDs.
    // AH0 (unstressed schwa) is kept as a separate key from AH (stressed ʌ).
    // All other stress variants (AA0/AA1/AA2 etc.) strip their digit before lookup.
    private val PHONES = mapOf(
        "AA"  to longArrayOf(69),
        "AE"  to longArrayOf(72),
        "AH"  to longArrayOf(138),
        "AH0" to longArrayOf(83),
        "AO"  to longArrayOf(76),
        "AW"  to longArrayOf(43, 135),
        "AY"  to longArrayOf(43, 102),
        "B"   to longArrayOf(44),
        "CH"  to longArrayOf(133),
        "D"   to longArrayOf(46),
        "DH"  to longArrayOf(81),
        "EH"  to longArrayOf(86),
        "ER"  to longArrayOf(85),
        "EY"  to longArrayOf(47, 102),
        "F"   to longArrayOf(48),
        "G"   to longArrayOf(92),
        "HH"  to longArrayOf(50),
        "IH"  to longArrayOf(102),
        "IY"  to longArrayOf(51),
        "JH"  to longArrayOf(82),
        "K"   to longArrayOf(53),
        "L"   to longArrayOf(54),
        "M"   to longArrayOf(55),
        "N"   to longArrayOf(56),
        "NG"  to longArrayOf(112),
        "OW"  to longArrayOf(57, 135),
        "OY"  to longArrayOf(76, 102),
        "P"   to longArrayOf(58),
        "R"   to longArrayOf(123),
        "S"   to longArrayOf(61),
        "SH"  to longArrayOf(131),
        "T"   to longArrayOf(62),
        "TH"  to longArrayOf(119),
        "UH"  to longArrayOf(135),
        "UW"  to longArrayOf(63),
        "V"   to longArrayOf(64),
        "W"   to longArrayOf(65),
        "Y"   to longArrayOf(52),
        "Z"   to longArrayOf(68),
        "ZH"  to longArrayOf(147),
    )

    private val PUNCT = mapOf(
        ',' to 3L, '.' to 4L, '!' to 5L, '?' to 6L, ';' to 1L, ':' to 2L,
    )
    private const val SPACE_TOKEN = 16L

    // Letter-name pronunciations used when spelling out acronyms (AI, GPU, LLM, etc.).
    // Kept separate from FALLBACK_DICT because CMU dict's first entry for "a" is the
    // article "AH0", not the letter name "EY1" — we need the letter-name form here.
    private val LETTER_NAMES: Map<Char, List<String>> = mapOf(
        'a' to l("EY1"),
        'b' to l("B","IY1"),
        'c' to l("S","IY1"),
        'd' to l("D","IY1"),
        'e' to l("IY1"),
        'f' to l("EH1","F"),
        'g' to l("JH","IY1"),
        'h' to l("EY1","CH"),
        'i' to l("AY1"),
        'j' to l("JH","EY1"),
        'k' to l("K","EY1"),
        'l' to l("EH1","L"),
        'm' to l("EH1","M"),
        'n' to l("EH1","N"),
        'o' to l("OW1"),
        'p' to l("P","IY1"),
        'q' to l("K","Y","UW1"),
        'r' to l("AA1","R"),
        's' to l("EH1","S"),
        't' to l("T","IY1"),
        'u' to l("Y","UW1"),
        'v' to l("V","IY1"),
        'w' to l("D","AH1","B","AH0","L","Y","UW0"),
        'x' to l("EH1","K","S"),
        'y' to l("W","AY1"),
        'z' to l("Z","IY1"),
    )

    // Loaded from cmudict.txt at KokoroEngine init time.
    private val cmudict = HashMap<String, List<String>>(140_000)
    private var cmudictLoaded = false

    fun loadDict(assets: AssetManager) {
        if (cmudictLoaded) return
        try {
            assets.open("cmudict.txt").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val space = line.indexOf(' ')
                    if (space < 0) return@forEachLine

                    var word = line.substring(0, space).lowercase()
                    // Skip alternate pronunciations — keep first (most common)
                    if (word.endsWith(')')) {
                        if (word.endsWith("(2)") || !word.endsWith("(1)")) return@forEachLine
                        word = word.substringBefore('(')
                    }
                    if (cmudict.containsKey(word)) return@forEachLine

                    val phones = line.substring(space + 1).trim().split(' ')
                    cmudict[word] = phones
                }
            }
            cmudictLoaded = true
            android.util.Log.i("Phonemizer", "CMU dict loaded: ${cmudict.size} entries")
        } catch (e: Exception) {
            android.util.Log.w("Phonemizer", "CMU dict load failed: ${e.message}")
        }
    }

    fun phonemize(text: String): LongArray {
        val result = mutableListOf<Long>()
        val words = splitWords(expandNumbers(text.trim()))

        for (word in words) {
            when {
                word.isBlank() -> Unit
                word.length == 1 && PUNCT.containsKey(word[0]) ->
                    result.add(PUNCT[word[0]]!!)
                else -> {
                    if (result.isNotEmpty() && result.last() != SPACE_TOKEN)
                        result.add(SPACE_TOKEN)
                    if (word.length >= 2 && word.all { it.isUpperCase() }) {
                        // Acronym (AI, GPU, LLM, API…) — spell each letter individually.
                        // Cannot use cmudict here: its first entry for "a" is the article
                        // "AH0", not the letter name "EY1".
                        word.forEachIndexed { idx, c ->
                            if (idx > 0) result.add(SPACE_TOKEN)
                            val phones = LETTER_NAMES[c.lowercaseChar()] ?: listOf("AH0")
                            for (phone in phones) {
                                val key = if (PHONES.containsKey(phone)) phone
                                          else phone.trimEnd('0', '1', '2')
                                result.addAll((PHONES[key] ?: PHONES["AH0"]!!).toList())
                            }
                        }
                    } else {
                        val phones = lookupPhones(word.lowercase())
                        for (phone in phones) {
                            val key = if (PHONES.containsKey(phone)) phone
                                      else phone.trimEnd('0', '1', '2')
                            result.addAll((PHONES[key] ?: PHONES["AH0"]!!).toList())
                        }
                    }
                }
            }
        }
        return result.toLongArray()
    }

    // CMU dict → hardcoded dict → rules
    private fun lookupPhones(word: String): List<String> =
        cmudict[word] ?: FALLBACK_DICT[word] ?: rulesPhones(word)

    // Small hardcoded dict kept as a fast path and cmudict-load fallback.
    private val FALLBACK_DICT: Map<String, List<String>> = mapOf(
        "a"    to l("EY1"),      "an"   to l("AE1","N"),  "the"  to l("DH","AH0"),
        "i"    to l("AY1"),      "you"  to l("Y","UW1"),   "he"   to l("HH","IY1"),
        "she"  to l("SH","IY1"), "we"   to l("W","IY1"),   "they" to l("DH","EY1"),
        "is"   to l("IH1","Z"),  "are"  to l("AA1","R"),   "was"  to l("W","AH1","Z"),
        "and"  to l("AE1","N","D"), "or" to l("AO1","R"),  "but"  to l("B","AH1","T"),
        "in"   to l("IH1","N"),  "of"   to l("AH0","V"),   "to"   to l("T","UW1"),
        "yes"  to l("Y","EH1","S"), "no" to l("N","OW1"), "ok"   to l("OW1","K","EY2"),
        "okay" to l("OW1","K","EY2"), "hello" to l("HH","AH0","L","OW1"),
        "gemma" to l("JH","EH1","M","AH0"),
    )

    private fun l(vararg phones: String): List<String> = phones.toList()

    private fun splitWords(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch.isLetter() || ch == '\'') {
                current.append(ch)
            } else {
                if (current.isNotEmpty()) { result.add(current.toString()); current.clear() }
                if (!ch.isWhitespace()) result.add(ch.toString())
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun expandNumbers(text: String): String =
        text.replace(Regex("\\d+")) { m ->
            m.value.toIntOrNull()?.let { numberToWords(it) } ?: m.value
        }

    private fun numberToWords(n: Int): String {
        if (n == 0) return "zero"
        if (n < 0)  return "minus ${numberToWords(-n)}"
        val ones = listOf("","one","two","three","four","five","six","seven","eight","nine",
            "ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen",
            "eighteen","nineteen")
        val tens = listOf("","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety")
        return when {
            n < 20      -> ones[n]
            n < 100     -> tens[n / 10] + if (n % 10 != 0) " ${ones[n % 10]}" else ""
            n < 1_000   -> "${ones[n / 100]} hundred" +
                           if (n % 100 != 0) " ${numberToWords(n % 100)}" else ""
            n < 1_000_000 -> "${numberToWords(n / 1000)} thousand" +
                             if (n % 1000 != 0) " ${numberToWords(n % 1000)}" else ""
            else        -> n.toString()
        }
    }

    private fun rulesPhones(word: String): List<String> {
        val phones = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            val rem = word.substring(i)
            val tri = rem.take(3)
            val bi  = rem.take(2)
            val ch  = rem[0]
            when {
                tri == "tch"                      -> { phones += "CH"; i += 3 }
                tri == "igh"                      -> { phones += "AY"; i += 3 }
                bi  == "th"                       -> { phones += "TH"; i += 2 }
                bi  == "ch"                       -> { phones += "CH"; i += 2 }
                bi  == "sh"                       -> { phones += "SH"; i += 2 }
                bi  == "ph"                       -> { phones += "F";  i += 2 }
                bi  == "ng"                       -> { phones += "NG"; i += 2 }
                bi  == "ck"                       -> { phones += "K";  i += 2 }
                bi  == "wh"                       -> { phones += "W";  i += 2 }
                bi  == "qu"                       -> { phones += "K"; phones += "W"; i += 2 }
                bi  == "ee" || bi == "ea"         -> { phones += "IY"; i += 2 }
                bi  == "oo"                       -> { phones += "UW"; i += 2 }
                bi  == "ou" || bi == "ow"         -> { phones += "AW"; i += 2 }
                bi  == "oi" || bi == "oy"         -> { phones += "OY"; i += 2 }
                bi  == "ai" || bi == "ay"         -> { phones += "EY"; i += 2 }
                bi  == "au" || bi == "aw"         -> { phones += "AO"; i += 2 }
                bi  == "ie" || bi == "ey"         -> { phones += "IY"; i += 2 }
                bi  == "ew" || bi == "ue"         -> { phones += "UW"; i += 2 }
                ch  == 'a' -> { phones += if (rem.length == 1) "AH0" else "AE"; i++ }
                ch  == 'e' -> { if (i == word.length - 1) i++ else { phones += "EH"; i++ } }
                ch  == 'i' -> { phones += "IH"; i++ }
                ch  == 'o' -> { phones += "OW"; i++ }
                ch  == 'u' -> { phones += "AH"; i++ }
                ch  == 'y' -> { phones += if (i == 0) "Y" else "IY"; i++ }
                ch  == 'b' -> { phones += "B";  i++ }
                ch  == 'c' -> {
                    phones += if (rem.getOrNull(1) in listOf('e','i','y')) "S" else "K"; i++
                }
                ch  == 'd' -> { phones += "D";  i++ }
                ch  == 'f' -> { phones += "F";  i++ }
                ch  == 'g' -> {
                    phones += if (rem.getOrNull(1) in listOf('e','i','y')) "JH" else "G"; i++
                }
                ch  == 'h' -> { phones += "HH"; i++ }
                ch  == 'j' -> { phones += "JH"; i++ }
                ch  == 'k' -> { phones += "K";  i++ }
                ch  == 'l' -> { phones += "L";  i++ }
                ch  == 'm' -> { phones += "M";  i++ }
                ch  == 'n' -> { phones += "N";  i++ }
                ch  == 'p' -> { phones += "P";  i++ }
                ch  == 'q' -> { phones += "K";  i++ }
                ch  == 'r' -> { phones += "R";  i++ }
                ch  == 's' -> {
                    val prev = if (i > 0) word[i - 1] else ' '
                    phones += if (prev in "aeiou") "Z" else "S"; i++
                }
                ch  == 't' -> { phones += "T";  i++ }
                ch  == 'v' -> { phones += "V";  i++ }
                ch  == 'w' -> { phones += "W";  i++ }
                ch  == 'x' -> { phones += "K"; phones += "S"; i++ }
                ch  == 'z' -> { phones += "Z";  i++ }
                else       -> i++
            }
        }
        return phones.ifEmpty { listOf("AH0") }
    }
}
