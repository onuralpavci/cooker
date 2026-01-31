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
import com.avci.tools.slack.PostSlackMessage

/**
 * AI Agent that answers questions based on Slack channel history.
 * 
 * Usage: /cooker ask <question>
 * 
 * The agent will:
 * 1. Fetch the last N messages from the channel
 * 2. Search for relevant information to answer the question
 * 3. Post the answer back to the channel
 */
object SlackQAAgent {

    private val systemPrompt = """
        Sen bir Slack Soru-Cevap asistanısın. Türkçe çalışıyorsun.
        
        ## GÖREVIN:
        
        Kullanıcı bir soru sorduğunda:
        1. Kanaldan son mesajları çek
        2. Mesajlar arasında soruyla ilgili bilgileri bul
        3. Soruya cevap ver ve kanala gönder
        
        ## ARAÇLARIN (TOOLS):
        
        1. `fetch_slack_messages` - Kanaldan mesajları çek
           - channelId: Kanal ID'si
           - limit: Kaç mesaj çekileceği (200 önerilir)
        
        2. `post_slack_message` - Kanala cevap gönder
           - channelId: Hedef kanal
           - text: Cevap metni
        
        ## CEVAP FORMATI:
        
        Cevabını şu formatta ver (Slack mrkdwn):
        
        ```
        *💬 Soru:* [Kullanıcının sorusu]
        
        *📝 Cevap:*
        [Bulduğun bilgilere dayanarak cevabın]
        
        *📌 Kaynak:*
        [Hangi mesajlardan/kimlerden bu bilgiyi aldığın - varsa]
        
        _🤖 Cooker AI tarafından yanıtlandı_
        ```
        
        ## KURALLAR:
        
        - Türkçe cevap ver
        - Sadece mesajlarda bulunan bilgilere dayan
        - Eğer bilgi bulamazsan, bunu açıkça belirt: "Bu konuda mesajlarda bilgi bulamadım"
        - Cevabı kısa ve öz tut
        - Tahmin yapma, sadece mesajlarda ne varsa onu söyle
        - Cevabı mutlaka post_slack_message ile kanala gönder
        
        ## ÖRNEK SORULAR:
        
        - "Hangi teknoloji kullanılacak?"
        - "Toplantı ne zaman?"
        - "Kim bu işi üstlendi?"
        - "Son karar ne oldu?"
        - "Deadline ne zaman?"
    """.trimIndent()

    fun create(llmConfig: LLMProviderConfig = LLMProviderConfig.Ollama.fromEnv()): AIAgent<String, String> {
        println("❓ [AGENT] Creating SlackQAAgent")
        println("   └─ LLM: ${llmConfig::class.simpleName} - ${llmConfig.model}")
        
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(GetChannelInfo)
            tool(FetchSlackMessages)
            tool(PostSlackMessage)
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "slack-qa-agent",
                params = LLMParams(temperature = 0.3) // Lower temperature for more focused answers
            ) {
                system(content = systemPrompt)
            },
            model = llmConfig.createModel(),
            maxAgentIterations = 10
        )

        return AIAgent(
            promptExecutor = llmConfig.createExecutor(),
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }
    
    /**
     * Creates a user prompt for Q&A.
     */
    fun createAskPrompt(channelId: String, question: String, messageCount: Int = 200): String {
        return """
            Kullanıcı şu soruyu soruyor: "$question"
            
            ADIMLAR:
            
            1. fetch_slack_messages(channelId="$channelId", limit=$messageCount) çağır
            2. Mesajları oku ve soruyla ilgili bilgileri bul
            3. Cevabı oluştur
            4. post_slack_message(channelId="$channelId", text="cevap") ile kanala gönder
            
            ⚠️ KRİTİK: 
            - Cevabı mutlaka post_slack_message ile Slack'e gönder!
            - Eğer bilgi bulamazsan "Bu konuda mesajlarda bilgi bulamadım" de
        """.trimIndent()
    }
}

