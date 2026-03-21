# Web Speech API — TTS Guide for PWA

## Overview
The Web Speech API (speechSynthesis) is built into all modern browsers. It uses the device's native TTS voices — meaning iOS Safari uses Apple's excellent voices, and Android Chrome uses Google's.

## Browser Support
| Browser | EN | ZH (Mandarin) | Offline | Notes |
|---------|-----|---------------|---------|-------|
| iOS Safari | ✅ Excellent | ✅ Excellent | ✅ Yes | Best quality — uses Siri voices |
| Android Chrome | ✅ Good | ✅ Good | ✅ Yes | Uses Google TTS |
| Desktop Chrome | ✅ Good | ✅ Good | ⚠️ Some voices need network | OK for testing |
| Desktop Safari | ✅ Good | ✅ Good | ✅ Yes | |

## Implementation

```typescript
const speak = (text: string, lang: 'en-US' | 'zh-CN') => {
  // Cancel any current speech (handles rapid tapping)
  speechSynthesis.cancel();

  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang;
  utterance.rate = 0.85;   // Slower for toddler comprehension
  utterance.pitch = 1.1;   // Slightly higher = friendlier

  // iOS Safari bug: voices may not be loaded on first call
  // Workaround: use voiceschanged event on app init
  const voices = speechSynthesis.getVoices();
  const targetVoice = voices.find(v => v.lang.startsWith(lang.split('-')[0]));
  if (targetVoice) {
    utterance.voice = targetVoice;
  }

  speechSynthesis.speak(utterance);
};
```

## Known Issues & Workarounds

### iOS Safari: First speech delayed
On iOS, `speechSynthesis.speak()` may fail silently on the very first call.
**Fix**: Trigger a silent utterance on first user interaction:
```typescript
const initTTS = () => {
  const silent = new SpeechSynthesisUtterance('');
  silent.volume = 0;
  speechSynthesis.speak(silent);
};
// Call on first tap anywhere in the app
```

### iOS Safari: Speech stops after ~15 seconds of no interaction
Safari pauses the speech synthesis queue if the user hasn't interacted recently.
**Fix**: Not an issue for us — toddlers are constantly tapping.

### iOS Safari: Voices not immediately available
`speechSynthesis.getVoices()` returns empty array on first call.
**Fix**: Listen for the `voiceschanged` event:
```typescript
useEffect(() => {
  const loadVoices = () => {
    const voices = speechSynthesis.getVoices();
    // Store ZH voice for faster access
    const zhVoice = voices.find(v => v.lang === 'zh-CN' || v.lang === 'zh_CN');
    setChineseVoice(zhVoice || null);
  };
  speechSynthesis.addEventListener('voiceschanged', loadVoices);
  loadVoices(); // Try immediately too
  return () => speechSynthesis.removeEventListener('voiceschanged', loadVoices);
}, []);
```

### Android Chrome: Some voices are network-dependent
Some higher-quality Google TTS voices require internet.
**Fix**: The default offline voices are fine for our use case. Acceptable quality.

## Voice Selection Priority

### English (en-US)
1. iOS: "Samantha" (compact) — always available offline
2. Android: default en-US voice
3. Fallback: any voice where `lang.startsWith('en')`

### Mandarin (zh-CN)
1. iOS: "Ting-Ting" — excellent quality, offline
2. Android: default zh-CN voice
3. Fallback: any voice where `lang.startsWith('zh')`

## Testing Checklist
- [ ] English words on iPhone Safari
- [ ] Mandarin words on iPhone Safari
- [ ] English words on Android Chrome
- [ ] Mandarin words on Android Chrome
- [ ] Rapid tapping (10 emojis in 3 seconds) — no crash
- [ ] Works with phone on silent mode (iOS)
- [ ] Works after "Add to Home Screen" (standalone mode)
- [ ] Works offline (airplane mode)
