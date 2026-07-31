import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { build, serialise } from "../generate-ios-data/index";

const OUT = resolve(
  import.meta.dirname,
  "../../android/app/src/main/assets/EmojiData.json",
);

const data = build();
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, serialise(data), "utf8");
console.log(
  `wrote ${OUT}\n  ${data.emojis.length} emojis, ${data.countables.length} countables, ` +
    `${data.languages.length} languages, ${data.categories.length} categories`,
);

