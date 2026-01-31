package com.avci.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import com.avci.core.llm.LLMProviderConfig
import com.avci.tools.slack.FetchSlackMessages
import com.avci.tools.slack.GetChannelInfo
import com.avci.tools.slack.ListSlackChannels
import com.avci.tools.slack.PostSlackMessage

/**
 * AI Agent that summarizes Slack channel conversations.
 * 
 * Capabilities:
 * - List available channels
 * - Fetch messages from any channel
 * - Summarize conversations in Turkish
 * - Post summary back to the same channel
 * - Support for threading (reply to messages)
 * 
 * Required Slack Bot Scopes:
 * - channels:read (list channels)
 * - channels:history (read messages)
 * - chat:write (post messages)
 */
object SlackSummarizerAgent {

    private val systemPrompt = """
        Sen bir Slack Kanal Özetleyicisisin. Türkçe çalışıyorsun.
        
        ## ARAÇLARIN (TOOLS):
        
        1. `list_slack_channels` - Mevcut kanalları listele
        2. `get_channel_info` - Kanal detaylarını al (isim, konu, üye sayısı)
        3. `fetch_slack_messages` - Kanaldan mesajları çek
           - channelId: Kanal ID'si (C ile başlar)
           - limit: Kaç mesaj çekileceği (default: 100)
        4. `post_slack_message` - Kanala mesaj gönder
           - channelId: Hedef kanal
           - text: Mesaj metni (mrkdwn formatında)
           - blocks: Slack Block Kit blokları (opsiyonel)
           - threadTs: Thread'e reply için (opsiyonel)
        
        ## İŞ AKIŞIN:
        
        1. Kullanıcı bir kanal ID'si ve mesaj sayısı verir
        2. `fetch_slack_messages` ile mesajları çek
        3. Mesajları analiz et ve Türkçe özetle
        4. `post_slack_message` ile özeti AYNI KANALA gönder
        
        ## ÖZET FORMATI:
        
        Özetini şu şekilde formatla (Slack mrkdwn):
        
        ```
        *📝 Kanal Özeti*
        _Son [N] mesajın analizi_
        
        *📋 Ana Konular*
        • Konu 1
        • Konu 2
        
        *✅ Kararlar & Aksiyonlar*
        • Karar/Aksiyon 1
        • Karar/Aksiyon 2
        
        *📢 Önemli Güncellemeler*
        • Güncelleme 1
        
        *❓ Açık Sorular*
        • Soru 1
        
        _🤖 Cooker AI tarafından oluşturuldu_
        ```
        
        ## KURALLAR:
        
        - Türkçe özetle
        - Kısa ve öz ol (max 500 kelime)
        - Önemli @mention'ları koru
        - Sistem mesajlarını (joined channel, added integration) atla
        - Eğer mesaj yoksa veya içerik boşsa, bunu belirt
        - Özeti her zaman aynı kanala gönder
        
        ## SLACK MRKDWN FORMATI:
        
        - *bold* - kalın
        - _italic_ - italik
        - `code` - kod
        - • bullet point
        - ~strikethrough~ - üstü çizili
        - <@USER_ID> - kullanıcı mention
        - <#CHANNEL_ID> - kanal mention
        
        ## BLOCK KIT KULLANIMI (ÖNERİLEN):
        
        Daha güzel görünüm için `post_slack_message` çağırırken `blocks` parametresini kullan.
        
        Örnek blocks parametresi:
        ```json
        [
          {"type": "header", "text": {"type": "plain_text", "text": "📝 Kanal Özeti", "emoji": true}},
          {"type": "section", "text": {"type": "mrkdwn", "text": "_Son 50 mesajın analizi_"}},
          {"type": "divider"},
          {"type": "section", "text": {"type": "mrkdwn", "text": "*📋 Ana Konular*\n• Konu 1\n• Konu 2\n• Konu 3"}},
          {"type": "section", "text": {"type": "mrkdwn", "text": "*✅ Kararlar & Aksiyonlar*\n• Aksiyon 1\n• Aksiyon 2"}},
          {"type": "section", "text": {"type": "mrkdwn", "text": "*❓ Açık Sorular*\n• Soru 1"}},
          {"type": "divider"},
          {"type": "context", "elements": [{"type": "mrkdwn", "text": "🤖 _Cooker AI tarafından oluşturuldu_"}]}
        ]
        ```
        
        Block tipleri:
        - `header`: Başlık (plain_text)
        - `section`: İçerik bloğu (mrkdwn destekler)
        - `divider`: Ayırıcı çizgi
        - `context`: Küçük footer metin
        
        ÖNEMLİ: blocks kullanırken text parametresini de doldur (fallback için).
    """.trimIndent()

    fun create(llmConfig: LLMProviderConfig = LLMProviderConfig.Ollama.fromEnv()): AIAgent<String, String> {
        println("💬 [AGENT] Creating SlackSummarizerAgent")
        println("   └─ LLM: ${llmConfig::class.simpleName} - ${llmConfig.model}")
        
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(ListSlackChannels)
            tool(GetChannelInfo)
            tool(FetchSlackMessages)
            tool(PostSlackMessage)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "slack-summarizer-agent",
                params = LLMParams(temperature = llmConfig.temperature)
            ) {
                system(content = systemPrompt)
            },
            model = llmConfig.createModel(),
            maxAgentIterations = 15
        )

        return AIAgent(
            promptExecutor = llmConfig.createExecutor(),
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
    
    /**
     * Creates a user prompt for summarization.
     */
    fun createSummarizePrompt(channelId: String, messageCount: Int): String {
        return """
            Kanal $channelId'deki son $messageCount mesajı özetle ve özeti aynı kanala gönder.
            
            ZORUNLU ADIMLAR (HEPSİNİ YAPMAN GEREKİYOR):
            
            1. ÖNCE: fetch_slack_messages tool'unu çağır
               fetch_slack_messages(channelId="$channelId", limit=$messageCount)
            
            2. SONRA: Mesajları analiz et ve Türkçe özetle
            
            3. EN SON: post_slack_message tool'unu MUTLAKA çağır
               post_slack_message(channelId="$channelId", text="özet metni buraya")
            
            ⚠️ KRİTİK: 3. adımı ATLAMA! post_slack_message tool'unu çağırmadan bitirme!
            Özeti sadece yazmak yetmez, Slack'e göndermen ZORUNLU.
        """.trimIndent()
    }
}
