Unzip into your project from the folder that contains `learnforge`:

    unzip -o ~/Downloads/learnforge-v5-update.zip -d learnforge
    git add learnforge
    git commit -m "Complete platform: paths, notifications, final exam, mock tests, scenarios, media import, voice, marketplace, plans"
    git push

Render rebuilds the backend (Flyway applies V5). Vercel rebuilds the frontend.
