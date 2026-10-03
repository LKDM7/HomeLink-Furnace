# Skills Ruflo pour HomeLinkFurnace

Les skills dans `skills/` sont accessibles à Codex : `$ruflo`,
`$swarm-orchestration`, `$memory-management`, `$sparc-methodology`
et `$security-audit`.

Les instructions du projet sont dans `AGENTS.md`, à la racine.
`config.toml` est un exemple de configuration MCP pour Windows.
Codex utilise `.codex/config.toml` pour la configuration du projet ;
ce fichier local est exclu de Git. Le runtime Ruflo utilise
`.claude-flow/config.yaml` et conserve sa mémoire dans `.swarm/`.

Prérequis : Node.js 20 ou supérieur et Ruflo installé avec
`npm.cmd install -g ruflo@3.42.4`. Le mod utilise Java 21 et Gradle,
indépendamment des outils Node de Ruflo.

Documentation : https://github.com/ruvnet/ruflo
