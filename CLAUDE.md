# HomeLinkFurnace - Claude Code

Lire `AGENTS.md` pour le contexte Java 21 / NeoForge, les commandes Gradle
et les règles de travail du projet.

Ruflo est initialisé dans `.claude-flow/`. Le serveur MCP `ruflo` est déclaré
dans `.mcp.json`. Les hooks de `.claude/settings.json` utilisent les helpers
Node de `.claude/helpers/`, compatibles avec Windows.

Utiliser `ruflo.cmd` dans PowerShell, ou `ruflo` dans un shell qui le prend
en charge. Les skills, agents et commandes Claude Code sont disponibles dans
`.claude/skills/`, `.claude/agents/` et `.claude/commands/`.

Consulter la mémoire pour retrouver le contexte pertinent et enregistrer
les décisions durables. Après un appel de coordination Ruflo, poursuivre
le travail et sa validation. Démarrer les essaims ou les workers uniquement
quand la tâche le justifie.
