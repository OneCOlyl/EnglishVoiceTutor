---
name: conventional-commits
description: Generates semantic commit messages in Russian following the Conventional Commits specification with proper types, scopes, breaking changes, and footers. Use when users request "write commit message", "conventional commit", "semantic commit", or "format commit".
---

# Conventional Commits (Russian descriptions)

**CRITICAL: Commit descriptions MUST be written in Russian.** Commit types (`feat`, `fix`, `docs`, etc.) remain in English as per the specification. Only the description, body, and footer text is in Russian.

## Core Workflow

1. **Determine task ID**: Extract task ID from the current branch name (e.g. branch `feature/ABC-123` → task `ABC-123`). Run `git branch --show-current`.
2. **Analyze changes**: Review staged files and modifications
3. **Determine type**: Select appropriate commit type (feat, fix, etc.)
4. **Write description**: Concise summary in imperative mood, **in Russian**
5. **Add body**: Optional detailed explanation in Russian
6. **Include footer**: Breaking changes, issue references

## Commit Message Format

```
<type>(<task-id>): <description in Russian>

[optional body in Russian]

[optional footer(s)]
```

## Commit Types

| Type | Description | Semver | Example |
|------|-------------|--------|---------|
| `feat` | New feature | MINOR | `feat(ABC-123): добавить аутентификацию` |
| `fix` | Bug fix | PATCH | `fix(ABC-123): исправить редирект после логина` |
| `docs` | Documentation only | - | `docs(ABC-123): обновить описание API` |
| `style` | Formatting, whitespace | - | `style(ABC-123): поправить отступы в утилитах` |
| `refactor` | Code change, no feature/fix | - | `refactor(ABC-123): вынести логику валидации` |
| `perf` | Performance improvement | PATCH | `perf(ABC-123): оптимизировать запросы к базе` |
| `test` | Adding/fixing tests | - | `test(ABC-123): добавить unit-тесты для авторизации` |
| `build` | Build system, dependencies | - | `build(ABC-123): обновить Node до 20 версии` |
| `ci` | CI/CD configuration | - | `ci(ABC-123): добавить GitHub Actions workflow` |
| `chore` | Maintenance tasks | - | `chore(ABC-123): обновить .gitignore` |
| `revert` | Revert previous commit | - | `revert(ABC-123): откатить изменение feature flag` |

## Scope

Scope is **always** the task ID from the branch name:

```bash
# Get task ID from current branch
git branch --show-current
# feature/ABC-123 → scope = ABC-123
# bugfix/ABC-123 → scope = ABC-123

# Commit with task scope
feat(ABC-123): добавить поддержку OAuth2
fix(ABC-123): обработать таймаут запросов
docs(ABC-123): добавить инструкцию по установке
```

## Breaking Changes

Mark breaking changes with `!` or `BREAKING CHANGE` footer:

```bash
# Using ! notation
feat(ABC-123)!: перевести формат ответа на JSON:API

# Using footer
feat(ABC-123): изменить формат ответов

BREAKING CHANGE: Ответы теперь соответствуют спецификации JSON:API.
Клиенты должны обновить парсеры.
```

## Commit Message Examples

### Simple Feature
```
feat(ABC-123): добавить переключатель тёмной темы
```

### Bug Fix
```
fix(ABC-123): исправить гонку при истечении сессии

Обновление сессии конфликтовало с проверкой срока действия,
вызывая спорадические разлогины.
```

### Breaking Change
```
feat(ABC-123)!: мигрировать на v2 формат ответов

BREAKING CHANGE: Все ответы API теперь используют camelCase
вместо snake_case. Обновите клиентские парсеры.

Инструкция по миграции: https://docs.example.com/v2-migration
```

### Multiple Footers
```
fix(ABC-123): исправить расчёт налогов для клиентов из ЕС

Расчёт налога теперь использует страну плательщика
вместо страны доставки для цифровых товаров.

Reviewed-by: Alice
Co-authored-by: Bob <bob@example.com>
```

### Revert Commit
```
revert(ABC-123): добавить поддержку OAuth2

Откатывает коммит abc123def456.

Причина: у OAuth-провайдера проблемы с rate limiting в проде.
Переделаем с нормальным кешированием.
```

## Description Guidelines

### Do
- Use imperative mood in Russian: "добавить" not "добавил" or "добавлено"
- Keep under 72 characters
- Start with lowercase
- No period at the end
- Be specific and concise

### Don't
- "Исправил баг" (too vague)
- "Обновил всякое" (not descriptive)
- "WIP" (commit when ready)
- "Разные правки" (split into separate commits)

