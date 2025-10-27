# BitChat Scanner - Архитектура кода

## 📁 Структура проекта

Проект полностью рефакторен для улучшения читаемости и поддерживаемости. Код разделён на логические модули:

```
com.vadim170.bitchatscanner/
├── MainActivity.kt                    # Точка входа + навигация (89 строк)
│
├── screens/                           # UI экраны приложения
│   ├── PermissionScreen.kt           # Экран запроса разрешений
│   ├── MainScreen.kt                 # Главный экран со списком устройств
│   ├── LogsScreen.kt                 # Экран детальных логов
│   └── MapScreen.kt                  # Полноэкранная карта всех обнаружений
│
├── components/                        # Переиспользуемые UI компоненты
│   ├── PermissionCard.kt             # Карточка описания разрешения
│   ├── DeviceCard.kt                 # Карточка устройства со всей информацией
│   └── DeviceMapView.kt              # Компонент мини-карты для карточки
│
├── utils/                             # Утилиты и вспомогательные функции
│   ├── PermissionUtils.kt            # Работа с разрешениями Android
│   ├── RssiUtils.kt                  # Вычисления на основе RSSI
│   └── MapUtils.kt                   # Работа с OSMdroid картами
│
├── constants/                         # Константы приложения
│   └── MapConstants.kt               # Константы для карт и отображения
│
├── viewmodel/                         # ViewModels (существующие)
│   ├── MainViewModel.kt
│   └── DevicesViewModel.kt
│
└── repository/                        # Data layer (существующие)
    └── ScannerRepository.kt
```

## 🎯 Основные улучшения

### 1. **Разделение ответственности**
- **MainActivity.kt**: Только Activity + навигация (было 997 строк → стало 89)
- **Screens**: Каждый экран в отдельном файле
- **Components**: Переиспользуемые UI элементы
- **Utils**: Чистые функции без UI логики
- **Constants**: Все магические числа вынесены в константы

### 2. **Устранение дублирования кода**
- Логика фильтрации точек на карте: `MapUtils.filterLocationsByMinRadius()`
- Создание кругов обнаружения: `MapUtils.createDetectionCircle()`
- Цвета по RSSI: `RssiUtils.getFillColorForRSSI()` / `getStrokeColorForRSSI()`
- Расчёт радиусов: `RssiUtils.calculateRadiusFromRSSI()`

### 3. **Именованные константы**
```kotlin
MapConstants.RECENT_DETECTION_THRESHOLD_MS  // 60_000L
MapConstants.MIN_MAP_SIZE_DEGREES           // 0.004
MapConstants.CIRCLE_POINTS_COUNT            // 32
MapConstants.SINGLE_POINT_ZOOM              // 16.0
```

### 4. **Документация**
- KDoc комментарии для всех публичных функций
- `@param` и `@return` теги
- Описание назначения каждого файла

## 📝 Как добавлять новые функции

### Добавить новый экран
1. Создать файл в `screens/NewScreen.kt`
2. Добавить экран в enum `Screen` в `MainActivity.kt`
3. Добавить навигацию в `RootScreen()`

### Добавить компонент
1. Создать файл в `components/MyComponent.kt`
2. Использовать компонент в нужных экранах

### Изменить логику карт
1. Изменить константы в `constants/MapConstants.kt`
2. Изменить утилиты в `utils/MapUtils.kt`
3. Логика RSSI в `utils/RssiUtils.kt`

### Добавить/изменить разрешения
1. Изменить `utils/PermissionUtils.kt`
2. UI обновится автоматически в `PermissionScreen`

## 🔧 Ключевые компоненты

### MapUtils
Центральное место для всей логики работы с картами:
- `filterLocationsByMinRadius()` - убирает дубликаты координат
- `calculateExpandedBoundingBox()` - вычисляет область карты
- `createDetectionCircle()` - создаёт круг на карте
- `calculateCenter()` - находит центр точек

### RssiUtils
Работа с силой сигнала:
- `calculateRadiusFromRSSI()` - радиус по силе сигнала
- `getFillColorForRSSI()` - цвет заливки круга
- `getStrokeColorForRSSI()` - цвет обводки круга

### DeviceCard
Умная карточка устройства:
- Автоматическая загрузка локаций при прокрутке
- Кэширование данных
- Индикация недавних обнаружений (зелёный цвет)
- Ленивая загрузка карт

## 🎨 Цветовая схема RSSI

| RSSI (dBm) | Расстояние | Цвет заливки | Цвет обводки | Радиус |
|------------|-----------|--------------|--------------|--------|
| > -50      | Очень близко | Зелёный | Темно-зелёный | 5м |
| > -60      | Близко | Зелёный | Темно-зелёный | 10м |
| > -70      | Средне | Жёлтый | Темно-жёлтый | 20м |
| > -80      | Далеко | Жёлтый | Темно-жёлтый | 50м |
| ≤ -80      | Очень далеко | Красный | Темно-красный | 100м |

## 🚀 Производительность

### Оптимизации карт
- Видимые элементы отслеживаются через `derivedStateOf`
- Карты загружаются только для видимых карточек
- Кэширование через `mapView.tag` предотвращает перерисовку
- Фильтрация дубликатов координат

### Оптимизация списков
- `LazyColumn` с ключами для стабильности
- Загрузка локаций только при прокрутке к элементу
- Проверка изменений через timestamp

## 📚 Дополнительные ресурсы

- **OSMdroid**: https://github.com/osmdroid/osmdroid
- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **BLE на Android**: https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview
