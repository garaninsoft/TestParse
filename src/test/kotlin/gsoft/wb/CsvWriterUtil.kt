package gsoft.wb

import com.opencsv.CSVWriter
import gsoft.wb.model.Review
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object CsvWriterUtil {
    fun write(reviews: List<Review>, out: Path) {
        Files.createDirectories(out.parent)
        Files.newOutputStream(out).use { os ->
            OutputStreamWriter(os, StandardCharsets.UTF_8).use { osw ->
                CSVWriter(osw).use { writer ->
                    writer.writeNext(arrayOf(
                        "Дата публикации",
                        "Автор",
                        "Текст отзыва",
                        "Оценка",
                        "Количество фотографий",
                        "Наличие видео",
                        "Теги"
                    ))
                    for (r in reviews) {
                        writer.writeNext(arrayOf(
                            r.date ?: "",
                            r.author,
                            r.text,
                            r.rating?.toString() ?: "",
                            r.photoCount.toString(),
                            if (r.hasVideo) "yes" else "no",
                            r.tags.joinToString("|")
                        ))
                    }
                }
            }
        }
    }
}