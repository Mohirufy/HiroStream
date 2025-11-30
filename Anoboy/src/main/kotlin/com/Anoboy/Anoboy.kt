package com.Anoboy

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

class Anoboy : MainAPI() {
    override var mainUrl = "https://ww1.anoboy.boo"
    
    override var name = "Anoboy"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "category/rekomended/" to "Rekomendasi",
        "category/anime-movie/" to "Movie",
        "category/tokusatsu/" to "Tokusatsu",
        "category/studio-ghibli/" to "Ghibli",
        "category/live-action-movie/" to "Live Action",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("div.column-content a:has(div.amv)").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.attr("title").toString()
            .let { it.substring(0, listOf(it.indexOf("("), it.indexOf("/"), it.indexOf("Season"), it.indexOf("|")).filter { idx -> idx >= 0 }.minOrNull() ?: it.length) }
            .trim()
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(this.select("div.amv img").attr("src").toString())
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results = document
            .select("div.column-content a:has(div.amv)")
            .filter {
                val t = it.attr("title").lowercase()
                !t.contains("episode") && !t.contains("download")
            }
        .mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val tvType = if (document.select("div.singlelink")
                .isEmpty()
        ) TvType.Movie else TvType.Anime

        val title = document.selectFirst("div.pagetitle h1")?.text()?.trim().toString().substringBefore("Subtitle")
        val poster = fixUrlNull(document.select("div.deskripsi img").attr("src").toString())
        val description = document.select("div.column-three-fourth div:nth-child(4)")?.text()?.trim()
        val tags = document.select("div.unduhan td#genre").text().trim().split(", ")
        val episodes: List<Episode> = if (document.select("div.singlelink a:matches(Streaming)").isNotEmpty()) {
            Log.d("Mohiro", "[Streaming]")
            val streamLink = fixUrl(
                document.select("div.singlelink a")
                    .firstOrNull { it.text().contains("Streaming", ignoreCase = true) }
                    ?.attr("href")
                    .toString()
            )

            val document = app.get(streamLink, timeout = 50L).document

            document.select("div.satu a").map {
                val href = fixUrl(it.attr("data-video"))
                val episode = "\\d+".toRegex().find(it.text())?.value?.toIntOrNull()
                val season = 1
                newEpisode(href) {
                    this.name = "Episode $episode"
                    this.season = season
                    this.episode = episode
                }
            }.reversed()
        } else {
            document.select("div.singlelink ul li > a:matches(Episode\\s*\\d+)").map {
                val href = fixUrl(it.attr("href"))
                val episode = it.text()
                    .substringAfter("Episode")
                    .substringBefore("Selesai")
                    .substringBefore("Tamat")
                    .trim()
                    .toIntOrNull()

                val season = if ("season-" in href) {
                    href.substringAfter("season-").substringBefore("-").toIntOrNull() ?: 1
                } else {
                    1
                }

                newEpisode(href) {
                    this.name = "Episode $episode"
                    this.season = season
                    this.episode = episode
                }
            }.reversed()
        }

        return if (tvType == TvType.Movie){
            val description = document.select("div.column-three-fourth div.unduhan")?.text()?.trim()
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
            newAnimeLoadResponse(title, url, TvType.Anime) {
                posterUrl = poster
                plot = description
                this.tags = tags
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
        document.select("span.ud a.udl").forEach{ gf->
            val url = gf.attr("href").toString()
            loadExtractor(url, subtitleCallback, callback)
        }
        
        val blogger = document.select("iframe").attr("src")
        loadExtractor(blogger, subtitleCallback, callback)

        document.select("div.vmiror a").forEach { vm->
            val vmiror = fixUrl(vm.attr("data-video"))
            val documentVmiror = app.get(vmiror).document
            documentVmiror.select("a.link").forEach { li->
                val url = li.attr("href").toString()
                Log.d("Mohiro", url.toString())
                loadExtractor(url, subtitleCallback, callback)
            }
        }
        return true
    }
}