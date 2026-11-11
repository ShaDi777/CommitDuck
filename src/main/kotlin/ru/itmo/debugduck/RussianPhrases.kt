package ru.itmo.debugduck

/**
 * Russian versions of the offline duck's phrases, keyed by the English phrase from [LocalDebugDuck], [DebugPhase] and
 * [DebugTopic]. `LocalDebugDuckTest` checks that every English phrase has a translation with the same placeholders.
 */
internal object RussianPhrases {
    val byEnglish: Map<String, String> = mapOf(
        "Quack! I'm your debug duck. Tell me what's going wrong - explaining a problem out loud is often half of the fix." to
            "Кря! Я твоя отладочная утка. Расскажи, что не так, — объяснить проблему вслух часто значит наполовину её решить.",
        "Quack! Something broke? Describe the problem to me step by step. I'm all ears (well, feathers)." to
            "Кря! Что-то сломалось? Опиши мне проблему шаг за шагом. Я вся внимание (точнее, все перья).",
        "Hi, I'm the debug duck. What are you stuck on? Tell me in your own words and I'll ask questions." to
            "Привет, я отладочная утка. На чём застрял? Расскажи своими словами, а я буду задавать вопросы.",
        "Quack! Great job! What was the root cause in the end? Is it worth adding a test so it never comes back?" to
            "Кря! Отличная работа! Так в чём была причина? Может, стоит добавить тест, чтобы баг не вернулся?",
        "Nice, the bug is gone! Is there any other place in the code with the same pattern that could break the same way?" to
            "Здорово, бага больше нет! Есть ли в коде другие места с таким же приёмом, которые могут сломаться так же?",
        "Well done! Can you explain the fix in one sentence? That sentence might be a perfect commit message." to
            "Молодец! Сможешь объяснить исправление одним предложением? Из него выйдет отличное сообщение коммита.",
        "Quack-tastic! Was the cause where you looked first, or somewhere completely different?" to
            "Кря-кря! Причина оказалась там, где ты искал сначала, или совсем в другом месте?",
        "Any time! Quack." to
            "Обращайся! Кря.",
        "You did the hard part - I just listened. Quack!" to
            "Самое сложное сделал ты, я только слушала. Кря!",
        "Happy to help. I'll be right here if the bug comes back." to
            "Рада помочь. Если баг вернётся, я здесь.",
        "Thanks for the code ({lines} from {file}). Read it to me line by line: what should each line do? Which one are you least sure about?" to
            "Спасибо за код ({lines} из {file}). Прочитай мне его построчно: что должна делать каждая строка? В какой ты уверен меньше всего?",
        "I see {lines} from {file}. Which value in this code do you expect to be different from what it actually is?" to
            "Передо мной {lines} из {file}. Какое значение в этом коде, по-твоему, получается не таким, как ты ожидаешь?",
        "Let's look at these {lines} from {file}. What goes into this code, and what should come out?" to
            "Посмотрим на код из {file} ({lines}). Что в него поступает на вход и что должно получиться на выходе?",
        "Here are {lines} from {file}. Which branch or call here do you think is executed - and have you checked that it really is?" to
            "Вот {lines} из {file}. Какая ветка или какой вызов здесь, по-твоему, выполняется — и ты проверил, что это действительно так?",
        "You're in {file}. Is the problem in this file, or in something it calls? Select the suspicious lines and send them with \"Attach from editor\" or \"Ask Debug Duck\" in the editor's context menu." to
            "Ты в файле {file}. Проблема в этом файле или в том, что он вызывает? Выдели подозрительные строки и отправь их через «Attach from editor» или «Ask Debug Duck» в контекстном меню редактора.",
        "That's fine - let's make it smaller. Comment out or skip half of the suspicious code. Does the problem stay or go away?" to
            "Ничего страшного — давай уменьшим задачу. Закомментируй или пропусти половину подозрительного кода. Проблема осталась или пропала?",
        "Let's collect facts instead of guesses: put a breakpoint or a log line before and after the suspicious part and compare the values." to
            "Давай собирать факты, а не догадки: поставь точку останова или вывод в лог до и после подозрительного места и сравни значения.",
        "Try to describe the problem in one sentence: \"The program should ..., but instead it ...\"." to
            "Попробуй описать проблему одним предложением: «Программа должна ..., а вместо этого ...».",
        "Go back to the last version that worked (git stash or an older commit) and make sure it really works. Then bring the changes back in small steps." to
            "Вернись к последней рабочей версии (git stash или более старый коммит) и убедись, что она правда работает. Потом возвращай изменения маленькими шагами.",
        "Write down what you know for sure about the bug, and separately what you only suspect. Which list is longer?" to
            "Выпиши, что ты знаешь о баге наверняка, и отдельно — что только предполагаешь. Какой список длиннее?",
        "What have you tried so far, and what did each attempt tell you?" to
            "Что ты уже пробовал и что тебе сказала каждая попытка?",
        "If a colleague looked at this, what would they ask first?" to
            "Если бы на это посмотрел коллега, что бы он спросил первым делом?",
        "Is there a simpler version of the problem you could solve first?" to
            "Есть ли более простая версия задачи, которую можно решить сначала?",
        "What would you search for in the docs or the issue tracker? Has anyone hit this before?" to
            "Что бы ты поискал в документации или в трекере задач? Сталкивался ли с этим кто-то раньше?",
        "Could you write a small test that fails because of this bug? Then you'll know for sure when it's fixed." to
            "Можешь написать маленький тест, который падает из-за этого бага? Тогда ты точно узнаешь, когда он исправлен.",
        "Take a short break and come back. With fresh eyes, what would you check first?" to
            "Сделай короткий перерыв и вернись. Что ты проверишь первым делом свежим взглядом?",
        "What exactly did you expect to happen, and what happens instead?" to
            "Что именно должно было произойти и что происходит на самом деле?",
        "Let's pin it down: what is the expected behaviour, and what do you actually observe?" to
            "Давай уточним: какое поведение ожидается и что ты видишь на самом деле?",
        "If everything worked, what would you see? And what do you see now?" to
            "Если бы всё работало, что бы ты увидел? А что видишь сейчас?",
        "Can you reproduce it every time? What are the exact steps?" to
            "Это воспроизводится каждый раз? Какие точные шаги?",
        "Does it happen always, or only sometimes? What is special about the times it happens?" to
            "Это происходит всегда или только иногда? Чем особенны случаи, когда это происходит?",
        "What is the smallest input or the shortest sequence of steps that still shows the problem?" to
            "Какие самые маленькие входные данные или самая короткая последовательность шагов, на которых проблема ещё видна?",
        "Did this ever work? What has changed since then - code, dependencies, data, environment?" to
            "Это когда-нибудь работало? Что изменилось с тех пор — код, зависимости, данные, окружение?",
        "When did you first notice it? What were you working on right before that?" to
            "Когда ты впервые это заметил? Над чем работал прямо перед этим?",
        "What was the last change before things broke? What happens if you undo it?" to
            "Какое изменение было последним перед тем, как всё сломалось? Что будет, если его откатить?",
        "Where does the program go wrong first? Can you narrow it down to a function or a line?" to
            "Где программа впервые начинает ошибаться? Можешь сузить место до функции или строки?",
        "What do the logs say right before the problem? If there are no logs there, what would you log?" to
            "Что пишут логи прямо перед проблемой? Если логов там нет, что бы ты вывел?",
        "If you split the way from the input to the output in half, is the data still correct in the middle?" to
            "Если разделить путь от входных данных до результата пополам, данные посередине ещё правильные?",
        "What are you assuming to be true here? Which of those assumptions have you actually checked?" to
            "Что ты здесь считаешь заведомо верным? Какие из этих допущений ты действительно проверил?",
        "Which part of the code are you most sure about? Let's double-check exactly that part." to
            "В какой части кода ты уверен больше всего? Давай перепроверим именно её.",
        "Is the code that runs really the code you are reading - the right file, the right branch, a fresh build?" to
            "Тот ли код выполняется, который ты читаешь, — нужный файл, нужная ветка, свежая сборка?",
        "What is your best guess about the cause right now? What small experiment would prove or disprove it?" to
            "Какая у тебя сейчас главная догадка о причине? Какой маленький эксперимент её подтвердит или опровергнет?",
        "If your hypothesis is right, what else should be true? Can you check that?" to
            "Если твоя гипотеза верна, что ещё должно быть правдой? Можешь это проверить?",
        "What is the cheapest thing you can try in the next five minutes to learn something new about the bug?" to
            "Что самое дешёвое можно попробовать за ближайшие пять минут, чтобы узнать о баге что-то новое?",
        "Explain the relevant code to me line by line, as if I were a duck. Which line surprises you?" to
            "Объясни мне нужный код построчно, как будто я утка. Какая строка тебя удивляет?",
        "Walk me through what happens step by step, from the input to the wrong result." to
            "Проведи меня по шагам: что происходит от входных данных до неправильного результата?",
        "Say out loud what each variable holds at the point where it fails. Does any value look odd?" to
            "Проговори вслух, что лежит в каждой переменной в момент ошибки. Какое-нибудь значение выглядит странно?",
        "What exactly does the compiler say? Read the message word by word: which symbol, type or token does it complain about?" to
            "Что именно пишет компилятор? Прочитай сообщение слово за словом: на какой символ, тип или токен он жалуется?",
        "Look at the line just before the one the compiler points to. A missing semicolon, bracket or quote often shows up a line later." to
            "Посмотри на строку прямо перед той, на которую указывает компилятор. Пропущенная точка с запятой, скобка или кавычка часто проявляется строкой ниже.",
        "Do the types fit? Check what each expression on that line returns and what is expected there, and whether everything it uses is declared and imported." to
            "Совпадают ли типы? Проверь, что возвращает каждое выражение в этой строке и что там ожидается, и всё ли, что там используется, объявлено и импортировано.",
        "Which exact value turns out to be null? Trace it back: where should it be assigned, and is that code actually reached before the place where it fails?" to
            "Какое именно значение оказывается null? Проследи его назад: где оно должно присваиваться и выполняется ли этот код раньше места падения?",
        "Can that value legitimately be missing - an empty lookup, an optional field, an object that isn't initialised yet? What should the code do then?" to
            "Может ли это значение законно отсутствовать — пустой результат поиска, необязательное поле, ещё не инициализированный объект? Что тогда должен делать код?",
        "Could it be an ordering issue - something read before it is set, like a lateinit property, a callback or an async result?" to
            "Может, дело в порядке — что-то читается раньше, чем задаётся, например lateinit-свойство, колбэк или асинхронный результат?",
        "Does the bug appear only sometimes? Which threads or coroutines touch the same data at the same time?" to
            "Баг проявляется только иногда? Какие потоки или корутины одновременно трогают одни и те же данные?",
        "What happens if you make it sequential and run everything on one thread? If the bug disappears, it is about ordering or shared state." to
            "Что будет, если сделать всё последовательно и запустить в одном потоке? Если баг пропадёт, дело в порядке выполнения или общем состоянии.",
        "Who waits for whom? If two pieces of code wait for each other's locks or results, that's a deadlock." to
            "Кто кого ждёт? Если два куска кода ждут блокировок или результатов друг друга, это дедлок.",
        "What SQL actually reaches the database? Log the final query with its parameters and run it by hand." to
            "Какой SQL на самом деле доходит до базы? Выведи итоговый запрос с параметрами и выполни его вручную.",
        "Is the data what you think it is? Look at the actual rows instead of assuming." to
            "Данные действительно такие, как ты думаешь? Посмотри на реальные строки в базе, а не предполагай.",
        "Could a transaction be involved - uncommitted changes, a rollback, or two transactions seeing different data?" to
            "Может, дело в транзакции — незакоммиченные изменения, откат или две транзакции видят разные данные?",
        "What exactly is sent and what comes back? Compare the real request (URL, headers, body) with what the server expects." to
            "Что именно отправляется и что приходит в ответ? Сравни реальный запрос (URL, заголовки, тело) с тем, что ожидает сервер.",
        "Does the same request work outside your code, for example with curl or Postman? That cuts the problem in half." to
            "Работает ли тот же запрос вне твоего кода, например через curl или Postman? Это сразу делит проблему пополам.",
        "What is the status code and the response body? 401, 404 and 500 each point in a different direction." to
            "Какой код ответа и что в теле ответа? 401, 404 и 500 указывают в совсем разные стороны.",
        "Is the code wrong, or does the test expect the wrong thing? Read the assertion message: what was expected and what was actual?" to
            "Неправ код или тест ожидает не то? Прочитай сообщение проверки: что ожидалось и что получилось?",
        "Does the test fail when you run it alone, or only together with other tests? Shared state and test order are classic suspects." to
            "Тест падает, если запустить его отдельно, или только вместе с другими? Общее состояние и порядок тестов — классические подозреваемые.",
        "Is the test deterministic? Time, randomness, collection ordering or network calls can make it flaky." to
            "Детерминирован ли тест? Время, случайность, порядок элементов в коллекциях или сетевые вызовы могут делать его нестабильным.",
        "What is the very first error in the build output? Later errors are often just consequences of the first one." to
            "Какая самая первая ошибка в выводе сборки? Остальные часто лишь следствия первой.",
        "Did dependencies or their versions change recently? Does a clean build behave the same way?" to
            "Менялись ли недавно зависимости или их версии? Чистая сборка ведёт себя так же?",
        "Does it build from the command line but not in the IDE, or the other way around? That points to configuration rather than code." to
            "Собирается из командной строки, но не в IDE, или наоборот? Это указывает скорее на настройки, чем на код.",
        "Have you measured where the time actually goes? A profiler or a few timestamps in the log usually beat guessing." to
            "Ты измерял, куда на самом деле уходит время? Профайлер или пара отметок времени в логе обычно лучше догадок.",
        "Does it get slower with more data? How does the time change when the input doubles?" to
            "Становится ли медленнее с ростом данных? Как меняется время, если удвоить входные данные?",
        "Is something running more often than you think - a loop inside a loop, a query per item, a repeated recalculation?" to
            "Может, что-то выполняется чаще, чем ты думаешь, — цикл в цикле, запрос на каждый элемент, повторный пересчёт?",
        "Is the data wrong, or is it only shown wrong? Check the value right before it is rendered." to
            "Неправильны сами данные или они только неправильно показаны? Проверь значение прямо перед отрисовкой.",
        "Is the UI updated on the right thread and at the right moment - after the data has actually changed?" to
            "Обновляется ли интерфейс в правильном потоке и в правильный момент — после того, как данные действительно изменились?",
        "Can you reproduce it with the simplest possible screen or component, without the rest of the app?" to
            "Можешь воспроизвести это на самом простом экране или компоненте, без остального приложения?",
        "What is different between where it works and where it doesn't - versions, config, environment variables, data?" to
            "Чем отличается место, где работает, от места, где не работает, — версии, настройки, переменные окружения, данные?",
        "When did it last work? If you know a good commit, git bisect can find the change that broke it." to
            "Когда это работало в последний раз? Если знаешь рабочий коммит, git bisect найдёт изменение, которое всё сломало.",
        "Is the configuration you are looking at the one that is actually loaded at runtime? Print it from inside the program." to
            "Та ли конфигурация, на которую ты смотришь, на самом деле загружается при запуске? Выведи её изнутри программы.",
        "Take one concrete input and compute the expected result by hand. At which step does the program start to disagree?" to
            "Возьми одни конкретные входные данные и посчитай ожидаемый результат вручную. На каком шаге программа начинает расходиться с тобой?",
        "Check the boundaries: empty input, a single element, the first and the last iteration of the loop. Do they behave?" to
            "Проверь граничные случаи: пустой ввод, один элемент, первую и последнюю итерацию цикла. Они работают правильно?",
        "Which condition decides between the right and the wrong branch? Is it evaluated the way you think - negation, precedence, < versus <=?" to
            "Какое условие выбирает между правильной и неправильной веткой? Оно вычисляется так, как ты думаешь, — отрицание, приоритет операций, < или <=?",
        "What does the error message say, word by word? Find the first stack trace frame from your own code - which line is it?" to
            "Что говорит сообщение об ошибке, слово за словом? Найди в стектрейсе первую строку из твоего кода — какая это строка?",
        "Is that exception the root cause, or is it wrapped? Look for the last \"Caused by\" in the stack trace - what does it say?" to
            "Это исключение — настоящая причина или обёртка? Найди в стектрейсе последнее «Caused by» — что там написано?",
        "What input or state reaches that line when it throws? Could you log it or set a breakpoint right before it?" to
            "С какими входными данными или состоянием код доходит до этой строки, когда падает? Можешь вывести их в лог или поставить точку останова прямо перед ней?",
        "Hmm. " to "Хм. ",
        "Got it. " to "Понятно. ",
        "I see. " to "Ясно. ",
        "Interesting. " to "Интересно. ",
        "Quack. " to "Кря. ",
        "Okay. " to "Хорошо. ",
        "A for-each loop only works with arrays and Iterable. Is the expression after the colon one of them? A stream, for example, is neither." to
            "Цикл for-each работает только с массивами и Iterable. Выражение после двоеточия — одно из них? Поток (Stream), например, — ни то ни другое.",
        "Is everything on that line declared and visible at that point: spelled the same way, imported, and in scope?" to
            "Всё ли в этой строке объявлено и видно в этом месте: написано так же, импортировано и находится в области видимости?",
        "Here are {lines} from {file}. Go through the line the compiler points to: what type does each expression there have, and what type does the code around it expect?" to
            "Вот {lines} из {file}. Пройдись по строке, на которую указывает компилятор: какой тип у каждого выражения в ней и какой тип ожидает код вокруг?",
        "I see {lines} from {file}. Which call or expression here are you least sure about: does it return what you think, and does that fit where it is used?" to
            "Передо мной {lines} из {file}. В каком вызове или выражении здесь ты уверен меньше всего: возвращает ли оно то, что ты думаешь, и подходит ли это туда, где используется?",
    )
}
