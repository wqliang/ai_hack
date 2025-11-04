# Git Flow Guide

This project uses Git Flow branching model for version control.

## Branch Structure

### Main Branches
- **main** - Production-ready code, always stable
- **develop** - Integration branch for features, next release preparation

### Supporting Branches

#### Feature Branches
- Branch from: `develop`
- Merge into: `develop`
- Naming: `feature/*` (e.g., `feature/add-streaming-support`)

```bash
# Create a feature branch
git checkout develop
git checkout -b feature/my-feature

# After completing the feature
git checkout develop
git merge --no-ff feature/my-feature
git branch -d feature/my-feature
```

#### Release Branches
- Branch from: `develop`
- Merge into: `main` and `develop`
- Naming: `release/*` (e.g., `release/1.0.0`)

```bash
# Create a release branch
git checkout develop
git checkout -b release/1.0.0

# Finalize the release
git checkout main
git merge --no-ff release/1.0.0
git tag -a v1.0.0 -m "Release version 1.0.0"

git checkout develop
git merge --no-ff release/1.0.0
git branch -d release/1.0.0
```

#### Hotfix Branches
- Branch from: `main`
- Merge into: `main` and `develop`
- Naming: `hotfix/*` (e.g., `hotfix/critical-bug`)

```bash
# Create a hotfix branch
git checkout main
git checkout -b hotfix/critical-bug

# After fixing
git checkout main
git merge --no-ff hotfix/critical-bug
git tag -a v1.0.1 -m "Hotfix version 1.0.1"

git checkout develop
git merge --no-ff hotfix/critical-bug
git branch -d hotfix/critical-bug
```

## Workflow Example

1. **Start a new feature**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-awesome-feature
   ```

2. **Work on the feature**
   ```bash
   # Make changes
   git add .
   git commit -m "feat: implement my awesome feature"
   ```

3. **Complete the feature**
   ```bash
   git checkout develop
   git pull origin develop
   git merge --no-ff feature/my-awesome-feature
   git push origin develop
   git branch -d feature/my-awesome-feature
   ```

4. **Prepare a release**
   ```bash
   git checkout develop
   git checkout -b release/1.1.0
   # Update version numbers, changelog, etc.
   git commit -m "chore: prepare release 1.1.0"
   ```

5. **Finalize the release**
   ```bash
   git checkout main
   git merge --no-ff release/1.1.0
   git tag -a v1.1.0 -m "Release version 1.1.0"
   git push origin main --tags

   git checkout develop
   git merge --no-ff release/1.1.0
   git push origin develop
   git branch -d release/1.1.0
   ```

## Commit Message Convention

Use conventional commits format:

- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `style:` - Code style changes (formatting, etc.)
- `refactor:` - Code refactoring
- `test:` - Adding or updating tests
- `chore:` - Maintenance tasks

## Installing Git Flow (Optional)

If you want to use git-flow commands:

### macOS
```bash
brew install git-flow
```

### Linux (Ubuntu/Debian)
```bash
apt-get install git-flow
```

### Initialize Git Flow
```bash
git flow init
# Accept defaults: main for production, develop for development
```
