# Rapport d’implémentation — HomeLink Furnace

1. **Implémentation.** Trois fours industriels, SMELTING serveur, jobs parallèles
   persistants, chauffe, HE exclusivement, multiblocs à maître unique, ports
   dédiés, XP, receipts HomeCore, GUI officielle, modèles et ventilateurs,
   traductions FR/EN, manuel, viewers optionnels et CI sans publication.

2. **Version Furnace.** 0.1.0 ; mod ID `homelink_furnace` ; package
   `fr.lkdm.homelink.furnace` ; licence Apache-2.0 ; auteur LKDM.

3. **Versions réellement utilisées.** Minecraft 1.21.1, NeoForge 21.1.252,
   Java 21, HomeCore 1.14.0 / DashboardAPI 1.9.0, HomeLink Energy 0.5.0.
   L’intégration optionnelle utilise le vrai HomeLink Storage 1.4.0 voisin.

4. **Fichiers principaux.** `HomeLinkFurnace`, `FurnaceTier`, `FurnaceLayout`,
   `IndustrialFurnaceBlock`, `FurnaceBlockItem`, `IndustrialFurnaceBlockEntity`,
   `FurnaceJob`, `FurnaceEnergyCost`, `FurnaceHeat`, `FurnaceScheduler`,
   `FurnaceDismantleRequests`, `FurnaceRecipeReloads`,
   `FurnaceServerConfig`, `FurnaceRegistries`, `FurnaceDevice`, `FurnaceHomeCore`,
   `FurnaceAccess`, `FurnaceMenu`, `FurnacePayloads`, `FurnaceScreen`,
   `FurnaceRenderer`, `ViewerInfo` et plugins JEI/REI. Ressources, tests et
   documentation accompagnent ces classes.

5. **Device.** Type unique `homelink_furnace:industrial_furnace`. 17 métriques :
   furnace_tier, furnace_status, enabled, temperature, active_jobs, max_jobs,
   queued_items, pending_outputs, energy_stored, energy_capacity,
   current_energy_draw, input_usage, output_usage, processed_total, xp_stored,
   network_connected, redstone_mode. Actions standard `homecore:power` et
   `homecore:rename`. Huit événements de transitions : started, stopped,
   no_power, power_restored, output_full, output_recovered, recipe_invalid,
   recipe_restored, avec le préfixe `homelink_furnace:furnace_`.

6. **Valeurs finales par défaut.**

   | Tier | Taille | Lanes | Vitesse | Entrée / sortie | HE |
   | --- | --- | --- | --- | --- | --- |
   | I | 1×1×1 | 2 | ×1 | 9 / 9 | 2 000 |
   | II | 2×1×1 | 4 | ×1,5 | 18 / 18 | 6 000 |
   | III | 2×2×2 | 8 | ×2 | 27 / 27 | 16 000 |

7. **Énergie.** `E = max(1, floor((base × cookingTicks + 100) / 200))`, base
   par défaut 100. Durée `ceil(cookingTicks / speed)` ; débit cumulé
   `floor(E × progress / duration)`. L’intégralité d’E est payée, indépendamment
   de la vitesse. Reste fractionnaire déterministe persisté ; aucun progrès
   possible sans HE stockée. Aucun FE/RF ni combustible.

8. **Chauffe.** COLD → WARMING → READY / PROCESSING → COOLING → COLD.
   I/II/III : chauffe 100/160/240 ticks, 100/300/800 HE ; maintien chaud
   200/300/400 ticks ; maintien payé 20/40/80 HE/min. OFF, redstone et coupure
   conservent les jobs ; après le maintien court, le refroidissement avance
   seulement pendant les ticks réellement exécutés.

9. **XP.** Somme décimale exacte des XP canoniques des recettes réellement
   terminées ; dix opérations à 0,7 donnent 7. Buffer borné, persisté, retrait
   serveur CONTROL uniquement. L’automatisation n’octroie aucune XP.
   La casse libère les points entiers une fois ; le reliquat fractionnaire est
   perdu à la destruction définitive.

10. **ProductionReceipt.** Émis lorsque le résultat existe, y compris pending
    si sortie pleine. Transaction stable, propriétaire réellement enregistré,
    source Furnace, recette et quantité réelles, ticks, séquence HomeCore,
    réseau et ProductionStart persisté. Aucun auteur fictif si owner inconnu.
    La réémission éventuelle garde la transaction pour déduplication.

