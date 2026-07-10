package io.github.brad1014z.hanzi.ingest

import kotlin.test.Test
import kotlin.test.assertEquals

class PinyinTest {

    @Test
    fun `CEDICT tone numbers convert to marks per the spec 03 rules`() {
        val cases = mapOf(
            "ni3" to "nǐ", "hao3" to "hǎo", "zhong1" to "zhōng", "guo2" to "guó",
            "xie4" to "xiè", "dian3" to "diǎn", "er2" to "ér",
            // a/e take the mark; "ou" marks o; otherwise last vowel
            "xiu1" to "xiū", "gui4" to "guì", "liu2" to "liú", "dou1" to "dōu",
            // ü via u: and v
            "lu:4" to "lǜ", "lv4" to "lǜ", "nu:3" to "nǚ",
            // neutral tone: no mark; erhua r5
            "ma5" to "ma", "de5" to "de", "r5" to "r",
        )
        for ((numbered, marked) in cases) {
            assertEquals(marked, numberedSyllableToMarks(numbered), "for $numbered")
        }
    }

    @Test
    fun `tone stripping undoes marks`() {
        assertEquals("ni hao", stripTones("nǐ hǎo"))
        assertEquals("lü", stripTones("lǜ"))
    }
}
