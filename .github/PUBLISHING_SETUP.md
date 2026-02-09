# Automated Publishing Setup

This repository automatically publishes to CurseForge and Modrinth when you push a version tag.

## Initial Setup (One-Time)

### 1. Get CurseForge API Token

1. Go to https://www.curseforge.com/settings/api-tokens
2. Click "Generate Token"
3. Name it "GitHub Actions - CobblemonSpawningREI"
4. Copy the token (you won't see it again!)

### 2. Get CurseForge Project ID

1. Create your project on CurseForge
2. Go to project settings
3. Copy the project ID (numeric)

### 3. Get Modrinth API Token (Optional)

1. Go to https://modrinth.com/settings/account
2. Scroll to "API tokens"
3. Create a token named "GitHub Actions"
4. Copy immediately

### 4. Get Modrinth Project ID (Optional)

Create your project on Modrinth, the project ID is the slug in the URL

### 5. Add Secrets to GitHub

Go to: https://github.com/Akkiruk/cobblemon-spawning-rei/settings/secrets/actions

Add these secrets:
- `CURSEFORGE_TOKEN` - Your CurseForge API token
- `CURSEFORGE_PROJECT_ID` - Your CurseForge project ID
- `MODRINTH_TOKEN` - Your Modrinth API token (optional)
- `MODRINTH_PROJECT_ID` - Your Modrinth project slug (optional)

## Publishing a New Version

1. **Update version in `gradle.properties`**:
   ```properties
   mod_version=1.4.3
   ```

2. **Update `CHANGELOG.md`**:
   ```markdown
   ## [1.4.3] - 2026-02-09
   
   ### Added
   - New spawn display features
   
   ### Fixed
   - Bug fixes
   ```

3. **Commit and tag**:
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "[1.4.3] Description"
   git tag -a v1.4.3 -m "Release version 1.4.3"
   git push origin main --tags
   ```

GitHub Actions will automatically build and publish to CurseForge and Modrinth.
