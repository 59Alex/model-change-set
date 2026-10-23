# model-change-set 0.3.0 — руководство

[English](en.md) · [Русский](ru.md)

[Версии](../README.md) · [README](../../README.ru.md) · [Возможности Java](java.ru.md)

Сравнивайте снимки моделей по стабильному ключу и явно заданным правилам полей. Получайте упорядоченные добавления, удаления и изменения либо обрабатывайте отсортированные источники по одному изменению.

## Сводка возможностей

| Возможность | Применение | Пример |
| --- | --- | --- |
| 1. Установка и подготовка примера | Нужен полный JDK 17 или новее. Поддерживаются LTS-версии 17, 21, 25 и non-LTS Java 27. У библиотеки нет зависимостей времени выполнения. | [Код и результат](#1-установка-и-подготовка-примера) |
| 2. Сравнение снимков и порядок результата | Выберите стабильный ключ, полный порядок ключей и поля, изменение которых важно для вашей задачи. Входные коллекции могут быть в любом порядке. | [Код и результат](#2-сравнение-снимков-и-порядок-результата) |
| 3. Правила равенства и null | Field.of использует Objects.deepEquals: поддерживает null и содержимое массивов; другие значения сравниваются их методом equals. Field.comparing позволяет задать предметное правило равенства. | [Код и результат](#3-правила-равенства-и-null) |
| 4. Фильтры результата и сравнение состава | Используйте added, removed, modified и isEmpty, чтобы выбрать следующее действие приложения. Пустой список полей сравнивает только состав ключей; заданное подмножество исключает несущественные поля. | [Код и результат](#4-фильтры-результата-и-сравнение-состава) |
| 5. Последовательное сравнение отсортированных источников | compareSorted принимает два итератора со строго возрастающими ключами по тому же компаратору и синхронно передаёт каждое изменение обработчику. Оба снимка целиком не индексируются. | [Код и результат](#5-последовательное-сравнение-отсортированных-источников) |
| 6. Проверка ключей и границы поведения | Дубликаты ключей и неоднозначный компаратор приводят к явной ошибке. Направление сравнения и форма изменения остаются видимыми в каждом результате. | [Код и результат](#6-проверка-ключей-и-границы-поведения) |
| 7. Запуск полного примера и диагностика | Скачайте Example.java и сохраните в корне репозитория после сборки версии 0.3.0. Команды для POSIX shell используют обычный jar, а не архив исходников или Javadoc. | [Код и результат](#7-запуск-полного-примера-и-диагностика) |

## 1. Установка и подготовка примера

Нужен полный JDK 17 или новее. Поддерживаются LTS-версии 17, 21, 25 и non-LTS Java 27. У библиотеки нет зависимостей времени выполнения.

> **Локальный артефакт, не релиз в Central**
>
> Команды ниже собирают исходники и устанавливают версию 0.3.0 в локальный Maven-репозиторий. Наличие координат не означает, что артефакт опубликован в Maven Central.

```bash
git clone https://github.com/59Alex/model-change-set.git
cd model-change-set
./gradlew build publishToMavenLocal -PtestJavaVersion=17
```

Запустите в копии исходников версии 0.3.0; Gradle нужен доступ к своему дистрибутиву и зависимостям сборки.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:model-change-set:0.3.0")
}
```

Добавьте в build.gradle.kts приложения после локальной установки.

Java-фрагменты ниже образуют одно руководство: добавьте импорты перед классом Example, объявите Column внутри класса, а выполняемый код разместите по порядку в методе main. Следующие разделы явно используют differ, before, after, fields и changes из раздела 2. Скачиваемый Example.java уже объединяет все фрагменты.

```java
import io.github.alex59.modelchanges.Change;
import io.github.alex59.modelchanges.ChangeSet;
import io.github.alex59.modelchanges.ChangeType;
import io.github.alex59.modelchanges.Field;
import io.github.alex59.modelchanges.ModelDiffer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Declare this record inside your Example class.
record Column(String key, String type, boolean nullable) {}
```

Общие импорты и модель. Ключ сохраняет значение между снимками.

Файл для скачивания: examples/model-change-set/Example.java. Нужны только jar библиотеки и JDK; команды компиляции и запуска приведены в разделе 7.

## 2. Сравнение снимков и порядок результата

Выберите стабильный ключ, полный порядок ключей и поля, изменение которых важно для вашей задачи. Входные коллекции могут быть в любом порядке.

```java
List<Field<Column, ?>> fields = List.of(
    Field.of("type", Column::type),
    Field.of("nullable", Column::nullable)
);
var differ = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), fields
);
var before = List.of(
    new Column("orders.note", "text", true),
    new Column("orders.id", "bigint", false),
    new Column("orders.legacy", "text", true)
);
var after = List.of(
    new Column("orders.id", "bigint", false),
    new Column("orders.created_at", "timestamp", false),
    new Column("orders.note", "text", false)
);
var changes = differ.compare(before, after);
changes.changes().forEach(change -> {
    System.out.println(change.type() + " " + change.key());
    change.fields().forEach(field -> System.out.println(
        "  " + field.name() + ": " + field.before() + " -> " + field.after()
    ));
});
```

После общих объявлений из раздела 1 поместите код внутрь main.

```text
ADDED orders.created_at
REMOVED orders.legacy
MODIFIED orders.note
  nullable: true -> false
```

Ожидаемый вывод. Неизменённый orders.id отсутствует в отчёте.

- ADDED означает, что ключ есть только в after. before равен null, after содержит модель целиком, fields пуст.
- REMOVED означает, что ключ есть только в before. after равен null, fields пуст.
- MODIFIED означает, что ключ есть в обоих снимках и хотя бы одно настроенное поле отличается. FieldChange содержит имя поля и исходные значения before/after.
- Изменения упорядочены компаратором ключей. Изменённые поля идут в порядке их объявления. Смена ключа отображается как удаление и добавление.

```java
var reorderedBefore = new ArrayList<>(before);
Collections.reverse(reorderedBefore);
System.out.println("same report=" + changes.equals(
    differ.compare(reorderedBefore, after)
));
var reverseDiffer = ModelDiffer.<Column, String>of(
    Column::key, Comparator.reverseOrder(), fields
);
System.out.println("descending=" + reverseDiffer.compare(before, after)
    .changes().stream().map(Change::key).toList());
```

Использует before, after, fields и changes выше. Перестановка входных данных не меняет отчёт; другой компаратор меняет порядок отчёта.

```text
same report=true
descending=[orders.note, orders.legacy, orders.created_at]
```

Ожидаемый вывод.

## 3. Правила равенства и null

Field.of использует Objects.deepEquals: поддерживает null и содержимое массивов; другие значения сравниваются их методом equals. Field.comparing позволяет задать предметное правило равенства.

```java
record Price(String key, BigDecimal amount) {}
var amount = Field.<Price, BigDecimal>comparing(
    "amount", Price::amount,
    (a, b) -> a == null ? b == null
        : b != null && a.compareTo(b) == 0
);
var priceDiffer = ModelDiffer.<Price, String>of(
    Price::key, Comparator.naturalOrder(), List.of(amount)
);
System.out.println("field=" + amount.name());
System.out.println("same amount=" + priceDiffer.compare(
    List.of(new Price("price", new BigDecimal("1.0"))),
    List.of(new Price("price", new BigDecimal("1.00")))
).isEmpty());
var priceChange = priceDiffer.compare(
    List.of(new Price("price", null)),
    List.of(new Price("price", new BigDecimal("1.00")))
).modified().get(0).fields().get(0);
System.out.println(priceChange.name() + ": "
    + priceChange.before() + " -> " + priceChange.after());
```

Использует общие импорты. Price — отдельный локальный record; масштаб числа не учитывается, но null отличается от числа.

```text
field=amount
same amount=true
amount: null -> 1.00
```

Ожидаемый вывод. В отчёт попадают исходные значения; правило равенства их не нормализует.

```java
var nullToEmpty = differ.compare(
    List.of(new Column("note", null, true)),
    List.of(new Column("note", "", true))
);
var absentToNull = differ.compare(
    List.of(), List.of(new Column("note", null, true))
);
System.out.println("null-to-empty modified=" + nullToEmpty.modified().size());
System.out.println("absent-to-null added=" + absentToNull.added().size());

record Blob(String key, byte[] bytes) {}
var blobDiffer = ModelDiffer.<Blob, String>of(
    Blob::key, Comparator.naturalOrder(), List.of(Field.of("bytes", Blob::bytes))
);
System.out.println("equal arrays=" + blobDiffer.compare(
    List.of(new Blob("blob", new byte[] {1, 2})),
    List.of(new Blob("blob", new byte[] {1, 2}))
).isEmpty());
```

Использует differ из раздела 2, затем создаёт отдельную модель с массивом.

```text
null-to-empty modified=1
absent-to-null added=1
equal arrays=true
```

Ожидаемый вывод: поле null не равно отсутствующей модели или пустой строке.

> **Контракт функций**
>
> Пользовательское правило получает null без преобразования. Функции извлечения и сравнения должны быть чистыми и стабильными; при совместном использовании differ между потоками функции и данные также должны допускать это. Стандартное сравнение не обнаруживает рекурсивно произвольные поля модели.

## 4. Фильтры результата и сравнение состава

Используйте added, removed, modified и isEmpty, чтобы выбрать следующее действие приложения. Пустой список полей сравнивает только состав ключей; заданное подмножество исключает несущественные поля.

```java
System.out.println("added=" + changes.added().stream().map(Change::key).toList());
System.out.println("removed=" + changes.removed().stream().map(Change::key).toList());
System.out.println("modified=" + changes.modified().stream().map(Change::key).toList());
System.out.println("has changes=" + !changes.isEmpty());

var membershipOnly = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), List.of()
);
System.out.println("membership changes=" + membershipOnly.compare(before, after)
    .changes().size());
System.out.println("same identity=" + membershipOnly.compare(
    List.of(new Column("id", "int", true)),
    List.of(new Column("id", "bigint", false))
).isEmpty());

var typeOnly = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(), List.of(Field.of("type", Column::type))
);
System.out.println("ignored nullable=" + typeOnly.compare(
    List.of(new Column("id", "int", true)),
    List.of(new Column("id", "int", false))
).isEmpty());
```

Использует changes, before и after из раздела 2. Каждый отфильтрованный результат — неизменяемый список в порядке ключей.

```text
added=[orders.created_at]
removed=[orders.legacy]
modified=[orders.note]
has changes=true
membership changes=2
same identity=true
ignored nullable=true
```

Ожидаемый вывод. Сравнение только по составу ключей исключает изменение nullable.

> **Неизменяемая коллекция не означает глубокую копию**
>
> ChangeSet копирует список изменений, Change — список полей. Модели, ключи и значения полей остаются ссылками на объекты вызывающего кода. Используйте неизменяемые снимки, если сохраняете отчёт для последующего просмотра.

## 5. Последовательное сравнение отсортированных источников

compareSorted принимает два итератора со строго возрастающими ключами по тому же компаратору и синхронно передаёт каждое изменение обработчику. Оба снимка целиком не индексируются.

```java
var emitted = new ArrayList<Change<Column, String>>();
differ.compareSorted(
    before.stream().sorted(Comparator.comparing(Column::key)).iterator(),
    after.stream().sorted(Comparator.comparing(Column::key)).iterator(),
    emitted::add
);
System.out.println("same merge=" + changes.equals(new ChangeSet<>(emitted)));

var prefix = new ArrayList<String>();
try {
    differ.compareSorted(
        List.of(
            new Column("b", "int", false),
            new Column("a", "int", false)
        ).iterator(),
        Collections.emptyIterator(),
        change -> prefix.add(change.key())
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
System.out.println("emitted prefix=" + prefix);
```

Использует differ, before, after и changes. В первой части небольшие списки сортируются в памяти для примера; во второй порядок намеренно нарушен.

```text
same merge=true
Keys in before are not strictly increasing: b then a
emitted prefix=[b]
```

Ожидаемый вывод. Удаление b было выдано до того, как следующий ключ обнаружил нарушение порядка.

- Для большого снимка БД передавайте действительно упорядоченный курсор вместо сортировки неограниченного потока в памяти. Библиотека не выполняет SQL и не управляет снимками БД.
- Слияние удерживает одну текущую модель из каждого источника и одно изменение. Сборщик вроде emitted::add сохраняет весь отчёт; если важна память, используйте обработчик без накопления результата.
- Поздний дубликат, нарушение порядка, ошибка функции поля или обработчика могут оставить уже выданную часть результата. Исключение обработчика немедленно останавливает работу и передаётся вызывающему коду.
- Итераторы и курсоры БД закрывает вызывающий код. Библиотека не создаёт транзакцию, не откатывает действия обработчика, не запускает потоки и не реализует реактивное управление спросом.

> **Полный отчёт или постепенная выдача**
>
> Выбирайте compare, когда вызывающий код должен получить полный ChangeSet либо исключение. Выбирайте compareSorted, когда нужны упорядоченный вход и постепенная выдача. Ни один вариант не делает пользовательские функции транзакционными.

## 6. Проверка ключей и границы поведения

Дубликаты ключей и неоднозначный компаратор приводят к явной ошибке. Направление сравнения и форма изменения остаются видимыми в каждом результате.

```java
try {
    differ.compare(
        List.of(new Column("x", "int", false), new Column("x", "int", false)),
        List.of()
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
try {
    differ.compare(List.of(), List.of(new Column(null, "int", false)));
} catch (NullPointerException failure) {
    System.out.println(failure.getMessage());
}
var insensitive = ModelDiffer.<Column, String>of(
    Column::key, String.CASE_INSENSITIVE_ORDER, fields
);
try {
    insensitive.compare(
        List.of(new Column("a", "int", false)),
        List.of(new Column("A", "int", false))
    );
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
try {
    changes.changes().clear();
} catch (UnsupportedOperationException failure) {
    System.out.println("result is unmodifiable");
}
```

Использует differ, fields и changes. Ошибки намеренные и перехватываются примером.

```text
Duplicate key in before: x
Null key in after
Comparator treats unequal keys as identical: a and A
result is unmodifiable
```

Ожидаемый вывод.

```java
var broken = ModelDiffer.<Column, String>of(
    Column::key, Comparator.naturalOrder(),
    List.of(Field.of("broken", column -> {
        throw new IllegalArgumentException("bad value");
    }))
);
try {
    broken.compare(
        List.of(new Column("x", "int", false)),
        List.of(new Column("x", "int", false))
    );
} catch (IllegalStateException failure) {
    System.out.println(failure.getMessage());
    System.out.println("cause=" + failure.getCause().getMessage());
}
try {
    new Change<>("x", ChangeType.MODIFIED,
        new Column("x", "int", false),
        new Column("x", "bigint", false),
        List.of());
} catch (IllegalArgumentException failure) {
    System.out.println(failure.getMessage());
}
```

Ошибка извлечения поля сохраняет исходную причину. Созданные напрямую records также проверяют свою структуру.

```text
Cannot compare field 'broken' at key x
cause=bad value
Invalid change shape for MODIFIED
```

Ожидаемый вывод.

- Ключи и модели не могут быть null. Ключи должны быть стабильными, а полный порядок компаратора — согласованным с equals. Равенство моделей не разрешает повторение ключа.
- Если компаратор возвращает ноль для неравных ключей, обнаруженная коллизия отклоняется, в том числе между снимками. Библиотека не может доказать транзитивность произвольного компаратора.
- Имена полей не могут быть null или пустыми и должны быть уникальны внутри differ. Функции извлечения и сравнения не могут быть null. Повторные определения отклоняются до сравнения.
- Change проверяет ADDED: before равен null, after задан, fields пуст; REMOVED: before задан, after равен null, fields пуст; MODIFIED: обе модели заданы и fields непуст. Ключ и тип обязательны.
- Для n старых моделей, m новых и f полей compare требует O(n log n + m log m + min(n,m) × f) плюс создание результата и O(n + m + размер результата) памяти. Стоимость пользовательских функций и сравнения считается постоянной.
- compareSorted требует O(n + m + min(n,m) × f) и O(f) рабочей памяти без учёта размера моделей и данных, сохранённых обработчиком. Неотсортированный источник от этого не становится ограниченным по памяти.

> **Область ответственности**
>
> Библиотека формирует различия. Она не распознаёт переименования, не определяет обратную совместимость, не создаёт и не выполняет миграции БД, не хранит аудит, не сериализует отчёты и не обходит произвольные графы объектов.

## 7. Запуск полного примера и диагностика

Скачайте Example.java и сохраните в корне репозитория после сборки версии 0.3.0. Команды для POSIX shell используют обычный jar, а не архив исходников или Javadoc.

```bash
mkdir -p example-classes
javac --release 17 -cp build/libs/model-change-set-0.3.0.jar \
  -d example-classes Example.java
java -cp example-classes:build/libs/model-change-set-0.3.0.jar Example
```

Пример выполняет все Java-сценарии выше. В Windows используйте точку с запятой вместо двоеточия в classpath запуска.

```text
All examples completed.
```

Последняя строка после ожидаемого вывода разделов 2–6.

```bash
./gradlew clean build runExample -PtestJavaVersion=17
./gradlew clean build runExample -PtestJavaVersion=21
./gradlew clean build runExample -PtestJavaVersion=25
./gradlew clean build runExample -PtestJavaVersion=27
```

Запустите тесты библиотеки и встроенный пример схемы на каждом установленном JDK. Компиляция всегда использует API и байткод Java 17.

- package io.github.alex59.modelchanges does not exist: проверьте наличие обычного jar 0.3.0 и его путь в classpath javac либо установите артефакт локально до разрешения Gradle-зависимости.
- UnsupportedClassVersionError: проверьте java -version и выбранный jar. Версия 0.3.0 рассчитана на Java 17; артефакт прежней версии с минимумом Java 27 не взаимозаменяем с ней.
- No matching Java installation found: установите JDK, указанный в testJavaVersion, и обеспечьте его обнаружение Gradle. JRE без javac не сможет собрать проект.
- Duplicate key in before/after: исправьте входные данные или выберите настоящий составной ключ. Не удаляйте одну из моделей молча, чтобы скрыть коллизию.
- Keys in before/after are not strictly increasing: отсортируйте данные тем же компаратором, что использует differ. Перед повтором удалите или компенсируйте частичный внешний отчёт.
- Cannot compare field: изучите getCause(), затем исправьте функцию извлечения, правило сравнения или данные. Исключения обработчика результата передаются без этой обёртки поля.

## Скачиваемые примеры

- [Example.java](examples/Example.java)

[MIT](../../LICENSE)
