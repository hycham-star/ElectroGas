# ElectroGas — Calculateur Sonelgaz

Application Android native en français, hors ligne, créée à partir de `Calculateur_Facture_Sonelgaz.xlsx` fourni par l’utilisateur. Version **1.1** : carnet de factures photographiées ou importées.

## Installer

**[Télécharger ElectroGas.apk](downloads/ElectroGas.apk?raw=true)** · **[Télécharger le ZIP](downloads/ElectroGas.zip?raw=true)**

Les fichiers installables se trouvent dans `downloads/`, avec leurs sommes SHA-256 dans `downloads/SHA256SUMS`. Après reconstruction, le nouvel APK est placé dans `dist/`.

Transférez `ElectroGas.apk` sur un téléphone Android 6.0 ou plus récent, ouvrez-le et autorisez l’installation depuis cette source si Android le demande. Aucun compte, connexion Internet ou permission d’accès aux fichiers n’est nécessaire. L’APK est signé avec un certificat de développement, pas une identité de publication Google Play.

La version 1.1 utilise la même clé que la version 1.0 : installez-la **par-dessus l’ancienne version** pour conserver vos données. N’effacez pas les données de l’application et ne la désinstallez pas pour effectuer la mise à jour.

## Utilisation

1. **Électricité** : index en kWh, coefficient de lecture, puissance et abonnement.
2. **Gaz** : index en m³, PCS et abonnement.
3. **Frais** : prestations, habitation, soutien, REPE/RGPE, timbre et droit fixe.
4. **Total** : HT, TVA, total TTC, moyenne quotidienne et détail des quatre tranches. Le bouton Partager utilise le menu Android.

Le montant se met à jour pendant la saisie. Les virgules et les points décimaux sont acceptés. Un index décroissant, une durée nulle ou une donnée invalide est refusé. Les saisies restent enregistrées localement. Le bouton de réinitialisation demande confirmation avant de rétablir les valeurs du classeur.

## Carnet de factures

Depuis **Mes factures · Photos et historique** :

1. Ajoutez une facture et renseignez son titre, sa date, sa période, son logement et, si vous le souhaitez, son montant réel et son statut payé.
2. Photographiez une ou plusieurs pages avec l’application photo du téléphone, ou importez des images / PDF depuis le sélecteur Android. Une copie privée est créée : supprimer le document d’origine de la galerie n’efface pas la facture enregistrée.
3. Appuyez sur **Enregistrer la facture**. Un brouillon non enregistré n’est pas une sauvegarde.
4. Recherchez une facture par titre, année, logement, période ou statut ; ouvrez les documents avec zoom, modifiez la fiche ou partagez / enregistrez une copie PDF.

Limites : 10 documents et 30 pages par facture, 20 Mo par import, 50 Mo au total par facture. Les photos sont orientées d’après leurs métadonnées EXIF et recopiées en JPEG avec une résolution maximale de 3 072 pixels sur le grand côté. Les PDF protégés par mot de passe ne sont pas pris en charge. Aucune lecture automatique OCR : les informations sont saisies manuellement. Le marquage « payée » est une indication de l’utilisateur, pas une preuve de paiement.

### Sauvegarde et restauration

**Exporter une sauvegarde ZIP** conserve les documents et leurs fiches, dans l’emplacement choisi via Android : PC, stockage externe ou fournisseur cloud personnel disponible dans le sélecteur. Il n’y a aucun transfert automatique ni serveur ElectroGas. Pour se protéger contre la perte du téléphone, conserver le ZIP ailleurs que sur ce téléphone. Ce ZIP n’est pas chiffré par l’application : choisir un emplacement privé.

**Restaurer une sauvegarde ZIP** vérifie les documents avant d’ajouter les nouvelles factures. Les identifiants déjà présents sont ignorés, sans écraser les modifications locales. Une sauvegarde incomplète, mal formée ou comportant des chemins inattendus est refusée sans remplacer l’historique. Limites : 512 Mo par sauvegarde et 500 factures par restauration. La désinstallation / l’effacement des données Android supprime l’historique local : exporter d’abord une sauvegarde.

### Gratuit et Premium

Cette version permet **5 factures gratuites**. La modification, la consultation, le partage, l’export et la restauration restent accessibles à la limite. Les factures restaurées restent consultables même si elles sont plus de cinq ; seul l’ajout d’une nouvelle facture est alors limité.

**L’achat Premium n’est pas activé.** L’écran présente le futur historique illimité mais ne simule pas de paiement ou de déverrouillage payant. `InvoiceStore.isPremium()` renvoie actuellement `false`. Avant de vendre : configurer le produit Google Play, intégrer et vérifier les achats / restaurations de droits. Aucun compte Play, prix ou identifiant de produit n’a été inventé. Pas de sauvegarde cloud automatique ni d’OCR dans cette version.

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

Tests du carnet sur un appareil / émulateur Android 8.0 ou plus récent avec au moins une place libre dans l’historique de test :

```sh
bash build.sh
ELECTROGAS_ADB=/chemin/vers/adb bash tests/run_android_tests.sh
```

L’instrumentation Android teste le stockage privé, le quota, les modifications à la limite, les PDF, la sauvegarde et la restauration, les archives invalides, le fournisseur de fichiers et les parcours UI avec caméra / sélection de PDF simulées. Les données de test sont isolées ; une facture UI temporaire identifiée est créée puis supprimée. La caméra physique et le sélecteur système réel doivent également être essayés sur téléphone avant publication commerciale.

Ancien test UI du calculateur (dépend du service d’accessibilité de l’émulateur) :

```sh
ELECTROGAS_ADB=/chemin/vers/adb python3 tests/android_smoke.py
```

Ce test installe l’APK et **efface ses données sur l’appareil de test**. Il vérifie les quatre pages, le total du classeur, le recalcul après modification d’index, le refus d’un index décroissant et la conservation des valeurs après redémarrage. La capture initiale est écrite dans `build/default-screen.png`.

### Résultats observés dans l’environnement de création

- Version 1.1 : **42 contrôles du carnet réussis sur Android 9**, comprenant copie privée, quota et modifications à la limite, export / import PDF, restauration additive et idempotente, rejet des archives invalides et des chemins dangereux, lecture seule des exports, réouverture photo sans troncature, caméra et sélecteur PDF simulés dans l’UI, affichage avec zoom et relecture des données enregistrées.
- 106 vérifications de calcul réussies, dont les 48 cellules avec formule du classeur.
- Signature APK et alignement vérifiés ; installation réussie sur un émulateur Android 9.
- Les quatre pages, le total initial et le recalcul ont été exercés. Le passage de l’index électrique 55 320 à 55 331 donne **3 178,34 DA**.
- L’index décroissant affiche un avertissement et masque le montant. Après correction, arrêt forcé et relancement, l’index **55 331** et le total **3 178,34 DA** ont été confirmés visuellement.
- Aucun crash AndroidRuntime observé. La suite UI automatisée n’a pas terminé : le service d’accessibilité de cet émulateur sans accélération matérielle échoue par intermittence avec `null root node`. Les captures et interactions directes ont servi à compléter les vérifications fonctionnelles ; cela ne constitue pas une réussite de la suite automatisée complète.
- Aucun essai sur téléphone physique ni publication Google Play.
