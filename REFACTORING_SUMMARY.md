# Рефакторинг логики сканирования BLE устройств

## Обзор изменений

Этот рефакторинг извлекает логику BLE сканирования в отдельный singleton-класс `BleScannerManager`, который может работать в двух режимах:
1. **IN_APP** - сканирование внутри приложения (когда оно открыто)
2. **SERVICE** - сканирование через foreground service (фоновый режим)

Главное преимущество: только один экземпляр сканера может быть активен одновременно, независимо от режима.

## Новые файлы

### BleScannerManager.kt
Центральный singleton для управления BLE сканированием:
- **Thread-safe singleton** с двойной проверкой блокировки
- **Два режима работы**: `ScanMode.IN_APP` и `ScanMode.SERVICE`
- **Взаимная эксклюзивность**: автоматически останавливает предыдущий режим при запуске нового
- **Единая логика**: вся обработка результатов сканирования в одном месте
- **Broadcast сообщения**: отправляет события `ACTION_SCANNER_STARTED`, `ACTION_SCANNER_STOPPED`, `ACTION_LOG_LINE`

Основные методы:
```kotlin
fun startScanning(mode: ScanMode): Boolean
fun stopScanning()
fun isScanning(): Boolean
fun getCurrentMode(): ScanMode?
```

### BleScannerManagerTest.kt
Простые unit-тесты для проверки:
- Корректность значений enum `ScanMode`
- Доступность публичных констант
- Сравнение режимов сканирования

## Изменённые файлы

### BleScannerService.kt
**До**: Содержал всю логику BLE сканирования (callback, обработка результатов, фильтрация)
**После**: Делегирует сканирование `BleScannerManager`:
- Удалены: `callback`, `handleResult()`, `startScan()`, `stopScan()`, `getBestLastKnownLocation()`
- Оставлено: Foreground service инфраструктура, уведомления
- Добавлено: `detectionReceiver` для получения событий от `BleScannerManager`
- Использует: `scannerManager.startScanning(ScanMode.SERVICE)` при старте

### MainViewModel.kt
**До**: Только управление foreground service
**После**: Поддерживает оба режима сканирования:
- Добавлен: `enum ScanningMode { NONE, IN_APP, SERVICE }`
- Новый метод: `startInAppScanning()` - запускает сканирование без service
- Улучшен: `stopScanner()` - останавливает любой активный режим
- Добавлено: `updateScannerState()` - отслеживает активный режим
- При закрытии ViewModel автоматически останавливает IN_APP сканирование

### MainScreen.kt
**До**: Одна кнопка Start/Stop Scanner
**После**: Разные кнопки в зависимости от состояния:
- Если не сканируем: показываем "Scan in App" и "Background Service"
- Если сканируем IN_APP: показываем "Stop In-App Scan"
- Если сканируем SERVICE: показываем "Stop Service"

### strings.xml
Добавлены новые строковые ресурсы:
- `start_in_app_scan` - "Scan in App"
- `start_service_scan` - "Background Service"
- `stop_in_app_scan` - "Stop In-App Scan"
- `stop_service_scan` - "Stop Service"

### ARCHITECTURE.md
Обновлена документация архитектуры:
- Добавлено описание `BleScannerManager`
- Документированы режимы IN_APP и SERVICE
- Объяснён механизм взаимной эксклюзивности
- Добавлены рекомендации для будущих расширений

## Преимущества архитектуры

### 1. Единая точка управления
Вся логика сканирования в одном классе - проще поддерживать и тестировать.

### 2. Взаимная эксклюзивность
Гарантированно работает только один сканер:
```kotlin
// Если уже сканируем в другом режиме, автоматически переключаемся
if (currentMode != null && currentMode != mode) {
    stopScanning()
}
```

### 3. Расширяемость
Легко добавить новый режим:
1. Добавить значение в `ScanMode` enum
2. Обновить UI для нового режима
3. Вся остальная логика остаётся без изменений

