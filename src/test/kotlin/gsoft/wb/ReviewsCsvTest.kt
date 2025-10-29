package gsoft.wb

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Path

class ReviewsCsvTest {
    private lateinit var driver: ChromeDriver
    private val url = TestConfig.productUrl
    private val output = Path.of(TestConfig.fileCsvPath)

    @BeforeEach
    fun setup() {
        // На macos перед исп chromeDriver в терминале xattr -d com.apple.quarantine chromeDriverPath
        System.setProperty("webdriver.chrome.driver", TestConfig.chromeDriverPath)
        val options = ChromeOptions()
        if (TestConfig.headless) options.addArguments("--headless=new")
        driver = ChromeDriver(options)
    }

    @AfterEach
    fun tearDown() {
        driver.quit()
    }

    @Test
    fun `scrape and write csv minimal`() {
        val scraper = ReviewsScraper(driver)
        val reviews = scraper.scrape(url, waitSeconds = 25)

        System.out.println(reviews.size)
        CsvWriterUtil.write(reviews, output)
        println("Wrote ${reviews.size} reviews to $output")
    }
}
