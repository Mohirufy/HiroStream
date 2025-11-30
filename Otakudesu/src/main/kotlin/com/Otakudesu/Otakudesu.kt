package com.Otakudesu

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

class Otakudesu : MainAPI() {
    
    override var mainUrl = "https://otakudesu.best"
    
    override var name = "Otakudesu"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "ongoing-anime" to "Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("div.venz li").mapNotNull { it.toPageResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toPageResult(): SearchResponse {
        val title = this.select("a div.thumbz h2.jdlflm").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a div.thumbz img").attr("src").toString())
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearchResult(): SearchResponse{
        val title = this.select("h2 a").text().let { it.substring(0, listOf(it.indexOf("(Episode"), it.indexOf("Sub Indo"), it.indexOf("Subtitle"), it.indexOf("BD")).filter { idx -> idx >= 0 }.minOrNull() ?: it.length) }.trim()
        val href = fixUrl(this.select("h2 a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src").toString())

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query&post_type=anime", timeout = 50L).document
            val results =document.select("div.page ul.chivsrc li").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title =
            document.selectFirst("div.infozingle p:nth-child(1) span")?.text()?.trim().toString().substringAfter(":")
        val poster = document.select("div.fotoanime img").attr("src").toString()
        val description = document.selectFirst("div.sinopc")?.text()?.trim()
        val tvtag = if (url.contains("anime")) TvType.TvSeries else TvType.AnimeMovie
        val genre = document.select("div.infozingle p:nth-child(11) span").select("a").map { it.text() }
        
        val episodeListDiv = document.select("div.episodelist")
            .firstOrNull { div ->
                div.selectFirst(".monktit")?.text()?.contains("Episode List", ignoreCase = true) == true
            }

        val episodes = episodeListDiv
            ?.select("ul li")
            ?.map {
                val href = fixUrl(it.select("a").attr("href"))
                val episode = it.select("a").text().substringAfter("Episode").substringBefore("Subtitle").substringBefore("(End)").trim().toIntOrNull()
                newEpisode(href)
                    {
                        this.name = "Episode $episode"
                        this.season = season
                        this.episode = episode
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

        val desu = document.select("div.responsive-embed-stream iframe").attr("src")
        Log.d("Mohiro", desu.toString())
        loadExtractor(desu, subtitleCallback, callback)

        document.select("div.download ul li").map { el ->
            el.select("a").map { li ->
                
                val res = app.get(li.attr("href"))
                val finalUrl = res.url
                Log.d("Mohiro", finalUrl.toString())
                loadFixedExtractor(
                        fixUrl(app.get(li.attr("href")).url),
                        el.select("strong").text().substringAfter("Mp4"),
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