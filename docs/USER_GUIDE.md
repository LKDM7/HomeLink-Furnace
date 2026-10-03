# Guide utilisateur

HomeLink Furnace n’accepte aucun combustible. Posez la machine avec sa façade
vers vous, raccordez un réseau HE à son port cuivre et insérez un item possédant
une vraie recette SMELTING. La chauffe démarre avant les premières cuissons.
Le bouton `?` ouvre aussi un manuel français / anglais directement dans le jeu.

| Tier | Entrée items | Sortie items | Entrée HE |
| --- | --- | --- | --- |
| I | Dessus | Arrière | Côté droit extérieur |
| II | Dessus cellule gauche | Arrière cellule droite | Côté droit extérieur |
| III | Dessus arrière-gauche-haut | Arrière arrière-droite-bas | Côté droit avant-droite-bas |

Gauche/droite désignent le repère local du maître : colonne suivant
`FACING.getClockWise()`, couche vers UP, rangée vers `FACING.getOpposite()`.
Tout bloc de la structure ouvre le même écran. Une structure non entièrement
plaçable conserve l'item. Casser un morceau démonte l'ensemble.
Si le maître est dans un chunk déchargé, la casse est enregistrée de façon
persistante et le démontage termine au chargement du maître. Aucun chunk n'est
forcé ; un simple déchargement ne constitue jamais une casse.

Production montre les entrées, sorties, lanes et XP. Shift-click depuis le
joueur insère seulement des items SMELTING ; les sorties vont vers l'inventaire
du joueur. Les slots d'un onglet caché sont également désactivés. L'automatisation
peut uniquement insérer à INPUT et extraire à OUTPUT.

Une coupure HE, OFF ou blocage redstone conserve les jobs à leur progression
exacte. La machine refroidit après inactivité et doit parfois chauffer à nouveau.
Un résultat terminé attend dans sa lane si les sorties sont pleines. Libérer de
la place suffit à reprendre ; aucun résultat n'est jeté au sol pendant ce blocage.

Énergie montre la charge, la consommation et la chauffe. Settings contrôle
IGNORE / REQUIRE_SIGNAL / REQUIRE_NO_SIGNAL, le nom, le power et le HomeNetwork.
Le propriétaire, les opérateurs et les membres autorisés selon HomeCore peuvent
agir. Le four éteint reste accessible au Dashboard et peut être rechargé.

L'XP provient uniquement des opérations achevées. L'extraction automatisée
n'accorde aucune XP. `Récupérer l'XP` exige CONTROL et retire seulement la valeur
accordée par le serveur. À la destruction, l'XP entière restante est libérée une
seule fois ; le reliquat inférieur à un point est perdu.

Si rien ne cuit, vérifiez HE, chauffe, power, redstone, structure, place en sortie
et recette SMELTING actuelle. Les recettes BLASTING / SMOKING ne sont pas ajoutées
automatiquement. Le charbon n'alimente jamais la machine.
