# Wildberries Reviews Scraper (Unit test in Kotlin)

Коротко: JUnit тест на Kotlin + Selenium, собирает отзывы со страницы Wildberries и сохраняет в CSV UTF-8.

Запуск:
1. Скачайте Chrome for Testing: https://googlechromelabs.github.io/chrome-for-testing/
2. Распакуйте и запомните путь к chromedriver
3. Запустите:
   ./gradlew test -Dwebdriver.chrome.driver=/path/to/chromedriver

Результат: build/reports/wb_reviews.csv

Зависимости:
- Kotlin/JVM
- Selenium Java 4.x
- OpenCSV
- JUnit5

Возможные улучшения: 
- пагинация,
- устойчивость к изменению селекторов,
- dockerized runner.
