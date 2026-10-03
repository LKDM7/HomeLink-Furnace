# HomeLinkFurnace

## Projet

Mod Minecraft `homelink_furnace`, en Java 21, pour Minecraft 1.21.1 et
NeoForge 21.1.252. Le projet utilise Gradle et le plugin ModDevGradle.
Les versions et les identifiants du mod sont dans `gradle.properties`.

## Commandes Windows

Depuis la racine du projet, dans PowerShell :

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
```

`build` compile et assemble le mod. `test` exécute les tests présents ;
une tâche sans sources de test ne constitue pas une validation fonctionnelle.
`runClient` et `runServer` démarrent Minecraft et restent interactifs.
`runData` génère les ressources du mod. Utiliser le wrapper Gradle fourni.

## Organisation

- `src/main/java/fr/lkdm/homelink/furnace/` : code Java du mod.
- `src/main/resources/` : traductions et ressources Minecraft.
- `src/main/templates/META-INF/neoforge.mods.toml` : métadonnées du mod.
- `src/generated/resources/` : ressources générées par les data generators.
- `build.gradle`, `settings.gradle`, `gradle.properties` : configuration Gradle.

Respecter le package `fr.lkdm.homelink.furnace` et le mod ID `homelink_furnace`. Garder le code
client isolé du code commun pour permettre le fonctionnement sur serveur dédié.
Vérifier les API pour les versions du projet avant de modifier les registres
ou les événements NeoForge.

## Ruflo

Ruflo 3.42.4 est installé globalement. Sous PowerShell, utiliser `ruflo.cmd`
pour éviter les restrictions d'exécution des scripts npm `.ps1`.

```powershell
ruflo.cmd init check
ruflo.cmd doctor -c config
ruflo.cmd memory stats
ruflo.cmd memory search --query "HomeLinkFurnace" --namespace project
```

- `.claude-flow/config.yaml` configure le runtime Ruflo.
- `.swarm/` contient la mémoire locale, exclue de Git.
- `.codex/config.toml` configure le MCP Ruflo pour Codex sur cette machine.
  Ce fichier local est exclu de Git ; `.agents/config.toml` fournit un exemple.
- `.mcp.json` configure le MCP Ruflo pour Claude Code dans ce projet.
- `.claude/settings.json` et `.claude/helpers/` contiennent les hooks Claude Code.
- `.agents/skills/` contient les skills Ruflo disponibles pour Codex.

Les skills disponibles sont `$ruflo`, `$swarm-orchestration`,
`$memory-management`, `$sparc-methodology` et `$security-audit`.
Utiliser la mémoire pour conserver les décisions utiles au projet.
Les outils de coordination enregistrent le travail ; continuer ensuite
l'implémentation et sa vérification. Choisir les outils selon les besoins
de la tâche. Les essaims et le daemon peuvent être démarrés à la demande.

## Règles de travail

Lire les fichiers avant modification et conserver les changements existants.
Vérifier les modifications Java avec le wrapper Gradle. Pour une modification
de configuration Ruflo, vérifier le chargement du MCP et la configuration.
Ne pas versionner les secrets, caches, journaux ou bases de mémoire locales.
Ne pas effectuer de commit, push ou publication sans demande de l'utilisateur.
