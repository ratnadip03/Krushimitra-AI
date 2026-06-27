package com.example.data

/**
 * Localized voice scripts: mic opening announcement, feature intros, guided questions.
 */
object FeatureVoiceGuidance {

    data class LocalizedText(val mr: String, val hi: String, val en: String) {
        fun forLang(lang: String = AppLanguageManager.currentLanguage): String =
            AppLanguageManager.localized(mr, hi, en)
    }

    fun fullMicOpeningAnnouncement(lang: String = AppLanguageManager.currentLanguage): String =
        when (AppLanguageManager.normalize(lang)) {
            "mr" -> """
                नमस्कार! मी KrishiMitra AI आहे. मी तुम्हाला या गोष्टींमध्ये मदत करू शकतो.
                एक - माती आरोग्य आणि NPK सल्ला.
                दोन - पेरणीपूर्वी पीक निवड.
                तीन - पीक फेरपालट योजना.
                चार - उत्पादनाची ताजेपणा तपासणी.
                पाच - स्मार्ट साठवण मार्गदर्शन.
                सहा - काढणीचा योग्य वेळ.
                सात - कोल्ड स्टोरेज सल्ला.
                आठ - विक्रीचा योग्य वेळ.
                नऊ - बाजारभाव अंदाज.
                दहा - काढणीनंतरचे नुकसान टाळणे.
                अकरा - अधिशेष पीक जुळवणी.
                बारा - मूल्यवर्धन सल्ला.
                तेरा - कृषी कचऱ्यापासून उत्पन्न.
                चौदा - कार्बन ट्रॅकर.
                पंधरा - सरकारी योजना पात्रता.
                सोळा - मी तुमचा ऑफलाइन व्हॉइस असिस्टंट आहे.
                तुम्हाला कशात मदत हवी आहे?
            """.trimIndent()
            "hi" -> """
                नमस्ते! मैं KrishiMitra AI हूँ. मैं आपकी इन कामों में मदद कर सकता हूँ.
                एक - मिट्टी स्वास्थ्य और NPK सलाह.
                दो - बुवाई से पहले फसल चुनाव.
                तीन - फसल चक्र योजना.
                चार - उपज की ताज़गी जाँच.
                पाँच - स्मार्ट भंडारण मार्गदर्शन.
                छह - कटाई का सही समय.
                सात - कोल्ड स्टोरेज सलाह.
                आठ - बेचने का सही समय.
                नौ - बाजार भाव अनुमान.
                दस - कटाई के बाद नुकसान से बचाव.
                ग्यारह - अधिक फसल मिलान.
                बारह - मूल्य वर्धन सलाह.
                तेरह - कृषि अपशिष्ट से आय.
                चौदह - कार्बन ट्रैकर.
                पंद्रह - सरकारी योजना पात्रता.
                सोलह - मैं आपका ऑफलाइन वॉयस असिस्टेंट भी हूँ.
                आप किस चीज़ में मदद चाहते हैं?
            """.trimIndent()
            else -> """
                Hello! I am KrishiMitra AI. I can help you with —
                One - Soil Health and NPK Advisory.
                Two - Pre Crop Soil Suitability.
                Three - Crop Rotation Planning.
                Four - Produce Freshness Check.
                Five - Smart Storage Guide.
                Six - Harvest Timing.
                Seven - Cold Chain Advisor.
                Eight - Best Time to Sell.
                Nine - Market Price Prediction.
                Ten - Post Harvest Loss Prevention.
                Eleven - Surplus Crop Matching.
                Twelve - Value Addition Advisor.
                Thirteen - Agri Waste Revenue.
                Fourteen - Carbon Tracker.
                Fifteen - Government Scheme Eligibility.
                Sixteen - I am your Offline Voice Assistant.
                Which feature do you want help with?
            """.trimIndent()
        }

