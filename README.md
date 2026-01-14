# 🧪 Cooker - AI Agents for DevOps

A collection of AI-powered agents built with [Koog Framework](https://docs.koog.ai/) to automate developer workflows.

## 🤖 Agents

### 🎂 Birthday PR Recap Agent

Celebrates team members' birthdays by creating personalized year-in-review recaps based on their GitHub contributions.

**What it does:**
1. Checks for birthdays today
2. Fetches the person's recent merged Pull Requests
3. Generates a warm, personalized summary of their contributions

### 🧪 UI Test Analyzer Agent

Analyzes Maestro UI test failures from GitHub Actions and categorizes them automatically.

**What it does:**
1. Fetches the last N workflow runs
2. Downloads all `maestro-test-summary-*` artifacts (auto-discovers all domains!)
3. Analyzes failure patterns across runs
4. Categorizes failures as:
   - 🆕 **NEW** - First time failure (investigate immediately!)
   - 🐛 **BUG** - Consistent failure (70%+ of runs)
   - 🐛 **LIKELY_BUG** - Frequent failure (40-70% of runs)
   - ⚠️ **FLAKY** - Intermittent failure (needs stabilization)

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

### Run the Agents

```bash
# Birthday PR Recap Agent
./gradlew runBirthdayAgent

# UI Test Analyzer Agent
./gradlew runUITestAnalyzer

# Or with environment variables
AGENT_TYPE=uitest ./gradlew run
```

## 🔧 Tools

### Birthday PR Recap Agent Tools

| Tool | Description |
|------|-------------|
| `check_birthday` | Checks if anyone has a birthday today from the team list |
| `get_github_username` | Maps a person's name to their GitHub username |
| `fetch_github_prs` | Fetches recent merged PRs for a GitHub user |

### UI Test Analyzer Agent Tools

| Tool | Description |
|------|-------------|
| `analyze_ui_test_failures` | Analyzes workflow runs, downloads artifacts with wildcard pattern, categorizes failures |

## 📁 Project Structure

```
src/main/kotlin/
├── Main.kt                            # Entry point (agent selector)
├── agents/
│   ├── BirthdayRecapAgent.kt         # Birthday agent config
│   └── UITestAnalyzerAgent.kt        # UI test analyzer config
└── tools/
    ├── CheckBirthday.kt              # Birthday checking
    ├── FetchGitHubPRs.kt             # GitHub PR fetcher
    ├── GetGitHubUsername.kt          # Name → username mapper
    ├── GetTodaysDate.kt              # Current date
    └── AnalyzeUITestFailures.kt      # UI test failure analyzer
```

## ⚙️ Configuration

### Ollama Configuration

Environment variables:
- `OLLAMA_URL` - Ollama server URL (default: `http://localhost:11434`)
- `OLLAMA_MODEL` - LLM model (default: `gpt-oss:20b`)
- `OLLAMA_TEMPERATURE` - Temperature (default: `0.7`)

### UI Test Analyzer Configuration

Environment variables:
- `TARGET_REPO` - Repository to analyze (default: `midas-engineering/mobile-android`)
- `TARGET_WORKFLOW` - Workflow name (default: `Maestro UI Test`)
- `RUN_COUNT` - Number of runs to analyze (default: `10`)

**Note:** Domains are auto-discovered! No configuration needed for new test domains.

## 🎯 Example Output

### Birthday PR Recap Agent

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

### UI Test Analyzer Agent

```
🔧 [TOOL CALL] analyze_ui_test_failures(...)
   └─ Fetching workflow runs...
      ✅ Parsed 10 workflow runs
   └─ Downloading test summaries...
      🔍 Using wildcard pattern: maestro-test-summary-*
      📥 Run 20985553906/trade: 1 failures
      📥 Run 20973198847/onboarding: 1 failures
      📊 Total: 2 artifacts with failures, 48 total failures
   └─ Found 48 unique failed tests

📝 Agent Response:
==================================================
🐛 LIKELY_BUG (1 test):
   • signinPhoneValidationErrorFlowTest - Fails 40% of runs
     Branches: develop, release/2.80.0

⚠️ FLAKY (47 tests):
   • transferToInvestmentBottomSheetFlowTest - Fails 20% of runs
   • userListItemDeleteFlowTest - Fails 30% of runs
   ...
```

## 📚 Resources

- [Koog Documentation](https://docs.koog.ai/)
- [Koog GitHub](https://github.com/JetBrains/koog)
- [Koog API Reference](https://api.koog.ai/)

## 📄 License

MIT

