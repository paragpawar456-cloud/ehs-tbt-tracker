package com.ehs.tbttracker.domain.parsing

import com.ehs.tbttracker.testutil.Fixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ContractorNormalizerTest {

    private val realNames = Fixtures.realSheet().rows.map { it.contractor }
    private val normalizer = ContractorNormalizer(realNames)

    @Test
    fun `merges case and whitespace variants`() {
        assertThat(normalizer.canonical("Choudhary construction ")).isEqualTo("Choudhary Construction")
        assertThat(normalizer.canonical("Choudhary Construction")).isEqualTo("Choudhary Construction")
        assertThat(normalizer.canonical("  ami   PLUMBING")).isEqualTo("Ami Plumbing")
    }

    @Test
    fun `merges abbreviated and extended spellings`() {
        assertThat(normalizer.canonical("Alu-wind")).isEqualTo("Alu-wind Infratech")
        assertThat(normalizer.canonical("Shree sidhivinayak enterprises tbt work team"))
            .isEqualTo("Shree Sidhivinayak Enterprises")
    }

    @Test
    fun `does not merge different companies that share a generic word`() {
        assertThat(normalizer.canonical("Shreyas enterprises")).isEqualTo("Shreyas Enterprises")
        assertThat(normalizer.canonical("Prachi Enterprises")).isEqualTo("Prachi Enterprises")
        assertThat(normalizer.canonical("N.A Enterprises")).isEqualTo("N.A Enterprises")
    }

    @Test
    fun `real sheet collapses 18 raw spellings into 15 contractors`() {
        assertThat(realNames.map { it.trim() }.distinct()).hasSize(18)
        assertThat(normalizer.all).hasSize(15)
        assertThat(normalizer.all).containsAtLeast(
            "Choudhary Construction", "Alu-wind Infratech", "Credible Construction Company", "Ami Plumbing",
        )
    }

    @Test
    fun `short single words never prefix-merge`() {
        val n = ContractorNormalizer(listOf("ABC", "ABC Builders"))
        assertThat(n.canonical("ABC")).isEqualTo("ABC")
        assertThat(n.canonical("ABC Builders")).isEqualTo("ABC Builders")
    }

    @Test
    fun `unknown and blank names`() {
        assertThat(normalizer.canonical("brand new agency")).isEqualTo("Brand New Agency")
        assertThat(normalizer.canonical("   ")).isEqualTo(ContractorNormalizer.UNKNOWN)
    }
}
