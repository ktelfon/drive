#!/bin/bash

BASE_URL="http://localhost:8080/races"
USER1_ID="11111111-1111-1111-1111-111111111111"
USER2_ID="22222222-2222-2222-2222-222222222222"

echo "--- 1. Creating Race (60s duration) ---"
RESPONSE=$(curl -s -X POST "$BASE_URL/" \
  -H "Content-Type: application/json" \
  -d '{"durationInSeconds": 60}')

echo "Response: $RESPONSE"

# Extract raceId
if command -v jq &> /dev/null; then
    RACE_ID=$(echo "$RESPONSE" | jq -r '.raceId')
else
    RACE_ID=$(echo "$RESPONSE" | grep -o '"raceId":"[^"]*' | cut -d'"' -f4)
fi

if [ -z "$RACE_ID" ] || [ "$RACE_ID" == "null" ]; then
    echo "Error: Could not extract raceId. Exiting."
    exit 1
fi

echo "Race ID: $RACE_ID"
echo ""

echo "--- 2. User 1 Joining ---"
curl -s -X POST "$BASE_URL/$RACE_ID/join" -H "X-User-ID: $USER1_ID"
echo -e "\nJoined."

echo "--- 3. User 2 Joining ---"
curl -s -X POST "$BASE_URL/$RACE_ID/join" -H "X-User-ID: $USER2_ID"
echo -e "\nJoined."

echo "--- 4. Starting Race ---"
curl -s -X POST "$BASE_URL/$RACE_ID/start"
echo -e "\nStarted."

echo "--- 5. User 1 Driving (10 times) ---"
for i in {1..10}; do
    echo -n "."
    curl -s -X POST "$BASE_URL/$RACE_ID/drive" -H "X-User-ID: $USER1_ID" > /dev/null
done
echo " Done."

echo "--- 6. User 2 Driving (10 times) ---"
for i in {1..10}; do
    echo -n "."
    curl -s -X POST "$BASE_URL/$RACE_ID/drive" -H "X-User-ID: $USER2_ID" > /dev/null
done
echo " Done."

echo "--- 7. User 1 uses Oil Slick (Cost 10) ---"
curl -s -X POST "$BASE_URL/$RACE_ID/abilities/oil-slick" -H "X-User-ID: $USER1_ID"
echo -e "\nOil Slick used."

echo "--- 8. User 2 uses Engine Hack (Cost 20) ---"
curl -s -X POST "$BASE_URL/$RACE_ID/abilities/engine-hack" -H "X-User-ID: $USER2_ID"
echo -e "\nEngine Hack used."

echo "--- 9. Get Race Details (Leaderboard) ---"
if command -v jq &> /dev/null; then
    curl -s -X GET "$BASE_URL/$RACE_ID" | jq .
else
    curl -s -X GET "$BASE_URL/$RACE_ID"
fi
echo ""

echo "--- Done ---"