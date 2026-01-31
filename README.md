# 🍳 Cooker - AI Agent Framework for Mobile Teams

A collection of AI-powered agents and tools built with [Koog Framework](https://docs.koog.ai/) to automate mobile development workflows.

## 🎯 Features

- **Slack Integration** - Read messages, post summaries, channel analysis
- **GitHub Integration** - PR analysis, UI test failure tracking
- **Jira Integration** - Task management, automated ticket creation
- **Figma Integration** - Design spec extraction
- **LLM Support** - Ollama (local), OpenAI, Anthropic (coming soon)

## 📝 Commands

### Summarize Slack Channel (CI/CD Optimized)

```bash
# Local
./gradlew run --args="summarize C0AD1763YAC 100"

# With environment variables
SLACK_CHANNEL_ID=C0AD1763YAC MESSAGE_COUNT=100 ./gradlew run --args="summarize"
```

**Slash Command Integration:**
```
/cooker summarize 50
```

## 🤖 Agents

### 🎂 Birthday PR Recap Agent
Celebrates team members' birthdays with personalized GitHub contribution recaps.

### 🧪 UI Test Analyzer Agent
Analyzes Maestro UI test failures from GitHub Actions:
- 🆕 **NEW** - First time failure
- 🐛 **BUG** - Consistent failure (70%+)
- ⚠️ **FLAKY** - Intermittent failure

### 💬 Slack Summarizer Agent
LLM-driven channel conversation analysis and summarization.

## 🚀 Quick Start

### Prerequisites

1. **Ollama** running locally:
   ```bash
   ollama serve
   ollama pull gpt-oss:20b
   ```

2. **GitHub CLI** authenticated:
   ```bash
   gh auth login
   ```

3. **Environment variables** (copy from `env.example`):
   ```bash
   export SLACK_BOT_TOKEN="xoxb-..."
   export SLACK_WEBHOOK_URL="https://hooks.slack.com/..."
   export OLLAMA_MODEL="gpt-oss:20b"
   ```

### Run Commands

```bash
# Show help
./gradlew run --args="help"

# Show configuration
./gradlew run --args="config"

# Summarize Slack channel
./gradlew run --args="summarize C0AD1763YAC 100"

# Run agents
./gradlew run --args="birthday"
./gradlew run --args="uitest"
./gradlew run --args="slack C0AD1763YAC"

# Test individual tools
./gradlew run --args="tool:list_slack_channels"
./gradlew run --args="tool:fetch_slack_messages C0AD1763YAC"
./gradlew run --args="tool:fetch_github_prs username"
```

## 🏗️ Project Structure

```
src/main/kotlin/com/avci/
├── Main.kt                     # CLI entry point
├── commands/
│   └── SummarizeCommand.kt     # Deterministic summarize flow
├── agents/
│   ├── BirthdayRecapAgent.kt
│   ├── UITestAnalyzerAgent.kt
│   └── SlackSummarizerAgent.kt
├── core/
│   ├── config/
│   │   └── CookerConfig.kt     # Centralized configuration
│   ├── llm/
│   │   └── LLMProvider.kt      # LLM abstraction
│   └── utils/
│       └── HttpClient.kt       # HTTP utilities
└── tools/
    ├── common/                 # Date, Birthday tools
    ├── github/                 # PR, UI test tools
    ├── slack/                  # Message, Channel tools
    ├── jira/                   # Task management tools
    ├── figma/                  # Design tools
    └── playstore/              # Review tools
```

## 🔧 CI/CD Integration

### GitHub Actions

Workflow: `.github/workflows/slack-summarize.yml`

**Trigger manually:**
```bash
gh workflow run slack-summarize.yml \
  -f channel_id=C0AD1763YAC \
  -f message_count=100
```

**Trigger from Lambda/API:**
```bash
curl -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/YOUR_USER/Cooker/dispatches \
  -d '{"event_type":"slack_summarize","client_payload":{"channel_id":"C0AD1763YAC","count":100}}'
```

### AWS Lambda + Slash Command

Architecture:
```
/cooker summarize 50
       │
       ▼
┌─────────────┐     repository_dispatch     ┌─────────────┐
│ AWS Lambda  │ ──────────────────────────► │   GitHub    │
│             │                             │   Actions   │
└─────────────┘                             └─────────────┘
                                                   │
                                            Cooker runs
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │   Slack     │
                                            │   Webhook   │
                                            └─────────────┘
```

## ⚙️ Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `OLLAMA_URL` | Ollama server URL | No (default: localhost:11434) |
| `OLLAMA_MODEL` | LLM model name | No (default: gpt-oss:20b) |
| `SLACK_BOT_TOKEN` | Slack Bot Token (xoxb-...) | For reading messages |
| `SLACK_WEBHOOK_URL` | Slack Webhook URL | For posting messages |
| `GITHUB_TOKEN` | GitHub PAT | For workflow triggers |
| `JIRA_BASE_URL` | Jira instance URL | For Jira tools |
| `JIRA_API_TOKEN` | Jira API token | For Jira tools |
| `FIGMA_ACCESS_TOKEN` | Figma PAT | For Figma tools |

## 🔑 Slack Setup

1. Create app at [api.slack.com/apps](https://api.slack.com/apps)
2. Add Bot Token Scopes:
   - `channels:read` - List channels
   - `channels:history` - Read messages
3. Install to workspace
4. Copy Bot Token (`xoxb-...`)
5. (Optional) Add Incoming Webhook for posting

## 📚 Resources

- [Koog Documentation](https://docs.koog.ai/)
- [Slack API](https://api.slack.com/)
- [GitHub Actions](https://docs.github.com/en/actions)

## 📄 License

MIT
