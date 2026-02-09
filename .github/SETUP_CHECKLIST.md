# 📋 Complete Setup Checklist for Automated Publishing

Follow these steps in order to enable automated CurseForge and Modrinth publishing.

---

## ✅ PHASE 1: CurseForge Project Setup (10 minutes)

- [ ] **Create CurseForge Project**
  - Go to https://www.curseforge.com/studio
  - Click **Create Project**
  - Project Name: `Cobblemon Spawning REI`
  - Game: Minecraft Java Edition
  - Project Type: Mod
  - Categories: Utility, Integration, Cobblemon
  - Client/Server: Client-side only

- [ ] **Get CurseForge API Token**
  - Go to https://www.curseforge.com/settings/api-tokens
  - Click **Generate Token**
  - Name: `GitHub Actions - CobblemonSpawningREI`
  - ⚠️ **COPY THE TOKEN IMMEDIATELY** (can't see it again!)
  - Save it somewhere safe temporarily

- [ ] **Get CurseForge Project ID**
  - Go to your project's "Manage Project" → "Settings"
  - Find the **Project ID** (numeric, like `123456`)
  - Save it somewhere safe temporarily

---

## ✅ PHASE 2: Modrinth Project Setup (Optional - 10 minutes)

- [ ] **Create Modrinth Project**
  - Go to https://modrinth.com/dashboard/projects
  - Click **Create a project**
  - Name: `Cobblemon Spawning REI`
  - Summary: `REI integration for Cobblemon spawn conditions and evolution chains`
  - Categories: Utility, Integration
  - Client/Server: Client-side
  - License: MIT

- [ ] **Get Modrinth API Token**
  - Go to https://modrinth.com/settings/account
  - Scroll to **API tokens**
  - Click **Create a token**
  - Name: `GitHub Actions`
  - ⚠️ **COPY IMMEDIATELY**
  - Save it somewhere safe temporarily

- [ ] **Get Modrinth Project ID**
  - It's the slug in your project URL
  - Example: `https://modrinth.com/mod/cobblemon-spawning-rei` → ID is `cobblemon-spawning-rei`
  - Save it somewhere safe temporarily

---

## ✅ PHASE 3: GitHub Repository Secrets (5 minutes)

- [ ] **Go to GitHub Secrets Page**
  - URL: https://github.com/Akkiruk/cobblemon-spawning-rei/settings/secrets/actions

- [ ] **Add Secret: CURSEFORGE_TOKEN**
  - Click **New repository secret**
  - Name: `CURSEFORGE_TOKEN`
  - Value: (paste your CurseForge API token from Phase 1)
  - Click **Add secret**

- [ ] **Add Secret: CURSEFORGE_PROJECT_ID**
  - Click **New repository secret**
  - Name: `CURSEFORGE_PROJECT_ID`
  - Value: (paste your CurseForge project ID from Phase 1)
  - Click **Add secret**

- [ ] **Add Secret: MODRINTH_TOKEN** (if using Modrinth)
  - Click **New repository secret**
  - Name: `MODRINTH_TOKEN`
  - Value: (paste your Modrinth API token from Phase 2)
  - Click **Add secret**

- [ ] **Add Secret: MODRINTH_PROJECT_ID** (if using Modrinth)
  - Click **New repository secret**
  - Name: `MODRINTH_PROJECT_ID`
  - Value: (paste your Modrinth project slug from Phase 2)
  - Click **Add secret**

---

## ✅ PHASE 4: Push GitHub Workflow Files (2 minutes)

The workflow files have already been created in your local repository. Now commit and push them:

```bash
cd "C:\Users\rboon\curseforge\minecraft\Instances\COBBLEVERSE - Pokemon Adventure [Cobblemon]\CobblemonSpawningREI"

git add .github/
git add CHANGELOG.md
git commit -m "Add automated CurseForge and Modrinth publishing workflow"
git push origin main
```

- [ ] **Files committed and pushed to GitHub**

---

## ✅ PHASE 5: Test the Workflow (Optional - 5 minutes)

To verify everything works, publish version `1.4.2` (or bump to `1.4.3`):

```bash
# Optional: bump version to 1.4.3
# Edit gradle.properties: mod_version=1.4.3
# Edit CHANGELOG.md: add entry for [1.4.3]

git add gradle.properties CHANGELOG.md
git commit -m "[1.4.2] Initial automated publishing setup"
git tag -a v1.4.2 -m "Release version 1.4.2"
git push origin main --tags
```

- [ ] **Version tag created and pushed**
- [ ] **Check GitHub Actions**: https://github.com/Akkiruk/cobblemon-spawning-rei/actions
- [ ] **Verify build succeeded**
- [ ] **Check CurseForge for new release**
- [ ] **Check Modrinth for new release** (if configured)

---

## 🎉 Setup Complete!

From now on, to publish a new version:

1. Update `mod_version` in `gradle.properties`
2. Add entry to `CHANGELOG.md`
3. Commit changes
4. Create and push git tag: `git tag -a vX.Y.Z -m "Release version X.Y.Z" && git push --tags`
5. Wait for GitHub Actions to publish automatically

---

## 📚 Reference Documents

- [PUBLISHING_SETUP.md](.github/PUBLISHING_SETUP.md) - Full publishing guide
- [SECRETS_SETUP.md](.github/SECRETS_SETUP.md) - Quick secrets setup reference
- [workflows/publish.yml](.github/workflows/publish.yml) - The automation workflow

---

## ⚠️ Troubleshooting

**Build fails in GitHub Actions:**
- Check the Actions logs for compilation errors
- Verify `gradle.properties` and `build.gradle.kts` are correct

**Publishing fails:**
- Verify all secrets are set correctly in GitHub
- Check that project IDs match your actual projects
- Ensure API tokens haven't expired

**Changelog not extracted:**
- Ensure format: `## [X.Y.Z] - YYYY-MM-DD`
- Version in `gradle.properties` must match exactly

**Need help?**
- Check GitHub Actions logs: https://github.com/Akkiruk/cobblemon-spawning-rei/actions
- Review existing CatchRate Display workflow for comparison
