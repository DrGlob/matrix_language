### Локальный запуск
```
./gradlew run --args="example.matrix"
```

### Особенности DSL
- `if` — выражение: `let x = if cond then expr1 else expr2`
- Локальное связывание: `let a = expr1 in expr2`
- Лямбды и коллекции: `array.map { x -> x * 2 }`, `array.reduce(0) { acc, x -> acc + x }`
- Можно опустить параметры лямбды с одним аргументом: `array.map { transform(it) }`
- zip/unzip: `let pairs = zip(a, b)` и `let parts = pairs.unzip()` (доступ к `parts.first`/`parts.second`)
- Значения типизированы через `Value`: числа, матрицы, списки, пары, функции/лямбды и `unit`
- `for` убран из синтаксиса (ключевое слово зарезервировано)

### Docker
1) Собрать образ:
```
docker build -t matrix-dsl .
```
2) Запустить REPL (интерактивно):
```
docker run --rm -it matrix-dsl
```
3) Запустить пример из репозитория:
```
docker run --rm matrix-dsl /app/example.matrix
```
4) Запустить свой файл, пробрасывая его в контейнер:
```
docker run --rm -v $(pwd)/my.matrix:/data/my.matrix matrix-dsl /data/my.matrix
```
