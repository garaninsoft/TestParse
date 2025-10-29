package gsoft.wb.model

data class Review(
    val date: String?,
    val author: String,
    val text: String,
    val rating: Int?,
    val photoCount: Int,
    val hasVideo: Boolean,
    val tags: List<String>
)