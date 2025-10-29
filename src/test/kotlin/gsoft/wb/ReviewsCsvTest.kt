package gsoft.wb

import com.opencsv.CSVWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.regex.Pattern

class ReviewsCsvTest {
    private lateinit var driver: WebDriver
    private val url = "https://www.wildberries.ru/catalog/521896959/feedbacks?imtId=234818091&size=720932801"
    private val outputCsv = Path.of("build/reports/wb_reviews.csv")
    private val diagDir = Path.of("build/reports/diagnostics")

    @BeforeEach
    fun setup() {
        val options = ChromeOptions()
        val headless = System.getProperty("headless", "false").toBoolean()
        if (headless) options.addArguments("--headless=new")
        options.addArguments("--no-sandbox", "--disable-gpu", "--window-size=1600,1000")
        driver = ChromeDriver(options)
    }

    @AfterEach
    fun tearDown() {
        driver.quit()
    }

    @Test
    fun `collect reviews and write csv`() {
        try {
            driver.get(url)

            // wait for SPA boot
            val waitInit = WebDriverWait(driver, Duration.ofSeconds(10))
            waitInit.until { drv -> drv.findElement(By.tagName("body")) }

            // wait for reviews to appear (JS-rendered)
            val waitReviews = WebDriverWait(driver, Duration.ofSeconds(30))
            val reviewsPresent = waitReviews.until(object : ExpectedCondition<Boolean> {
                override fun apply(drv: WebDriver): Boolean {
                    return try {
                        val js = drv as JavascriptExecutor
                        val res = js.executeScript(
                            "return document.querySelectorAll(\"li[itemprop='review'], li.comments__item.feedback\").length;"
                        )
                        val cnt = when (res) {
                            is Long -> res.toInt()
                            is Int -> res
                            else -> 0
                        }
                        println("JS found reviews count = $cnt")
                        cnt > 0
                    } catch (e: Exception) {
                        false
                    }
                }
            })

            if (!reviewsPresent) throw NoSuchElementException("Отзывы не появились в DOM")

            val reviewElements = driver.findElements(By.cssSelector("li[itemprop='review'], li.comments__item.feedback"))
            println("Found review elements via Selenium: ${reviewElements.size}")

            Files.createDirectories(outputCsv.parent)
            Files.newOutputStream(outputCsv).use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { osw ->
                    CSVWriter(osw).use { writer ->
                        // Заголовки строго по ТЗ
                        writer.writeNext(arrayOf(
                            "Дата публикации",
                            "Автор",
                            "Текст отзыва",
                            "Оценка",
                            "Количество фотографий",
                            "Наличие видео",
                            "Теги"
                        ))

                        for (r in reviewElements) {
                            val date = extractDate(r)
                            val author = extractAuthor(r)
                            val text = safeFindText(r, "p[itemprop='reviewBody'], .feedback__text.j-feedback__text")
                            val rating = extractRating(r)
                            val photosCount = countElements(r, "ul.feedback__photos li.feedback__photo img")
                            val hasVideo = if (exists(r, "button.feedback__video-btn, .feedback__video-btn")) "yes" else "no"
                            val tags = extractTags(r)

                            writer.writeNext(arrayOf(date, author, text, rating, photosCount.toString(), hasVideo, tags))
                        }
                    }
                }
            }
            println("CSV saved to: ${outputCsv.toAbsolutePath()}")
        } catch (e: Exception) {
            // diagnostics
            try { Files.createDirectories(diagDir) } catch(_:Exception){}
            try {
                val pageSourceFile = diagDir.resolve("page-source.html").toFile()
                pageSourceFile.writeText(driver.pageSource, Charsets.UTF_8)
                println("Saved page source: ${pageSourceFile.absolutePath}")
            } catch (ex: Exception) { println("Fail save page source: ${ex.message}") }
            try {
                val screenshotFile = diagDir.resolve("screenshot.png").toFile()
                if (driver is TakesScreenshot) {
                    val ts = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
                    screenshotFile.writeBytes(ts)
                    println("Saved screenshot: ${screenshotFile.absolutePath}")
                }
            } catch (ex: Exception) { println("Fail save screenshot: ${ex.message}") }
            throw e
        }
    }

    // --- helper extractors ---

    private fun extractDate(parent: org.openqa.selenium.WebElement): String {
        // Try multiple options in order of likelihood
        // 1) time[itemprop='datePublished'] or meta[itemprop='datePublished']
        val bys = listOf(
            "time[itemprop='datePublished']",
            "meta[itemprop='datePublished']",
            ".feedback__date",
            "[data-date]",
            "[data-published]",
            "time"
        )
        for (css in bys) {
            try {
                val el = parent.findElements(By.cssSelector(css))
                if (el.isNotEmpty()) {
                    // meta -> content, time -> datetime or text, generic -> text
                    val first = el.first()
                    val tag = first.tagName.lowercase()
                    when {
                        tag == "meta" -> {
                            val v = first.getAttribute("content")
                            if (!v.isNullOrBlank()) return v.trim()
                        }
                        tag == "time" -> {
                            val dt = first.getAttribute("datetime")
                            if (!dt.isNullOrBlank()) return dt.trim()
                            val t = first.text
                            if (!t.isNullOrBlank()) return t.trim()
                        }
                        else -> {
                            val v = first.getAttribute("data-date") ?: first.getAttribute("data-published")
                            if (!v.isNullOrBlank()) return v.trim()
                            val txt = first.text
                            if (!txt.isNullOrBlank()) return txt.trim()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If nothing found inside review element — try global find (some sites put date in child nodes elsewhere)
        try {
            val globalMeta = driver.findElements(By.cssSelector("meta[itemprop='datePublished'], meta[name='date']"))
            if (globalMeta.isNotEmpty()) {
                val v = globalMeta.first().getAttribute("content") ?: ""
                if (v.isNotBlank()) return v.trim()
            }
        } catch (_: Exception) {}

        return "" // fallback пусто
    }

    private fun extractAuthor(parent: org.openqa.selenium.WebElement): String {
        val authorMeta = safeFindAttr(parent, "meta[itemprop='author']", "content")
        if (authorMeta.isNotBlank()) return authorMeta
        val header = safeFindText(parent, ".feedback__header")
        if (header.isNotBlank()) return header
        return safeFindText(parent, "[itemprop='author'], .user-name")
    }

    private fun extractTags(parent: org.openqa.selenium.WebElement): String {
        val tags = mutableListOf<String>()
        // 1) feedback__tags .tag
        try {
            val nodes1 = parent.findElements(By.cssSelector(".feedback__tags .tag, .feedback__tags span, .feedback__tags li"))
            for (n in nodes1) {
                val t = n.text.trim()
                if (t.isNotEmpty()) tags.add(t)
            }
        } catch (_: Exception) {}
        // 2) feedback__params items (цвет/и т.п.)
        try {
            val nodes2 = parent.findElements(By.cssSelector(".feedback__params .feedback__params-item--color, .feedback__params .feedback__params-item--gray, .feedback__params li, .feedback__params span"))
            for (n in nodes2) {
                val t = n.text.trim()
                if (t.isNotEmpty()) tags.add(t)
            }
        } catch (_: Exception) {}
        // 3) badges / small labels e.g., "Закреплён"
        try {
            val badges = parent.findElements(By.cssSelector(".feedback__pinned, .feedback__badge, .feedback__flag"))
            for (b in badges) {
                val t = b.text.trim()
                if (t.isNotEmpty()) tags.add(t)
            }
        } catch (_: Exception) {}
        // 4) If still empty — try to infer tag-like words from review text headings (Достоинства/Комментарий/Недостатки)
        if (tags.isEmpty()) {
            try {
                val text = safeFindText(parent, "p[itemprop='reviewBody'], .feedback__text.j-feedback__text")
                val m = Pattern.compile("Достоинства:|Недостатки:|Комментарий:|Плюсы:|Минусы:", Pattern.CASE_INSENSITIVE).matcher(text)
                val found = mutableListOf<String>()
                while (m.find()) found.add(m.group().replace(":", "").trim())
                if (found.isNotEmpty()) tags.addAll(found)
            } catch (_: Exception) {}
        }
        // join with '|'
        return tags.distinct().joinToString("|")
    }

    private fun safeFindText(parent: org.openqa.selenium.WebElement, css: String): String {
        return try {
            val el = parent.findElement(By.cssSelector(css))
            el.text.trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun safeFindAttr(parent: org.openqa.selenium.WebElement, css: String, attr: String): String {
        return try {
            val el = parent.findElement(By.cssSelector(css))
            el.getAttribute(attr)?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun countElements(parent: org.openqa.selenium.WebElement, css: String): Int {
        return try {
            parent.findElements(By.cssSelector(css)).size
        } catch (e: Exception) {
            0
        }
    }

    private fun exists(parent: org.openqa.selenium.WebElement, css: String): Boolean {
        return try {
            parent.findElements(By.cssSelector(css)).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun extractRating(parent: org.openqa.selenium.WebElement): String {
        return try {
            val el = parent.findElement(By.cssSelector("span.feedback__rating"))
            val classes = el.getAttribute("class") ?: ""
            val m = Pattern.compile("star(\\d+)").matcher(classes)
            if (m.find()) m.group(1) else ""
        } catch (e: Exception) {
            ""
        }
    }
}