    fun featureIntro(route: FeatureRoute, lang: String = AppLanguageManager.currentLanguage): String =
        when (route) {
            FeatureRoute.SOIL_PASSPORT -> LocalizedText(
                mr = "हे माती आरोग्य तपासणी आहे. तुमचा माती अहवाल फोटो काढा.",
                hi = "यह मिट्टी स्वास्थ्य जाँच है. अपनी मिट्टी रिपोर्ट की फोटो लें.",
                en = "This is Soil Health check. Take a photo of your soil report."
            )
            FeatureRoute.PRE_CROP -> LocalizedText(
                mr = "हे पेरणीपूर्वी पीक सल्ला आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह बुवाई से पहले फसल सलाह है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Pre-Crop Advisory. I will ask you a few questions."
            )
            FeatureRoute.CROP_ROTATION -> LocalizedText(
                mr = "हे पीक फेरपालट योजना आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह फसल चक्र योजना है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Crop Rotation Planning. I will ask you a few questions."
            )
            FeatureRoute.FRESHNESS -> LocalizedText(
                mr = "हे उत्पादन ताजेपणा तपासणी आहे. तुमच्या फळ किंवा भाजीचा फोटो काढा.",
                hi = "यह उपज ताज़गी जाँच है. अपने फल या सब्जी की फोटो लें.",
                en = "This is Freshness Check. Take a photo of your fruit or vegetable."
            )
            FeatureRoute.STORAGE_GUIDE -> LocalizedText(
                mr = "हे साठवण मार्गदर्शन आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह भंडारण मार्गदर्शन है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Storage Guide. I will ask you a few questions."
            )
            FeatureRoute.HARVEST_TIMING -> LocalizedText(
                mr = "हे काढणी वेळ सल्ला आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह कटाई समय सलाह है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Harvest Timing. I will ask you a few questions."
            )
            FeatureRoute.COLD_CHAIN -> LocalizedText(
                mr = "हे कोल्ड स्टोरेज सल्ला आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह कोल्ड स्टोरेज सलाह है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Cold Chain Advisor. I will ask you a few questions."
            )
            FeatureRoute.SELL_ADVISOR -> LocalizedText(
                mr = "हे विक्री वेळ सल्ला आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह बेचने का समय सलाह है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Sell Timing Advisor. I will ask you a few questions."
            )
            FeatureRoute.MARKET_PRICE -> LocalizedText(
                mr = "हे बाजारभाव अंदाज आहे. तुमचे पीक सांगा.",
                hi = "यह बाजार भाव अनुमान है. अपनी फसल बताएं.",
                en = "This is Market Price Prediction. Tell me your crop name."
            )
            FeatureRoute.POSTHARVEST_LOSS -> LocalizedText(
                mr = "हे नुकसान टाळण्याचा सल्ला आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह नुकसान से बचाव सलाह है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Post Harvest Loss Prevention. I will ask you a few questions."
            )
            FeatureRoute.SURPLUS -> LocalizedText(
                mr = "हे अधिशेष पीक जुळवणी आहे. तुमच्याकडे किती जास्त पीक आहे?",
                hi = "यह अधिक फसल मिलान है. आपके पास कितनी अधिक फसल है?",
                en = "This is Surplus Crop Matching. How much surplus crop do you have?"
            )
            FeatureRoute.VALUE_ADDITION -> LocalizedText(
                mr = "हे मूल्यवर्धन सल्ला आहे. तुमचे पीक सांगा.",
                hi = "यह मूल्य वर्धन सलाह है. अपनी फसल बताएं.",
                en = "This is Value Addition Advisor. Tell me your crop name."
            )
            FeatureRoute.WASTE_ENGINE -> LocalizedText(
                mr = "हे कृषी कचऱ्यापासून उत्पन्न आहे. तुमचा कचरा प्रकार सांगा.",
                hi = "यह कृषि अपशिष्ट से आय है. अपना कचरा प्रकार बताएं.",
                en = "This is Agri Waste Revenue. Tell me your waste type."
            )
            FeatureRoute.CARBON_TRACKER -> LocalizedText(
                mr = "हे कार्बन ट्रॅकर आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह कार्बन ट्रैकर है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Carbon Tracker. I will ask you a few questions."
            )
            FeatureRoute.GOVT_SCHEMES -> LocalizedText(
                mr = "हे सरकारी योजना तपासणी आहे. मी तुम्हाला काही प्रश्न विचारतो.",
                hi = "यह सरकारी योजना जाँच है. मैं आपसे कुछ सवाल पूछूँगा.",
                en = "This is Government Scheme Check. I will ask you a few questions."
            )
            FeatureRoute.GENERAL -> LocalizedText(
                mr = "मी तुमचा व्हॉइस असिस्टंट आहे. बोला, मी ऐकतो आहे.",
                hi = "मैं आपका वॉयस असिस्टेंट हूँ. बोलिए, मैं सुन रहा हूँ.",
                en = "I am your voice assistant. Speak, I am listening."
            )
        }.forLang(lang)

