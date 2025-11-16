package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Emturbovid : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = true
    
    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val fileUrl = document.select("div#video_player").attr("data-hash")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = fileUrl,
                INFER_TYPE
            ) {
                this.referer = url
            }
        )
    }
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) { 
        val id = url.substringAfter("id=")
        val res = app.post(
                "$mainUrl/api.php?id=$id",
                data = mapOf(
                        "r" to "https://playeriframe.shop/",
                        "d" to "stream.hownetwork.xyz",
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                )
        ).parsedSafe<Sources>()

        res?.file?.let { fileUrl ->
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = fileUrl,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = getQualityFromName(res.file ?: "")
                }
            )
        }
    }

    data class Sources(
        val poster: String?,
        val file: String?,
        val type: String?,
        val title: String? 
    ) 
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}