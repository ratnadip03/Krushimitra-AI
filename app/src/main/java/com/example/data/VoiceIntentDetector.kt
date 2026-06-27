package com.example.data

/**
 * Maps spoken voice commands (English, Hindi, Marathi) to feature routes.
 */
object VoiceIntentDetector {

    private data class IntentPattern(val route: FeatureRoute, val keywords: List<String>)

    private val helpPhrases = listOf(
        "help", "what can you do", "what do you do", "what features",
        "tum kya kar sakte ho", "tum kya kar sakte", "aap kya kar sakte ho",
        "kya kar sakte ho", "kya kar sakte",
        "tumhi kay karu shakta", "tumhi kay karu", "kay karu shakta",
        "kon kon se kaam", "features batao", "features sanga",
        "madad", "sahayata"
    )

    private val featurePatterns = listOf(
        IntentPattern(FeatureRoute.SOIL_PASSPORT, listOf(
            "soil", "mati", "mitti", "npk", "n p k", "एनपीके", "माती", "मिट्टी"
        )),
        IntentPattern(FeatureRoute.PRE_CROP, listOf(
            "crop suggest", "pik nivad", "fasal chunav", "konti pik", "kaun si fasal",
            "कोणती पीक", "पीक निवड", "कौन सी फसल", "फसल चुनाव", "precrop", "pre crop"
        )),
        IntentPattern(FeatureRoute.CROP_ROTATION, listOf(
            "rotation", "pik ferpalt", "pik ferpalat", "fasal chakra", "fasal chakr",
            "फेरपालट", "फसल चक्र", "crop rotation", "ferpalat"
        )),
        IntentPattern(FeatureRoute.FRESHNESS, listOf(
            "freshness", "taaza", "taajepan", "tajepana", "tazgi", "tajgi", "photo",
            "ताजेपणा", "ताज़गी", "shelf life", "fresh check"
        )),
        IntentPattern(FeatureRoute.STORAGE_GUIDE, listOf(
            "storage", "sathavan", "sathvann", "bhandaran", "store produce",
            "साठवण", "भंडारण", "storage guide"
        )),
        IntentPattern(FeatureRoute.HARVEST_TIMING, listOf(
            "harvest", "kadhani", "kadni", "kaadhni", "katai", "kadhni", "timing",
            "काढणी", "कटाई", "harvest timing", "harvest time"
        )),
        IntentPattern(FeatureRoute.COLD_CHAIN, listOf(
            "cold storage", "cold chain", "cold store", "thanda sathvann", "कोल्ड",
            "थंड साठवण", "कोल्ड स्टोरेज", "cold room"
        )),
        IntentPattern(FeatureRoute.SELL_ADVISOR, listOf(
            "sell", "bech", "bechna", "vikri", "bechne", "when to sell",
            "विक्री", "बेचना", "sell advisor", "best time to sell"
        )),
        IntentPattern(FeatureRoute.MARKET_PRICE, listOf(
            "price", "bhav", "bajarbhav", "kimat", "mandi price", "market price", "mandi",
            "भाव", "कीमत", "mandi bhav", "market rate"
        )),
        IntentPattern(FeatureRoute.POSTHARVEST_LOSS, listOf(
            "loss", "nuksan", "nuksaan", "tabahi", "post harvest loss",
            "नुकसान", "तोटा", "postharvest", "harvest loss"
        )),
        IntentPattern(FeatureRoute.SURPLUS, listOf(
            "surplus", "jast pik", "zyada fasal", "adhishesh", "atirikt",
            "जास्त पीक", "अधिशेष", "अधिक फसल", "extra crop"
        )),
        IntentPattern(FeatureRoute.VALUE_ADDITION, listOf(
            "value add", "value addition", "pickle", "loncha", "lonche", "mulyavardhan",
            "मूल्यवर्धन", "लोणचे", "processing"
        )),
        IntentPattern(FeatureRoute.WASTE_ENGINE, listOf(
            "waste", "kachra", "kachara", "compost", "composting", "krishi kachra",
            "कचरा", "कृषी कचरा", "खत", "agri waste"
        )),
        IntentPattern(FeatureRoute.CARBON_TRACKER, listOf(
            "carbon", "carbon track", "carbon tracker", "carbon footprint",
            "उत्सर्जन", "कार्बन", "karbon"
        )),
        IntentPattern(FeatureRoute.GOVT_SCHEMES, listOf(
            "scheme", "yojana", "subsidy", "govt scheme", "government scheme", "sarkari",
            "योजना", "अनुदान", "सरकारी", "pm kisan"
        )),
        IntentPattern(FeatureRoute.GENERAL, listOf(
            "voice", "mic", "microphone", "awaz", "aavaj", "aawaz",
            "आवाज", "आवाज़", "voice assistant", "bolo"
        ))
    )

    enum class VoiceIntent {
        HELP, FEATURE, GENERAL
    }

    data class DetectionResult(
        val intent: VoiceIntent,
        val route: FeatureRoute = FeatureRoute.GENERAL
    )

    fun detect(query: String): DetectionResult {
        val normalized = query.lowercase().trim()
        if (normalized.isBlank()) return DetectionResult(VoiceIntent.GENERAL)

        if (helpPhrases.any { normalized.contains(it) }) {
            return DetectionResult(VoiceIntent.HELP)
        }

        for (pattern in featurePatterns) {
            if (pattern.keywords.any { keyword ->
                    if (keyword.any { it.code > 127 }) normalized.contains(keyword)
                    else normalized.contains(keyword.lowercase())
                }) {
                return DetectionResult(VoiceIntent.FEATURE, pattern.route)
            }
        }

        return DetectionResult(VoiceIntent.GENERAL)
    }
}