11. **Ports physiques.** I : UP entrée, BACK sortie, côté horaire HE.
    II : dessus gauche entrée, arrière droite sortie, côté extérieur droit HE.
    III : arrière-gauche-haut UP entrée ; arrière-droite-bas BACK sortie ;
    avant-droite-bas côté droit HE. Toutes les conversions utilisent le repère
    local centralisé ; cliquer une partie ouvre le maître.

12. **Tests unitaires.** Le build final contient 26 tests passés,
    sans échec ni skip : core, tiers/layout, énergie, chauffe, jobs/NBT,
    ressources/traductions et microbenchmark pur 50/100 machines. Le bilan
    final de commande est consigné dans [VALIDATION](VALIDATION.md).

13. **GameTests.** 71 tests requis passés en 2,429 secondes sur serveur dédié
    avec le vrai Storage : les 49 scénarios demandés, intégrations Energy/Pipes,
    recettes runtime, XP exacte, ports mis en cache après retrait, NBT XP
    malformé et événements de récupération lors de OFF/ON. Les régressions
    finales couvrent le démontage différé à travers un chunk déchargé, les
    requêtes persistées / périmées, l’intention créative transitoire, la dernière
    étape thermique, l’événement de reload juste avant la fin d’un job et les
    trois modes redstone sans charger le chunk adjacent.

14. **Client smoke.** Le smoke client JEI final a terminé 77 étapes : menus FR/EN,
    onglets via clic réel, slots cachés désactivés, petit écran, focus clavier,
    inventaire et conservation lors d’un clic. Le test vérifie aussi l’absence
    de chevauchement entre slots actifs et widgets. Le smoke REI a passé 77
    étapes, avec le manuel FR/EN et les ports supérieurs capturés. Les deux
    runs JEI et REI ont terminé sans échec pour l’implémentation initiale.
    Après la révision visuelle, le smoke JEI a été relancé : 77 étapes réussies
    et 19 captures actualisées. REI et les GameTests n’ont pas été relancés
    pour cette révision des modèles, textures et renderer client.

15. **Captures.** 19 captures réelles dans
    `build/validation/client/screenshots` : faces et ports I/II/III,
    III chaud/ventilateurs, Production/Energy/Settings FR, petits écrans FR,
    Production/Settings EN avec autre GUI scale, ports supérieurs des trois
    tiers et manuel FR/EN. Les copies partageables sont dans
    [docs/screenshots](screenshots/README.md).

16. **JEI/REI.** JEI 19.57.0.449 : vrai runtime vérifié, trois catalysts
    SMELTING et trois informations. REI 16.0.799 : vrai runtime vérifié, trois
    workstations SMELTING et trois informations, avec attente effective de la
    fin du rechargement asynchrone des plugins.

17. **Storage Pipes.** Transport réel testé : barrel → Storage Pipe → INPUT
    Furnace → SMELTING → OUTPUT → Storage Pipe → barrel. Aucune dépendance
    Java Furnace vers Storage en production ; seul le source set de vérification
    optionnel charge Storage.

18. **Limites.** Les tests de reload manipulent les vrais NBT / RecipeManager et
    continuent la production, mais ne remplacent pas une campagne de longue
    durée sur chaque modpack ou une matrice de redémarrages brutaux. Les clics
    automatisés et captures ne valident pas exhaustivement la narration et
    chaque interaction externe. Le benchmark pur ne mesure pas les TPS.
    La CI distante est écrite et relue, mais n’a pas été exécutée sur GitHub.
    Aucun objectif SMELTING artificiel n’a été ajouté à Tasks.

19. **Repos voisins.** Aucun changement requis dans HomeCore, Dashboard,
    Storage, Tasks, Energy ou Quarry. Les contrats publics réellement présents
    suffisent. La CI n’exécute aucune publication ; aucun commit/push demandé.

20. **État vérifiable.** Les preuves, commandes, résultats finaux et limites
    figurent dans [VALIDATION](VALIDATION.md). Les validations non exécutées
    mentionnées dans les limites restent explicitement signalées.

Révision visuelle de la 0.1.0 : cabinets distincts I/II/III, chambre continue
sur les multiblocs, portes et prises en relief, verre creusé, seize textures
64 × 64, résistances lumineuses propres à chaque tier et ventilateurs alignés.
Les modèles d’items représentent la machine entière. Le détail de génération
et les limites de rendu figurent dans [DESIGN](DESIGN.md).
