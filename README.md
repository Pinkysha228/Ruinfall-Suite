# Ruinfall Suite 1.21.4

единый Paper-плагин для Minecraft 1.21.4. Все семь исходных подсистем собраны в **один JAR** и загружаются одним `plugin.yml`.

## Что входит

- Acid Rain: `/aweather`
- Particle Shields: `/particleshield`
- Bed Bombs
- Herobrine Ritual: `/startritual`
- Custom Arsenal: `/giveitems`
- Radiation Zones
- Event Mobs: `/ediff`

## Конфиги

Каждая подсистема сохраняет свой отдельный YAML:

`plugins/Ruinfall Suite/configs/`

- `acid-rain.yml`
- `particle-shields.yml`
- `bed-bombs.yml`
- `herobrine-ritual.yml`
- `custom-arsenal.yml`
- `radiation-zones.yml`
- `event-mobs.yml`

Это один плагин, но конфиги независимы. Красота человеческой инженерии, когда хаос хотя бы разложен по папкам.

## Сборка

Требуется JDK 21. Maven автоматически скачает Paper API 1.21.4 из репозитория PaperMC.

```bash
mvn clean package
```

Готовый файл:

`target/Ruinfall-Suite-1.21.4.jar`

## Установка

Положить **только этот один JAR** в:

`server/plugins/`

После первого запуска конфиги появятся в:

`plugins/Ruinfall Suite/configs/`

Данные ритуала и прогресс Event Mobs сохраняются отдельно в корне папки плагина.

## Логирование

Каждая подсистема пишет сообщения запуска и отключения. События можно включать/выключать через `logging.enabled` и `logging.events` в соответствующем конфиге.
