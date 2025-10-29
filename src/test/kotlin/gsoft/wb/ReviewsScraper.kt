package gsoft.wb

import gsoft.wb.model.Review
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

class ReviewsScraper(private val driver: WebDriver) {
    fun scrape(url: String, waitSeconds: Long = 20): List<Review> {
        driver.get(url)

        val selector = "li[itemprop='review'], li.comments__item.feedback"

        WebDriverWait(driver, Duration.ofSeconds(waitSeconds)).until {
            !driver.findElements(By.cssSelector(selector)).isEmpty()
        }

        return driver.findElements(By.cssSelector(selector)).mapNotNull(::parseReview)
    }

    private fun parseReview(el: WebElement): Review? {

        val author = el.safeText(".feedback__header")
        val text = el.safeText("p[itemprop='reviewBody'], .feedback__text.j-feedback__text")

        val rating = extractRating(el)
        val photos = el.findElements(By.cssSelector("ul.feedback__photos li.feedback__photo img")).size
        val hasVideo = el.findElements(By.cssSelector("button.feedback__video-btn, .feedback__video-btn")).isNotEmpty()
        val tags = extractTags(el)
        val date = el.safeText(".feedback__date")

        return Review(
            date = date,
            author = author?:"",
            text = text?:"",
            rating = rating,
            photoCount = photos,
            hasVideo = hasVideo,
            tags = tags
        )
    }

    private fun WebElement.firstOrNull(css: String): WebElement? =
        this.findElements(By.cssSelector(css)).firstOrNull()

    private fun WebElement.safeText(css: String): String? =
        this.firstOrNull(css)?.text?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractRating(el: WebElement): Int? {
        val ratingClass = el.firstOrNull(".feedback__rating")?.getAttribute("class") ?: ""
        val rating = Regex("star(\\d)").find(ratingClass)?.groupValues?.get(1)?.toIntOrNull()
        return rating
    }

    private fun extractTags(el: WebElement): List<String> {
        val list = mutableListOf<String>()
        val nodes =
            el.findElements(By.cssSelector(".feedback__tags .tag, .feedback__params .feedback__params-item--color, .feedback__params li"))
        for (n in nodes) {
            val t = n.text.trim()
            if (t.isNotEmpty()) list.add(t)
        }
        val badges = el.findElements(By.cssSelector(".feedback__pinned, .feedback__badge"))
        for (b in badges) {
            val t = b.text.trim()
            if (t.isNotEmpty()) list.add(t)
        }
        return list.distinct()
    }
}