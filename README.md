# 🎂 Cooker - Birthday PR Recap Agent

An AI-powered agent built with [Koog Framework](https://docs.koog.ai/) that celebrates team members' birthdays by creating personalized year-in-review recaps based on their GitHub contributions.

## ✨ What it does

1. **Checks for birthdays** - Automatically detects if anyone on the team has a birthday today
2. **Fetches GitHub activity** - Retrieves the person's recent merged Pull Requests using GitHub CLI
3. **Creates a recap** - Uses AI to generate a warm, personalized summary of their contributions

## 🛠️ Tech Stack

- **[Koog](https://github.com/JetBrains/koog)** - JetBrains' Kotlin framework for building AI agents
- **[Ollama](https://ollama.ai/)** - Local LLM inference
- **[GitHub CLI](https://cli.github.com/)** - Fetching PR data
- **Kotlin** - Because we love type-safe code

## 🚀 Getting Started

### Prerequisites

1. **Ollama** running locally:
   ```bash
   ollama serve
   ```

2. **GitHub CLI** authenticated:
   ```bash
   gh auth login
   ```

### Run the Agent

```bash
./gradlew run
```

## 🔧 Tools

The agent uses these custom tools:

| Tool | Description |
|------|-------------|
| `check_birthday` | Checks if anyone has a birthday today from the team list |
| `get_github_username` | Maps a person's name to their GitHub username |
| `fetch_github_prs` | Fetches recent merged PRs for a GitHub user |

## 📁 Project Structure

```
src/main/kotlin/
├── Main.kt                          # Entry point
├── agents/
│   └── BirthdayRecapAgent.kt       # Agent configuration & system prompt
└── tools/
    ├── CheckBirthday.kt            # Birthday checking tool
    ├── FetchGitHubPRs.kt           # GitHub PR fetcher
    ├── GetGitHubUsername.kt        # Name → GitHub username mapper
    └── GetTodaysDate.kt            # Current date tool
```

## ⚙️ Configuration

Edit `OllamaConfig` in `Main.kt` to customize:

```kotlin
data class OllamaConfig(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "gpt-oss:20b",
    val temperature: Double = 0.7
)
```

Or use environment variables:
- `OLLAMA_URL`
- `OLLAMA_MODEL`
- `OLLAMA_TEMPERATURE`

## 🎯 Example Output

```
🔧 [TOOL CALL] check_birthday()
   └─ Result: YES! Today (JANUARY 13) is a birthday! Birthday celebrants: Onuralp Avcı

🔧 [TOOL CALL] get_github_username(name="Onuralp Avcı")
   └─ Result: GitHub username for 'Onuralp Avcı' is: onuralp-avci_midas

🔧 [TOOL CALL] fetch_github_prs(username="onuralp-avci_midas", limit=10)
   └─ Result: Found PRs (2500 chars)

📝 Agent Response:
==================================================
🎂 Happy Birthday Onuralp! What an incredible year you've had! 
You merged 10 PRs including CI/CD improvements, Maestro test 
infrastructure, and motion system animations. Your dedication 
to quality shows in every commit. Here's to another amazing year! 🚀
```

## 📚 Resources

- [Koog Documentation](https://docs.koog.ai/)
- [Koog GitHub](https://github.com/JetBrains/koog)
- [Koog API Reference](https://api.koog.ai/)

## 📄 License

MIT

