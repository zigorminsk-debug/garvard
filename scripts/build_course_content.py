#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор полного курса лекций «Справедливость: Как поступать правильно?»
по мотивам знаменитого курса профессора Майкла Сэндела в Гарвардском университете (ER22: Justice).
Создаёт:
1. Папку course/ с программой курса и 12 подробными лекциями (lecture-01.md ... lecture-12.md)
2. Полную книгу-курс в корне репозитория: COURSE_JUSTICE_HARVARD.md
3. Структурированную базу знаний для Android-приложения: app/src/main/assets/course_data.json
"""

import json
import os

from part1_lectures import LECTURES_PART1
from part2_lectures import LECTURES_PART2
from extras_data import DILEMMAS, PHILOSOPHERS, GLOSSARY

def main():
    lectures = LECTURES_PART1 + LECTURES_PART2
    os.makedirs("course", exist_ok=True)
    os.makedirs("app/src/main/assets", exist_ok=True)

    # 1. Generate course/README.md
    readme_lines = [
        "# Курс лекций «Справедливость: Как поступать правильно?»",
        "### По мотивам легендарного курса профессора Майкла Сэндела в Гарвардском университете (Moral Reasoning 22 / Justice)",
        "",
        "> *«Спрашивать, является ли общество справедливым, — значит спрашивать, как оно распределяет вещи, которые мы ценим: доходы и богатство, обязанности и права, полномочия и возможности, должности и почести. Справедливое общество воздаёт каждому должное. Трудность начинается тогда, когда мы спрашиваем, что именно по праву причитается человеку и почему.»*",
        "> — **Майкл Сэндел**, Гарвардский университет",
        "",
        "---",
        "",
        "## О курсе",
        "",
        "Этот курс представляет собой полное, систематизированное изложение двенадцати лекций гарвардского курса **«Justice» (Справедливость)**, который на протяжении десятилетий читал философ **Майкл Сэндел (Michael J. Sandel)** в знаменитом театре Сандерса (Sanders Theatre).",
        "",
        "Курс построен вокруг **трёх великих подходов к пониманию справедливости**:",
        "1. **Максимизация благосостояния (Утилитаризм)** — Иеремия Бентам и Джон Стюарт Милль: справедливость как наибольшее счастье для наибольшего числа людей.",
        "2. **Уважение свободы и индивидуальных прав** — от либертарианства свободного рынка (Джон Локк, Роберт Нозик, Милтон Фридман) до деонтологии чистого разума (Иммануил Кант) и эгалитарного общественного договора (Джон Ролз).",
        "3. **Культивирование добродетели и общее благо** — классическая телеология Аристотеля и современная коммунитаристская философия (Аласдер Макинтайр, Майкл Сэндел).",
        "",
        "---",
        "",
        "## Программа курса (12 лекций)",
        ""
    ]

    for lec in lectures:
        num = f"{lec['id']:02d}"
        readme_lines.append(f"### [Лекция {lec['id']}. {lec['title']}](lecture-{num}.md)")
        readme_lines.append(f"*{lec['subtitle']}*")
        readme_lines.append(f"- **Модуль:** {lec['module']}")
        readme_lines.append(f"- **Ключевые мыслители:** {', '.join(lec['philosophers'])}")
        readme_lines.append(f"- **Главный кейс:** {lec['caseStudy']['title']}")
        readme_lines.append(f"- **Краткое содержание:** {lec['summary']}")
        readme_lines.append("")

    with open("course/README.md", "w", encoding="utf-8") as f:
        f.write("\n".join(readme_lines))

    # 2. Generate individual lecture files and combined COURSE_JUSTICE_HARVARD.md
    full_book_lines = [
        "# ГАРВАРДСКИЙ КУРС ЛЕКЦИЙ: «СПРАВЕДЛИВОСТЬ: КАК ПОСТУПАТЬ ПРАВИЛЬНО?»",
        "",
        "**Авторский учебно-философский курс на русском языке по мотивам лекций профессора Майкла Сэндела (Гарвардский университет)**",
        "",
        "---",
        "",
        "## Оглавление",
        ""
    ]

    for lec in lectures:
        full_book_lines.append(f"{lec['id']}. **Лекция {lec['id']}. {lec['title']}** — *{lec['subtitle']}*")
    full_book_lines.append("\n---\n")

    for lec in lectures:
        num = f"{lec['id']:02d}"
        lec_md = []
        lec_md.append(f"# Лекция {lec['id']}. {lec['title']}")
        lec_md.append(f"### {lec['subtitle']}")
        lec_md.append("")
        lec_md.append(f"- **Модуль курса:** {lec['module']}")
        lec_md.append(f"- **Ключевые мыслители:** {', '.join(lec['philosophers'])}")
        lec_md.append(f"- **Время вдумчивого чтения:** ~{lec['readingMinutes']} мин.")
        lec_md.append("")
        lec_md.append(f"> «{lec['epigraph']['quote']}»")
        lec_md.append(f"> — **{lec['epigraph']['author']}**")
        lec_md.append("")
        lec_md.append("## Главный тезис лекции")
        lec_md.append("")
        lec_md.append(lec["summary"])
        lec_md.append("")

        for idx, sec in enumerate(lec["sections"], 1):
            lec_md.append(f"## {idx}. {sec['heading']}")
            lec_md.append("")
            lec_md.append(sec["body"])
            lec_md.append("")
            if sec.get("keyQuote"):
                lec_md.append(f"> 💡 **Ключевая мысль:** {sec['keyQuote']}")
                lec_md.append("")

        cs = lec["caseStudy"]
        lec_md.append(f"## Практикум в театре Сандерса: {cs['title']}")
        lec_md.append("")
        lec_md.append(cs["scenario"])
        lec_md.append("")
        lec_md.append("### Аргументы стороны А")
        lec_md.append(cs["sideA"])
        lec_md.append("")
        lec_md.append("### Аргументы стороны Б")
        lec_md.append(cs["sideB"])
        lec_md.append("")
        lec_md.append("### Философский вывод Майкла Сэндела")
        lec_md.append(cs["conclusion"])
        lec_md.append("")

        lec_md.append("## Ключевые выводы лекции")
        lec_md.append("")
        for takeaway in lec["takeaways"]:
            lec_md.append(f"- {takeaway}")
        lec_md.append("")

        lec_md.append("## Вопросы для самопроверки и семинарской дискуссии")
        lec_md.append("")
        for q_idx, q in enumerate(lec["reflectionQuestions"], 1):
            lec_md.append(f"{q_idx}. {q}")
        lec_md.append("")

        lec_md.append("## Тест по материалам лекции")
        lec_md.append("")
        for q_idx, quiz in enumerate(lec["quiz"], 1):
            lec_md.append(f"**Вопрос {q_idx}. {quiz['question']}**")
            for opt_idx, opt in enumerate(quiz["options"]):
                prefix = "✅" if opt_idx == quiz["correctIndex"] else "▫️"
                lec_md.append(f"- {prefix} {opt}")
            lec_md.append(f"  - *Пояснение:* {quiz['explanation']}")
            lec_md.append("")

        lec_text = "\n".join(lec_md)
        with open(f"course/lecture-{num}.md", "w", encoding="utf-8") as f:
            f.write(lec_text)

        full_book_lines.append(lec_text)
        full_book_lines.append("\n---\n")

    # Append Philosophers and Glossary to the full book
    full_book_lines.append("# Приложение А. Галерея философов курса\n")
    for p in PHILOSOPHERS:
        full_book_lines.append(f"## {p['name']} ({p['years']})")
        full_book_lines.append(f"- **Школа мысли:** {p['school']}")
        full_book_lines.append(f"- **Главный труд:** *{p['mainWork']}*")
        full_book_lines.append(f"- **Формула справедливости:** {p['coreIdea']}")
        full_book_lines.append(f"\n{p['biography']}\n")
        full_book_lines.append(f"**Уязвимое место / критика:** {p['critique']}\n")

    full_book_lines.append("\n---\n# Приложение Б. Философский словарь курса\n")
    for g in GLOSSARY:
        full_book_lines.append(f"### {g['term']} (*{g['origin']}*)")
        full_book_lines.append(f"{g['definition']}\n")

    with open("COURSE_JUSTICE_HARVARD.md", "w", encoding="utf-8") as f:
        f.write("\n".join(full_book_lines))

    # 3. Generate JSON for the Android application
    course_json = {
        "courseTitle": "Гарвард: Справедливость",
        "courseSubtitle": "Курс лекций о моральной и политической философии по мотивам курса Майкла Сэндела",
        "university": "Harvard University • Moral Reasoning 22 (ER22)",
        "professor": "Профессор Майкл Сэндел (Michael J. Sandel)",
        "lectures": lectures,
        "dilemmas": DILEMMAS,
        "philosophers": PHILOSOPHERS,
        "glossary": GLOSSARY
    }

    with open("app/src/main/assets/course_data.json", "w", encoding="utf-8") as f:
        json.dump(course_json, f, ensure_ascii=False, indent=2)

    print(f"Successfully generated {len(lectures)} lectures, {len(DILEMMAS)} dilemmas, {len(PHILOSOPHERS)} philosophers, {len(GLOSSARY)} glossary terms.")

if __name__ == "__main__":
    main()
