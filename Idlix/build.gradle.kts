// use an integer for version numbers
version = 1


cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Hexated, Phisher98, Mohirufy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "Movie",
        "TvSeries",
    )


    iconUrl = "https://icons.duckduckgo.com/ip3/tv10.idlixku.com.ico"

    isCrossPlatform = true
}
