# Animation Spec

## Library: react-native-reanimated 3
All animations run on the UI thread for 60fps. No JS-thread animations.

## Animation Inventory

### 1. Emoji Tap Bounce
**Trigger**: Emoji button pressed
**Duration**: 350ms
**Easing**: spring(damping: 12, stiffness: 200)
```
Scale: 1.0 → 0.88 (press) → 1.25 (overshoot) → 1.0 (settle)
```

### 2. Typing Row Pop-In
**Trigger**: Emoji added to typing row
**Duration**: 300ms
**Easing**: spring(damping: 15, stiffness: 250)
```
Scale: 0 → 1.3 → 1.0
Opacity: 0 → 1
```

### 3. Word Bubble Float
**Trigger**: Emoji tapped (shows word label)
**Duration**: 2000ms
**Easing**: ease-in-out
```
0ms:    opacity 0, translateY +10, scale 0.8
400ms:  opacity 1, translateY 0, scale 1.05
600ms:  scale 1.0
1600ms: opacity 1
2000ms: opacity 0, translateY -8
```

### 4. Category Switch
**Trigger**: Category tab tapped
**Duration**: 200ms
**Easing**: ease-out
```
Grid items: staggered fade (20ms delay per item, max 200ms total)
Opacity: 0 → 1
Scale: 0.95 → 1.0
```

### 5. Language Picker Dropdown
**Trigger**: Language button tapped
**Duration**: 250ms
**Easing**: spring(damping: 20, stiffness: 300)
```
Open:  scale 0.95 → 1.0, opacity 0 → 1, translateY -8 → 0
Close: scale 1.0 → 0.95, opacity 1 → 0, translateY 0 → -8
```

### 6. Celebration Confetti (Phase 2)
**Trigger**: Word milestone reached (10, 25, 50, 100 words)
**Duration**: 3000ms
**Particles**: 40-60 colored shapes
```
Each particle:
- Random x position across screen width
- Fall from top with slight horizontal drift
- Random rotation
- Random color from accent palette
- Fade out in last 500ms
```

### 7. Replay Highlight (Phase 2)
**Trigger**: Replay all button pressed
**Duration**: Per emoji in sequence
```
Current emoji in typing row:
- Scale: 1.0 → 1.3 → 1.0 (300ms)
- Background: transparent → accent blue 20% → transparent
```

## Performance Rules
1. ALWAYS use `useAnimatedStyle` and `useSharedValue` from Reanimated
2. NEVER use `Animated.Value` from React Native core
3. NEVER trigger layout animations on the JS thread
4. Use `withSpring` for interactive feedback, `withTiming` for transitions
5. Set `reduceMotion` check: skip animations if system preference is set
6. Test animations on iPhone SE (lowest-spec supported device)

## Haptic Feedback
Pair with Expo Haptics:
- Emoji tap: `Haptics.impactAsync(ImpactFeedbackStyle.Light)`
- Replay: `Haptics.impactAsync(ImpactFeedbackStyle.Medium)`
- Clear all: `Haptics.notificationAsync(NotificationFeedbackType.Warning)`
- Milestone: `Haptics.notificationAsync(NotificationFeedbackType.Success)`
