package com.soul.neurokaraoke.data.util

object EnglishTitleMap {

    /**
     * Returns the curated English translation of a Japanese/Chinese song title,
     * or null if no translation is available.
     */
    fun getEnglishTitle(japaneseTitle: String): String? {
        return titleMap[japaneseTitle.trim()]
    }

    // Curated map of well-known Vocaloid/J-Pop song titles to English translations.
    // Keys are the original Japanese titles exactly as they appear in the catalog.
    private val titleMap: Map<String, String> = mapOf(
        // --- Pinocchio-P ---
        "すろぉもぉしょん" to "Slow Motion",
        "ノーナイワンダーランド" to "Nounai Wonderland",
        "アルティメットセンパイ" to "Ultimate Senpai",
        "神っぽいな" to "God-ish",
        "匿名M" to "Anonymous M",
        "ありふれたせかいせいふく" to "Common World Domination",
        "すきなことだけでいいです" to "Just Be Friends With What You Like",
        "内臓ありますか" to "Do You Have Organs?",
        "転生林檎" to "Reincarnation Apple",
        "ラヴィット" to "Love It",
        "マッシュルームマザー" to "Mushroom Mother",
        "腐れ外道とチョコレゐト" to "Rotten Heresy and Chocolate",
        "こどものしくみ" to "How Children Work",
        "なにがわるい" to "What's Wrong",
        "おばけのウケねらい" to "Ghost Seeking Laughs",
        "ぼくらはみんな意味不明" to "We Are All Incomprehensible",
        "頓珍漢の宴" to "Feast of Nonsense",
        "ゴッドイーター" to "God Eater",

        // --- DECO*27 ---
        "ゴーストルール" to "Ghost Rule",
        "愛言葉III" to "Love Words III",
        "愛言葉" to "Love Words",
        "乙女解剖" to "Girl Dissection",
        "ヒバナ" to "Hibana",
        "モザイクロール" to "Mosaic Roll",
        "二息歩行" to "Two-Breaths Walking",
        "妄想税" to "Delusion Tax",
        "妄想感傷代償連盟" to "Delusion Sentimentality Compensation Federation",
        "サイコグラム" to "Psychogram",
        "アニマル" to "Animal",
        "ヴァンパイア" to "Vampire",
        "シンデレラ" to "Cinderella",
        "ネガティブ進化論" to "Negative Evolution Theory",
        "人質" to "Hostage",
        "ラビットホール" to "Rabbit Hole",
        "スクランブル交際" to "Scramble Relationship",

        // --- Kanaria ---
        "KING" to "KING",
        "酔いどれ知らず" to "Unaware Drunkard",
        "エンヴィーベイビー" to "Envy Baby",
        "アイデンティティ" to "Identity",
        "百鬼祭" to "Hyakki Festival",

        // --- wowaka / Hitorie ---
        "ワールズエンド・ダンスホール" to "World's End Dancehall",
        "裏表ラバーズ" to "Two-Faced Lovers",
        "ローリンガール" to "Rolling Girl",
        "アンノウン・マザーグース" to "Unknown Mother Goose",
        "アンハッピーリフレイン" to "Unhappy Refrain",
        "グレーゾーンにて。" to "In the Gray Zone.",

        // --- Hachi / Kenshi Yonezu ---
        "砂の惑星" to "Sand Planet",
        "マトリョシカ" to "Matryoshka",
        "パンダヒーロー" to "Panda Hero",
        "ドーナツホール" to "Donut Hole",
        "結ンデ開イテ羅刹ト骸" to "Tie and Open, Rakshasa and Corpse",
        "演劇テレプシコーラ" to "Theater Terpsichora",
        "クローズアンドオープン" to "Close and Open",
        "リンネ" to "Rinne",
        "病棟305号室" to "Hospital Ward Room 305",

        // --- Kikuo ---
        "愛して愛して愛して" to "Love Me Love Me Love Me",
        "きみはデキない子" to "You're a Useless Child",
        "僕をそんな目で見ないで" to "Don't Look at Me With Those Eyes",
        "幸福な死を" to "A Happy Death",
        "天使だと思っていたのに" to "I Thought You Were an Angel",

        // --- Maretu ---
        "うみたがり" to "Wanting to Give Birth",
        "脳内革命ガール" to "Brain Revolution Girl",
        "マインドブランド" to "Mind Brand",
        "コインロッカーベイビー" to "Coin Locker Baby",

        // --- Mitchie M ---
        "ビバハピ" to "Viva Happy",
        "好き！雪！本気マジック" to "Love! Snow! Really Magic",
        "アゲアゲアゲイン" to "Ageage Again",
        "ニュース39" to "News 39",
        "愛Dee" to "iDee",

        // --- kz / livetune ---
        "Tell Your World" to "Tell Your World",
        "ファインダー" to "Finder",
        "Packaged" to "Packaged",

        // --- ryo (supercell) ---
        "メルト" to "Melt",
        "ワールドイズマイン" to "World Is Mine",
        "ブラック★ロックシューター" to "Black Rock Shooter",
        "恋は戦争" to "Love Is War",
        "こっち向いて Baby" to "Look This Way Baby",
        "君の知らない物語" to "The Story You Don't Know",

        // --- Reol ---
        "ヒビカセ" to "Hibikase",
        "ギガンティックO.T.N" to "Gigantic O.T.N",
        "宵々古今" to "YoiYoi Kokon",
        "サイサキ" to "Saisaki",
        "激唱" to "Fierce Song",

        // --- Eve ---
        "ドラマツルギー" to "Dramaturgy",
        "ナンセンス文学" to "Nonsense Literature",
        "お気に召すまま" to "As You Like It",
        "トーキョーゲットー" to "Tokyo Ghetto",
        "群青讃歌" to "Ultramarine Anthem",
        "心海" to "Shinkai",
        "いのちの食べ方" to "How to Eat Life",
        "廻廻奇譚" to "Kaikai Kitan",

        // --- Ado ---
        "うっせぇわ" to "Usseewa",
        "踊" to "Odo",
        "新時代" to "New Genesis",
        "私は最強" to "I'm Invincible",
        "逆光" to "Backlight",
        "阿修羅ちゃん" to "Ashura-chan",
        "ギラギラ" to "Gira Gira",
        "レディメイド" to "Readymade",
        "永遠のあくる日" to "The Day After Forever",
        "風のゆくえ" to "Where the Wind Goes",

        // --- Yorushika ---
        "ただ君に晴れ" to "Just a Sunny Day for You",
        "だから僕は音楽を辞めた" to "That's Why I Gave Up on Music",
        "花に亡霊" to "Ghost in a Flower",
        "言って。" to "Say It.",
        "春泥棒" to "Spring Thief",
        "又三郎" to "Matasaburo",

        // --- YOASOBI ---
        "夜に駆ける" to "Racing into the Night",
        "群青" to "Ultramarine",
        "怪物" to "Monster",
        "三原色" to "RGB",
        "祝福" to "Blessing",
        "アイドル" to "Idol",

        // --- Other popular songs ---
        "千本桜" to "Senbonzakura",
        "六兆年と一夜物語" to "A Tale of Six Trillion Years and a Night",
        "脳漿炸裂ガール" to "Brain Fluid Explosion Girl",
        "ロキ" to "Roki",
        "命に嫌われている" to "Hated by Life Itself",
        "シャルル" to "Charles",
        "フォニイ" to "Phony",
        "ビターチョコデコレーション" to "Bitter Choco Decoration",
        "ロストワンの号哭" to "Lost One's Weeping",
        "カゲロウデイズ" to "Kagerou Daze",
        "チルドレンレコード" to "Children Record",
        "夜咄ディセイブ" to "Yobanashi Deceive",
        "アウターサイエンス" to "Outer Science",
        "如月アテンション" to "Kisaragi Attention",
        "天ノ弱" to "Ama no Jaku",
        "深海少女" to "Deep Sea Girl",
        "からくりピエロ" to "Karakuri Pierrot",
        "ダブルラリアット" to "Double Lariat",
        "炉心融解" to "Meltdown",
        "Just Be Friends" to "Just Be Friends",
        "ルカルカ★ナイトフィーバー" to "Luka Luka Night Fever",
        "いーあるふぁんくらぶ" to "1, 2 Fanclub",
        "ヤンキーボーイ・ヤンキーガール" to "Yankee Boy Yankee Girl",
        "おねがいダーリン" to "Please Darling",
        "ベノム" to "Venom",
        "フラジール" to "Fragile",
        "劣等上等" to "Inferiority Superiority",
        "テオ" to "Teo",
        "金木犀" to "Osmanthus",
        "オートファジー" to "Autophagy",
        "ヒトリエ" to "Hitorie",
        "ロウワー" to "Lower",
        "乱躁滅裂ガール" to "Nonsensical Girl",
        "エンパシー" to "Empathy",
        "プラネタリウム" to "Planetarium",
        "耳のあるロボットの唄" to "Song of the Eared Robot",
        "独りんぼエンヴィー" to "Solitary Envy",
        "恋愛裁判" to "Love Trial",
        "太陽系デスコ" to "Solar System Disco",
        "セツナトリップ" to "Setsuna Trip",
        "アスノヨゾラ哨戒班" to "Night Sky Patrol of Tomorrow",
        "ハッピーシンセサイザ" to "Happy Synthesizer",
        "メランコリック" to "Melancholic",
        "スキスキ星人" to "Love Love Alien",
        "テレキャスタービーボーイ" to "Telecaster B-Boy",
        "ボッカデラベリタ" to "Bocca della Verita",
        "可愛くてごめん" to "Sorry for Being Cute",
        "酔いどれ知らず" to "Tipsy Unaware",
        "ダーリンダンス" to "Darling Dance",
        "限りなく灰色へ" to "Endlessly Toward Gray",
    )
}
