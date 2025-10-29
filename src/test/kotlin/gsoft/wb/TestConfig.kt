package gsoft.wb

import java.util.*

object TestConfig {
    const val ERR_MSG = "ERR CONFIG"
    private val props: Properties = Properties()

    init {
        val stream = this::class.java.classLoader.getResourceAsStream("test-config.properties")
        if (stream != null) {
            props.load(stream)
        } else {
            throw IllegalStateException(ERR_MSG)
        }
    }

    val chromeDriverPath: String
        get() = props.getProperty("chromedriver.path") ?: error(ERR_MSG)

    val productUrl: String
        get() = props.getProperty("product.url") ?: error(ERR_MSG)

    val fileCsvPath: String
        get() = props.getProperty("filecsv.path") ?: error(ERR_MSG)

    val headless: Boolean
        get() = props.getProperty("headless", "false").toBoolean()
}