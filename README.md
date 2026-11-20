# Cuervo Clavier

Clavier Android (français, AZERTY) avec IA intégrée et émojis personnalisés.

## Compiler l'APK (depuis le téléphone)
1. Dépose ce projet dans un dépôt GitHub (branche `main`).
2. L'onglet **Actions** compile automatiquement. Au bout de 4 à 6 minutes, l'APK apparaît dans **Releases**.
3. Télécharge `app-debug.apk` et installe-le (autorise « sources inconnues » si Android le demande).

## Activer l'IA (Supabase)
1. Déploie `supabase/functions/ai-keyboard` (sans vérification JWT, déjà réglé dans `supabase/config.toml`).
2. Ajoute deux secrets : `GEMINI_API_KEY` (clé gratuite sur https://aistudio.google.com/apikey) et `CUERVO_TOKEN` (un mot de passe long de ton choix).
3. Dans l'application : colle l'adresse de la fonction (`https://<projet>.supabase.co/functions/v1/ai-keyboard`) et le mot de passe `CUERVO_TOKEN`, puis « Tester l'IA ».

## Installer le clavier
Ouvre l'application → étape 1 (activer) → étape 2 (choisir Cuervo Clavier).
