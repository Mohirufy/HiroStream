package com.Kuramanime

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll

class Kuramanime : MainAPI() {
    
    override var mainUrl = "https://v8.kuramanime.tel"

    override var name = "Kuramanime"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    companion object {
        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Selesai Tayang" -> ShowStatus.Completed
                "Sedang Tayang" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }
    

    override val mainPage = mainPageOf(
        "quick/ongoing?order_by=updated&page=" to "Terbaru",
        "properties/season/fall-2025?order_by=popular" to "Musim Ini",
        "quick/finished?order_by=updated" to "Selesai Tayang",
        "quick/movie?order_by=updated" to "Movie",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page", timeout = 50L).document
        val home = document.select("div.col-lg-4.col-md-6.col-sm-6").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/episode")) {
            Regex("(.*)/episode/.+").find(uri)?.groupValues?.get(1).toString() + "/"
        } else {
            uri
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h5 a")!!.text()
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val posterUrl = fixUrlNull(this.select("a div.product__item__pic.set-bg").attr("data-setbg"))
        val episode =
                this.select("div.ep span").text().let {
                    Regex("Ep\\s(\\d+)\\s/").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("$mainUrl/anime?search=$query&order_by=latest", timeout = 50L).document
        return document.select("div#animeList div.product__item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title = document.selectFirst("div.anime__details__title h3")?.text()?.trim().toString()
        val poster = document.select("div.anime__details__pic.set-bg").attr("data-setbg").toString()
        val description = document.selectFirst("div.anime__details__text > p")?.text()?.trim()?.substringBefore("Catatan: Sinopsis diterjemahkan")
        val tags = document.select("div.anime__details__widget > div > div:nth-child(2) > ul > li:nth-child(1)").text().trim().replace("Genre: ", "").split(", ")
        val status = getStatus(document.select("div.anime__details__widget > div > div:nth-child(1) > ul > li:nth-child(3)").text().trim().replace("Status: ", ""))
        val tvType = if (document.select("div.anime__details__widget div.row div:nth-child(1) li:nth-child(1) div.row div.col-9 a").text() == "Movie") TvType.Movie else TvType.Anime
        val movieUrl = "$url/episode/1"
        val episodes = mutableListOf<Episode>()


        for (i in 1..10) {
            val doc = app.get("$url?page=$i").document
            val eps =
                    Jsoup.parse(doc.select("#episodeLists").attr("data-content"))
                            .select("a.btn.btn-sm.btn-danger")
                            .mapNotNull {
                                val name = it.text().trim()
                                val episode =
                                        Regex("(\\d+[.,]?\\d*)")
                                                .find(name)
                                                ?.groupValues
                                                ?.getOrNull(0)
                                                ?.toIntOrNull()
                                val link = it.attr("href")
                                newEpisode(link){
                                    this.episode
                                }
                            }
            if (eps.isEmpty()) break else episodes.addAll(eps)
        }

        return if (tvType == TvType.Movie){
            newMovieLoadResponse(title, url, TvType.Movie, movieUrl) {
                posterUrl = poster
                plot = description
                this.tags = tags
            }
        } else {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                engName = title
                posterUrl = poster
                plot = description
                this.tags = tags
                showStatus = status
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val page = getPageFromUrl(data)

        val kpsValue = document.select("div[data-kk]")?.attr("data-kk")
        val js = app.get("https://v8.kuramanime.tel/assets/js/$kpsValue.js").text

        val pageTokenRegex = Regex("""MIX_PAGE_TOKEN_KEY:\s*'([^']+)'""")
        val pageToken = pageTokenRegex.find(js)?.groupValues?.get(1)
        val streamKeyRegex = Regex("""MIX_STREAM_SERVER_KEY:\s*'([^']+)'""")
        val streamKey = streamKeyRegex.find(js)?.groupValues?.get(1)
        val authRouteRegex = Regex("""MIX_AUTH_ROUTE_PARAM:\s*'([^']+)'""")
        val authRoute = authRouteRegex.find(js)?.groupValues?.get(1)

        val authToken = app.get("https://v8.kuramanime.tel/assets/$authRoute").text

        val realPage = app.get(data + "?$pageToken=$authToken&$streamKey=kuramadrive&page=$page").document

        Log.d("Mohiro", data + "?$pageToken=$authToken&$streamKey=kuramadrive&page=$page")

        realPage.select("div#animeDownloadLink a").map { el->
        val url = el.attr("href").toString()
        loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }

    private suspend fun getPageFromUrl(data: String): Int {
        val document = app.get(data, timeout = 50L).document
        val episodePath = data.substringAfter("${mainUrl}")
        val target = document.select("#animeEpisodes a[href*=$episodePath]").attr("href") ?: return 1

        val page = Regex("page=(\\d+)").find(target)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        return page
    }
}