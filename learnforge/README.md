# LearnForge: AI Experiential Learning Platform (starter)

Spring Boot 3 (Java 21) backend + React (JavaScript) frontend + PostgreSQL.

## What works in this starter
- Organisation sign-up, login (JWT), 8 roles, user creation by admins
- Courses (basic), persona builder
- Text and browser-voice role-play simulations, 5 difficulty levels
- AI evaluation with scores, strengths, gaps and recommended learning
- Certificates with unique ID, public verification page and QR code, revocation
- Docker Compose deployment

## Not built yet (see roadmap)
Content upload and AI structuring, RAG tutor, quizzes/tests/mock tests, adaptive testing,
gamification, learner coach, analytics dashboards, AI Studio generators, real-time streaming voice.

## Run with Docker
    cp .env.example .env     # fill in DB_PASSWORD, JWT_SECRET, GEMINI_API_KEY
    docker compose up --build
    # open http://localhost:8081

## Run for development
    # database
    docker run -d --name lf-db -e POSTGRES_PASSWORD=learnforge -e POSTGRES_USER=learnforge -e POSTGRES_DB=learnforge -p 5432:5432 pgvector/pgvector:pg16
    # backend
    cd backend
    export JWT_SECRET=<32+ chars> GEMINI_API_KEY=<your key>
    mvn spring-boot:run
    # frontend
    cd frontend && npm install && npm run dev      # http://localhost:5173

API docs: http://localhost:8080/swagger-ui.html

## First run
1. Open the app, choose "Set up a new organisation" (you become ORG_ADMIN).
2. Create a persona (for example Dr. Raj Mehta, sceptical cardiologist) and start a simulation.
3. Create learner users via POST /api/users, issue a certificate via POST /api/certificates.

## Before going live
- Verify the Gemini model name in GEMINI_MODEL against Google AI Studio.
- Use a billing-enabled key, restricted to the Generative Language API, one key per environment.
- Put TLS in front (reverse proxy or load balancer) and set APP_PUBLIC_URL to the https address.
- Add rate limiting, audit logging, backups, and monitoring. Review data-protection needs for client data.