### Good Examples
```bash
feat(ABC-123): добавить верификацию email
fix(ABC-123): заблокировать повторную отправку формы
refactor(ABC-123): вынести обработку платежей в сервис
perf(ABC-123): закешировать пользовательские настройки
docs(ABC-123): добавить примеры аутентификации в API
```

### Bad Examples
```bash
# Missing task scope
feat: добавить верификацию email

# Too vague
fix(ABC-123): починил

# Wrong grammatical form
feat(ABC-123): добавлена новая фича

# Too long
feat(ABC-123): добавить возможность экспорта данных в разные форматы включая CSV JSON и XML
```

## Body Guidelines

When to include a body:
- Changes need context or explanation
- Complex logic that isn't self-evident
- Breaking changes require migration info
- Multiple related changes in one commit

```
fix(ABC-123): инвалидировать кеш пользователя при обновлении профиля

Раньше изменения профиля не были видны до истечения кеша.
Это сбивало с толку пользователей после смены аватара.

Исправление добавляет инвалидацию кеша после обновления профиля
и гарантирует сброс CDN для статических ресурсов.
```

## Footer Tokens

| Token | Purpose | Example |
|-------|---------|---------|
| `Fixes` | Closes issue | `Fixes #123` |
| `Closes` | Closes issue | `Closes #456` |
| `Refs` | References issue | `Refs #789` |
| `BREAKING CHANGE` | Breaking change | `BREAKING CHANGE: description` |
| `Reviewed-by` | Reviewer credit | `Reviewed-by: Name` |
| `Co-authored-by` | Co-author credit | `Co-authored-by: Name <email>` |

## Integration with Tooling

### Commitlint Configuration

```javascript
// commitlint.config.js
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat', 'fix', 'docs', 'style', 'refactor',
        'perf', 'test', 'build', 'ci', 'chore', 'revert'
      ]
    ],
    'scope-case': [2, 'always', 'kebab-case'],
    'subject-case': [2, 'always', 'lower-case'],
    'subject-max-length': [2, 'always', 72],
    'body-max-line-length': [2, 'always', 100]
  }
};
```

### Husky Pre-commit Hook

```bash
# .husky/commit-msg
#!/bin/sh
. "$(dirname "$0")/_/husky.sh"
npx --no-install commitlint --edit "$1"
```

### Package.json Setup

```json
{
  "devDependencies": {
    "@commitlint/cli": "^18.0.0",
    "@commitlint/config-conventional": "^18.0.0",
    "husky": "^8.0.0"
  },
  "scripts": {
    "prepare": "husky install"
  }
}
```

## Semantic Release Integration

Conventional commits enable automated versioning:

```yaml
# .releaserc.yml
branches:
  - main
plugins:
  - "@semantic-release/commit-analyzer"
  - "@semantic-release/release-notes-generator"
  - "@semantic-release/changelog"
  - "@semantic-release/npm"
  - "@semantic-release/git"
```

### Version Bumping Rules

| Commit Type | Version Bump | Example |
|-------------|--------------|---------|
| `feat` | Minor (0.X.0) | 1.2.0 → 1.3.0 |
| `fix` | Patch (0.0.X) | 1.2.0 → 1.2.1 |
| `perf` | Patch (0.0.X) | 1.2.0 → 1.2.1 |
| `BREAKING CHANGE` | Major (X.0.0) | 1.2.0 → 2.0.0 |
| Others | No bump | 1.2.0 → 1.2.0 |

## Commit Message Generator

When analyzing changes, generate a commit message with a Russian description:

```bash
# 1. Check staged changes
git diff --cached --name-only

# 2. Get task ID from branch
git branch --show-current
# feature/ABC-123 → scope = ABC-123

# 3. Analyze change type
# - New files = likely feat
# - Modified test files = test
# - Modified docs = docs
# - Bug-related keywords = fix

# 4. Generate message (description in Russian)
feat(ABC-123): добавить состояние загрузки для кнопки
```

## Best Practices

1. **One logical change per commit**: Don't mix features with fixes
2. **Commit early, commit often**: Small, focused commits
3. **Write for reviewers**: Messages should explain why, not just what
4. **Reference issues**: Link to tickets/issues when applicable
5. **Use scopes consistently**: Establish team conventions
6. **Review before committing**: `git diff --cached` to verify changes

## Output Checklist

Every commit message should:

- [ ] Start with valid type (feat, fix, docs, etc.)
- [ ] Use imperative mood in Russian for description
- [ ] Keep description under 72 characters
- [ ] Include task ID as scope (from branch name) — required
- [ ] Mark breaking changes with `!` or footer
- [ ] Reference related issues in footer
- [ ] Provide body for complex changes
- [ ] Follow team's scope conventions
