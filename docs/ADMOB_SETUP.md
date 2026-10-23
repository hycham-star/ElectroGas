# Publicités Google : test puis monétisation

## Essayer la version 1.3

Installez l’APK par-dessus votre version existante pour conserver vos factures. Faites défiler le calculateur jusqu’à **Publicités de test · Confidentialité**, puis choisissez **Activer les tests Google**. Le même menu permet de les désactiver. Une connexion Internet est nécessaire aux annonces, pas au calcul ni au carnet. Une erreur réseau ne bloque aucune fonctionnalité.

Une seule bannière adaptative apparaît en bas du calculateur, séparée des commandes. Aucune publicité dans les photos, le carnet ou le lecteur de factures, ni publicité plein écran. Les changements d’onglet réutilisent la bannière. Sa fermeture / désactivation la détruit.

Identifiants officiels de démonstration Google :

- Application : `ca-app-pub-3940256099942544~3347511713`.
- Bannière adaptative : `ca-app-pub-3940256099942544/9214589741`.

Ces annonces ne rapportent aucun revenu. Ne testez jamais en cliquant sur vos propres annonces réelles. Sur émulateur, utilisez de préférence une image Android récente avec Google Play / Google APIs et Internet.

## Consentement : limite de cette démonstration

Les annonces sont désactivées par défaut. L’activation explicite autorise une connexion à Google après information. UMP est intégré : actualisation du statut, présentation du formulaire requis lorsqu’il est disponible et options de confidentialité si requises. Aucune annonce n’est initialisée avant cette activation locale.

Le compte d’exemple Google ne permet pas de publier vos propres messages UMP. En cas d’indisponibilité ou de refus du formulaire, le prototype peut néanmoins demander **uniquement des annonces de démonstration**, après cette activation explicite. L’exception est isolée dans `TestAds.sampleOnly()` et limitée à l’App ID de démonstration. **Ce choix local ne remplace pas le consentement applicable aux annonces réelles.** Les requêtes sont marquées non personnalisées (`npa=1`) ; cela ne dispense pas du consentement applicable. Les permissions d’identifiant publicitaire Android et AdServices sont retirées du manifeste fusionné.

**Ne remplacez pas simplement les IDs pour activer les revenus.** Le code refuse une App ID autre que celle de démonstration. La prochaine intégration doit retirer l’exception de test et n’initialiser le SDK / demander une annonce que si `ConsentInformation.canRequestAds()` le permet après actualisation UMP et fermeture du formulaire requis. Une erreur sans consentement valable doit rester sans annonce. Tester également les préférences acquises, le retrait et les règles d’âge. Ne pas forcer une région de test dans un APK public.

## Créer votre compte AdMob

1. Ouvrez [admob.google.com](https://admob.google.com/). Créez votre compte avec votre compte Google. Saisissez votre vrai pays, vos informations de paiement et les vérifications demandées **uniquement sur les pages sécurisées Google**. N’envoyez aucun mot de passe, document d’identité ou renseignement bancaire dans le dépôt ou la conversation.
2. Ajoutez une application **Android**, nom **ElectroGas**, package `dz.electrogas`. Si elle n’est pas encore en boutique, choisissez l’option correspondante si proposée. Les tests ne nécessitent pas de compte Play Console. Pour les annonces réelles, suivez les exigences et validations affichées par AdMob (boutique prise en charge, vérification de propriété, approbation de l’application).
3. Copiez l’**ID d’application** (contient `~`), puis créez un bloc **Bannière** et copiez son **ID de bloc d’annonces** (contient `/`). Ces deux IDs ne sont pas secrets : vous pourrez les fournir pour préparer l’intégration réelle. Aucune clé privée n’est nécessaire.
4. Dans **Confidentialité et messages / Privacy & messaging**, configurez et publiez les messages applicables à votre audience, notamment EEE / Royaume-Uni / Suisse et États américains concernés. UMP dépend de cette configuration. Renseignez correctement le public et les paramètres d’âge.
5. Publiez une politique accessible publiquement. [PRIVACY.md](../PRIVACY.md) décrit le prototype : complétez l’identité / contact privé de l’éditeur et le fonctionnement réel avant publication commerciale. Ajoutez son URL dans les consoles et l’app. Suivez les instructions AdMob pour le site développeur, `app-ads.txt` et les vérifications de propriété. Aucun domaine n’est configuré par ce dépôt.
6. Intégrez le chemin production décrit ci-dessus, puis vérifiez acceptation, refus, retrait, hors-ligne, rotation, erreurs et restauration des préférences avec **des IDs Google de test ou appareils de test déclarés**. Complétez les déclarations Sécurité des données Google Play selon les SDK livrés, même sans identifiant publicitaire. Passez aux IDs réels après validation et approbations du compte / application.

Premium reste distinct : aucun achat ni retrait payant de publicité n’est activé.

## Références Google

- [Annonces de test](https://developers.google.com/admob/android/test-ads)
- [SDK Android](https://developers.google.com/admob/android/quick-start)
- [UMP et confidentialité](https://developers.google.com/admob/android/privacy)
- [Politique Google](https://policies.google.com/privacy)
