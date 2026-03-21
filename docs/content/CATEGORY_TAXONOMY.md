# Category Taxonomy

## Design Principles
1. Categories use emojis as icons (on-brand)
2. Category names are short (one word) for the tab bar
3. "All" is always first
4. Order: concrete/familiar → abstract (fruits before faces)
5. Free tier: at least 2-3 emojis visible per category

## Categories (v1)

| Order | ID | Label | Icon | Free Count | Pro Count | Total |
|-------|-----|-------|------|-----------|-----------|-------|
| 0 | all | All | 🌟 | 30 | 91 | 121 |
| 1 | fruits | Fruits | 🍎 | 5 | 10 | 15 |
| 2 | food | Food | 🍕 | 5 | 23 | 28 |
| 3 | animals | Animals | 🐶 | 8 | 20 | 28 |
| 4 | vehicles | Vehicles | 🚗 | 4 | 6 | 10 |
| 5 | nature | Nature | 🌈 | 4 | 6 | 10 |
| 6 | objects | Things | 🎈 | 2 | 15 | 17 |
| 7 | people | People | 👶 | 0 | 7 | 7 |
| 8 | faces | Faces | 😀 | 2 | 4 | 6 |

## Future Categories (Phase 3+)
- Clothes (👕👗🧦🧤)
- Weather (🌤️⛈️🌪️🌁)
- Instruments (🎹🥁🎺🎻)
- Sports (🏊⛷️🤸🏋️)
- Places (🏖️🏔️🏰🎡)
- Season packs (🎃 Halloween, 🎄 Christmas, 🧧 CNY)

## Localized Category Names
Store in categories.json:
```json
{
  "id": "animals",
  "label": {
    "en": "Animals",
    "zh": "动物",
    "ms": "Haiwan",
    "ja": "どうぶつ",
    "es": "Animales"
  }
}
```
