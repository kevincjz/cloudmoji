import type { Language } from "../types";

export interface Countable {
  emoji: string;
  en: string;
  /** zh and ms bake the measure word in ("只狗", "ekor anjing"). */
  zh: string;
  ms: string;
  /** ja and tl stay BARE — the counter lives in the number word (ja) or the linker (tl). */
  ja: string;
  tl: string;
}

export const COUNTABLES: Countable[] = [
  { emoji: "🐮", en: "cow", zh: "头牛", ms: "ekor lembu", ja: "うし", tl: "baka" },
  { emoji: "🐶", en: "dog", zh: "只狗", ms: "ekor anjing", ja: "いぬ", tl: "aso" },
  { emoji: "🐱", en: "cat", zh: "只猫", ms: "ekor kucing", ja: "ねこ", tl: "pusa" },
  { emoji: "🐸", en: "frog", zh: "只青蛙", ms: "ekor katak", ja: "かえる", tl: "palaka" },
  { emoji: "🦁", en: "lion", zh: "头狮子", ms: "ekor singa", ja: "ライオン", tl: "leon" },
  { emoji: "🐘", en: "elephant", zh: "头大象", ms: "ekor gajah", ja: "ぞう", tl: "elepante" },
  { emoji: "🐧", en: "penguin", zh: "只企鹅", ms: "ekor penguin", ja: "ペンギン", tl: "pengwin" },
  { emoji: "🐷", en: "pig", zh: "只猪", ms: "ekor babi", ja: "ぶた", tl: "baboy" },
  { emoji: "🐰", en: "rabbit", zh: "只兔子", ms: "ekor arnab", ja: "うさぎ", tl: "kuneho" },
  { emoji: "🦆", en: "duck", zh: "只鸭子", ms: "ekor itik", ja: "あひる", tl: "bibe" },
  { emoji: "🐟", en: "fish", zh: "条鱼", ms: "ekor ikan", ja: "さかな", tl: "isda" },
  { emoji: "🐻", en: "bear", zh: "只熊", ms: "ekor beruang", ja: "くま", tl: "oso" },
  { emoji: "🐼", en: "panda", zh: "只熊猫", ms: "ekor panda", ja: "パンダ", tl: "panda" },
  { emoji: "🦋", en: "butterfly", zh: "只蝴蝶", ms: "ekor rama-rama", ja: "ちょうちょ", tl: "paruparo" },
  { emoji: "🐢", en: "turtle", zh: "只乌龟", ms: "ekor kura-kura", ja: "かめ", tl: "pagong" },
  { emoji: "🐵", en: "monkey", zh: "只猴子", ms: "ekor monyet", ja: "さる", tl: "unggoy" },
  { emoji: "🐔", en: "chicken", zh: "只鸡", ms: "ekor ayam", ja: "にわとり", tl: "manok" },
  { emoji: "🐝", en: "bee", zh: "只蜜蜂", ms: "ekor lebah", ja: "みつばち", tl: "bubuyog" },
  { emoji: "🦀", en: "crab", zh: "只螃蟹", ms: "ekor ketam", ja: "かに", tl: "alimango" },
  { emoji: "🦒", en: "giraffe", zh: "头长颈鹿", ms: "ekor zirafah", ja: "きりん", tl: "dyirap" },
  { emoji: "🐬", en: "dolphin", zh: "条海豚", ms: "ekor ikan lumba-lumba", ja: "いるか", tl: "dolpin" },
  { emoji: "🦈", en: "shark", zh: "条鲨鱼", ms: "ekor jerung", ja: "さめ", tl: "pating" },
  { emoji: "🐍", en: "snake", zh: "条蛇", ms: "ekor ular", ja: "へび", tl: "ahas" },
  { emoji: "🍎", en: "apple", zh: "个苹果", ms: "biji epal", ja: "りんご", tl: "mansanas" },
  { emoji: "🍌", en: "banana", zh: "根香蕉", ms: "biji pisang", ja: "バナナ", tl: "saging" },
  { emoji: "🍓", en: "strawberry", zh: "颗草莓", ms: "biji strawberi", ja: "いちご", tl: "strawberry" },
  { emoji: "🍊", en: "orange", zh: "个橙子", ms: "biji oren", ja: "みかん", tl: "dalandan" },
  { emoji: "🍉", en: "watermelon", zh: "个西瓜", ms: "biji tembikai", ja: "すいか", tl: "pakwan" },
  { emoji: "🍑", en: "peach", zh: "个桃子", ms: "biji pic", ja: "もも", tl: "melokoton" },
  { emoji: "🥭", en: "mango", zh: "个芒果", ms: "biji mangga", ja: "マンゴー", tl: "mangga" },
  { emoji: "🍋", en: "lemon", zh: "个柠檬", ms: "biji limau", ja: "レモン", tl: "limon" },
  { emoji: "🍕", en: "pizza", zh: "片披萨", ms: "keping piza", ja: "ピザ", tl: "pizza" },
  { emoji: "🧁", en: "cupcake", zh: "个纸杯蛋糕", ms: "biji kek cawan", ja: "カップケーキ", tl: "cupcake" },
  { emoji: "🍪", en: "cookie", zh: "块饼干", ms: "keping biskut", ja: "クッキー", tl: "biskwit" },
  { emoji: "🥚", en: "egg", zh: "个鸡蛋", ms: "biji telur", ja: "たまご", tl: "itlog" },
  { emoji: "🍩", en: "donut", zh: "个甜甜圈", ms: "biji donat", ja: "ドーナツ", tl: "donut" },
  { emoji: "🍔", en: "hamburger", zh: "个汉堡", ms: "biji burger", ja: "ハンバーガー", tl: "burger" },
  { emoji: "🥕", en: "carrot", zh: "根胡萝卜", ms: "batang lobak merah", ja: "にんじん", tl: "karot" },
  { emoji: "🍤", en: "shrimp", zh: "只虾", ms: "ekor udang", ja: "えび", tl: "hipon" },
  { emoji: "🌟", en: "star", zh: "颗星星", ms: "biji bintang", ja: "ほし", tl: "bituin" },
  { emoji: "🎈", en: "balloon", zh: "个气球", ms: "biji belon", ja: "ふうせん", tl: "lobo" },
  { emoji: "🚗", en: "car", zh: "辆车", ms: "buah kereta", ja: "くるま", tl: "kotse" },
  { emoji: "🚌", en: "bus", zh: "辆公交车", ms: "buah bas", ja: "バス", tl: "bus" },
  { emoji: "🚂", en: "train", zh: "列火车", ms: "buah kereta api", ja: "でんしゃ", tl: "tren" },
  { emoji: "✈️", en: "airplane", zh: "架飞机", ms: "buah kapal terbang", ja: "ひこうき", tl: "eroplano" },
  { emoji: "🚀", en: "rocket", zh: "枚火箭", ms: "buah roket", ja: "ロケット", tl: "rocket" },
  { emoji: "🚲", en: "bicycle", zh: "辆自行车", ms: "buah basikal", ja: "じてんしゃ", tl: "bisikleta" },
  { emoji: "⛵", en: "boat", zh: "艘船", ms: "buah bot", ja: "ふね", tl: "bangka" },
  { emoji: "🌸", en: "flower", zh: "朵花", ms: "kuntum bunga", ja: "おはな", tl: "bulaklak" },
  { emoji: "🌲", en: "tree", zh: "棵树", ms: "batang pokok", ja: "き", tl: "puno" },
  { emoji: "✏️", en: "pencil", zh: "支铅笔", ms: "batang pensel", ja: "えんぴつ", tl: "lapis" },
  { emoji: "🎁", en: "gift", zh: "个礼物", ms: "biji hadiah", ja: "プレゼント", tl: "regalo" },
  { emoji: "👟", en: "shoe", zh: "只鞋子", ms: "pasang kasut", ja: "くつ", tl: "sapatos" },
  { emoji: "🧸", en: "teddy bear", zh: "只泰迪熊", ms: "buah beruang teddy", ja: "ぬいぐるみ", tl: "teddy bear" },
];

export const NUMBER_WORDS: Record<Language, string[]> = {
  en: ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"],
  zh: ["一", "两", "三", "四", "五", "六", "七", "八", "九", "十"],
  ms: ["satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "lapan", "sembilan", "sepuluh"],
  // Universal ～つ counter — the first counting system Japanese children learn.
  ja: ["ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ", "むっつ", "ななつ", "やっつ", "ここのつ", "とお"],
  tl: ["isa", "dalawa", "tatlo", "apat", "lima", "anim", "pito", "walo", "siyam", "sampu"],
};
