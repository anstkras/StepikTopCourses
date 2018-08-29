import com.eclipsesource.json.Json
import java.net.URL
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

fun main(vararg args: String) {
    val topCount = args.getOrNull(0)?.toIntOrNull()
    if (args.size > 1 || topCount === null || topCount <= 0) {
        println("usage: java -jar StepikTopCourses.jar <count>\n" +
                "count must be a positive number")
        return
    }

    val topQueue = PriorityQueue<Pair<Int, String>>(topCount, Comparator { (learnersCount1, _), (learnersCount2, _) ->
        Integer.compare(learnersCount1, learnersCount2)
    })

    val threadsNumber = Runtime.getRuntime().availableProcessors()

    val countdown = CountDownLatch(threadsNumber)
    val threadPool = Executors.newFixedThreadPool(threadsNumber)
    class Task(var page: Int) : Runnable {
        override fun run() = try {
            val url = "https://stepik.org/api/courses?page=$page"
            val response = URL(url).openStream().readBytes().toString(Charsets.UTF_8)

            val json = Json.parse(response).asObject()
            synchronized(topQueue) {
                for (course in json.get("courses").asArray()) {
                    val learnersCount = course.asObject().get("learners_count").asInt()
                    val title = course.asObject().get("title").asString()

                    if (topQueue.size < topCount) {
                        topQueue.add(learnersCount to title)
                    } else if (topQueue.peek().first < learnersCount) {
                        topQueue.poll()
                        topQueue.add(learnersCount to title)
                    }
                }
            }

            if (json.get("meta").asObject().get("has_next").asBoolean()) {
                page += threadsNumber
                threadPool.execute(this)
            } else {
                countdown.countDown()
            }
        } catch (e: Exception) {
            countdown.countDown()
        }
    }
    repeat(threadsNumber) { n ->
        threadPool.execute(Task(n + 1))
    }
    countdown.await()
    threadPool.shutdown()

    val answer = Array<Pair<Int, String>>(topQueue.size) { topQueue.poll() }
    println("Топ $topCount курсов на Stepik:")
    for ((index, course) in answer.reversed().withIndex()) {
        val (students, title) = course
        println("${index + 1}. $title. Количество студентов: $students.")
    }
}
