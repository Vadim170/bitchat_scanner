# UI Changes Summary

## New User Interface

The main screen now shows:

### Status Indicator
```
"Scanning will start automatically" (when stopped)
"Active scanning (in app)" (when IN_APP mode active)
"Background scanning (service)" (when SERVICE mode active)
"Background scanning (passive)" (when PASSIVE mode active)
```

### Background Control Button
Single button that toggles background scanning:
- **When stopped**: "Start Background Scanning"
- **When running**: "Stop Background Scanning"

### Background Service Checkbox
```
☑ Work in background service
   Stable background operation. Disabling uses passive scanning (energy efficient)
```

### Notify Checkbox (unchanged)
```
☑ Notify about detections
```

## Behavior

1. **App Opens** → IN_APP scanning starts automatically
2. **User starts background scanning**:
   - If checkbox **ON** → Starts SERVICE mode (foreground service)
   - If checkbox **OFF** → Starts PASSIVE mode (system callbacks)
3. **User stops background scanning** → Returns to IN_APP mode automatically
4. **User closes app** → IN_APP stops, background scanning continues if active

## Mode Comparison

| Mode | When Active | Energy Usage | Stability |
|------|-------------|--------------|-----------|
| IN_APP | App open | Medium | Good |
| SERVICE | Always (with notification) | High | Excellent |
| PASSIVE | Always (no notification) | Low | Good |

## Key Improvements

✅ No manual buttons for IN_APP (automatic)
✅ Single button for background control
✅ Clear explanation of stable vs energy-efficient
✅ Clean imports (no full class paths)
✅ Scalable architecture for future modes
