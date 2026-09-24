package com.ehs.tbttracker.domain.parsing

import com.ehs.tbttracker.domain.model.PhotoRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DriveLinkResolverTest {
    private val id = "1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO"

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://drive.google.com/open?id=1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO",
            "https://drive.google.com/file/d/1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO/view?usp=sharing",
            "https://drive.google.com/uc?export=view&id=1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO",
            "https://lh3.googleusercontent.com/d/1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO=w800",
            "https://drive.google.com/open?id=1Ou6BGC3-26fr1a74iwUBJgD2MECRteRO, https://drive.google.com/open?id=other123456789",
        ],
    )
    fun `extracts file id from all Drive url shapes`(url: String) {
        assertThat(DriveLinkResolver.extractFileId(url)).isEqualTo(id)
    }

    @Test
    fun `ids with underscores and trailing dash are kept intact`() {
        assertThat(DriveLinkResolver.extractFileId("https://drive.google.com/open?id=1JbVMXIsetGSiIJlL0eU1Iks48gbBLV-_"))
            .isEqualTo("1JbVMXIsetGSiIJlL0eU1Iks48gbBLV-_")
    }

    @Test
    fun `builds direct thumbnail urls`() {
        assertThat(DriveLinkResolver.thumbnailUrl(id)).isEqualTo("https://lh3.googleusercontent.com/d/$id=w800")
        assertThat(DriveLinkResolver.driveThumbnailUrl(id, 400)).isEqualTo("https://drive.google.com/thumbnail?id=$id&sz=w400")
    }

    @Test
    fun `maps to photo refs`() {
        assertThat(DriveLinkResolver.toPhotoRef("https://drive.google.com/open?id=$id")).isEqualTo(
            PhotoRef.Drive(id, "https://drive.google.com/open?id=$id"),
        )
        assertThat(DriveLinkResolver.toPhotoRef("https://example.com/a.jpg")).isEqualTo(PhotoRef.Url("https://example.com/a.jpg"))
        assertThat(DriveLinkResolver.toPhotoRef("")).isNull()
        assertThat(DriveLinkResolver.toPhotoRef("not a link")).isNull()
    }
}
