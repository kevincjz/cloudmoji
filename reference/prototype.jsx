import { useState, useEffect, useRef, useCallback } from "react";

const EMOJIS = [
  { emoji: "🍎", cat: "fruits", en: "apple", zh: "苹果" },
  { emoji: "🍌", cat: "fruits", en: "banana", zh: "香蕉" },
  { emoji: "🍊", cat: "fruits", en: "orange", zh: "橙子" },
  { emoji: "🍇", cat: "fruits", en: "grapes", zh: "葡萄" },
  { emoji: "🍓", cat: "fruits", en: "strawberry", zh: "草莓" },
  { emoji: "🍉", cat: "fruits", en: "watermelon", zh: "西瓜" },
  { emoji: "🍒", cat: "fruits", en: "cherries", zh: "樱桃" },
  { emoji: "🥝", cat: "fruits", en: "kiwi", zh: "猕猴桃" },
  { emoji: "🍑", cat: "fruits", en: "peach", zh: "桃子" },
  { emoji: "🍋", cat: "fruits", en: "lemon", zh: "柠檬" },
  { emoji: "🍍", cat: "fruits", en: "pineapple", zh: "菠萝" },
  { emoji: "🥭", cat: "fruits", en: "mango", zh: "芒果" },
  { emoji: "🫐", cat: "fruits", en: "blueberries", zh: "蓝莓" },
  { emoji: "🥥", cat: "fruits", en: "coconut", zh: "椰子" },
  { emoji: "🥑", cat: "fruits", en: "avocado", zh: "牛油果" },
  { emoji: "🍚", cat: "food", en: "rice", zh: "米饭" },
  { emoji: "🍞", cat: "food", en: "bread", zh: "面包" },
  { emoji: "🧀", cat: "food", en: "cheese", zh: "奶酪" },
  { emoji: "🥚", cat: "food", en: "egg", zh: "鸡蛋" },
  { emoji: "🍟", cat: "food", en: "french fries", zh: "薯条" },
  { emoji: "🍕", cat: "food", en: "pizza", zh: "披萨" },
  { emoji: "🍔", cat: "food", en: "hamburger", zh: "汉堡" },
  { emoji: "🌽", cat: "food", en: "corn", zh: "玉米" },
  { emoji: "🥕", cat: "food", en: "carrot", zh: "胡萝卜" },
  { emoji: "🥦", cat: "food", en: "broccoli", zh: "西兰花" },
  { emoji: "🍅", cat: "food", en: "tomato", zh: "番茄" },
  { emoji: "🥒", cat: "food", en: "cucumber", zh: "黄瓜" },
  { emoji: "🍗", cat: "food", en: "chicken", zh: "鸡腿" },
  { emoji: "🍰", cat: "food", en: "cake", zh: "蛋糕" },
  { emoji: "🍦", cat: "food", en: "ice cream", zh: "冰淇淋" },
  { emoji: "🍪", cat: "food", en: "cookie", zh: "饼干" },
  { emoji: "🍩", cat: "food", en: "donut", zh: "甜甜圈" },
  { emoji: "🍫", cat: "food", en: "chocolate", zh: "巧克力" },
  { emoji: "🧁", cat: "food", en: "cupcake", zh: "纸杯蛋糕" },
  { emoji: "🥐", cat: "food", en: "croissant", zh: "牛角包" },
  { emoji: "🍜", cat: "food", en: "noodles", zh: "面条" },
  { emoji: "🍱", cat: "food", en: "bento box", zh: "便当" },
  { emoji: "🥪", cat: "food", en: "sandwich", zh: "三明治" },
  { emoji: "🌮", cat: "food", en: "taco", zh: "墨西哥卷" },
  { emoji: "🍿", cat: "food", en: "popcorn", zh: "爆米花" },
  { emoji: "🥛", cat: "food", en: "milk", zh: "牛奶" },
  { emoji: "🍤", cat: "food", en: "shrimp", zh: "虾" },
  { emoji: "🍼", cat: "food", en: "baby bottle", zh: "奶瓶" },
  { emoji: "🐶", cat: "animals", en: "dog", zh: "狗" },
  { emoji: "🐱", cat: "animals", en: "cat", zh: "猫" },
  { emoji: "🐰", cat: "animals", en: "rabbit", zh: "兔子" },
  { emoji: "🐻", cat: "animals", en: "bear", zh: "熊" },
  { emoji: "🐸", cat: "animals", en: "frog", zh: "青蛙" },
  { emoji: "🐔", cat: "animals", en: "chicken", zh: "鸡" },
  { emoji: "🐷", cat: "animals", en: "pig", zh: "猪" },
  { emoji: "🐮", cat: "animals", en: "cow", zh: "牛" },
  { emoji: "🐵", cat: "animals", en: "monkey", zh: "猴子" },
  { emoji: "🐍", cat: "animals", en: "snake", zh: "蛇" },
  { emoji: "🐢", cat: "animals", en: "turtle", zh: "乌龟" },
  { emoji: "🐘", cat: "animals", en: "elephant", zh: "大象" },
  { emoji: "🦁", cat: "animals", en: "lion", zh: "狮子" },
  { emoji: "🐟", cat: "animals", en: "fish", zh: "鱼" },
  { emoji: "🦋", cat: "animals", en: "butterfly", zh: "蝴蝶" },
  { emoji: "🐝", cat: "animals", en: "bee", zh: "蜜蜂" },
  { emoji: "🐛", cat: "animals", en: "bug", zh: "虫子" },
  { emoji: "🐧", cat: "animals", en: "penguin", zh: "企鹅" },
  { emoji: "🦆", cat: "animals", en: "duck", zh: "鸭子" },
  { emoji: "🐙", cat: "animals", en: "octopus", zh: "章鱼" },
  { emoji: "🦀", cat: "animals", en: "crab", zh: "螃蟹" },
  { emoji: "🐊", cat: "animals", en: "crocodile", zh: "鳄鱼" },
  { emoji: "🦒", cat: "animals", en: "giraffe", zh: "长颈鹿" },
  { emoji: "🐳", cat: "animals", en: "whale", zh: "鲸鱼" },
  { emoji: "🐬", cat: "animals", en: "dolphin", zh: "海豚" },
  { emoji: "🐞", cat: "animals", en: "ladybug", zh: "瓢虫" },
  { emoji: "🦈", cat: "animals", en: "shark", zh: "鲨鱼" },
  { emoji: "🐼", cat: "animals", en: "panda", zh: "熊猫" },
  { emoji: "🚗", cat: "vehicles", en: "car", zh: "汽车" },
  { emoji: "🚌", cat: "vehicles", en: "bus", zh: "公交车" },
  { emoji: "🚂", cat: "vehicles", en: "train", zh: "火车" },
  { emoji: "✈️", cat: "vehicles", en: "airplane", zh: "飞机" },
  { emoji: "🚀", cat: "vehicles", en: "rocket", zh: "火箭" },
  { emoji: "🚲", cat: "vehicles", en: "bicycle", zh: "自行车" },
  { emoji: "🚁", cat: "vehicles", en: "helicopter", zh: "直升机" },
  { emoji: "⛵", cat: "vehicles", en: "boat", zh: "船" },
  { emoji: "🚒", cat: "vehicles", en: "fire truck", zh: "消防车" },
  { emoji: "🚑", cat: "vehicles", en: "ambulance", zh: "救护车" },
  { emoji: "🌈", cat: "nature", en: "rainbow", zh: "彩虹" },
  { emoji: "⭐", cat: "nature", en: "star", zh: "星星" },
  { emoji: "🌙", cat: "nature", en: "moon", zh: "月亮" },
  { emoji: "☀️", cat: "nature", en: "sun", zh: "太阳" },
  { emoji: "🌊", cat: "nature", en: "wave", zh: "海浪" },
  { emoji: "🌸", cat: "nature", en: "flower", zh: "花" },
  { emoji: "🌲", cat: "nature", en: "tree", zh: "树" },
  { emoji: "🍄", cat: "nature", en: "mushroom", zh: "蘑菇" },
  { emoji: "🔥", cat: "nature", en: "fire", zh: "火" },
  { emoji: "❄️", cat: "nature", en: "snowflake", zh: "雪花" },
  { emoji: "⚽", cat: "objects", en: "soccer ball", zh: "足球" },
  { emoji: "🏀", cat: "objects", en: "basketball", zh: "篮球" },
  { emoji: "🎈", cat: "objects", en: "balloon", zh: "气球" },
  { emoji: "🎸", cat: "objects", en: "guitar", zh: "吉他" },
  { emoji: "📚", cat: "objects", en: "books", zh: "书" },
  { emoji: "✏️", cat: "objects", en: "pencil", zh: "铅笔" },
  { emoji: "🎨", cat: "objects", en: "art", zh: "画画" },
  { emoji: "🔑", cat: "objects", en: "key", zh: "钥匙" },
  { emoji: "🎁", cat: "objects", en: "gift", zh: "礼物" },
  { emoji: "🏠", cat: "objects", en: "house", zh: "房子" },
  { emoji: "👟", cat: "objects", en: "shoe", zh: "鞋子" },
  { emoji: "👒", cat: "objects", en: "hat", zh: "帽子" },
  { emoji: "🧸", cat: "objects", en: "teddy bear", zh: "泰迪熊" },
  { emoji: "🎵", cat: "objects", en: "music", zh: "音乐" },
  { emoji: "💎", cat: "objects", en: "diamond", zh: "钻石" },
  { emoji: "🎪", cat: "objects", en: "circus", zh: "马戏团" },
  { emoji: "🎢", cat: "objects", en: "roller coaster", zh: "过山车" },
  { emoji: "👶", cat: "people", en: "baby", zh: "宝宝" },
  { emoji: "👀", cat: "people", en: "eyes", zh: "眼睛" },
  { emoji: "👃", cat: "people", en: "nose", zh: "鼻子" },
  { emoji: "👄", cat: "people", en: "mouth", zh: "嘴巴" },
  { emoji: "👋", cat: "people", en: "hello", zh: "你好" },
  { emoji: "👏", cat: "people", en: "clap", zh: "拍手" },
  { emoji: "❤️", cat: "people", en: "heart", zh: "心" },
  { emoji: "😀", cat: "faces", en: "happy", zh: "开心" },
  { emoji: "😢", cat: "faces", en: "sad", zh: "伤心" },
  { emoji: "😡", cat: "faces", en: "angry", zh: "生气" },
  { emoji: "😴", cat: "faces", en: "sleepy", zh: "困了" },
  { emoji: "🤩", cat: "faces", en: "wow", zh: "哇" },
  { emoji: "😂", cat: "faces", en: "laughing", zh: "大笑" },
];

