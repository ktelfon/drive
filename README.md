# Racing Game API

This is a Spring Boot application for managing a racing game. It allows users to create races, join them, earn points by "driving", and use abilities to hinder opponents.

## Endpoints

### Race Management

*   **Create Race**
    *   `POST /races`
    *   Body: `{"durationInSeconds": 60}`
    *   Description: Creates a new race with the specified duration. Returns the `raceId`.

*   **Join Race**
    *   `POST /races/{raceId}/join`
    *   Header: `X-User-ID: <uuid>`
    *   Description: Joins a user to the specified race. A user can only be in one active race at a time.

*   **Start Race**
    *   `POST /races/{raceId}/start`
    *   Description: Starts the race. The race will automatically finish after the duration expires.

*   **Get Race Details**
    *   `GET /races/{raceId}`
    *   Description: Returns the current status of the race and the leaderboard (sorted by score).

### Gameplay

*   **Drive**
    *   `POST /races/{raceId}/drive`
    *   Header: `X-User-ID: <uuid>`
    *   Description: Earns points for the user. Points are fetched from an external engine service. If the user is frozen (by Oil Slick), they earn 0 points.

### Abilities

*   **Oil Slick**
    *   `POST /races/{raceId}/abilities/oil-slick`
    *   Header: `X-User-ID: <uuid>`
    *   Description: Costs **10 points**. Freezes all other opponents for 1% of the race duration. Multiple uses stack the freeze duration.

*   **Engine Hack**
    *   `POST /races/{raceId}/abilities/engine-hack`
    *   Header: `X-User-ID: <uuid>`
    *   Description: Costs **20 points**. Applies a random score penalty (0-10 points) to all opponents.

## Testing

You can use the provided shell script `new.sh` (or `test_race.sh`) to run a full manual test scenario.

### Prerequisites
*   The application must be running on `http://localhost:8080`.
*   `curl` must be installed.
*   `jq` is recommended for parsing JSON output (optional).

### Running the Test Script
Run the script from your terminal (Git Bash, WSL, or Linux):

```bash
bash new.sh
```

This script will:
1.  Create a race.
2.  Join two users.
3.  Start the race.
4.  Simulate driving for both users (multiple times to earn points).
5.  Use abilities (Oil Slick and Engine Hack).
6.  Display the final leaderboard.
