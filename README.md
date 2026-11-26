# Terminfinder – lokales MVP

Vaadin-Portal zur Vermittlung kurzfristig frei gewordener Arzttermine. Das MVP bildet den kompletten Weg von der einmaligen Patientensuche über die Auswahl durch eine Praxis bis zur verbindlichen Buchung und Portalgebühr ab.

> **Nur Demo:** Alle Personen, Praxen und Termine sind synthetisch. Zahlungen werden simuliert und der klar gekennzeichnete lokale Demo-Kalender hat keine Verbindung zu einer Praxissoftware.

Die lokale Radiusberechnung verwendet für den Pilotbetrieb ausschließlich synthetische Berliner Koordinaten (PLZ 10115, 10117, 10119 und 10999). Eine spätere Geocoding-Anbindung ist hinter dem Ortsdienst austauschbar.

## Voraussetzungen und Start

- Java 21
- Docker mit Docker Compose

```bash
docker compose up -d
./mvnw spring-boot:run
```

Danach ist die Anwendung unter [http://localhost:8080](http://localhost:8080) erreichbar.

## Demo-Zugänge

Das Passwort lautet für alle Konten `demo`.

| Rolle | Benutzername | Demo-Fall |
| --- | --- | --- |
| Patientin | `patient.anna` | automatische Suche, 5 € |
| Patient | `patient.ben` | persönliche Suche, 25 € |
| Patientin | `patient.clara` | Selbstzahlersuche |
| Praxis | `praxis.haut` | Hautarztpraxis |
| Portalteam | `portal.mia` | Arbeitsliste persönlicher Suchen |

Beim ersten Start werden ein Hautkrebs-Screening **morgen 10:30** sowie passende Suchanfragen angelegt. Anna ist die älteste passende Anfrage und steht deshalb standardmäßig oben. Der Tarif ist für die Praxis nicht sichtbar und beeinflusst die Reihenfolge nicht.

## Ablauf selbst testen

### Angebot und Buchung

1. Als `praxis.haut` anmelden und die Praxisansicht öffnen.
2. Den Termin morgen 10:30 auswählen. Anna und Ben erscheinen als passende Kassenanfragen; Claras Selbstzahleranfrage erscheint nicht.
3. Einer Person den Termin anbieten und danach abmelden.
4. Als ausgewählter Patient anmelden. Das Angebot zeigt Praxis, Zeitpunkt, Portalgebühr und Behandlungskosten getrennt.
5. Angebot annehmen. Der Termin ist danach gebucht und es existiert genau eine simulierte Gebühr von 5 € beziehungsweise 25 €.

### Ablehnung und Neuvergabe

1. Als Praxis den Termin zunächst Anna anbieten.
2. Als `patient.anna` das Angebot ablehnen.
3. Wieder als Praxis anmelden und denselben freigegebenen Termin Ben anbieten.
4. Als `patient.ben` annehmen. Erst diese Annahme erzeugt Buchung und Gebühr.

Die beiden Varianten verwenden denselben Demo-Termin und sind daher am einfachsten jeweils mit einer frischen lokalen Demo-Datenbank durchspielbar.

### Arbeitsliste des Portalteams

1. Als `portal.mia` anmelden und die Arbeitsliste öffnen.
2. Bens persönliche Suche auswählen.
3. Eine kontaktierte Praxis, Kontaktweg, Rückmeldung und Notiz erfassen.

## Tests

```bash
./mvnw test
```

Die Integrationstests verwenden PostgreSQL per Testcontainers und benötigen deshalb ebenfalls Docker. Sie prüfen unter anderem Ablehnung und Neuvergabe, den Ausschluss reiner Selbstzahlertermine bei einer Kassensuche und parallele Annahmeversuche.

## Architektur in Kürze

- Java 21, Spring Boot, Vaadin und Spring Security
- PostgreSQL mit versioniertem Schema durch Flyway; Hibernate validiert das Schema
- rollen- und eigentümerbezogene Serviceabfragen für Patient, Praxis und Portalteam
- Kalender- und Zahlungsanbieter hinter kleinen Interfaces; lokal laufen ausschließlich Demo-Adapter
- atomare Buchungstransaktion mit pessimistischen Datenbanksperren, optimistischen Versionsfeldern und Unique Constraints

Damit bleiben Annahmen bei Doppelklicks idempotent und parallele Versuche können weder denselben Termin doppelt buchen noch eine zweite Gebühr erzeugen.
