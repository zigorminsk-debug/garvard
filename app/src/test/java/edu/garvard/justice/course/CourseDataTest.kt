package edu.garvard.justice.course

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Проверка целостности учебного контента (assets/course_data.json).
 * Тест запускается в CI перед сборкой APK: если структура курса повреждена, сборка не пройдёт.
 */
class CourseDataTest {

    private val json: JSONObject by lazy {
        val file = File("src/main/assets/course_data.json")
        assertTrue("Не найден course_data.json", file.exists())
        JSONObject(file.readText(Charsets.UTF_8))
    }

    @Test
    fun courseHasTwelveLectures() {
        val lectures = json.getJSONArray("lectures")
        assertEquals(12, lectures.length())
    }

    @Test
    fun everyLectureIsComplete() {
        val lectures = json.getJSONArray("lectures")
        for (i in 0 until lectures.length()) {
            val lecture = lectures.getJSONObject(i)
            val id = lecture.getInt("id")
            assertEquals(i + 1, id)

            assertTrue("Лекция $id: пустое название", lecture.getString("title").isNotBlank())
            assertTrue("Лекция $id: нет модуля", lecture.getString("module").isNotBlank())
            assertTrue("Лекция $id: нет мыслителей", lecture.getJSONArray("philosophers").length() > 0)
            assertTrue("Лекция $id: мало разделов", lecture.getJSONArray("sections").length() >= 3)
            assertTrue("Лекция $id: мало выводов", lecture.getJSONArray("takeaways").length() >= 3)
            assertTrue("Лекция $id: нет вопросов", lecture.getJSONArray("reflectionQuestions").length() >= 2)
            assertTrue("Лекция $id: нет теста", lecture.getJSONArray("quiz").length() >= 2)

            val case = lecture.getJSONObject("caseStudy")
            assertTrue("Лекция $id: нет кейса", case.getString("scenario").isNotBlank())
            assertTrue("Лекция $id: нет вывода кейса", case.getString("conclusion").isNotBlank())

            val epigraph = lecture.getJSONObject("epigraph")
            assertTrue("Лекция $id: нет эпиграфа", epigraph.getString("quote").isNotBlank())
        }
    }

    @Test
    fun quizAnswersAreValid() {
        val lectures = json.getJSONArray("lectures")
        for (i in 0 until lectures.length()) {
            val lecture = lectures.getJSONObject(i)
            val quiz = lecture.getJSONArray("quiz")
            for (q in 0 until quiz.length()) {
                val question = quiz.getJSONObject(q)
                val options = question.getJSONArray("options")
                val correct = question.getInt("correctIndex")
                assertTrue("Лекция ${lecture.getInt("id")}, вопрос ${q + 1}: мало вариантов", options.length() >= 3)
                assertTrue("Лекция ${lecture.getInt("id")}, вопрос ${q + 1}: неверный индекс", correct in 0 until options.length())
                assertTrue("Лекция ${lecture.getInt("id")}, вопрос ${q + 1}: нет пояснения", question.getString("explanation").isNotBlank())
            }
        }
    }

    @Test
    fun dilemmasReferenceExistingLectures() {
        val lectureIds = mutableSetOf<Int>()
        val lectures = json.getJSONArray("lectures")
        for (i in 0 until lectures.length()) lectureIds.add(lectures.getJSONObject(i).getInt("id"))

        val dilemmas = json.getJSONArray("dilemmas")
        assertTrue("Мало дилемм", dilemmas.length() >= 8)
        for (i in 0 until dilemmas.length()) {
            val d = dilemmas.getJSONObject(i)
            assertTrue("Дилемма ${d.getString("id")}: нет лекции", lectureIds.contains(d.getInt("lectureId")))
            assertTrue("Дилемма ${d.getString("id")}: пуст сценарий", d.getString("scenario").isNotBlank())
            listOf("optionA", "optionB").forEach { key ->
                val option = d.getJSONObject(key)
                assertTrue("Дилемма ${d.getString("id")}: пустой вариант", option.getString("label").isNotBlank())
                assertTrue("Дилемма ${d.getString("id")}: нет школы", option.getString("school").isNotBlank())
                assertTrue("Дилемма ${d.getString("id")}: нет разбора", option.getString("analysis").isNotBlank())
            }
        }
    }

    @Test
    fun philosophersAndGlossaryAreFilled() {
        val philosophers = json.getJSONArray("philosophers")
        assertTrue("Мало философов", philosophers.length() >= 8)
        for (i in 0 until philosophers.length()) {
            val p = philosophers.getJSONObject(i)
            assertTrue(p.getString("name").isNotBlank())
            assertTrue("${p.getString("name")}: нет идеи", p.getString("coreIdea").isNotBlank())
            assertTrue("${p.getString("name")}: нет критики", p.getString("critique").isNotBlank())
        }

        val glossary = json.getJSONArray("glossary")
        assertTrue("Мало терминов", glossary.length() >= 20)
        for (i in 0 until glossary.length()) {
            val g = glossary.getJSONObject(i)
            assertNotNull(g.getString("term"))
            assertTrue("Термин ${g.getString("term")}: нет определения", g.getString("definition").isNotBlank())
        }
    }

    @Test
    fun appAssetsArePresent() {
        val html = File("src/main/assets/app.html")
        assertTrue("Не найден app.html", html.exists())
        assertTrue(
            "В app.html отсутствует плейсхолдер __COURSE_DATA__",
            html.readText(Charsets.UTF_8).contains("__COURSE_DATA__")
        )
    }
}
