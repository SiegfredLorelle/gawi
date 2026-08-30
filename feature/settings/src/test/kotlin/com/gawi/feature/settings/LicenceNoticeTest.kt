package com.gawi.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenceNoticeTest {

    @Test
    fun `lines inside a paragraph join, paragraphs stay apart`() {
        val text = "The goals of the Open Font License (OFL) are to stimulate\nworldwide\ndevelopment.\n\nSecond paragraph\nhere.\n"

        assertEquals(
            "The goals of the Open Font License (OFL) are to stimulate worldwide development.\n\nSecond paragraph here.",
            reflowNotice(text),
        )
    }

    @Test
    fun `a boxed heading keeps its rules on their own lines`() {
        val text = "-----------\nSIL OPEN FONT LICENSE Version 1.1\n-----------\n\nPREAMBLE\nThe goals"

        assertEquals("-----------\nSIL OPEN FONT LICENSE Version 1.1\n-----------\n\nPREAMBLE\nThe goals", reflowNotice(text))
    }

    @Test
    fun `a wrapped all-capitals disclaimer still joins`() {
        val text = "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."

        assertEquals(text.replace("\n", " "), reflowNotice(text))
    }

    @Test
    fun `nothing but line breaks changes`() {
        val text = "ISC License\n\nCopyright (c) 2026 Lucide Icons and Contributors\n\nPermission to use,\ncopy.\n"

        // Every line break becomes a space or stays a line break, so mapping
        // both to spaces must leave the two texts identical, word for word.
        assertEquals(text.trimEnd().replace("\n", " "), reflowNotice(text).replace("\n", " "))
    }
}
