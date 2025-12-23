# matrix_language

Matrix_language — это DSL для матричных вычислений и экспериментов с функциональным стилем поверх Kotlin/JVM.
Проект объединяет ядро матриц, интерпретатор .matrix и вспомогательные инструменты.

## Архитектура репозитория
- `src/main/kotlin/core` — матрицы, умножение, конфиги, блоковые и out-of-core представления.
- `src/main/kotlin/dsl` — Kotlin-DSL над core (builder-стиль).
- `src/main/kotlin/parser` — лексер, парсер, AST, интерпретатор, stdlib.
- `src/main/kotlin/syntax` — справочный слой ключевых слов/операторов DSL.
- `src/main/kotlin/type` — типовая модель и инференс для AST.
- `src/main/kotlin/planner` — граф вычислений, оценка стоимости и базовый execution pipeline.
- `src/main/kotlin/cli` — запуск файлов и REPL.
- `android-viewer` — TODO: модуля нет в репозитории (только планируется визуализация).

## Что за язык и зачем
Язык .matrix позволяет описывать матричные вычисления декларативно:
- матричные литералы `[[1, 2], [3, 4]]` и вектора `[1, 2, 3]`,
- выражения `if ... then ... else ...` и `let ... in ...`,
- лямбды `{ x -> expr }` и `{ expr }` (с `it`),
- функции высшего порядка `map/reduce/zip/unzip`,
- stdlib-операции `zeros/ones/identity/transpose/norm/poly`.

Цель — быстро описывать линейную алгебру и проверять разные стратегии умножения.

## Запуск
REPL:
```bash
./gradlew run
```

Запуск файла .matrix:
```bash
./gradlew run -Pargs="examples/map_reduce.matrix"
```

Альтернатива:
```bash
./gradlew runFile -Pfile=examples/functional_if.matrix
```

JAR:
```bash
./gradlew jar
java -jar build/libs/matrix_language-1.0-SNAPSHOT.jar examples/map_reduce.matrix
```

## Примеры
- `examples/functional_if.matrix` — if как выражение.
- `examples/map_reduce.matrix` — map/reduce.
- `examples/matrix_functional_ops.matrix` — map/reduce над матрицами.
- `examples/polynomial_matrix.matrix` — полиномы матриц с выбором алгоритма умножения.

## Параллельные вычисления
Параллельность реализована на уровне умножения матриц в core:
- `MultiplicationAlgorithm.PARALLEL` использует корутины на блоках.
- выбор алгоритма задается через `MatMulConfig` или через stdlib `polyWith`.

`planner` строит граф вычислений по AST и оценивает стоимость узлов.
Execution pipeline исполняет выражение через интерпретатор и может эмитить события по узлам графа.
Параллельного планировщика AST пока нет.

## Android viewer
TODO: отсутствует модуль `android-viewer` и формат JSON-событий исполнения.
Предполагаемая цель — визуализация DAG/AST событий по replay-файлу.

## Docker
1) Собрать образ:
```bash
docker build -t matrix-dsl .
```
2) Запустить REPL (интерактивно):
```bash
docker run --rm -it matrix-dsl
```
3) Запустить пример из репозитория:
```bash
docker run --rm matrix-dsl /app/example.matrix
```
4) Запустить свой файл, пробрасывая его в контейнер:
```bash
docker run --rm -v $(pwd)/my.matrix:/data/my.matrix matrix-dsl /data/my.matrix
```
