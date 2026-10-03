# Captures du client Minecraft

Ces 19 captures proviennent du smoke client réellement exécuté le 3 octobre
2026 avec Minecraft 1.21.1 / NeoForge 21.1.252. Elles ne sont pas des maquettes.
Elles montrent la révision des modèles et textures 64 × 64, après correction
des coins des cadres et vérification dans le client avec JEI.
Le test ouvre les menus, clique les onglets et déplace huit inputs dans une
fenêtre de 640 × 480 ; les assertions serveur contrôlent leur conservation.

| Vue | Capture |
| --- | --- |
| I, façade froide et port HE | [Façade I](furnace-tier-1-cold-ports.png) |
| I, sortie arrière | [Arrière I](furnace-tier-1-rear-ports.png) |
| I, entrée supérieure | [Dessus I](furnace-tier-1-top-input.png) |
| II, façade froide et port HE | [Façade II](furnace-tier-2-cold-ports.png) |
| II, sortie arrière | [Arrière II](furnace-tier-2-rear-ports.png) |
| II, entrée supérieure | [Dessus II](furnace-tier-2-top-input.png) |
| III, façade froide et port HE | [Façade III](furnace-tier-3-cold-ports.png) |
| III, sortie arrière | [Arrière III](furnace-tier-3-rear-ports.png) |
| III, entrée supérieure dédiée | [Dessus III](furnace-tier-3-top-input.png) |
| III, chambre chaude et ventilateur actif | [Production III](furnace-tier-3-hot-fans.png) |
| Production, français | [Production FR](furnace-gui-production-fr.png) |
| Énergie, français | [Énergie FR](furnace-gui-energy-fr.png) |
| Réglages, français | [Réglages FR](furnace-gui-settings-fr.png) |
| Manuel, français | [Manuel FR](furnace-manual-fr.png) |
| Production, petite fenêtre | [Petite fenêtre FR](furnace-gui-small-fr.png) |
| Énergie, petite fenêtre | [Petite fenêtre énergie FR](furnace-gui-small-energy-fr.png) |
| Production, anglais, autre GUI scale | [Production EN](furnace-gui-production-en-scale3.png) |
| Réglages, anglais, autre GUI scale | [Réglages EN](furnace-gui-settings-en-scale3.png) |
| Manuel, anglais, autre GUI scale | [Manuel EN](furnace-manual-en-scale3.png) |

Une capture fixe montre le ventilateur mais ne prouve pas, seule, sa rotation.
Le smoke contrôle l'état visuel chaud reçu côté client ; la rotation est calculée
localement, sans mises à jour de bloc à chaque frame. La couverture et les limites
des autres contrôles sont consignées dans [VALIDATION](../VALIDATION.md).
