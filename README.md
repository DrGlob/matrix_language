### Локальный запуск
```
./gradlew run --args="example.matrix"
```

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