const CATS = [
  { id: "all", icon: "🌟", label: "All", labelZh: "全部" },
  { id: "fruits", icon: "🍎", label: "Fruits", labelZh: "水果" },
  { id: "food", icon: "🍕", label: "Food", labelZh: "食物" },
  { id: "animals", icon: "🐶", label: "Animals", labelZh: "动物" },
  { id: "vehicles", icon: "🚗", label: "Go!", labelZh: "交通" },
  { id: "nature", icon: "🌈", label: "Nature", labelZh: "自然" },
  { id: "objects", icon: "🎈", label: "Things", labelZh: "物品" },
  { id: "people", icon: "👶", label: "People", labelZh: "人物" },
  { id: "faces", icon: "😀", label: "Faces", labelZh: "表情" },
];

function CloudMascot({ mood = "happy", size = 64 }) {
  const isBeaming = mood === "beaming";
  const isSpeaking = mood === "speaking";
  const isExcited = mood === "excited";
  const s = size;
  const animName = isSpeaking ? "mascotBounce" : isBeaming ? "mascotBeam" : "mascotFloat";
  const animDur = isSpeaking ? "0.4s" : isBeaming ? "0.6s" : "3s";
  const shadowColor = isBeaming ? "rgba(255,230,109,0.5)" : "rgba(78,205,196,0.35)";

  return (
    <div style={{
      width: s, height: s * 0.78, position: "relative",
      animation: animName + " " + animDur + " ease" + (isSpeaking || isBeaming ? "" : "-in-out") + " infinite",
      filter: "drop-shadow(0 " + (s*0.06) + "px " + (s*0.15) + "px " + shadowColor + ")",
      transition: "filter 0.3s ease",
    }}>
      <svg viewBox="0 0 120 78" width={s} height={s * 0.78} style={{ overflow: "visible" }}>
        <defs>
          <radialGradient id="beamGlow">
            <stop offset="0%" stopColor="#FFE66D" stopOpacity="0.6" />
            <stop offset="100%" stopColor="#FFE66D" stopOpacity="0" />
          </radialGradient>
        </defs>

        {isBeaming && (
          <circle cx="60" cy="45" r="52" fill="url(#beamGlow)" opacity="0.4" style={{ animation: "sparkle 1.2s ease-in-out infinite" }} />
        )}

        <circle cx="30" cy="46" r="20" fill="white" />
        <circle cx="52" cy="36" r="23" fill="white" />
        <circle cx="72" cy="30" r="26" fill="white" />
        <circle cx="94" cy="42" r="19" fill="white" />
        <circle cx="42" cy="44" r="16" fill="white" />
        <rect x="12" y="48" width="96" height="24" rx="12" fill="white" />
        <circle cx="72" cy="22" r="12" fill="#F8FCFF" />
        <circle cx="50" cy="30" r="8" fill="#F8FCFF" opacity="0.7" />
        <ellipse cx="60" cy="68" rx="44" ry="6" fill="#E8EEF4" opacity="0.4" />

        <ellipse cx="34" cy="58" rx={isBeaming ? 10 : 8} ry={isBeaming ? 5.5 : 4.5} fill={isBeaming ? "#FF9E9E" : "#FFB5B5"} opacity={isBeaming ? 0.7 : 0.55} />
        <ellipse cx="86" cy="58" rx={isBeaming ? 10 : 8} ry={isBeaming ? 5.5 : 4.5} fill={isBeaming ? "#FF9E9E" : "#FFB5B5"} opacity={isBeaming ? 0.7 : 0.55} />

        {isBeaming ? (
          <g>
            <path d="M39 51 Q46 45 53 51" fill="none" stroke="#2D3436" strokeWidth="2.5" strokeLinecap="round" />
            <path d="M67 51 Q74 45 81 51" fill="none" stroke="#2D3436" strokeWidth="2.5" strokeLinecap="round" />
          </g>
        ) : isExcited ? (
          <g>
            <text x="46" y="53" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">★</text>
            <text x="74" y="53" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">★</text>
          </g>
        ) : isSpeaking ? (
          <g>
            <text x="46" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">●</text>
            <text x="74" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">●</text>
          </g>
        ) : (
          <g>
            <text x="46" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">◠</text>
            <text x="74" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">◠</text>
          </g>
        )}

        {isSpeaking ? (
          <ellipse cx="60" cy="62" rx="5.5" ry="4.5" fill="#FF6B6B" stroke="#E55555" strokeWidth="0.5" />
        ) : isBeaming ? (
          <path d="M46 59 Q60 74 74 59" fill="#FF6B6B" stroke="#E55555" strokeWidth="0.5" />
        ) : isExcited ? (
          <path d="M53 60 Q60 69 67 60" fill="#FF6B6B" stroke="none" />
        ) : (
          <path d="M54 61 Q60 66 66 61" fill="none" stroke="#2D3436" strokeWidth="1.8" strokeLinecap="round" />
        )}

        {(isExcited || isBeaming) && (
          <g>
            <text x="102" y="24" fontSize="10" style={{ animation: "sparkle 0.6s ease infinite" }}>✨</text>
            <text x="12" y="28" fontSize="8" style={{ animation: "sparkle 0.8s ease infinite 0.2s" }}>✨</text>
          </g>
        )}
        {isBeaming && (
          <g>
            <text x="4" y="50" fontSize="9" style={{ animation: "sparkle 0.7s ease infinite 0.1s" }}>⭐</text>
            <text x="110" y="48" fontSize="9" style={{ animation: "sparkle 0.9s ease infinite 0.4s" }}>⭐</text>
            <text x="58" y="12" fontSize="11" style={{ animation: "sparkle 0.5s ease infinite 0.3s" }}>🌟</text>
          </g>
        )}
      </svg>
    </div>
  );
}

