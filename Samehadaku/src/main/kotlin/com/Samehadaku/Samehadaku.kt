package com.Samehadaku

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Samehadaku : MainAPI() {
    
    override var mainUrl = "https://v1.samehadaku.how"
    
    override var name = "Samehadaku"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "daftar-anime-2/?title=&status=&type=&order=update" to "Terbaru",
        "daftar-anime-2/?title=&status=&type=TV&order=popular" to "TV Populer",
        "daftar-anime-2/?title=&status=&type=OVA&order=title" to "OVA",
        "daftar-anime-2/?title=&status=&type=Movie&order=title" to "Movie",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}", timeout = 50L).document
        val home = document.select("div.animposx").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a div.content-thumb img").attr("src").toString())
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            // this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results =document.select("div.animposx").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title =
            document.selectFirst("h1.entry-title")?.text()?.trim().toString().substringBefore("Sub Indo")
        val poster = document.select("div.thumb img").attr("src").toString()
        val description = document.selectFirst("div.entry-content.entry-content-single p")?.text()?.trim()
        val tvtag = if (url.contains("anime")) TvType.TvSeries else TvType.AnimeMovie
        val genre = document.select("div.genre-info").select("a").map { it.text() }
        val episodes = mutableListOf<Episode>()
        document.select("div.lstepsiode.listeps").amap { info -> 
            info.select("ul li div.epsleft span.lchx a").forEach { it ->
                // val name = it.select("a").text().substringAfter("Episode").trim()
                val href = it.select("a").attr("href") ?: ""
                val Rawepisode = it.select("a").text().substringAfter("Episode").trim().toIntOrNull()
                episodes.add(
                    newEpisode(href)
                    {
                        "Episode"
                        this.episode=Rawepisode
                        // this.name=name
                    }
                )
            }
        }
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genre
            addEpisodes(DubStatus.Subbed, episodes)
        }
    } 

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div#downloadb li").map { el ->
            el.select("a").map { li ->
                loadFixedExtractor(
                        fixUrl(li.attr("href")),
                        el.select("strong").text(),
                        "$mainUrl/",
                        subtitleCallback,
                        callback
                )
            }
        }
        return true
    }

    private suspend fun loadFixedExtractor(
            url: String,
            name: String,
            referer: String? = null,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                            link.name,
                            link.name,
                            link.url,
                    ) {
                        this.quality = name.fixQuality()
                    }
                )
            }
        }
    }

    private fun String.fixQuality(): Int {
        return when (this.uppercase()) {
            "4K" -> Qualities.P2160.value
            "FULLHD" -> Qualities.P1080.value
            "MP4HD" -> Qualities.P720.value
            else -> this.filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
        }
    }
}