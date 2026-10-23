# ElectroGas — Calculateur Sonelgaz

Application Android native en français, hors ligne, créée à partir de `Calculateur_Facture_Sonelgaz.xlsx` fourni par l’utilisateur.

## Installer

**[Télécharger ElectroGas.apk](downloads/ElectroGas.apk?raw=true)** · **[Télécharger le ZIP](downloads/ElectroGas.zip?raw=true)**

Les fichiers installables se trouvent dans `downloads/`, avec leurs sommes SHA-256 dans `downloads/SHA256SUMS`. Après reconstruction, le nouvel APK est placé dans `dist/`.

Transférez `ElectroGas.apk` sur un téléphone Android 6.0 ou plus récent, ouvrez-le et autorisez l’installation depuis cette source si Android le demande. Aucun compte, connexion Internet ou permission d’accès aux fichiers n’est nécessaire. L’APK est signé avec un certificat de développement, pas une identité de publication Google Play.

## Utilisation

1. **Électricité** : index en kWh, coefficient de lecture, puissance et abonnement.
2. **Gaz** : index en m³, PCS et abonnement.
3. **Frais** : prestations, habitation, soutien, REPE/RGPE, timbre et droit fixe.
4. **Total** : HT, TVA, total TTC, moyenne quotidienne et détail des quatre tranches. Le bouton Partager utilise le menu Android.

Le montant se met à jour pendant la saisie. Les virgules et les points décimaux sont acceptés. Un index décroissant, une durée nulle ou une donnée invalide est refusé. Les saisies restent enregistrées localement. Le bouton de réinitialisation demande confirmation avant de rétablir les valeurs du classeur.

## Fidélité au classeur

- Valeurs initiales des cellules : **114 kWh**, **655 m³**, **6 340,40 Th**, **3 157,010877 DA** au total (**3 157,01 DA** affichés). Les notes écrites du classeur évoquent d’autres exemples ; elles ne remplacent pas ses cellules actuelles.
- Même allocation progressive et mêmes bases de TVA. Aucun arrondi intermédiaire : seul l’affichage est arrondi à deux décimales.
- Seuils trimestriels fixes : modifier les mois ne modifie que l’abonnement, comme dans Excel.
- Droit fixe : 0 DA jusqu’à 70 kWh inclus, 25 DA au-delà de 70, 50 DA au-delà de 190, 200 DA au-delà de 390. Les trois montants sont modifiables.
- Le soutien État est ajouté algébriquement : saisir une valeur négative pour une réduction. Aucune éligibilité ni réduction n’est calculée automatiquement.
- Les frais de prestation sont inclus dans le HT sans TVA supplémentaire, conformément à la formule du classeur.
- Un seul compteur sur toute la période ; pas de prorata pour un remplacement de compteur.
- Barèmes repris du fichier sans vérification réglementaire en temps réel. Application indépendante, non affiliée à Sonelgaz. L’estimation ne remplace pas une facture officielle.

## Reconstruire

Prérequis : Linux x86_64, Java 21, Python 3, curl et keytool. Aucun Gradle, compte Android ou bibliothèque applicative externe requis.

```sh
cd /workspace/ElectroGas
python3 tools/bootstrap.py
bash build.sh
```

`ELECTROGAS_TOOLS` permet de choisir un répertoire d’outils différent de `/workspace/android-tools`. Le téléchargement officiel vérifie les sommes SHA-1 publiées par Google et Maven Central et conserve la vérification TLS. Le script utilise la plateforme Android 35, les build tools 35.0.0 et le compilateur ECJ 3.39.0. Les dépendances sont hors du dépôt.

Le build lance les tests, compile Java, crée le DEX, assemble et aligne l’APK, le signe et vérifie les signatures. L’APK et sa somme SHA-256 sont placés dans `dist/`.

La clé de développement est créée dans le répertoire d’outils, hors du dépôt, et réutilisée lors des builds suivants. Sa phrase de passe conventionnelle `android` sert uniquement au développement. Conservez la même clé pour installer les futures mises à jour sur une installation existante. Pour distribuer publiquement, utilisez une clé de publication privée et adaptez la commande de signature ; ne publiez pas la clé de développement.

## Validation

`tests/workbook-results.tsv` contient les valeurs mises en cache des **48 cellules avec formule** du classeur fourni. Les tests comparent les résultats Java à toutes ces valeurs, puis vérifient les seuils de tranches, la taxe aux bornes exactes, les consommations nulles, les abonnements, les frais, les réductions et les entrées invalides : **106 vérifications**.

```sh
java -jar /workspace/android-tools/ecj.jar -17 -encoding UTF-8 -proc:none \
  -d build/test-classes app/src/main/java/dz/electrogas/Calculator.java tests/CalculatorTest.java
java -cp build/test-classes dz.electrogas.CalculatorTest
```

La vérification cryptographique et l’alignement sont également contrôlés par `build.sh`. Les essais sur émulateur ou appareil doivent être distingués de ces tests de calcul.

Avec un appareil de test connecté (ou un émulateur déjà démarré) :

```sh
ELECTROGAS_ADB=/chemin/vers/adb python3 tests/android_smoke.py
```

Ce test installe l’APK et **efface ses données sur l’appareil de test**. Il vérifie les quatre pages, le total du classeur, le recalcul après modification d’index, le refus d’un index décroissant et la conservation des valeurs après redémarrage. La capture initiale est écrite dans `build/default-screen.png`.

### Résultats observés dans l’environnement de création

- 106 vérifications de calcul réussies, dont les 48 cellules avec formule du classeur.
- Signature APK et alignement vérifiés ; installation réussie sur un émulateur Android 9.
- Les quatre pages, le total initial et le recalcul ont été exercés. Le passage de l’index électrique 55 320 à 55 331 donne **3 178,34 DA**.
- L’index décroissant affiche un avertissement et masque le montant. Après correction, arrêt forcé et relancement, l’index **55 331** et le total **3 178,34 DA** ont été confirmés visuellement.
- Aucun crash AndroidRuntime observé. La suite UI automatisée n’a pas terminé : le service d’accessibilité de cet émulateur sans accélération matérielle échoue par intermittence avec `null root node`. Les captures et interactions directes ont servi à compléter les vérifications fonctionnelles ; cela ne constitue pas une réussite de la suite automatisée complète.
- Aucun essai sur téléphone physique ni publication Google Play.
