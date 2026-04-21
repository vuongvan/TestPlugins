package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://phim.nguonc.com"
    override var name = "NguonC"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // 1. Trang chủ: Lấy danh sách phim mới
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

    // 2. Tìm kiếm (Sử dụng API search của NguonC nếu có, ở đây dùng tạm logic page)
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

    // 3. Chi tiết phim và danh sách tập
    override suspend fun load(url: String): LoadResponse? {
        val apiUrl = "$mainUrl/api/film/$url"
        val response = app.get(apiUrl).text
        val data = parseJson<NguonCDetailResponse>(response)
        val movie = data.movie ?: return null

        val episodes = data.episodes?.flatMap { server ->
            server.items.map { ep ->
                Episode(
                    data = ep.embed, // Gửi link embed vào loadLinks để xử lý
                    name = ep.name,
                    headerName = server.server_name
                )
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodes) {
            this.posterUrl = movie.poster_url ?: movie.thumb_url
            this.plot = movie.description?.replace(Regex("<[^>]*>"), "") // Xóa tag HTML nếu có
            this.year = movie.created?.substringBefore("-")?.toIntOrNull()
        }
    }

    // 4. Xử lý link Embed (Giải quyết vấn đề link m3u8 trong JSON bị lỗi)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 'data' là link embed, ví dụ: https://embed.streamc.xyz/embed.php?hash=...
        
        // Gọi lên trang embed kèm Referer của web gốc để tránh 403
        val embedResponse = app.get(data, referer = "$mainUrl/").text

        // Dùng Regex lấy link m3u8 thật sự trong script của trang embed
        val m3u8Regex = Regex("""file:\s*"([^"]+\.m3u8)"""")
        val finalUrl = m3u8Regex.find(embedResponse)?.groupValues?.get(1)

        if (finalUrl != null) {
            callback.invoke(
                ExtractorLink(
                    source = "NguonC (StreamC)",
                    name = "Mộc Player",
                    url = finalUrl,
                    referer = "https://embed.streamc.xyz/", // Header quan trọng nhất để chạy video
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
            return true
        }

        return false
    }

    // --- CẤU TRÚC DỮ LIỆU JSON ---

    data class NguonCPageResponse(
        val items: List<NguonCMovieItem>?
    )

    data class NguonCMovieItem(
        val name: String,
        val slug: String,
        val thumb_url: String
    )

    data class NguonCDetailResponse(
        val movie: NguonCMovieDetail?,
        val episodes: List<NguonCServer>?
    )

    data class NguonCMovieDetail(
        val name: String,
        val description: String?,
        val thumb_url: String,
        val poster_url: String?,
        val created: String?
    )

    data class NguonCServer(
        val server_name: String,
        val items: List<NguonCEpisodeItem>
    )

    data class NguonCEpisodeItem(
        val name: String,
        val embed: String
    )
                              }
                              
