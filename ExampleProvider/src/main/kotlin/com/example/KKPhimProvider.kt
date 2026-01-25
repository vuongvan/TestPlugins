package com.example // Đảm bảo dòng này khớp với cấu trúc thư mục của bạn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

class KKPhimProvider : MainAPI() {
    override var mainUrl = "https://phimapi.com" // Dùng trực tiếp API làm URL chính cho ổn định
    override var name = "KKPhim"
    override val hasMainPage = true
    override var lang = "vi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // 1. Cấu hình trang chủ
    override suspend fun getMainPage(page: Int, request: HomePageRequest): HomePageResponse? {
        val url = "$mainUrl/danh-sach/phim-moi-cap-nhat?page=$page"
        val response = app.get(url).text
        val data = parseJson<KKListResponse>(response)
        
        val home = data.items?.map {
            newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                this.posterUrl = "https://phimimg.com/${it.poster_url}"
            }
        } ?: emptyList()
        
        return newHomePageResponse("Phim Mới Cập Nhật", home)
    }

    // 2. Tìm kiếm
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/api/tim-kiem?keyword=$query&limit=20"
        val response = app.get(url).text
        val data = parseJson<KKSearchResponse>(response)
        
        return data.data?.items?.map {
            newMovieSearchResponse(it.name ?: "", "$mainUrl/phim/${it.slug}", TvType.Movie) {
                this.posterUrl = "https://phimimg.com/${it.poster_url}"
            }
        } ?: emptyList()
    }

    // 3. Chi tiết phim
    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val res = parseJson<KKDetailResponse>(response)
        val movie = res.movie ?: return null
        
        val episodes = res.episodes?.flatMap { server ->
            server.server_data?.map { ep ->
                Episode(
                    data = ep.link_m3u8 ?: "",
                    name = ep.name ?: "",
                    episode = ep.name?.toIntOrNull()
                )
            } ?: emptyList()
        } ?: emptyList()

        return if (movie.type == "series") {
            newTvSeriesLoadResponse(movie.name ?: "", url, TvType.TvSeries, episodes) {
                this.posterUrl = movie.poster_url
                this.plot = movie.content
            }
        } else {
            newMovieLoadResponse(movie.name ?: "", url, TvType.Movie, episodes.firstOrNull()?.data ?: "") {
                this.posterUrl = movie.poster_url
                this.plot = movie.content
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                this.name,
                "HLS Provider",
                data,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}

// Data Classes với tính năng bảo vệ chống Null (tránh lỗi Crash khi API thay đổi)
data class KKListResponse(@JsonProperty("items") val items: List<KKItem>? = null)
data class KKItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("poster_url") val poster_url: String? = null
)
data class KKSearchResponse(@JsonProperty("data") val data: KKListData? = null)
data class KKListData(@JsonProperty("items") val items: List<KKItem>? = null)
data class KKDetailResponse(
    @JsonProperty("movie") val movie: KKMovie? = null,
    @JsonProperty("episodes") val episodes: List<KKServer>? = null
)
data class KKMovie(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("poster_url") val poster_url: String? = null,
    @JsonProperty("content") val content: String? = null
)
data class KKServer(@JsonProperty("server_data") val server_data: List<KKEpisode>? = null)
data class KKEpisode(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("link_m3u8") val link_m3u8: String? = null
)
