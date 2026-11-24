package com.lig7oo7.gutgi;

/**
 * 기본 추천 사전. 여기에 단어·문구를 추가하면 초성/앞글자로 추천된다.
 * (앱 설정의 '내 단어'와 상용구도 자동으로 함께 추천됨)
 */
public class Dict {

    public static final String[] DOMAINS = {
            "gmail.com", "naver.com", "kakao.com", "daum.net", "hanmail.net", "nate.com", "icloud.com"
    };

    public static final String[] WORDS = {
            // 인사·대답
            "안녕", "안녕하세요", "안녕히 계세요", "안녕히 가세요", "감사합니다", "고맙습니다", "고마워",
            "죄송합니다", "미안해", "괜찮아", "괜찮습니다", "알겠습니다", "알겠어", "네 알겠습니다",
            "수고하셨습니다", "수고하세요", "수고했어", "고생했어", "고생하셨습니다", "잘 부탁드립니다",
            "좋은 아침", "좋은 하루 보내세요", "잘 자", "잘자요", "주말 잘 보내세요", "다음에 봐",
            "축하합니다", "축하해", "생일 축하해", "사랑해", "보고 싶어", "화이팅",
            // 연락·약속
            "지금 운전 중이에요", "운전 중이라 이따 연락할게", "이따 연락할게", "나중에 연락드릴게요",
            "확인했습니다", "확인했어", "전화 주세요", "전화할게", "문자 주세요", "어디야", "어디쯤이야",
            "도착했어", "출발했어", "곧 도착해요", "조금 늦을 것 같아요", "금방 갈게", "기다려 줘",
            "오늘", "내일", "모레", "어제", "이번 주", "다음 주", "주말", "저녁", "점심", "아침",
            "시간 괜찮아", "몇 시에 볼까", "어디서 볼까", "밥 먹었어", "밥 먹자", "커피 한잔",
            // 배달·일
            "배달", "배달 완료", "픽업", "픽업 완료", "도착", "출발", "콜", "대기 중", "주문",
            "문 앞에 두고 갈게요", "벨 누르지 말아 주세요", "음식 도착했습니다", "조심히 가세요",
            // 과수원·가족
            "사과", "과수원", "엄마", "아빠", "부모님", "병원", "약 드셨어요", "건강 챙기세요",
            // 자주 쓰는 말
            "그래", "그래요", "좋아", "좋아요", "맞아", "맞아요", "아니", "아니요", "진짜", "정말",
            "대박", "그럼", "그런데", "그래서", "그리고", "하지만", "왜냐하면", "때문에", "생각해 볼게",
            "가능합니다", "불가능합니다", "모르겠어", "모르겠습니다", "궁금해", "부탁해", "부탁드립니다",
            "감사해요", "고마워요", "미안해요", "잠깐만", "잠시만요", "여보세요", "얼마예요", "계좌번호",
    };

    /** 영어: 2~3글자 치면 앞글자로 추천 */
    public static final String[] ENGLISH = {
            "the", "and", "that", "have", "for", "not", "with", "you", "this", "but", "his", "from", "they",
            "say", "her", "she", "will", "one", "all", "would", "there", "their", "what", "out", "about",
            "who", "get", "which", "when", "make", "can", "like", "time", "just", "him", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see", "other", "than",
            "then", "now", "look", "only", "come", "its", "over", "think", "also", "back", "after", "use",
            "two", "how", "our", "work", "first", "well", "way", "even", "new", "want", "because", "any",
            "these", "give", "day", "most", "us", "hello", "thanks", "thank", "please", "sorry", "okay",
            "yes", "yeah", "sure", "maybe", "today", "tomorrow", "tonight", "morning", "night", "weekend",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "love", "miss",
            "great", "nice", "cool", "awesome", "amazing", "beautiful", "happy", "birthday",
            "congratulations", "welcome", "goodbye", "later", "soon", "again", "always", "never",
            "sometimes", "really", "very", "much", "more", "less", "where", "why", "home", "office", "house",
            "school", "hospital", "store", "market", "restaurant", "coffee", "lunch", "dinner", "breakfast",
            "meeting", "call", "message", "email", "phone", "address", "number", "delivery", "order",
            "pickup", "arrived", "arriving", "coming", "going", "leaving", "waiting", "busy", "free",
            "available", "tired", "hungry", "check", "confirm", "cancel", "schedule", "appointment", "apple",
            "orchard", "farm", "tree", "fruit", "harvest", "weather", "rain", "snow", "sunny", "cloudy",
            "windy", "cold", "hot", "warm", "google", "gmail", "naver", "kakao", "samsung", "galaxy",
            "youtube", "instagram", "facebook", "amazon", "netflix", "chatgpt", "gemini", "claude",
            "account", "password", "login", "download", "upload", "update", "install", "keyboard",
            "computer", "internet", "wifi", "bluetooth", "battery", "charger", "screen", "camera", "picture",
            "photo", "video", "music", "information", "question", "answer", "problem", "solution",
            "important", "interesting", "different", "difficult", "possible", "impossible", "necessary",
            "probably", "definitely", "exactly", "actually", "basically", "before", "between", "during",
            "without", "through", "around", "together", "another", "anything", "everything", "something",
            "nothing", "someone", "everyone", "anyone", "everybody",
    };
}
