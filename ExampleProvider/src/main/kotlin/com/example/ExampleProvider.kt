package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://phim.nguonc.com"
    override var name = "ExampleProvider"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = "$mainUrl/api/films/phim-moi-cap-nhat?page=$page"
        val response = app.get(url).text
        val data = parseJson<NguonCPageResponse>(response)

        val homeItems = data.items?.map {
            newMovieSearchResponse(it.name, it.slug, TvType.Movie) {
                this.posterUrl = it.thumb_url
            }
        } ?: return null

        return newHomePageResponse("Phim Mới Cập Nhật", homeItems)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/films/danh-sach/phim-moi?keyword=$query"
        val response = app.get(url).text
        val data = parseJson<NguonCPageResponse>(response)

        return data.items?.map {
            newMovieSearchResponse(it.name, it.slug, TvType.Movie) {
                this.posterUrl = it.thumb_url
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val apiUrl = "$mainUrl/api/film/$url"
        val response = app.get(apiUrl).text
        val data = parseJson<NguonCDetailResponse>(response)
        val movie = data.movie ?: return null

        val episodes = data.episodes?.flatMap { server ->
            server.items.map { ep ->
                newEpisode(ep.embed) {
                    this.name = "${server.server_name}: ${ep.name}"
                }
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodes) {
            this.posterUrl = movie.poster_url ?: movie.thumb_url
            this.plot = movie.description?.replace(Regex("<[^>]*>"), "")
            this.year = movie.created?.substringBefore("-")?.toIntOrNull()
        }
    }

    // Dùng Suppress ở đây để bỏ qua lỗi build do warning 'deprecated'
    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val embedResponse = app.get(data, referer = "$mainUrl/").text
        val m3u8Regex = Regex("""file:\s*"([^"]+\.m3u8)"""")
        val finalUrl = m3u8Regex.find(embedResponse)?.groupValues?.get(1)

        if (finalUrl != null) {
            // Chúng ta dùng constructor trực tiếp thay vì builder để gán được 'val'
            val link = ExtractorLink(
                source = "NguonC",
                name = "Mộc Player",
                url = finalUrl,
                referer = "https://embed.streamc.xyz/",
                quality = Qualities.P1080.value,
                isM3u8 = true
            )
            callback.invoke(link)
            return true
        }

        return false
    }

    // --- DATA CLASSES ---
    data class NguonCPageResponse(val items: List<NguonCMovieItem>?)
    data class NguonCMovieItem(val name: String, val slug: String, val thumb_url: String)
    data class NguonCDetailResponse(val movie: NguonCMovieDetail?, val episodes: List<NguonCServer>?)
    data class NguonCMovieDetail(val name: String, val description: String?, val thumb_url: String, val poster_url: String?, val created: String?)
    data class NguonCServer(val server_name: String, val items: List<NguonCEpisodeItem>)
    data class NguonCEpisodeItem(val name: String, val embed: String)
                              }
                              