### 4. Thread-safe
Singleton безопасен для многопоточного использования:
```kotlin
@Synchronized
fun startScanning(mode: ScanMode): Boolean { ... }

@Synchronized
fun stopScanning() { ... }
```

## Сценарии использования

### Сценарий 1: Пользователь открывает приложение
1. UI показывает две кнопки: "Scan in App" и "Background Service"
2. Пользователь нажимает "Scan in App"
3. `MainViewModel.startInAppScanning()` вызывает `BleScannerManager.startScanning(IN_APP)`
4. Сканирование начинается без service
5. При закрытии приложения ViewModel автоматически останавливает сканирование

### Сценарий 2: Запуск фонового сканирования
1. Пользователь нажимает "Background Service"
2. `MainViewModel.startScanner()` запускает `BleScannerService`
3. Service вызывает `BleScannerManager.startScanning(SERVICE)`
4. Если был активен IN_APP режим, он автоматически останавливается
5. Сканирование продолжается в фоне даже при закрытом приложении

### Сценарий 3: Переключение режимов
1. Активно IN_APP сканирование
2. Пользователь запускает service
3. `BleScannerManager` автоматически останавливает IN_APP
4. Запускается SERVICE режим
5. Никаких конфликтов или дублирования

## Технические детали

### Singleton Pattern
```kotlin
companion object {
    @Volatile
    private var INSTANCE: BleScannerManager? = null
    
    fun getInstance(context: Context): BleScannerManager {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: BleScannerManager(context.applicationContext).also { 
                INSTANCE = it 
            }
        }
    }
}
```

### Broadcast события
Manager отправляет те же события, что раньше отправлял service:
- `ACTION_SCANNER_STARTED` - сканирование запущено
- `ACTION_SCANNER_STOPPED` - сканирование остановлено
- `ACTION_LOG_LINE` - новое обнаружение или событие

UI получает эти события через `BroadcastReceiver` и обновляется.

### Обработка разрешений
Manager проверяет разрешения перед запуском:
```kotlin
if (!hasScanPermission()) {
    sendLine("${sdf.format(Date())},no_scan_permission")
    return false
}
```

## Обратная совместимость

Все существующие функции сохранены:
- Service продолжает работать как foreground service
- UI получает те же broadcast события
- База данных и логирование работают без изменений
- Firebase Crashlytics логирует события

## Будущие улучшения

Архитектура готова для:
1. **Scheduled scanning** - запуск по расписанию
2. **Location-triggered scanning** - запуск при входе в геозону
3. **Battery-aware scanning** - адаптация под уровень батареи
4. **Smart scanning** - изменение частоты в зависимости от результатов

Для добавления нового режима достаточно:
- Добавить значение в `ScanMode` enum
- Создать новый способ запуска (Activity/WorkManager/AlarmManager)
- Использовать существующий `BleScannerManager`

## Тестирование

### Unit тесты
`BleScannerManagerTest.kt` проверяет:
- Корректность enum значений
- Доступность констант
- Базовую функциональность

### Интеграционное тестирование
Рекомендуется протестировать:
1. Запуск/остановка IN_APP режима
2. Запуск/остановка SERVICE режима
3. Переключение между режимами
4. Поведение при закрытии приложения
5. Восстановление после перезапуска приложения

## Миграция

Для пользователей изменения прозрачны:
- Если service был запущен, он продолжит работать
- Все сохранённые данные остаются доступны
- Настройки уведомлений сохраняются
- История обнаружений не теряется

## Выводы

Этот рефакторинг:
✅ Извлекает логику сканирования в отдельный singleton
✅ Поддерживает два режима работы (IN_APP и SERVICE)
✅ Гарантирует работу только одного сканера
✅ Сохраняет обратную совместимость
✅ Готов к расширению новыми режимами
✅ Улучшает тестируемость кода
✅ Упрощает поддержку и отладку
