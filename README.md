# Distributed Raft Cache

Отказоустойчивое распределенное хранилище (In-Memory) на Java 21 & Spring Boot 3. Реализован алгоритм консенсуса **Raft** для выбора лидера и репликации данных без использования баз данных.

[![Java](https://img.shields.io/badge/Java-21-orange?style=plastic&logoColor=%23000000&labelColor=white
)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/spring-%25236DB33F.svg?style=plastic&logo=spring&logoColor=white
)](https://spring.io/projects/spring-boot)
[![Raft](https://img.shields.io/badge/Algorithm-Raft-red?style=plastic
)](https://raft.github.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=plastic
)](https://opensource.org/licenses/MIT)
<br>

## Стек технологий

*   Язык: Java 21
*   Фреймворк: Spring Boot 3
*   Многопоточность: ScheduledExecutorService (таймеры), CompletableFuture (сеть)
*   Хранилище: ConcurrentHashMap + JSON-файл (имитация записи на диск)
*   Сеть: RestTemplate (RPC вызовы между узлами)

## Как запустить (One Click)

Просто нажми кнопку Run в IDEA три раза. Чтобы узлы не конфликтовали, перед запуском каждого добавь параметр порта:

1.  **Первый узел (9090):**
    *   Нажми на выпадающий список рядом с кнопкой Play -> Edit Configurations.
    *   В поле **Program arguments** впиши: `--server.port=9090`
    *   Запусти.
2.  **Второй узел (9091):**
    *   Скопируй конфигурацию первого узла.
    *   Измени порт на: `--server.port=9091`
    *   Запусти.
3.  **Третий узел (9092):**
    *   Скопируй еще раз.
    *   Порт: `--server.port=9092`
    *   Запусти.

> 💡 **Совет:** Между запусками делай паузу в 2 секунды, чтобы первый успел стать лидером.


## Что должно получиться (Ожидаемый лог)

В терминалах ты увидишь распределение ролей. Это значит, что кластер работает стабильно:

**Узел-Лидер (например, 9090):**
```
[Node 9090] Starting election.
[Node 9090] Got vote (2/2) in term 14 
[Node 9090] Became LEADER for term 14 
[Node 9090] Heartbeat sent to 9091 
[Node 9090] Heartbeat sent to 9092
```
*(Лидер шлет пульсы до бесконечности)*  

**Узлы-Фолловеры (9091, 9092):**
```
[Node 9091] Stepping down. New term is 14 
[Node 9091] Received AppendEntries from leader node-9090
```
*(Они подчиняются лидеру и ничего не делают сами)*

## Тест на выживаемость (Failover)

Это главная фишка проекта. Проверь её так:
* Оставь все три терминала работать.
* Найди окно Лидера и закрой его (нажми красный крестик).
* Мгновенно посмотри на два оставшихся окна.
* Ты увидишь:
```
  [Node 9091] Starting election.
  [Node 9091] Got vote (2/2) in term 15
  [Node 9091] Became LEADER for term 15
```
* Кластер жив! Отправь новый curl новому Лидеру — всё работает.

## Кодовая база

Вся магия спрятана здесь:
*   service/RaftState.java — Сердце алгоритма (переходы состояний).
*   controller/RaftController.java — Входные точки (API эндпоинты /raft/*).
*   model/rpc/*.java — Сообщения, которые узлы кидают друг другу по сети.

Made with ☕ Java