    fun photoCameraHint(lang: String = AppLanguageManager.currentLanguage): String = LocalizedText(
        mr = "कृपया स्क्रीनवरील कॅमेरा बटण दाबा.",
        hi = "कृपया स्क्रीन पर कैमरा बटन दबाएं.",
        en = "Please tap the camera button on the screen."
    ).forLang(lang)

    fun needsPhoto(route: FeatureRoute): Boolean =
        route == FeatureRoute.SOIL_PASSPORT || route == FeatureRoute.FRESHNESS

    fun guidedQuestions(route: FeatureRoute): List<LocalizedText> = when (route) {
        FeatureRoute.PRE_CROP -> listOf(
            LocalizedText("तुमची जमीन किती एकर आहे?", "आपकी जमीन कितने एकड़ है?", "How many acres is your land?"),
            LocalizedText("तुमची माती कोणती आहे?", "आपकी मिट्टी कौन सी है?", "What is your soil type?"),
            LocalizedText("तुमच्याकडे पाणी किती आहे?", "आपके पास पानी कितना है?", "How much water is available?")
        )
        FeatureRoute.CROP_ROTATION -> listOf(
            LocalizedText("मागील हंगामात कोणते पीक घेतले?", "पिछले सीजन में कौन सी फसल ली?", "What crop did you grow last season?"),
            LocalizedText("तुमची जमीन किती एकर आहे?", "आपकी जमीन कितने एकड़ है?", "How many acres is your land?")
        )
        FeatureRoute.STORAGE_GUIDE -> listOf(
            LocalizedText("तुम्ही कोणते पीक साठवत आहात?", "आप कौन सी फसल रख रहे हैं?", "What crop are you storing?"),
            LocalizedText("किती दिवस साठवायचे आहे?", "कितने दिन रखना है?", "How many days do you want to store?")
        )
        FeatureRoute.HARVEST_TIMING -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?"),
            LocalizedText("पेरणी कधी केली?", "बुवाई कब की?", "When did you sow?")
        )
        FeatureRoute.COLD_CHAIN -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?"),
            LocalizedText("तुम्ही कुठे आहात?", "आप कहाँ हैं?", "Where are you located?")
        )
        FeatureRoute.SELL_ADVISOR -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?"),
            LocalizedText("किती किलो आहे?", "कितने किलो है?", "How many kilos do you have?")
        )
        FeatureRoute.MARKET_PRICE -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?")
        )
        FeatureRoute.POSTHARVEST_LOSS -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?"),
            LocalizedText("बाजार किती दूर आहे?", "बाजार कितना दूर है?", "How far is the market?")
        )
        FeatureRoute.SURPLUS -> listOf(
            LocalizedText("कोणते पीक जास्त आहे?", "कौन सी फसल अधिक है?", "Which crop is surplus?"),
            LocalizedText("किती किलो आहे?", "कितने किलो है?", "How many kilos?")
        )
        FeatureRoute.VALUE_ADDITION -> listOf(
            LocalizedText("तुमचे पीक कोणते आहे?", "आपकी फसल कौन सी है?", "What is your crop?")
        )
        FeatureRoute.WASTE_ENGINE -> listOf(
            LocalizedText("तुमच्याकडे कोणता कचरा आहे?", "आपके पास कौन सा कचरा है?", "What type of waste do you have?")
        )
        FeatureRoute.CARBON_TRACKER -> listOf(
            LocalizedText("तुमची जमीन किती एकर आहे?", "आपकी जमीन कितने एकड़ है?", "How many acres is your land?"),
            LocalizedText("तुम्ही कोणते खत वापरता?", "आप कौन सी खाद इस्तेमाल करते हैं?", "What fertilizer do you use?")
        )
        FeatureRoute.GOVT_SCHEMES -> listOf(
            LocalizedText("तुमची जमीन किती एकर आहे?", "आपकी जमीन कितने एकड़ है?", "How many acres is your land?"),
            LocalizedText("तुमचे वार्षिक उत्पन्न किती आहे?", "आपकी सालाना आय कितनी है?", "What is your annual income?")
        )
        else -> emptyList()
    }

    fun hasGuidedQuestions(route: FeatureRoute): Boolean = guidedQuestions(route).isNotEmpty()
}
