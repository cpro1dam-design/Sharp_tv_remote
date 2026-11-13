# Télécommande TV

Appli Android qui remplace la télécommande d'une télé **Android TV / Google TV**
(par exemple une Sharp Android TV). Elle passe par le Wi-Fi, avec le même
protocole que l'appli officielle Google TV (« Android TV Remote v2 »).

## Installer l'appli sur le téléphone

1. Récupère le fichier `app-release.apk` :
   - soit celui envoyé dans la conversation ;
   - soit sur GitHub : onglet **Actions** → dernière compilation « Build APK » →
     section *Artifacts* → `telecommande-tv-apk` (c'est un zip qui contient l'APK).
2. Ouvre l'APK sur le téléphone. Android demande d'autoriser l'installation
   d'applis depuis cette source (Chrome, Fichiers…) : accepte.
3. Si Google Play Protect affiche un avertissement (« appli inconnue »), choisis
   **Installer quand même** : l'appli n'est simplement pas publiée sur le Play Store.

## Première utilisation

1. Allume la télé ; le téléphone doit être sur **le même Wi-Fi**.
2. Ouvre l'appli : la télé apparaît dans la liste (sinon, entre son adresse IP,
   visible sur la télé dans *Paramètres → Réseau et Internet → ton Wi-Fi*).
3. Un **code à 6 caractères** s'affiche sur la télé : recopie-le dans l'appli.
4. C'est fini : les fois suivantes, l'appli se reconnecte toute seule.

## Ce que fait la télécommande

Tout tient sur un seul écran, sans défiler ; la taille des boutons s'adapte au téléphone.

- En haut : marche/arrêt, nom de la télé (appuyer dessus pour changer de télé), source
- Flèches + OK (maintenir pour répéter)
- Recherche, retour, accueil (au centre), menu, paramètres
- Volume au centre (avec niveau affiché), raccourcis SmartTube, Netflix, VLC, Disney+ de chaque côté
- **Boutons de volume du téléphone** : ils règlent le son de la télé quand l'appli est ouverte
- Lecture/pause, avance/retour rapide, précédent/suivant
- En bas : sourdine, pavé numérique, clavier
- Pavé numérique (avec guide des programmes) et clavier : s'ouvrent en volet par le bas

### Si un raccourci d'appli n'ouvre pas la bonne appli

Ouvre l'appli voulue sur la télé avec la télécommande, puis **reste appuyé** sur le
bouton du raccourci dans l'appli : il ouvrira désormais cette appli-là.

## Limites connues

- **Allumer la télé depuis le téléphone** ne marche que si la télé garde le Wi-Fi
  en veille. Sur la télé : *Paramètres → Système → Alimentation* (ou *Réseau*) →
  activer « Allumer via le Wi-Fi / réseau » ou « Veille réseau » si l'option existe.
- Le clavier envoie les lettres en minuscules.
- Un raccourci d'appli ne fait rien si l'appli n'est pas installée sur la télé.
- Quand le volet du clavier est ouvert, les boutons de volume du téléphone règlent le téléphone.

## Structure du code

- `core/` : le protocole (Kotlin pur, testé sur ordinateur)
  - `PairingClient` : appairage par code (port 6467)
  - `RemoteClient` : envoi des touches, ping, volume (port 6466)
  - `ClientIdentity` : certificat du téléphone, mémorisé par la télé
- `app/` : l'appli Android (Jetpack Compose)

Compiler : `./gradlew :core:test :app:assembleRelease` (Android SDK nécessaire).
L'APK de sortie est signé avec la clé de debug, pour une installation directe.