export default function Cloudmoji() {
  const [lang, setLang] = useState(() => {
    try { return window.localStorage?.getItem("cm_lang") || "en"; } catch { return "en"; }
  });
  const [muted, setMuted] = useState(false);
  const [category, setCategory] = useState("all");
  const [typed, setTyped] = useState([]);
  const [showWord, setShowWord] = useState(null);
  const [bounceIdx, setBounceIdx] = useState(null);
  const [mascotMood, setMascotMood] = useState("happy");
  const [tapCount, setTapCount] = useState(0);
  const typedRef = useRef(null);
  const wordTimerRef = useRef(null);
  const ttsInit = useRef(false);
  const beamingRef = useRef(false);

  useEffect(() => {
    try { window.localStorage?.setItem("cm_lang", lang); } catch {}
  }, [lang]);

  useEffect(() => {
    if (typedRef.current) typedRef.current.scrollLeft = typedRef.current.scrollWidth;
  }, [typed]);

  const safeMood = useCallback((m) => {
    if (beamingRef.current && m !== "beaming") return;
    setMascotMood(m);
  }, []);

  const initTTS = useCallback(() => {
    if (ttsInit.current) return;
    try {
      const s = new SpeechSynthesisUtterance("");
      s.volume = 0;
      speechSynthesis.speak(s);
      ttsInit.current = true;
    } catch {}
  }, []);

  const speak = useCallback((text, langCode) => {
    if (muted) return;
    initTTS();
    try {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = langCode;
      u.rate = 0.85;
      u.pitch = 1.1;
      const voices = speechSynthesis.getVoices();
      const match = voices.find(function(v) { return v.lang.startsWith(langCode.split("-")[0]); });
      if (match) u.voice = match;
      u.onstart = function() { safeMood("speaking"); };
      u.onend = function() { safeMood("happy"); };
      u.onerror = function() { safeMood("happy"); };
      speechSynthesis.speak(u);
    } catch {}
  }, [initTTS, muted, safeMood]);

  const handleTap = useCallback((item, idx) => {
    initTTS();
    const word = lang === "zh" ? item.zh : item.en;
    const speechLang = lang === "zh" ? "zh-CN" : "en-US";

    setTyped(function(prev) { return prev.concat({ emoji: item.emoji, word: word, id: Date.now() }); });
    setShowWord({ emoji: item.emoji, word: word, id: Date.now() });
    setBounceIdx(idx);
    if (!beamingRef.current) setMascotMood("excited");

    speak(word, speechLang);

    const newCount = tapCount + 1;
    setTapCount(newCount);

    if (newCount === 10 || newCount === 25 || newCount === 50 || newCount === 100) {
      setTimeout(function() {
        beamingRef.current = true;
        setMascotMood("beaming");
        setTimeout(function() {
          beamingRef.current = false;
          setMascotMood("happy");
        }, 3000);
      }, 500);
    }

    if (wordTimerRef.current) clearTimeout(wordTimerRef.current);
    wordTimerRef.current = setTimeout(function() { setShowWord(null); }, 2200);
    setTimeout(function() { setBounceIdx(null); }, 400);
    setTimeout(function() { safeMood("happy"); }, 600);
  }, [lang, speak, tapCount, initTTS, safeMood]);

  const replayAll = useCallback(() => {
    if (typed.length === 0 || muted) return;
    speechSynthesis.cancel();
    const speechLang = lang === "zh" ? "zh-CN" : "en-US";
    typed.forEach(function(item, i) {
      setTimeout(function() {
        const found = EMOJIS.find(function(e) { return e.emoji === item.emoji; });
        const word = found ? found[lang] : item.word;
        setShowWord({ emoji: item.emoji, word: word, id: Date.now() });
        speak(word, speechLang);
      }, i * 1200);
    });
    setTimeout(function() { setShowWord(null); }, typed.length * 1200 + 1500);
  }, [typed, lang, speak, muted]);

  const filtered = category === "all" ? EMOJIS : EMOJIS.filter(function(e) { return e.cat === category; });

  return (
    <div style={{
      minHeight: "100vh",
      background: "linear-gradient(160deg, #0F0E2A 0%, #1A1145 40%, #0D2137 100%)",
      fontFamily: "'Nunito', sans-serif",
      overflow: "hidden", display: "flex", flexDirection: "column",
      position: "relative", WebkitTapHighlightColor: "transparent", userSelect: "none",
    }}>
      <link href="https://fonts.googleapis.com/css2?family=Lilita+One&family=Nunito:wght@700;800;900&display=swap" rel="stylesheet" />
      <style>{"\
        @keyframes popIn{0%{transform:scale(0);opacity:0}60%{transform:scale(1.35)}100%{transform:scale(1);opacity:1}}\
        @keyframes bounceEmoji{0%,100%{transform:scale(1)}50%{transform:scale(1.3)}}\
        @keyframes wordFloat{0%{opacity:0;transform:translateY(12px) scale(0.7)}15%{opacity:1;transform:translateY(0) scale(1.06)}25%{transform:scale(1)}78%{opacity:1}100%{opacity:0;transform:translateY(-10px) scale(0.95)}}\
        @keyframes mascotFloat{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}\
        @keyframes mascotBounce{0%,100%{transform:translateY(0) scale(1)}50%{transform:translateY(-3px) scale(1.06)}}\
        @keyframes mascotBeam{0%,100%{transform:translateY(0) scale(1.08)}50%{transform:translateY(-6px) scale(1.15)}}\
        @keyframes sparkle{0%,100%{opacity:0.2;transform:scale(0.7)}50%{opacity:1;transform:scale(1.3)}}\
        @keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(78,205,196,0.3)}50%{box-shadow:0 0 0 8px rgba(78,205,196,0)}}\
        @keyframes bgGlow{0%,100%{opacity:0.4}50%{opacity:0.7}}\
        .emoji-btn:active{transform:scale(0.85)!important}\
        .cat-btn:active{transform:scale(0.9)!important}\
        .ctrl-btn:active{transform:scale(0.88)!important}\
        .no-scroll::-webkit-scrollbar{display:none}\
        .no-scroll{-ms-overflow-style:none;scrollbar-width:none}\
      "}</style>

      <div style={{ position: "fixed", inset: 0, pointerEvents: "none", zIndex: 0, overflow: "hidden" }}>
        <div style={{ position: "absolute", width: 300, height: 300, borderRadius: "50%", background: "radial-gradient(circle, rgba(255,107,107,0.08) 0%, transparent 70%)", top: "-10%", right: "-10%", animation: "bgGlow 6s ease-in-out infinite" }} />
        <div style={{ position: "absolute", width: 250, height: 250, borderRadius: "50%", background: "radial-gradient(circle, rgba(78,205,196,0.08) 0%, transparent 70%)", bottom: "10%", left: "-8%", animation: "bgGlow 8s ease-in-out infinite 2s" }} />
        <div style={{ position: "absolute", width: 200, height: 200, borderRadius: "50%", background: "radial-gradient(circle, rgba(255,230,109,0.06) 0%, transparent 70%)", top: "40%", right: "5%", animation: "bgGlow 7s ease-in-out infinite 1s" }} />
      </div>

      <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", minHeight: "100vh", maxWidth: 500, margin: "0 auto", width: "100%" }}>

        <div style={{ padding: "10px 14px 6px", display: "flex", alignItems: "center", justifyContent: "space-between", flexShrink: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <CloudMascot mood={mascotMood} size={64} />
            <div>
              <div style={{
                fontFamily: "'Lilita One', sans-serif", fontSize: 21,
                background: "linear-gradient(135deg, #FF6B6B, #FFE66D, #4ECDC4)",
                WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
                lineHeight: 1.1, letterSpacing: 0.5,
              }}>Cloudmoji</div>
              <div style={{ fontSize: 10, fontWeight: 800, color: "rgba(255,255,255,0.3)", letterSpacing: 0.5, marginTop: 1 }}>
                Tap. Listen. Learn!</div>
            </div>
          </div>

          <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
            <button onClick={function() { setMuted(function(m) { if (!m) speechSynthesis.cancel(); return !m; }); }}
              className="ctrl-btn" style={{
                background: muted ? "rgba(255,107,107,0.2)" : "rgba(255,255,255,0.06)",
                border: muted ? "2px solid rgba(255,107,107,0.3)" : "2px solid rgba(255,255,255,0.12)",
                borderRadius: 14, width: 40, height: 40, fontSize: 18, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center", transition: "all 0.2s",
              }}>{muted ? "🔇" : "🔊"}</button>

            <button onClick={function() { setLang(function(l) { return l === "en" ? "zh" : "en"; }); }}
              className="ctrl-btn" style={{
                background: "rgba(255,255,255,0.06)", border: "2px solid rgba(255,255,255,0.12)",
                borderRadius: 14, padding: "6px 12px", color: "#fff", fontSize: 14, fontWeight: 900,
                cursor: "pointer", display: "flex", alignItems: "center", gap: 5,
                fontFamily: "'Nunito', sans-serif", transition: "all 0.2s",
              }}>
              <span style={{ fontSize: 17 }}>{lang === "en" ? "🇬🇧" : "🇨🇳"}</span>
              <span>{lang === "en" ? "EN" : "中文"}</span>
            </button>
          </div>
        </div>

        <div style={{
          margin: "4px 12px 0", background: "rgba(255,255,255,0.04)", borderRadius: 20,
          padding: "7px 10px", minHeight: 56, display: "flex", alignItems: "center",
          border: "1.5px solid rgba(255,255,255,0.06)", flexShrink: 0, gap: 4,
        }}>
          <div ref={typedRef} className="no-scroll" style={{
            display: "flex", gap: 3, flex: 1, overflowX: "auto", alignItems: "center", scrollBehavior: "smooth",
          }}>
            {typed.length === 0 ? (
              <span style={{ color: "rgba(255,255,255,0.2)", fontSize: 13, fontWeight: 800, padding: "0 4px" }}>
                {lang === "zh" ? "点击下面的表情 👇" : "Tap emojis below! 👇"}</span>
            ) : typed.map(function(item) {
              return (
                <span key={item.id} style={{ fontSize: 32, animation: "popIn 0.3s ease-out", cursor: "pointer", lineHeight: 1 }}
                  onClick={function() {
                    var found = EMOJIS.find(function(x) { return x.emoji === item.emoji; });
                    if (!found) return;
                    var word = lang === "zh" ? found.zh : found.en;
                    setShowWord({ emoji: item.emoji, word: word, id: Date.now() });
                    speak(word, lang === "zh" ? "zh-CN" : "en-US");
                    if (wordTimerRef.current) clearTimeout(wordTimerRef.current);
                    wordTimerRef.current = setTimeout(function() { setShowWord(null); }, 2200);
                  }}>{item.emoji}</span>
              );
            })}
          </div>
          {typed.length > 0 && (
            <div style={{ display: "flex", gap: 4, flexShrink: 0 }}>
              {!muted && (
                <button className="ctrl-btn" onClick={replayAll} style={{
                  background: "rgba(78,205,196,0.2)", border: "1.5px solid rgba(78,205,196,0.3)",
                  borderRadius: 12, width: 34, height: 34, fontSize: 15, cursor: "pointer",
                  display: "flex", alignItems: "center", justifyContent: "center",
                }}>🔊</button>
              )}
              <button className="ctrl-btn" onClick={function() { setTyped(function(p) { return p.slice(0,-1); }); }} style={{
                background: "rgba(255,179,71,0.2)", border: "1.5px solid rgba(255,179,71,0.3)",
                borderRadius: 12, width: 34, height: 34, fontSize: 14, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>⌫</button>
              <button className="ctrl-btn" onClick={function() { setTyped([]); }} style={{
                background: "rgba(255,107,107,0.2)", border: "1.5px solid rgba(255,107,107,0.3)",
                borderRadius: 12, width: 34, height: 34, fontSize: 13, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>✕</button>
            </div>
          )}
        </div>

        <div style={{ height: 38, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          {showWord && (
            <div key={showWord.id} style={{ animation: "wordFloat 2.2s ease-in-out forwards" }}>
              <span style={{
                background: "linear-gradient(135deg, rgba(255,107,107,0.2), rgba(78,205,196,0.2))",
                padding: "5px 18px", borderRadius: 18, fontWeight: 900, fontSize: 18,
                letterSpacing: 0.5, border: "1.5px solid rgba(255,255,255,0.1)",
                color: "#fff", display: "inline-flex", alignItems: "center", gap: 6,
              }}>
                <span style={{ fontSize: 22 }}>{showWord.emoji}</span>
                {showWord.word}
              </span>
            </div>
          )}
        </div>

        <div className="no-scroll" style={{ display: "flex", gap: 5, padding: "2px 12px 6px", overflowX: "auto", flexShrink: 0 }}>
          {CATS.map(function(cat) {
            var isActive = category === cat.id;
            return (
              <button key={cat.id} className="cat-btn" onClick={function() { setCategory(cat.id); }} style={{
                background: isActive ? "rgba(78,205,196,0.2)" : "rgba(255,255,255,0.04)",
                border: isActive ? "1.5px solid rgba(78,205,196,0.4)" : "1.5px solid rgba(255,255,255,0.06)",
                borderRadius: 14, padding: "5px 11px",
                color: isActive ? "#4ECDC4" : "rgba(255,255,255,0.35)",
                fontSize: 12, fontWeight: 800, cursor: "pointer", whiteSpace: "nowrap",
                display: "flex", alignItems: "center", gap: 4,
                fontFamily: "'Nunito', sans-serif", transition: "all 0.2s", flexShrink: 0,
              }}>
                <span style={{ fontSize: 15 }}>{cat.icon}</span>
                {lang === "zh" ? cat.labelZh : cat.label}
              </button>
            );
          })}
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: "2px 10px 24px", WebkitOverflowScrolling: "touch" }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))", gap: 8 }}>
            {filtered.map(function(item, i) {
              return (
                <button key={item.emoji + item.cat} className="emoji-btn" onClick={function() { handleTap(item, i); }} style={{
                  background: "rgba(255,255,255,0.04)",
                  border: "1.5px solid rgba(255,255,255,0.06)",
                  borderRadius: 18, padding: 0, height: 72, fontSize: 38,
                  cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center",
                  transition: "transform 0.1s",
                  animation: bounceIdx === i ? "bounceEmoji 0.35s ease" : "none",
                  WebkitTapHighlightColor: "transparent",
                }}>{item.emoji}</button>
              );
            })}
          </div>
        </div>

        <div style={{
          position: "fixed", bottom: 8, right: 12, fontSize: 10, fontWeight: 800,
          color: "rgba(255,255,255,0.12)", fontFamily: "'Nunito', sans-serif", zIndex: 10,
        }}>{tapCount > 0 ? tapCount + " ✨" : ""}</div>
      </div>
    </div>
  );
}
